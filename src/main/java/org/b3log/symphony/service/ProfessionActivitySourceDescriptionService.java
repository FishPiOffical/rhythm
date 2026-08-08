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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 为职业动态生成不含内部标识和原始载荷的来源说明。 */
@Service
public class ProfessionActivitySourceDescriptionService {

    @Inject private ProfessionSourceEventRepository sourceEventRepository;

    public Map<String, String> descriptions(final List<JSONObject> effects, final boolean owner)
            throws RepositoryException {
        final Set<String> sourceEventIds = new HashSet<>();
        for (final JSONObject effect : effects) {
            final String sourceEventId = effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID);
            if (!sourceEventId.isBlank()) {
                sourceEventIds.add(sourceEventId);
            }
        }
        final Map<String, JSONObject> events = eventsById(sourceEventIds);
        final Map<String, String> result = new HashMap<>();
        for (final JSONObject effect : effects) {
            result.put(effect.getString(Keys.OBJECT_ID), description(effect,
                    events.get(effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID)), owner));
        }
        return result;
    }

    private Map<String, JSONObject> eventsById(final Set<String> sourceEventIds) throws RepositoryException {
        final Map<String, JSONObject> result = new HashMap<>();
        for (final JSONObject event : sourceEventRepository.getByIds(sourceEventIds)) {
            result.put(event.getString(Keys.OBJECT_ID), event);
        }
        return result;
    }

    private String description(final JSONObject effect, final JSONObject event, final boolean owner) {
        if (null == event) {
            return "level.migration".equals(effect.optString(ProfessionEventEffect.ACTION_CODE))
                    ? "调整等级方案" : "职业自动化";
        }
        return switch (event.optString(ProfessionSourceEvent.EVENT_TYPE)) {
            case "article.published" -> "发布帖子";
            case "comment.published" -> "发表评论";
            case "breezemoon.published" -> "发布清风明月";
            case "repeater.published" -> "发布复读机内容";
            case "chatroom.message.published" -> "聊天室发言";
            case "user.online.settled" -> "在线时长结算";
            case "long_article.read_settled" -> "长篇阅读结算";
            case "profession.source.reversed" -> "撤销来源内容";
            case "external.profession.experience" -> externalDescription(event, owner);
            default -> "职业自动化";
        };
    }

    private String externalDescription(final JSONObject event, final boolean owner) {
        if (!owner) {
            return "外部应用";
        }
        final String scene = new JSONObject(event.getString(ProfessionSourceEvent.PAYLOAD_JSON))
                .optString("sourceScene").trim();
        return scene.isBlank() ? "外部应用" : "外部应用 · " + scene;
    }
}
