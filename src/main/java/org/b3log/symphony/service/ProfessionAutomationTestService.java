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
import org.json.JSONArray;
import org.json.JSONObject;

/** 在不写入来源事件和效果的前提下测试自动化配置。 */
@Service
public class ProfessionAutomationTestService {

    @Inject
    private ProfessionAutomationRegistry registry;

    public JSONObject test(final String configurationJson, final String payloadJson) {
        final JSONObject configuration = new JSONObject(configurationJson);
        final JSONObject payload = new JSONObject(payloadJson);
        registry.validate(configuration);
        final JSONObject result = new JSONObject();
        final boolean matched = registry.matches(configuration.optJSONObject("condition"), payload);
        result.put("matched", matched);
        result.put("effects", matched ? effects(configuration.getJSONArray("actions"), payload) : new JSONArray());
        return result;
    }

    private JSONArray effects(final JSONArray actions, final JSONObject payload) {
        final JSONArray effects = new JSONArray();
        for (int index = 0; index < actions.length(); index++) {
            final JSONObject action = actions.getJSONObject(index);
            final JSONObject effect = new JSONObject();
            effect.put("actionCode", action.optString("actionCode", action.getString("actionType") + '.' + index));
            effect.put("actionType", action.getString("actionType"));
            if (registry.isContributionAction(action)) {
                effect.put("contributionRecorded", true);
                effects.put(effect);
                continue;
            }
            if (!registry.isExperienceAction(action)) {
                effect.put("notificationContent", action.getString("notificationContent"));
                effect.put("notificationWhen", action.optString("notificationWhen", "ALWAYS"));
                effects.put(effect);
                continue;
            }
            final JSONObject calculator = action.optJSONObject("calculator");
            effect.put("experienceDelta", null == calculator ? action.getLong("experienceDelta")
                    : registry.calculate(calculator, payload));
            effects.put(effect);
        }
        return effects;
    }
}
