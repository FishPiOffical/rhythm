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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.MedalRepository;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/** 职业等级展示与奖励的受控配置注册表。 */
@Service
public class ProfessionDefinitionRegistry {

    private static final Set<String> POSITIONS = Set.of("default", "selectionCard", "homeProfile", "userCard",
            "professionPage", "ranking", "levelUpDialog", "compactMobile");
    private static final Set<String> PRESENTATION_KEYS = Set.of("imageUrl", "iconUrl", "badgeUrl", "avatarFrameUrl",
            "primaryColor", "secondaryColor", "backgroundColor", "textColor", "borderColor", "borderWidth",
            "shape", "textureUrl", "decorationUrl", "shadow", "glow", "progressStyle", "levelUpAnimation");
    private static final Set<String> REWARD_TYPES = Set.of("POINT", "MEDAL");
    private static final Set<String> GRANT_POLICIES = Set.of("FIRST_REACH");
    private static final Set<String> DOWNGRADE_POLICIES = Set.of("KEEP");
    private static final Map<String, Set<String>> PRESENTATION_PRESETS = Map.of(
            "borderWidth", Set.of("none", "thin", "medium", "thick"),
            "shape", Set.of("square", "rounded", "pill", "circle"),
            "shadow", Set.of("none", "soft", "medium", "strong"),
            "glow", Set.of("none", "soft", "medium", "strong"),
            "progressStyle", Set.of("solid", "gradient", "segmented", "ring"),
            "levelUpAnimation", Set.of("none", "fade", "scale", "burst"));
    private static final Set<String> POINT_CONFIG_KEYS = Set.of("amount", "memo");
    private static final Set<String> MEDAL_CONFIG_KEYS = Set.of("medalId", "durationMillis", "data");
    private static final int MAX_PRESENTATION_LENGTH = 16_384;
    private static final int MAX_REWARD_CONFIG_LENGTH = 4_096;
    private static final int MAX_RESOURCE_URL_LENGTH = 512;
    private static final int MAX_PRESET_LENGTH = 32;
    private static final int MAX_REWARD_MEMO_LENGTH = 64;
    private static final int MAX_MEDAL_DATA_LENGTH = 128;
    private static final double MIN_TEXT_CONTRAST = 4.5D;
    private static final double SRGB_THRESHOLD = 0.03928D;
    private static final double SRGB_DIVISOR = 12.92D;
    private static final double SRGB_OFFSET = 0.055D;
    private static final double SRGB_SCALE = 1.055D;
    private static final double SRGB_EXPONENT = 2.4D;
    private static final double CONTRAST_OFFSET = 0.05D;
    private static final double COLOR_COMPONENT_MAX = 255D;
    private static final double RED_WEIGHT = 0.2126D;
    private static final double GREEN_WEIGHT = 0.7152D;
    private static final double BLUE_WEIGHT = 0.0722D;

    @Inject
    private MedalRepository medalRepository;

    public void validatePresentation(final ProfessionLevelPresentationDraft presentation) {
        if (null == presentation) {
            throw new IllegalArgumentException("展示位置不合法");
        }
        validatePositionCode(presentation.positionCode());
        validateJsonLength(presentation.lightConfigJson(), MAX_PRESENTATION_LENGTH, "明亮样式");
        validateJsonLength(presentation.darkConfigJson(), MAX_PRESENTATION_LENGTH, "深色样式");
        validatePresentationConfig(new JSONObject(presentation.lightConfigJson()));
        validatePresentationConfig(new JSONObject(presentation.darkConfigJson()));
    }

    public JSONObject presentationMetadata() {
        final JSONObject result = new JSONObject();
        final JSONArray positions = new JSONArray();
        POSITIONS.stream().sorted().forEach(positions::put);
        result.put("positions", positions);
        final JSONObject presets = new JSONObject();
        PRESENTATION_PRESETS.forEach((key, values) -> {
            final JSONArray options = new JSONArray();
            values.stream().sorted().forEach(options::put);
            presets.put(key, options);
        });
        result.put("presets", presets);
        return result;
    }

    public void validatePositionCode(final String positionCode) {
        if (!POSITIONS.contains(positionCode)) {
            throw new IllegalArgumentException("展示位置不合法");
        }
    }

    public void validateReward(final ProfessionLevelRewardDraft reward) {
        if (null == reward || !REWARD_TYPES.contains(reward.rewardType())
                || !GRANT_POLICIES.contains(reward.grantPolicy())
                || !DOWNGRADE_POLICIES.contains(reward.downgradePolicy())) {
            throw new IllegalArgumentException("奖励配置不合法");
        }
        if (!reward.rewardCode().matches("^[a-z][a-z0-9._-]{0,63}$")) {
            throw new IllegalArgumentException("奖励编码不合法");
        }
        if (reward.sortOrder() < 0) {
            throw new IllegalArgumentException("奖励排序不合法");
        }
        validateJsonLength(reward.rewardConfigJson(), MAX_REWARD_CONFIG_LENGTH, "奖励配置");
        validateRewardConfig(reward.rewardType(), new JSONObject(reward.rewardConfigJson()));
    }

    private void validatePresentationConfig(final JSONObject config) {
        for (final String key : config.keySet()) {
            if (!PRESENTATION_KEYS.contains(key)) {
                throw new IllegalArgumentException("展示样式包含未注册字段");
            }
            validatePresentationValue(key, config.get(key));
        }
        validateContrast(config);
    }

    private void validateContrast(final JSONObject config) {
        if (!config.has("backgroundColor") || !config.has("textColor")) {
            return;
        }
        final double background = luminance(config.getString("backgroundColor"));
        final double text = luminance(config.getString("textColor"));
        final double ratio = (Math.max(background, text) + CONTRAST_OFFSET)
                / (Math.min(background, text) + CONTRAST_OFFSET);
        if (ratio < MIN_TEXT_CONTRAST) {
            throw new IllegalArgumentException("展示文字与背景对比度不足");
        }
    }

    private double luminance(final String color) {
        final double red = linear(Integer.parseInt(color.substring(1, 3), 16) / COLOR_COMPONENT_MAX);
        final double green = linear(Integer.parseInt(color.substring(3, 5), 16) / COLOR_COMPONENT_MAX);
        final double blue = linear(Integer.parseInt(color.substring(5, 7), 16) / COLOR_COMPONENT_MAX);
        return red * RED_WEIGHT + green * GREEN_WEIGHT + blue * BLUE_WEIGHT;
    }

    private double linear(final double component) {
        return component <= SRGB_THRESHOLD ? component / SRGB_DIVISOR
                : Math.pow((component + SRGB_OFFSET) / SRGB_SCALE, SRGB_EXPONENT);
    }

    private void validatePresentationValue(final String key, final Object value) {
        if (key.endsWith("Url")) {
            validateResourceUrl(String.valueOf(value));
            return;
        }
        if (key.endsWith("Color") && !String.valueOf(value).matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("展示颜色不合法");
        }
        final Set<String> presets = PRESENTATION_PRESETS.get(key);
        if (null != presets && !presets.contains(String.valueOf(value))) {
            throw new IllegalArgumentException("展示预设值不合法");
        }
        if (null == presets && !key.endsWith("Color")
                && (!(value instanceof String) || String.valueOf(value).length() > MAX_PRESET_LENGTH)) {
            throw new IllegalArgumentException("展示字段值不合法");
        }
    }

    private void validateResourceUrl(final String value) {
        if (value.isBlank() || value.length() > MAX_RESOURCE_URL_LENGTH) {
            throw new IllegalArgumentException("展示资源地址不合法");
        }
        final String lowerValue = value.toLowerCase();
        if (lowerValue.contains("%2e") || lowerValue.contains("%2f") || lowerValue.contains("%5c")) {
            throw new IllegalArgumentException("展示资源地址不合法");
        }
        if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")
                && !value.contains("/../") && !value.endsWith("/..")) {
            return;
        }
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("展示资源地址不合法", e);
        }
        if (!"https".equals(uri.getScheme()) || !"file.fishpi.cn".equals(uri.getHost())
                || null != uri.getUserInfo() || null != uri.getFragment()
                || !uri.normalize().getPath().equals(uri.getPath())) {
            throw new IllegalArgumentException("展示资源地址不合法");
        }
    }

    private void validateRewardConfig(final String type, final JSONObject config) {
        switch (type) {
            case "POINT" -> validatePoint(config);
            case "MEDAL" -> validateMedalConfig(config);
            default -> throw new IllegalArgumentException("奖励类型不合法");
        }
    }

    private void validatePoint(final JSONObject config) {
        validateKeys(config, POINT_CONFIG_KEYS);
        requirePositiveLong(config, "amount");
        if (!isSafeText(config.optString("memo"), MAX_REWARD_MEMO_LENGTH)) {
            throw new IllegalArgumentException("奖励说明长度不合法");
        }
    }

    private void validateMedalConfig(final JSONObject config) {
        validateKeys(config, MEDAL_CONFIG_KEYS);
        validateMedal(config);
    }

    private void validateKeys(final JSONObject config, final Set<String> allowedKeys) {
        for (final String key : config.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("奖励配置包含未注册字段");
            }
        }
    }

    private void requirePositiveLong(final JSONObject config, final String key) {
        final long amount = config.getLong(key);
        if (amount <= 0L || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("奖励积分必须大于零");
        }
    }

    private void validateMedal(final JSONObject config) {
        final String medalId = config.optString("medalId");
        if (medalId.isBlank() || medalId.length() > 64) {
            throw new IllegalArgumentException("勋章标识不合法");
        }
        final long durationMillis = config.optLong("durationMillis", 0L);
        if (durationMillis < 0L || !isSafeText(config.optString("data"), MAX_MEDAL_DATA_LENGTH)) {
            throw new IllegalArgumentException("勋章有效期或数据不合法");
        }
        try {
            final Query query = new Query().setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            if (null == medalRepository.getFirst(query)) {
                throw new IllegalArgumentException("勋章不存在");
            }
        } catch (final RepositoryException e) {
            throw new IllegalStateException("查询勋章失败", e);
        }
    }

    private void validateJsonLength(final String json, final int maxLength, final String name) {
        if (null == json || json.isBlank() || json.length() > maxLength) {
            throw new IllegalArgumentException(name + "长度不合法");
        }
    }

    private boolean isSafeText(final String value, final int maxLength) {
        return value.length() <= maxLength && !value.contains("<") && !value.contains(">")
                && value.chars().noneMatch(character -> Character.isISOControl(character)
                && !Character.isWhitespace(character));
    }
}
