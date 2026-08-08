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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.BreezemoonRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 公开职业动态只保留当前仍可公开访问的来源。 */
@Service
public class ProfessionProfileActivityVisibilityService {

    @Inject private ProfessionSourceEventRepository sourceEventRepository;
    @Inject private ArticleRepository articleRepository;
    @Inject private CommentRepository commentRepository;
    @Inject private BreezemoonRepository breezemoonRepository;
    @Inject private RepeaterContentRepository repeaterContentRepository;

    public List<JSONObject> filter(final List<JSONObject> effects, final boolean owner) throws RepositoryException {
        if (owner) return effects;
        final Set<String> ids = new HashSet<>();
        effects.forEach(effect -> ids.add(effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID)));
        final Set<String> visible = new HashSet<>();
        for (final JSONObject event : sourceEventRepository.getByIds(ids)) {
            if (publicEvent(event) && targetVisible(event)) visible.add(event.getString(Keys.OBJECT_ID));
        }
        return effects.stream().filter(effect -> visible.contains(effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID))).toList();
    }

    private boolean publicEvent(final JSONObject event) {
        return "PUBLIC".equals(event.optString(ProfessionSourceEvent.VISIBILITY_CLASS));
    }

    private boolean targetVisible(final JSONObject event) throws RepositoryException {
        return switch (event.optString(ProfessionSourceEvent.TARGET_TYPE)) {
            case "article" -> articleVisible(articleRepository.get(event.optString(ProfessionSourceEvent.TARGET_ID)));
            case "comment" -> commentVisible(commentRepository.get(event.optString(ProfessionSourceEvent.TARGET_ID)));
            case "breezemoon" -> breezemoonVisible(breezemoonRepository.get(event.optString(ProfessionSourceEvent.TARGET_ID)));
            case "repeater" -> repeaterVisible(repeaterContentRepository.get(event.optString(ProfessionSourceEvent.TARGET_ID)));
            case "chatroom_message" -> true;
            default -> false;
        };
    }

    private boolean articleVisible(final JSONObject article) {
        return null != article && article.optInt(Article.ARTICLE_STATUS) == Article.ARTICLE_STATUS_C_VALID
                && article.optInt(Article.ARTICLE_ANONYMOUS) == Article.ARTICLE_ANONYMOUS_C_PUBLIC
                && article.optInt(Article.ARTICLE_ANONYMOUS_VIEW) != Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW;
    }

    private boolean commentVisible(final JSONObject comment) throws RepositoryException {
        if (null == comment || comment.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID
                || comment.optInt(Comment.COMMENT_ANONYMOUS) != Comment.COMMENT_ANONYMOUS_C_PUBLIC
                || comment.optInt(Comment.COMMENT_VISIBLE) != Comment.COMMENT_VISIBLE_C_ALL) return false;
        return articleVisible(articleRepository.get(comment.optString(Comment.COMMENT_ON_ARTICLE_ID)));
    }

    private boolean breezemoonVisible(final JSONObject value) {
        return null != value && value.optInt(Breezemoon.BREEZEMOON_STATUS) == Breezemoon.BREEZEMOON_STATUS_C_VALID;
    }

    private boolean repeaterVisible(final JSONObject value) {
        return null != value && value.optInt(RepeaterContent.STATUS) == RepeaterContent.STATUS_C_VALID;
    }
}
