package org.b3log.symphony.util;

import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Set;

/** Only the growth sources and reward summaries already displayed by the selection page. */
public final class ApiV3ProfessionGuideDto {
    private ApiV3ProfessionGuideDto() { }
    private static final Set<String> TRIGGERS = Set.of("long_article.read_settled", "article.published", "comment.published",
            "breezemoon.published", "repeater.published", "chatroom.message.published", "user.online.settled");
    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "职业成长提示暂不可用");
    }
    public static String text(final JSONObject row, final String key, final int max, final boolean empty) {
        if (!(row.opt(key) instanceof String value) || value.length() > max || (!empty && value.isBlank())
                || value.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
        try { ApiV3Json.canonical(value); } catch (final IllegalArgumentException e) { throw unavailable(); }
        return value;
    }
    public static long number(final JSONObject row, final String key) {
        if (!(row.opt(key) instanceof Number value)) throw unavailable();
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch (final ArithmeticException | NumberFormatException e) { throw unavailable(); }
    }
    public static JSONObject trigger(final JSONObject row) {
        final String type = text(row, "triggerType", 64, false);
        if (!TRIGGERS.contains(type)) throw unavailable();
        return new JSONObject().put("triggerType", type);
    }
    public static JSONObject reward(final JSONObject row) {
        final String type = text(row,"rewardType",32,false);
        if (!(row.opt("rewardConfigJson") instanceof String raw) || raw.isBlank() || raw.length()>4096) throw unavailable();
        try { ApiV3Json.canonical(raw); } catch (final IllegalArgumentException e) { throw unavailable(); }
        final JSONObject config = new JSONObject(raw);
        final JSONObject result = new JSONObject().put("rewardType",type);
        switch (type) {
            case "POINT" -> {
                if (!Set.of("amount","memo").containsAll(config.keySet())) throw unavailable();
                final long amount = number(config,"amount");
                if (amount <= 0 || amount > Integer.MAX_VALUE) throw unavailable();
                if (config.has("memo")) safeDescription(config,"memo",64);
                result.put("amount",amount);
            }
            case "MEDAL" -> {
                if (!Set.of("medalId","durationMillis","data").containsAll(config.keySet())) throw unavailable();
                text(config,"medalId",64,false);
                if (config.has("durationMillis") && number(config,"durationMillis") < 0) throw unavailable();
                if (config.has("data")) safeDescription(config,"data",128);
            }
            default -> throw unavailable();
        }
        return result;
    }
    private static void safeDescription(final JSONObject config, final String key, final int max) {
        if (!(config.opt(key) instanceof String value) || value.length()>max || value.contains("<") || value.contains(">")
                || value.chars().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))) throw unavailable();
        try { ApiV3Json.canonical(value); } catch (final IllegalArgumentException e) { throw unavailable(); }
    }
}
