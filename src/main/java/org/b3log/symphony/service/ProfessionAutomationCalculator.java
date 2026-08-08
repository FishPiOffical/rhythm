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

import org.b3log.latke.service.annotation.Service;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

/** 校验并计算自动化经验表达式。 */
@Service
public class ProfessionAutomationCalculator {

    private static final Set<String> TYPES = Set.of("FIXED", "FIELD", "ADD", "SUBTRACT", "MULTIPLY",
            "DIVIDE", "MIN", "MAX", "CLAMP", "TIERED", "WEIGHTED_SUM");
    private static final int MAX_DEPTH = 16;
    private static final int MAX_COLLECTION_ITEMS = 128;

    public long calculate(final JSONObject calculator, final JSONObject payload) {
        return calculate(calculator, payload, 0);
    }

    private long calculate(final JSONObject calculator, final JSONObject payload, final int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("职业计算器层级过深");
        }
        return switch (calculator.getString("calculatorType")) {
            case "FIXED" -> calculator.getLong("value");
            case "FIELD" -> number(payload.get(calculator.getString("fieldCode")));
            case "ADD" -> binary(calculator, payload, "+", depth);
            case "SUBTRACT" -> binary(calculator, payload, "-", depth);
            case "MULTIPLY" -> binary(calculator, payload, "*", depth);
            case "DIVIDE" -> divide(calculator, payload, depth);
            case "MIN" -> Math.min(value(calculator, "left", payload, depth), value(calculator, "right", payload, depth));
            case "MAX" -> Math.max(value(calculator, "left", payload, depth), value(calculator, "right", payload, depth));
            case "CLAMP" -> clamp(calculator, payload, depth);
            case "TIERED" -> tiered(calculator, payload, depth);
            case "WEIGHTED_SUM" -> weightedSum(calculator.getJSONArray("items"), payload, depth);
            default -> throw new IllegalArgumentException("未注册的计算器类型");
        };
    }

    public void validate(final JSONObject calculator, final ProfessionAutomationTriggerDefinition trigger) {
        validate(calculator, trigger, 0);
    }

    private void validate(final JSONObject calculator, final ProfessionAutomationTriggerDefinition trigger,
                          final int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("职业计算器层级过深");
        }
        final String type = calculator.optString("calculatorType");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("未注册的计算器类型");
        validateField(calculator, trigger, type);
        validateShape(calculator, trigger, type, depth);
    }

    public JSONArray types() {
        final JSONArray result = new JSONArray();
        TYPES.stream().sorted().forEach(result::put);
        return result;
    }

    private long binary(final JSONObject value, final JSONObject payload, final String operator, final int depth) {
        final long left = value(value, "left", payload, depth + 1);
        final long right = value(value, "right", payload, depth + 1);
        return switch (operator) {
            case "+" -> Math.addExact(left, right);
            case "-" -> Math.subtractExact(left, right);
            default -> Math.multiplyExact(left, right);
        };
    }

    private long divide(final JSONObject calculator, final JSONObject payload, final int depth) {
        final long divisor = value(calculator, "right", payload, depth + 1);
        if (divisor == 0L) {
            return calculator.getLong("zeroValue");
        }
        final long dividend = value(calculator, "left", payload, depth + 1);
        if (dividend == Long.MIN_VALUE && divisor == -1L) {
            throw new ArithmeticException("职业计算器除法结果超出范围");
        }
        return dividend / divisor;
    }

    private long clamp(final JSONObject calculator, final JSONObject payload, final int depth) {
        return Math.max(calculator.getLong("min"), Math.min(calculator.getLong("max"), value(calculator, "value", payload, depth + 1)));
    }

    private long tiered(final JSONObject calculator, final JSONObject payload, final int depth) {
        final long input = value(calculator, "input", payload, depth + 1);
        final JSONArray tiers = calculator.getJSONArray("tiers");
        if (tiers.length() > MAX_COLLECTION_ITEMS) {
            throw new IllegalArgumentException("阶梯计算器项目过多");
        }
        long result = calculator.getLong("defaultValue");
        for (final Object item : tiers) {
            final JSONObject tier = (JSONObject) item;
            if (input >= tier.getLong("minInclusive")) result = tier.getLong("value");
        }
        return result;
    }

    private long weightedSum(final JSONArray items, final JSONObject payload, final int depth) {
        if (items.length() > MAX_COLLECTION_ITEMS) {
            throw new IllegalArgumentException("加权计算器项目过多");
        }
        long result = 0L;
        for (final Object item : items) {
            final JSONObject weighted = (JSONObject) item;
            result = Math.addExact(result,
                    Math.multiplyExact(value(weighted, "value", payload, depth + 1), weighted.getLong("weight")));
        }
        return result;
    }

    private long value(final JSONObject calculator, final String key, final JSONObject payload, final int depth) {
        return calculate(calculator.getJSONObject(key), payload, depth);
    }

    private long number(final Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("条件数值类型不匹配");
    }

    private void validateField(final JSONObject value, final ProfessionAutomationTriggerDefinition trigger, final String type) {
        if ("FIELD".equals(type) && trigger.fields().get(value.optString("fieldCode")) != ProfessionAutomationFieldType.NUMBER) {
            throw new IllegalArgumentException("计算器只能读取数值字段");
        }
        if ("FIXED".equals(type)) value.getLong("value");
    }

    private void validateShape(final JSONObject value, final ProfessionAutomationTriggerDefinition trigger,
                               final String type, final int depth) {
        if (Set.of("ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "MIN", "MAX").contains(type)) {
            validatePair(value, trigger, depth);
        }
        if (Set.of("CLAMP", "TIERED").contains(type)) {
            validate(value.getJSONObject("CLAMP".equals(type) ? "value" : "input"), trigger, depth + 1);
        }
        if ("CLAMP".equals(type) && value.getLong("min") > value.getLong("max")) throw new IllegalArgumentException("上下限计算器范围不合法");
        if ("DIVIDE".equals(type) && !value.has("zeroValue")) throw new IllegalArgumentException("除法计算器缺少除零值");
        if ("TIERED".equals(type)) validateTiers(value);
        if ("WEIGHTED_SUM".equals(type)) {
            final JSONArray items = value.getJSONArray("items");
            if (items.isEmpty() || items.length() > MAX_COLLECTION_ITEMS) {
                throw new IllegalArgumentException("加权计算器项目数量不合法");
            }
            for (final Object item : items) validate(((JSONObject) item).getJSONObject("value"), trigger, depth + 1);
        }
    }

    private void validatePair(final JSONObject value, final ProfessionAutomationTriggerDefinition trigger,
                              final int depth) {
        validate(value.getJSONObject("left"), trigger, depth + 1);
        validate(value.getJSONObject("right"), trigger, depth + 1);
    }

    private void validateTiers(final JSONObject value) {
        value.getLong("defaultValue");
        if (value.getJSONArray("tiers").length() > MAX_COLLECTION_ITEMS) {
            throw new IllegalArgumentException("阶梯计算器项目过多");
        }
        long previous = Long.MIN_VALUE;
        for (final Object item : value.getJSONArray("tiers")) {
            final JSONObject tier = (JSONObject) item;
            final long minimum = tier.getLong("minInclusive");
            if (minimum < previous) throw new IllegalArgumentException("阶梯计算器阈值必须递增");
            tier.getLong("value");
            previous = minimum;
        }
    }
}
