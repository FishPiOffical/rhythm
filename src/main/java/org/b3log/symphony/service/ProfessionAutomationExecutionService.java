/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionAutomationRevision;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionExecutionState;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 消费职业来源事件并创建可幂等应用的职业效果。 */
@Service
public class ProfessionAutomationExecutionService {

    private static final int BATCH_SIZE = 100;
    private static final long RETRY_DELAY_MILLIS = 60_000L;

    @Inject
    private ProfessionSourceEventRepository sourceEventRepository;
    @Inject
    private ProfessionAutomationRevisionRepository revisionRepository;
    @Inject
    private ProfessionRepository professionRepository;
    @Inject
    private ProfessionEventEffectRepository effectRepository;
    @Inject
    private ProfessionAutomationRegistry registry;
    @Inject
    private ProfessionSourceEventExecutionService sourceEventExecutionService;

    public void processPendingEvents() {
        try {
            for (final JSONObject event : sourceEventExecutionService.getReady(BATCH_SIZE)) {
                processEvent(event.optString(Keys.OBJECT_ID));
            }
        } catch (final Exception e) {
            throw new IllegalStateException("读取待处理职业事件失败", e);
        }
    }

    public void replay(final String eventId) {
        if (null == eventId || eventId.isBlank() || eventId.length() > 19) {
            throw new IllegalArgumentException("职业来源事件标识不合法");
        }
        final Transaction transaction = sourceEventRepository.beginTransaction();
        try {
            if (!sourceEventRepository.requeue(eventId, System.currentTimeMillis())) {
                throw new IllegalArgumentException("职业来源事件不存在");
            }
            transaction.commit();
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("职业来源事件回放失败", e);
        }
    }

    private void processEvent(final String eventId) {
        final ProfessionSourceEventExecutionService.Claim claim = sourceEventExecutionService.claim(eventId);
        if (null == claim) {
            return;
        }
        final Transaction transaction = sourceEventRepository.beginTransaction();
        try {
            final JSONObject event = sourceEventExecutionService.lockClaimed(claim);
            if (null == event) {
                transaction.commit();
                return;
            }
            final ExecutionContext context = context(event, claim.claimedAt(), System.currentTimeMillis());
            if (waitsForOriginal(context)) {
                transaction.commit();
                return;
            }
            if (context.isReversal()) {
                createReversalEffects(context);
            } else {
                createPositiveEffects(context);
            }
            succeed(context);
            transaction.commit();
        } catch (final Exception e) {
            retry(claim, transaction, e);
        }
    }

    private boolean waitsForOriginal(final ExecutionContext context) throws Exception {
        if (!context.isReversal()) {
            return false;
        }
        final JSONObject original = sourceEventRepository.getByEventKey(context.reversalOfEventKey());
        if (null != original && "SUCCEEDED".equals(original.optString(ProfessionSourceEvent.STATUS))) {
            return false;
        }
        sourceEventExecutionService.updateState(state(context, new StateRequest("WAITING_ORIGINAL",
                context.now() + RETRY_DELAY_MILLIS, "ORIGINAL_EVENT_PENDING", "等待原事件完成")));
        return true;
    }

    private void createPositiveEffects(final ExecutionContext context) throws Exception {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionAutomationRevision.TRIGGER_TYPE,
                FilterOperator.EQUAL, context.eventType()));
        for (final JSONObject revision : revisionRepository.getList(query)) {
            if ("PUBLISHED".equals(revision.optString(ProfessionAutomationRevision.STATUS))) {
                createRevisionEffects(context, revision);
            }
        }
    }

    private void createRevisionEffects(final ExecutionContext context, final JSONObject revision) throws Exception {
        final JSONObject configuration = new JSONObject(revision.getString(ProfessionAutomationRevision.CONFIGURATION_JSON));
        if (!registry.matches(configuration.optJSONObject("condition"), context.payload())) {
            return;
        }
        final JSONObject profession = requireProfession(revision);
        final JSONArray actions = configuration.getJSONArray("actions");
        int sequence = createActionEffects(context, revision, profession, actions, true, 0);
        createActionEffects(context, revision, profession, actions, false, sequence);
    }

    private int createActionEffects(final ExecutionContext context, final JSONObject revision, final JSONObject profession,
                                    final JSONArray actions, final boolean experience, final int start) throws Exception {
        int sequence = start;
        for (int index = 0; index < actions.length(); index++) {
            final JSONObject action = actions.getJSONObject(index);
            if (registry.isExperienceAction(action) != experience) {
                continue;
            }
            createPositiveEffect(new EffectRequest(context, revision, profession, action,
                    action.optString("actionCode", action.getString("actionType") + '.' + index), sequence++));
        }
        return sequence;
    }

    private JSONObject requireProfession(final JSONObject revision) throws Exception {
        final JSONObject profession = professionRepository.get(revision.getString(ProfessionAutomationRevision.PROFESSION_ID));
        if (null == profession) {
            throw new IllegalStateException("自动化所属职业不存在");
        }
        return profession;
    }

    private void createPositiveEffect(final EffectRequest request) throws Exception {
        final long delta = registry.isExperienceAction(request.action())
                ? experienceDelta(request.action(), request.context().payload()) : 0L;
        effectRepository.addIfAbsent(effect(request, delta));
    }

    private long experienceDelta(final JSONObject action, final JSONObject payload) {
        final JSONObject calculator = action.optJSONObject("calculator");
        return null == calculator ? action.getLong("experienceDelta") : registry.calculate(calculator, payload);
    }

    private void createReversalEffects(final ExecutionContext context) throws Exception {
        int sequence = 0;
        for (final JSONObject original : effectRepository.getByEventKey(context.reversalOfEventKey())) {
            final String reversalEffectId = Ids.genTimeMillisId();
            if (!effectRepository.markReversed(original.getString(Keys.OBJECT_ID), reversalEffectId, context.now())) {
                continue;
            }
            effectRepository.addIfAbsent(reversalEffect(context, original, reversalEffectId, sequence++));
        }
    }

    private JSONObject reversalEffect(final ExecutionContext context, final JSONObject original, final String effectId,
                                     final int sequence) {
        final JSONObject effect = new JSONObject();
        effect.put(Keys.OBJECT_ID, effectId);
        effect.put(ProfessionEventEffect.EFFECT_KEY, effectKey(context, original));
        effect.put(ProfessionEventEffect.SOURCE_EVENT_ID, context.eventId());
        effect.put(ProfessionEventEffect.EVENT_KEY, context.eventKey());
        effect.put(ProfessionEventEffect.BUCKET_ID, original.optString(ProfessionEventEffect.BUCKET_ID));
        effect.put(ProfessionEventEffect.BUCKET_KEY, original.optString(ProfessionEventEffect.BUCKET_KEY));
        effect.put(ProfessionEventEffect.AUTOMATION_REVISION_ID, original.getString(ProfessionEventEffect.AUTOMATION_REVISION_ID));
        effect.put(ProfessionEventEffect.ACTION_CODE, original.getString(ProfessionEventEffect.ACTION_CODE));
        effect.put(ProfessionEventEffect.USER_ID, original.getString(ProfessionEventEffect.USER_ID));
        effect.put(ProfessionEventEffect.PROFESSION_ID, original.getString(ProfessionEventEffect.PROFESSION_ID));
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID, original.getString(ProfessionEventEffect.PROFESSION_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID, original.getString(ProfessionEventEffect.LEVEL_SCHEME_ID));
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA, Math.negateExact(original.getLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA)));
        effect.put(ProfessionEventEffect.BEFORE_EXPERIENCE, 0L);
        effect.put(ProfessionEventEffect.AFTER_EXPERIENCE, 0L);
        effect.put(ProfessionEventEffect.BEFORE_LEVEL_CODE, "");
        effect.put(ProfessionEventEffect.AFTER_LEVEL_CODE, "");
        effect.put(ProfessionEventEffect.STATUS, "PENDING_APPLY");
        effect.put(ProfessionEventEffect.REVERSES_EFFECT_ID, original.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.REVERSED_BY_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.OCCURRED_AT, context.occurredAt());
        effect.put(ProfessionEventEffect.CREATED_AT, Math.addExact(context.now(), sequence));
        effect.put(ProfessionEventEffect.UPDATED_AT, context.now());
        return effect;
    }

    private JSONObject effect(final EffectRequest request, final long delta) {
        final ExecutionContext context = request.context();
        final JSONObject revision = request.revision();
        final JSONObject profession = request.profession();
        final JSONObject effect = new JSONObject();
        effect.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        effect.put(ProfessionEventEffect.EFFECT_KEY, context.eventKey() + ':' + revision.getString(Keys.OBJECT_ID) + ':'
                + request.actionCode() + ':' + context.subjectUserId());
        effect.put(ProfessionEventEffect.SOURCE_EVENT_ID, context.eventId());
        effect.put(ProfessionEventEffect.EVENT_KEY, context.eventKey());
        effect.put(ProfessionEventEffect.BUCKET_ID, "");
        effect.put(ProfessionEventEffect.BUCKET_KEY, "");
        effect.put(ProfessionEventEffect.AUTOMATION_REVISION_ID, revision.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.ACTION_CODE, request.actionCode());
        effect.put(ProfessionEventEffect.USER_ID, context.subjectUserId());
        effect.put(ProfessionEventEffect.PROFESSION_ID, profession.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID, profession.optString(Profession.CURRENT_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID, profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID));
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA, delta);
        effect.put(ProfessionEventEffect.BEFORE_EXPERIENCE, 0L);
        effect.put(ProfessionEventEffect.AFTER_EXPERIENCE, 0L);
        effect.put(ProfessionEventEffect.BEFORE_LEVEL_CODE, "");
        effect.put(ProfessionEventEffect.AFTER_LEVEL_CODE, "");
        effect.put(ProfessionEventEffect.STATUS, "PENDING_APPLY");
        effect.put(ProfessionEventEffect.REVERSES_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.REVERSED_BY_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.OCCURRED_AT, context.occurredAt());
        effect.put(ProfessionEventEffect.CREATED_AT, context.now());
        effect.put(ProfessionEventEffect.UPDATED_AT, context.now());
        return effect;
    }

    private String effectKey(final ExecutionContext context, final JSONObject original) {
        return context.eventKey() + ':' + original.getString(ProfessionEventEffect.AUTOMATION_REVISION_ID) + ':'
                + original.getString(ProfessionEventEffect.ACTION_CODE) + ':' + original.getString(ProfessionEventEffect.USER_ID);
    }

    private void succeed(final ExecutionContext context) throws Exception {
        sourceEventExecutionService.updateState(state(context, new StateRequest("SUCCEEDED", 0L, "", "")));
    }

    private ProfessionExecutionState state(final ExecutionContext context, final StateRequest request) {
        return new ProfessionExecutionState(context.eventId(), request.status(), request.nextRetryAt(),
                request.errorCode(), request.errorMessage(), context.claimedAt(), context.now());
    }

    private void retry(final ProfessionSourceEventExecutionService.Claim claim, final Transaction transaction,
                       final Exception exception) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
        sourceEventExecutionService.recordFailure(claim, exception);
    }

    private ExecutionContext context(final JSONObject event, final long claimedAt, final long now) {
        return new ExecutionContext(event.getString(Keys.OBJECT_ID), event.getString(ProfessionSourceEvent.EVENT_KEY),
                event.getString(ProfessionSourceEvent.EVENT_TYPE), event.getString(ProfessionSourceEvent.SUBJECT_USER_ID),
                event.optString(ProfessionSourceEvent.REVERSAL_OF_EVENT_KEY), event.getLong(ProfessionSourceEvent.OCCURRED_AT),
                new JSONObject(event.getString(ProfessionSourceEvent.PAYLOAD_JSON)), claimedAt, now);
    }

    private record ExecutionContext(String eventId, String eventKey, String eventType, String subjectUserId,
                                    String reversalOfEventKey, long occurredAt, JSONObject payload,
                                    long claimedAt, long now) {
        private boolean isReversal() {
            return !reversalOfEventKey.isBlank();
        }
    }

    private record EffectRequest(ExecutionContext context, JSONObject revision, JSONObject profession, JSONObject action,
                                 String actionCode, int sequence) {
    }

    private record StateRequest(String status, long nextRetryAt, String errorCode, String errorMessage) {
    }
}
