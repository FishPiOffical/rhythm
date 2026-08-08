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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Notification;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 职业自动化可发布组件注册表。 */
@Service
public class ProfessionAutomationRegistry {

    private static final Set<String> NODE_TYPES = Set.of("ALL", "ANY", "NOT", "LEAF");
    private static final String EXPERIENCE_ACTION = "profession.experience.adjust";
    private static final String NOTIFICATION_ACTION = "profession.system.notify";
    private static final String CONTRIBUTION_ACTION = "profession.contribution.record";
    private static final Set<String> ACTION_TYPES = Set.of(EXPERIENCE_ACTION, NOTIFICATION_ACTION, CONTRIBUTION_ACTION);
    private static final Set<String> NOTIFICATION_WHEN = Set.of("ALWAYS", "LEVEL_UP");
    private static final int MAX_CONDITION_DEPTH = 16;
    private static final int MAX_CONDITION_CHILDREN = 64;
    private static final int MAX_ACTIONS = 64;
    private static final Map<String, ProfessionAutomationTriggerDefinition> TRIGGERS = Map.of(
            "long_article.read_settled", ProfessionAutomationTriggerDefinition.create(1, "settlementId", ProfessionAutomationFieldType.TEXT, "articleId", ProfessionAutomationFieldType.TEXT,
                    "authorUserId", ProfessionAutomationFieldType.TEXT, "windowStart", ProfessionAutomationFieldType.NUMBER, "windowEnd", ProfessionAutomationFieldType.NUMBER,
                    "registeredReaderCount", ProfessionAutomationFieldType.NUMBER, "anonymousReaderCount", ProfessionAutomationFieldType.NUMBER,
                    "countedAnonymousReaderCount", ProfessionAutomationFieldType.NUMBER, "rewardPoint", ProfessionAutomationFieldType.NUMBER),
            "article.published", ProfessionAutomationTriggerDefinition.create(1, "articleId", ProfessionAutomationFieldType.TEXT, "authorUserId", ProfessionAutomationFieldType.TEXT,
                    "articleType", ProfessionAutomationFieldType.NUMBER, "tagCount", ProfessionAutomationFieldType.NUMBER, "wordCount", ProfessionAutomationFieldType.NUMBER,
                    "publishedAt", ProfessionAutomationFieldType.TIME),
            "comment.published", ProfessionAutomationTriggerDefinition.create(1, "commentId", ProfessionAutomationFieldType.TEXT, "articleId", ProfessionAutomationFieldType.TEXT,
                    "authorUserId", ProfessionAutomationFieldType.TEXT, "parentCommentId", ProfessionAutomationFieldType.TEXT, "publishedAt", ProfessionAutomationFieldType.TIME),
            "breezemoon.published", ProfessionAutomationTriggerDefinition.create(1, "breezemoonId", ProfessionAutomationFieldType.TEXT, "authorUserId", ProfessionAutomationFieldType.TEXT,
                    "publishedAt", ProfessionAutomationFieldType.TIME),
            "repeater.published", ProfessionAutomationTriggerDefinition.create(1, "contentId", ProfessionAutomationFieldType.TEXT, "authorUserId", ProfessionAutomationFieldType.TEXT,
                    "contentType", ProfessionAutomationFieldType.TEXT, "publishedAt", ProfessionAutomationFieldType.TIME),
            "chatroom.message.published", ProfessionAutomationTriggerDefinition.create(1, "messageId", ProfessionAutomationFieldType.TEXT,
                    "authorUserId", ProfessionAutomationFieldType.TEXT, "messageType", ProfessionAutomationFieldType.TEXT,
                    "contentLength", ProfessionAutomationFieldType.NUMBER, "sentAt", ProfessionAutomationFieldType.TIME),
            "user.online.settled", ProfessionAutomationTriggerDefinition.create(1, "userId", ProfessionAutomationFieldType.TEXT,
                    "onlineMinuteBefore", ProfessionAutomationFieldType.NUMBER, "onlineMinuteAfter", ProfessionAutomationFieldType.NUMBER,
                    "onlineMinuteDelta", ProfessionAutomationFieldType.NUMBER, "settledAt", ProfessionAutomationFieldType.TIME));

    @Inject private ProfessionAutomationCalculator calculator;

    public void validate(final JSONObject configuration) {
        final ProfessionAutomationTriggerDefinition trigger = validateTrigger(requiredObject(configuration, "trigger"));
        validateCondition(configuration.optJSONObject("condition"), trigger, 0);
        validateActions(requiredArray(configuration, "actions"), trigger);
    }

    public boolean matches(final JSONObject condition, final JSONObject payload) {
        return matches(condition, payload, 0);
    }

    private boolean matches(final JSONObject condition, final JSONObject payload, final int depth) {
        if (null == condition) {
            return true;
        }
        if (depth > MAX_CONDITION_DEPTH) {
            throw new IllegalArgumentException("条件层级过深");
        }
        return switch (condition.getString("type")) {
            case "ALL" -> childrenMatch(condition.getJSONArray("children"), payload, true, depth);
            case "ANY" -> childrenMatch(condition.getJSONArray("children"), payload, false, depth);
            case "NOT" -> !matches(condition.getJSONObject("child"), payload, depth + 1);
            case "LEAF" -> leafMatches(condition, payload);
            default -> throw new IllegalArgumentException("未注册的条件类型");
        };
    }

    public long calculate(final JSONObject calculator, final JSONObject payload) {
        return this.calculator.calculate(calculator, payload);
    }

    public JSONObject editorMetadata() {
        final JSONObject result = new JSONObject();
        result.put("triggers", triggerMetadata());
        result.put("conditionTypes", sorted(NODE_TYPES));
        result.put("calculatorTypes", calculator.types());
        result.put("actions", new JSONArray()
                .put(new JSONObject().put("actionType", EXPERIENCE_ACTION).put("actionCode", "experience")
                        .put("configType", "CALCULATOR").put("displayName", "增加职业经验"))
                .put(new JSONObject().put("actionType", NOTIFICATION_ACTION).put("actionCode", "notification")
                        .put("configType", "NOTIFICATION").put("displayName", "发送系统通知"))
                .put(new JSONObject().put("actionType", CONTRIBUTION_ACTION).put("actionCode", "contribution")
                        .put("configType", "CONTRIBUTION").put("displayName", "记录贡献")));
        return result;
    }

    public boolean isExperienceAction(final JSONObject action) {
        return EXPERIENCE_ACTION.equals(action.optString("actionType"));
    }

    public boolean isNotificationAction(final JSONObject action) {
        return NOTIFICATION_ACTION.equals(action.optString("actionType"));
    }

    public boolean isContributionAction(final JSONObject action) {
        return CONTRIBUTION_ACTION.equals(action.optString("actionType"));
    }

    private boolean childrenMatch(final JSONArray children, final JSONObject payload, final boolean all, final int depth) {
        if (children.length() > MAX_CONDITION_CHILDREN) {
            throw new IllegalArgumentException("条件组项目过多");
        }
        for (final Object child : children) {
            if (matches((JSONObject) child, payload, depth + 1) != all) {
                return !all;
            }
        }
        return all;
    }

    private boolean leafMatches(final JSONObject condition, final JSONObject payload) {
        final Object actual = payload.opt(condition.getString("fieldCode"));
        return switch (condition.getString("operator")) {
            case "EQ" -> equalsValue(actual, condition.opt("value"));
            case "NE" -> !equalsValue(actual, condition.opt("value"));
            case "GT" -> number(actual) > number(condition.get("value"));
            case "GTE" -> number(actual) >= number(condition.get("value"));
            case "LT" -> number(actual) < number(condition.get("value"));
            case "LTE" -> number(actual) <= number(condition.get("value"));
            case "BETWEEN" -> number(actual) >= condition.getLong("min") && number(actual) <= condition.getLong("max");
            case "IN" -> contains(condition.getJSONArray("values"), actual);
            case "PREFIX" -> String.valueOf(actual).startsWith(condition.getString("value"));
            case "SUFFIX" -> String.valueOf(actual).endsWith(condition.getString("value"));
            case "CONTAINS" -> String.valueOf(actual).contains(condition.getString("value"));
            case "IS_EMPTY" -> null == actual || String.valueOf(actual).isBlank();
            case "IS_TRUE" -> Boolean.TRUE.equals(actual);
            case "IS_FALSE" -> Boolean.FALSE.equals(actual);
            case "IN_WINDOW" -> number(actual) >= condition.getLong("from") && number(actual) <= condition.getLong("to");
            case "OLDER_THAN" -> number(actual) < condition.getLong("time");
            case "NEWER_THAN" -> number(actual) > condition.getLong("time");
            default -> throw new IllegalArgumentException("未注册的条件操作符");
        };
    }

    private boolean contains(final JSONArray values, final Object actual) {
        for (final Object value : values) {
            if (equalsValue(actual, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsValue(final Object actual, final Object expected) {
        return actual instanceof Number && expected instanceof Number
                ? number(actual) == number(expected) : String.valueOf(actual).equals(String.valueOf(expected));
    }

    private long number(final Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("条件数值类型不匹配");
    }

    private JSONArray triggerMetadata() {
        final JSONArray result = new JSONArray();
        TRIGGERS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            final JSONObject trigger = new JSONObject().put("triggerType", entry.getKey())
                    .put("schemaVersion", entry.getValue().schemaVersion());
            final JSONArray fields = new JSONArray();
            entry.getValue().fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(field ->
                    fields.put(new JSONObject().put("fieldCode", field.getKey()).put("fieldType", field.getValue().name())
                            .put("operators", sorted(field.getValue().operators()))));
            result.put(trigger.put("fields", fields));
        });
        return result;
    }

    private JSONArray sorted(final Set<String> values) {
        final JSONArray result = new JSONArray();
        values.stream().sorted().forEach(result::put);
        return result;
    }


    private ProfessionAutomationTriggerDefinition validateTrigger(final JSONObject trigger) {
        final ProfessionAutomationTriggerDefinition definition = TRIGGERS.get(trigger.optString("triggerType"));
        if (null == definition || trigger.optInt("schemaVersion") != definition.schemaVersion()) {
            throw new IllegalArgumentException("未注册或不兼容的触发器");
        }
        return definition;
    }

    private void validateCondition(final JSONObject condition, final ProfessionAutomationTriggerDefinition trigger,
                                   final int depth) {
        if (null == condition) {
            return;
        }
        if (depth > MAX_CONDITION_DEPTH) {
            throw new IllegalArgumentException("条件层级过深");
        }
        final String type = condition.optString("type");
        if (!NODE_TYPES.contains(type)) {
            throw new IllegalArgumentException("未注册的条件类型");
        }
        if ("LEAF".equals(type)) {
            validateLeaf(condition, trigger);
            return;
        }
        if ("NOT".equals(type)) {
            validateCondition(requiredObject(condition, "child"), trigger, depth + 1);
            return;
        }
        final JSONArray children = requiredArray(condition, "children");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("条件组不能为空");
        }
        if (children.length() > MAX_CONDITION_CHILDREN) {
            throw new IllegalArgumentException("条件组项目过多");
        }
        for (final Object child : children) {
            validateCondition((JSONObject) child, trigger, depth + 1);
        }
    }

    private void validateLeaf(final JSONObject condition, final ProfessionAutomationTriggerDefinition trigger) {
        final ProfessionAutomationFieldType type = trigger.fields().get(condition.optString("fieldCode"));
        if (null == type) {
            throw new IllegalArgumentException("条件引用了触发器未声明字段");
        }
        final String operator = condition.optString("operator");
        if (!type.operators().contains(operator)) {
            throw new IllegalArgumentException("条件操作符与字段类型不兼容");
        }
    }

    private void validateActions(final JSONArray actions, final ProfessionAutomationTriggerDefinition trigger) {
        if (actions.isEmpty() || actions.length() > MAX_ACTIONS) {
            throw new IllegalArgumentException("自动化至少需要一个执行器");
        }
        final Set<String> actionCodes = new HashSet<>();
        for (int index = 0; index < actions.length(); index++) {
            final JSONObject action = actions.getJSONObject(index);
            if (!ACTION_TYPES.contains(action.optString("actionType"))) {
                throw new IllegalArgumentException("执行器尚不可发布");
            }
            final String actionCode = action.optString("actionCode", action.getString("actionType") + '.' + index);
            if (!actionCode.matches("^[a-z][a-z0-9._-]{0,63}$") || !actionCodes.add(actionCode)) {
                throw new IllegalArgumentException("执行器编码不合法");
            }
            validateAction(action, trigger);
        }
    }

    private void validateAction(final JSONObject action, final ProfessionAutomationTriggerDefinition trigger) {
        if (isExperienceAction(action)) {
            validateExperienceAction(action, trigger);
            return;
        }
        if (isContributionAction(action)) {
            return;
        }
        final String content = action.optString("notificationContent");
        if (content.isBlank() || content.length() > Notification.MAX_LENGTH_C_CUSTOM_SYS_CONTENT
                || !NOTIFICATION_WHEN.contains(action.optString("notificationWhen", "ALWAYS"))) {
            throw new IllegalArgumentException("系统通知配置不合法");
        }
    }

    private void validateExperienceAction(final JSONObject action, final ProfessionAutomationTriggerDefinition trigger) {
        final JSONObject calculator = action.optJSONObject("calculator");
        if (null == calculator && !action.has("experienceDelta")) {
            throw new IllegalArgumentException("经验执行器缺少经验值");
        }
        if (null != calculator) {
            this.calculator.validate(calculator, trigger);
        }
    }

    private JSONObject requiredObject(final JSONObject source, final String key) {
        final JSONObject value = source.optJSONObject(key);
        if (null == value) {
            throw new IllegalArgumentException("自动化缺少必填对象");
        }
        return value;
    }

    private JSONArray requiredArray(final JSONObject source, final String key) {
        final JSONArray value = source.optJSONArray(key);
        if (null == value) {
            throw new IllegalArgumentException("自动化缺少必填数组");
        }
        return value;
    }

}
