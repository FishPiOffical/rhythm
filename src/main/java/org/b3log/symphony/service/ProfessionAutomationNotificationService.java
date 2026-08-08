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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.ProfessionAutomation;
import org.b3log.symphony.model.ProfessionAutomationRevision;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 依据自动化不可变版本发送幂等职业系统通知。 */
@Service
public class ProfessionAutomationNotificationService {

    private static final String NOTIFICATION_ACTION = "profession.system.notify";
    private static final String MIGRATION_NOTIFICATION_SOURCE = "level-migration-notification";
    private static final String EXTERNAL_AUTOMATION_REVISION_ID = "0";

    private final ConcurrentMap<String, Map<String, NotificationAction>> actionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> professionNameCache = new ConcurrentHashMap<>();

    @Inject private ProfessionAutomationRevisionRepository automationRevisionRepository;
    @Inject private ProfessionAutomationRepository automationRepository;
    @Inject private ProfessionRevisionRepository professionRevisionRepository;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private ProfessionLevelResolver levelResolver;
    @Inject private NotificationMgmtService notificationMgmtService;

    public boolean isNotificationEffect(final JSONObject effect) throws RepositoryException {
        final String revisionId = effect.optString(ProfessionEventEffect.AUTOMATION_REVISION_ID);
        if (revisionId.isBlank() || EXTERNAL_AUTOMATION_REVISION_ID.equals(revisionId)) {
            return false;
        }
        return null != action(effect);
    }

    public void send(final JSONObject effect) throws Exception {
        if (!effect.optString(ProfessionEventEffect.REVERSES_EFFECT_ID).isBlank()) {
            return;
        }
        final NotificationAction action = action(effect);
        if (null == action) {
            return;
        }
        final NotificationContext context = context(effect);
        if ("LEVEL_UP".equals(action.when()) && !context.levelUp()) {
            return;
        }
        final String content = render(action.content(), context);
        if (content.length() > Notification.MAX_LENGTH_C_CUSTOM_SYS_CONTENT) {
            throw new IllegalStateException("职业系统通知内容超过长度限制");
        }
        notificationMgmtService.addSysAnnounceCustomNotification(content,
                effect.getString(ProfessionEventEffect.USER_ID));
    }

    public void sendMigrationNotifications(final JSONObject migrationEffect, final List<JSONObject> levels, final long now)
            throws RepositoryException {
        if (levels.isEmpty()) {
            return;
        }
        try {
            for (final NotificationTarget target : levelUpNotificationTargets(
                    migrationEffect.getString(ProfessionEventEffect.PROFESSION_ID))) {
                for (final JSONObject level : levels) {
                    sendMigrationNotification(migrationEffect, target, level, now);
                }
            }
        } catch (final RepositoryException e) {
            throw e;
        } catch (final Exception e) {
            throw new RepositoryException(e);
        }
    }

    private void sendMigrationNotification(final JSONObject migrationEffect, final NotificationTarget target,
                                           final JSONObject level, final long now) throws Exception {
        final JSONObject effect = migrationNotification(migrationEffect, target, level, now);
        if (!effectRepository.addIfAbsent(effect)) {
            return;
        }
        send(effect);
        effect.put(ProfessionEventEffect.STATUS, "APPLIED");
        effect.put(ProfessionEventEffect.UPDATED_AT, now);
        effectRepository.update(effect.getString(Keys.OBJECT_ID), effect);
    }

    private List<NotificationTarget> levelUpNotificationTargets(final String professionId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionAutomation.PROFESSION_ID,
                FilterOperator.EQUAL, professionId)).addSort(ProfessionAutomation.SORT_ORDER, SortDirection.ASCENDING);
        final List<NotificationTarget> result = new ArrayList<>();
        for (final JSONObject automation : automationRepository.getList(query)) {
            if (!"PUBLISHED".equals(automation.optString(ProfessionAutomation.STATUS))) {
                continue;
            }
            final String revisionId = automation.optString(ProfessionAutomation.CURRENT_REVISION_ID);
            for (final Map.Entry<String, NotificationAction> entry : actions(revisionId).entrySet()) {
                if ("LEVEL_UP".equals(entry.getValue().when())) {
                    result.add(new NotificationTarget(revisionId, entry.getKey()));
                }
            }
        }
        return List.copyOf(result);
    }

    private JSONObject migrationNotification(final JSONObject migration, final NotificationTarget target,
                                             final JSONObject level, final long now) {
        final JSONObject effect = new JSONObject();
        effect.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        effect.put(ProfessionEventEffect.EFFECT_KEY, migration.getString(Keys.OBJECT_ID) + ":notify:"
                + target.revisionId() + ':' + target.actionCode() + ':' + level.getString("levelCode"));
        effect.put(ProfessionEventEffect.SOURCE_EVENT_ID, MIGRATION_NOTIFICATION_SOURCE);
        effect.put(ProfessionEventEffect.EVENT_KEY, migration.getString(Keys.OBJECT_ID));
        effect.put(ProfessionEventEffect.BUCKET_ID, "");
        effect.put(ProfessionEventEffect.BUCKET_KEY, "");
        effect.put(ProfessionEventEffect.AUTOMATION_REVISION_ID, target.revisionId());
        effect.put(ProfessionEventEffect.ACTION_CODE, target.actionCode());
        effect.put(ProfessionEventEffect.USER_ID, migration.getString(ProfessionEventEffect.USER_ID));
        effect.put(ProfessionEventEffect.PROFESSION_ID, migration.getString(ProfessionEventEffect.PROFESSION_ID));
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID, migration.getString(ProfessionEventEffect.PROFESSION_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID, migration.getString(ProfessionEventEffect.LEVEL_SCHEME_ID));
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA, 0L);
        effect.put(ProfessionEventEffect.BEFORE_EXPERIENCE, migration.getLong(ProfessionEventEffect.AFTER_EXPERIENCE));
        effect.put(ProfessionEventEffect.AFTER_EXPERIENCE, migration.getLong(ProfessionEventEffect.AFTER_EXPERIENCE));
        effect.put(ProfessionEventEffect.BEFORE_LEVEL_CODE, migration.getString(ProfessionEventEffect.BEFORE_LEVEL_CODE));
        effect.put(ProfessionEventEffect.AFTER_LEVEL_CODE, level.getString("levelCode"));
        effect.put(ProfessionEventEffect.STATUS, "APPLYING");
        effect.put(ProfessionEventEffect.REVERSES_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.REVERSED_BY_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.OCCURRED_AT, now);
        effect.put(ProfessionEventEffect.CREATED_AT, now);
        effect.put(ProfessionEventEffect.UPDATED_AT, now);
        return effect;
    }

    private NotificationAction action(final JSONObject effect) throws RepositoryException {
        final String revisionId = effect.optString(ProfessionEventEffect.AUTOMATION_REVISION_ID);
        if (revisionId.isBlank() || EXTERNAL_AUTOMATION_REVISION_ID.equals(revisionId)) {
            return null;
        }
        return actions(revisionId).get(effect.optString(ProfessionEventEffect.ACTION_CODE));
    }

    private Map<String, NotificationAction> actions(final String revisionId) throws RepositoryException {
        final Map<String, NotificationAction> cached = actionCache.get(revisionId);
        if (null != cached) {
            return cached;
        }
        final JSONObject revision = automationRevisionRepository.get(revisionId);
        if (null == revision) {
            throw new RepositoryException("自动化版本不存在");
        }
        final Map<String, NotificationAction> loaded = notificationActions(revision);
        final Map<String, NotificationAction> existing = actionCache.putIfAbsent(revisionId, loaded);
        return null == existing ? loaded : existing;
    }

    private Map<String, NotificationAction> notificationActions(final JSONObject revision) {
        final JSONObject configuration = new JSONObject(revision.getString(ProfessionAutomationRevision.CONFIGURATION_JSON));
        final JSONArray values = configuration.getJSONArray("actions");
        final Map<String, NotificationAction> result = new ConcurrentHashMap<>();
        for (int index = 0; index < values.length(); index++) {
            final JSONObject value = values.getJSONObject(index);
            if (NOTIFICATION_ACTION.equals(value.optString("actionType"))) {
                final String code = value.optString("actionCode", NOTIFICATION_ACTION + '.' + index);
                result.put(code, new NotificationAction(value.getString("notificationContent"),
                        value.optString("notificationWhen", "ALWAYS")));
            }
        }
        return Map.copyOf(result);
    }

    private NotificationContext context(final JSONObject effect) throws Exception {
        if (MIGRATION_NOTIFICATION_SOURCE.equals(effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID))) {
            return migrationContext(effect);
        }
        final List<JSONObject> effects = relatedExperienceEffects(effect);
        final long beforeExperience = effects.isEmpty() ? effect.getLong(ProfessionEventEffect.BEFORE_EXPERIENCE)
                : effects.getFirst().getLong(ProfessionEventEffect.BEFORE_EXPERIENCE);
        final long afterExperience = effect.getLong(ProfessionEventEffect.AFTER_EXPERIENCE);
        final String schemeId = effect.getString(ProfessionEventEffect.LEVEL_SCHEME_ID);
        final JSONObject beforeLevel = levelResolver.resolve(schemeId, beforeExperience);
        final JSONObject afterLevel = levelResolver.resolve(schemeId, afterExperience);
        final List<JSONObject> crossed = levelResolver.crossedLevels(schemeId, beforeExperience, afterExperience);
        return new NotificationContext(professionName(effect.getString(ProfessionEventEffect.PROFESSION_REVISION_ID)),
                beforeLevel, afterLevel, beforeExperience, afterExperience, crossed,
                afterExperience > beforeExperience && !beforeLevel.getString("levelCode").equals(afterLevel.getString("levelCode")));
    }

    private NotificationContext migrationContext(final JSONObject effect) throws Exception {
        final String schemeId = effect.getString(ProfessionEventEffect.LEVEL_SCHEME_ID);
        final JSONObject afterLevel = levelResolver.findByCode(schemeId,
                effect.getString(ProfessionEventEffect.AFTER_LEVEL_CODE));
        if (null == afterLevel) {
            throw new RepositoryException("迁移通知等级不存在");
        }
        final long beforeExperience = Math.max(0L, afterLevel.getLong("requiredTotalExperience") - 1L);
        final JSONObject beforeLevel = levelResolver.resolve(schemeId, beforeExperience);
        return new NotificationContext(professionName(effect.getString(ProfessionEventEffect.PROFESSION_REVISION_ID)),
                beforeLevel, afterLevel, effect.getLong(ProfessionEventEffect.BEFORE_EXPERIENCE),
                effect.getLong(ProfessionEventEffect.AFTER_EXPERIENCE), List.of(afterLevel), true);
    }

    private List<JSONObject> relatedExperienceEffects(final JSONObject effect) throws RepositoryException {
        final List<JSONObject> result = new ArrayList<>();
        for (final JSONObject item : effectRepository.getByEventKey(effect.getString(ProfessionEventEffect.EVENT_KEY))) {
            if (sameAutomationEffect(effect, item) && item.optLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA) != 0L) {
                result.add(item);
            }
        }
        result.sort(Comparator.<JSONObject>comparingLong(item -> item.getLong(ProfessionEventEffect.CREATED_AT))
                .thenComparing(item -> item.getString(Keys.OBJECT_ID)));
        return List.copyOf(result);
    }

    private boolean sameAutomationEffect(final JSONObject effect, final JSONObject candidate) {
        return effect.getString(ProfessionEventEffect.SOURCE_EVENT_ID).equals(candidate.optString(ProfessionEventEffect.SOURCE_EVENT_ID))
                && effect.getString(ProfessionEventEffect.AUTOMATION_REVISION_ID)
                .equals(candidate.optString(ProfessionEventEffect.AUTOMATION_REVISION_ID))
                && effect.getString(ProfessionEventEffect.USER_ID).equals(candidate.optString(ProfessionEventEffect.USER_ID));
    }

    private String professionName(final String revisionId) throws RepositoryException {
        final String cached = professionNameCache.get(revisionId);
        if (null != cached) {
            return cached;
        }
        final JSONObject revision = professionRevisionRepository.get(revisionId);
        if (null == revision) {
            throw new RepositoryException("职业版本不存在");
        }
        final String name = revision.getString(ProfessionRevision.DISPLAY_NAME);
        final String existing = professionNameCache.putIfAbsent(revisionId, name);
        return null == existing ? name : existing;
    }

    private String render(final String template, final NotificationContext context) {
        return template.replace("{职业}", context.professionName()).replace("{等级}", context.afterLevel().getString("displayName"))
                .replace("{经验}", String.valueOf(context.afterExperience()))
                .replace("{变化}", String.valueOf(context.afterExperience() - context.beforeExperience()))
                .replace("{奖励}", rewards(context.crossedLevels()));
    }

    private String rewards(final List<JSONObject> levels) {
        final List<String> result = new ArrayList<>();
        for (final JSONObject level : levels) {
            for (final Object item : level.getJSONArray("rewards")) {
                final JSONObject reward = (JSONObject) item;
                final JSONObject config = new JSONObject(reward.getString("rewardConfigJson"));
                result.add("POINT".equals(reward.getString("rewardType")) ? "+" + config.getInt("amount") + "积分"
                        : "勋章 " + config.optString("medalId"));
            }
        }
        return result.isEmpty() ? "无" : String.join("、", result);
    }

    private record NotificationAction(String content, String when) {
    }

    private record NotificationTarget(String revisionId, String actionCode) {
    }

    private record NotificationContext(String professionName, JSONObject beforeLevel, JSONObject afterLevel,
                                       long beforeExperience, long afterExperience, List<JSONObject> crossedLevels,
                                       boolean levelChanged) {
        private boolean levelUp() {
            return levelChanged;
        }
    }
}
