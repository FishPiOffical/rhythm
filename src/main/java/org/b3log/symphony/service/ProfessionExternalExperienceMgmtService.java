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

import org.apache.commons.codec.digest.DigestUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.regex.Pattern;

/** 接收金手指的可审计、幂等职业经验写入。 */
@Service
public class ProfessionExternalExperienceMgmtService {

    private static final Pattern APP_ID = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final long MINUTE_MILLIS = 60_000L;

    @Inject private ProfessionSourceEventRepository sourceEventRepository;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private ProfessionRepository professionRepository;
    @Inject private UserQueryService userQueryService;

    public JSONObject grant(final GrantRequest request) throws RepositoryException {
        validate(request);
        final Transaction transaction = sourceEventRepository.beginTransaction();
        try {
            final JSONObject existing = sourceEventRepository.getByExternalRequest(request.sourceAppId(), request.requestId());
            if (null != existing) {
                transaction.commit();
                return receipt(existing, true);
            }
            final long now = System.currentTimeMillis();
            validateRate(request.sourceAppId(), now);
            final JSONObject committed = sourceEventRepository.getByExternalRequest(request.sourceAppId(), request.requestId());
            if (null != committed) {
                transaction.commit();
                return receipt(committed, true);
            }
            final JSONObject user = requireUser(request.userName());
            final JSONObject profession = requireProfession(request.professionId());
            final JSONObject event = event(request, user.getString(Keys.OBJECT_ID), now);
            if (!addEvent(event, request)) {
                transaction.commit();
                return receipt(sourceEventRepository.getByExternalRequest(request.sourceAppId(), request.requestId()), true);
            }
            if (!effectRepository.addIfAbsent(effect(request, event, user, profession, now))) {
                throw new RepositoryException("职业经验效果幂等写入失败");
            }
            transaction.commit();
            return receipt(event, false);
        } catch (final Exception e) {
            rollback(transaction);
            throw exception(e);
        }
    }

    private void validate(final GrantRequest request) {
        if (null == request || !APP_ID.matcher(request.sourceAppId()).matches()
                || !REQUEST_ID.matcher(request.requestId()).matches() || !request.userName().matches("^[a-zA-Z0-9_-]{1,20}$")
                || !request.professionId().matches("^[0-9]{1,19}$") || request.experienceDelta() < 1L
                || request.experienceDelta() > maxExperience() || request.sourceScene().length() > 64
                || (!request.sourceScene().isEmpty() && request.sourceScene().isBlank())) {
            throw new IllegalArgumentException("职业经验请求字段不合法");
        }
    }

    private boolean addEvent(final JSONObject event, final GrantRequest request) throws RepositoryException {
        try {
            sourceEventRepository.add(event);
            return true;
        } catch (final RepositoryException duplicate) {
            final JSONObject existing = sourceEventRepository.getByExternalRequest(request.sourceAppId(), request.requestId());
            if (null != existing) return false;
            throw duplicate;
        }
    }

    private void validateRate(final String sourceAppId, final long now) throws RepositoryException {
        if (sourceEventRepository.countRecentExternalRequestsForUpdate(sourceAppId, now - MINUTE_MILLIS) >= requestsPerMinute()) {
            throw new IllegalStateException("来源应用请求频率超出配置上限");
        }
    }

    private JSONObject requireUser(final String userName) throws RepositoryException {
        final JSONObject user = userQueryService.getUserByName(userName);
        if (null == user) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private JSONObject requireProfession(final String professionId) throws RepositoryException {
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession || !"PUBLISHED".equals(profession.optString(Profession.STATUS))
                || profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID).isBlank()) {
            throw new IllegalArgumentException("职业不存在或尚未完成发布");
        }
        return profession;
    }

    private JSONObject event(final GrantRequest request, final String userId, final long now) {
        final JSONObject event = new JSONObject();
        event.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        event.put(ProfessionSourceEvent.EVENT_KEY, eventKey(request));
        event.put(ProfessionSourceEvent.EVENT_TYPE, "external.profession.experience");
        event.put(ProfessionSourceEvent.SCHEMA_VERSION, 1);
        event.put(ProfessionSourceEvent.SUBJECT_USER_ID, userId);
        event.put(ProfessionSourceEvent.OCCURRED_AT, now);
        event.put(ProfessionSourceEvent.CAPTURED_AT, now);
        event.put(ProfessionSourceEvent.PAYLOAD_JSON, payload(request).toString());
        event.put(ProfessionSourceEvent.VISIBILITY_CLASS, "SELF");
        event.put(ProfessionSourceEvent.SOURCE_SYSTEM, "gold-finger");
        event.put(ProfessionSourceEvent.SOURCE_APP_ID, request.sourceAppId());
        event.put(ProfessionSourceEvent.EXTERNAL_REQUEST_ID, request.requestId());
        event.put(ProfessionSourceEvent.STATUS, "SUCCEEDED");
        event.put(ProfessionSourceEvent.CREATED_AT, now);
        event.put(ProfessionSourceEvent.UPDATED_AT, now);
        return event;
    }

    private JSONObject effect(final GrantRequest request, final JSONObject event, final JSONObject user,
                              final JSONObject profession, final long now) {
        final JSONObject effect = new JSONObject();
        effect.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        effect.put(ProfessionEventEffect.EFFECT_KEY, event.getString(ProfessionSourceEvent.EVENT_KEY) + ":0:external.experience:" + user.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.SOURCE_EVENT_ID, event.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.EVENT_KEY, event.getString(ProfessionSourceEvent.EVENT_KEY));
        effect.put(ProfessionEventEffect.AUTOMATION_REVISION_ID, "0");
        effect.put(ProfessionEventEffect.ACTION_CODE, "external.experience");
        effect.put(ProfessionEventEffect.USER_ID, user.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.PROFESSION_ID, profession.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID, profession.getString(Profession.CURRENT_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID, profession.getString(Profession.CURRENT_LEVEL_SCHEME_ID));
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA, request.experienceDelta());
        effect.put(ProfessionEventEffect.STATUS, "PENDING_APPLY");
        effect.put(ProfessionEventEffect.OCCURRED_AT, now);
        effect.put(ProfessionEventEffect.CREATED_AT, now);
        effect.put(ProfessionEventEffect.UPDATED_AT, now);
        return effect;
    }

    private JSONObject payload(final GrantRequest request) {
        return new JSONObject().put("professionId", request.professionId()).put("experienceDelta", request.experienceDelta())
                .put("sourceScene", request.sourceScene());
    }

    private JSONObject receipt(final JSONObject event, final boolean duplicate) {
        return new JSONObject().put("eventId", event.getString(Keys.OBJECT_ID)).put("accepted", true).put("duplicate", duplicate);
    }

    private String eventKey(final GrantRequest request) { return "external:" + DigestUtils.sha256Hex(request.sourceAppId() + ':' + request.requestId()); }
    private long maxExperience() { return Long.parseLong(Symphonys.get("profession.external.maxExperience")); }
    private int requestsPerMinute() { return Integer.parseInt(Symphonys.get("profession.external.requestsPerMinute")); }
    private RepositoryException exception(final Exception e) { return e instanceof RepositoryException result ? result : new RepositoryException(e); }
    private void rollback(final Transaction transaction) { if (transaction.isActive()) transaction.rollback(); }

    public record GrantRequest(String sourceAppId, String requestId, String sourceScene, String userName,
                               String professionId, long experienceDelta) {
    }
}
