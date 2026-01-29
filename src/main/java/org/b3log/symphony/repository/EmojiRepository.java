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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Emoji;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * Emoji repository.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Repository
public class EmojiRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public EmojiRepository() {
        super(Emoji.EMOJI);
    }


    /**
     * Gets emojis by ids.
     *
     * @param emojiIds the emoji ids
     * @return emoji list
     * @throws RepositoryException repository exception
     */
    public List<JSONObject> getEmojisByIds(final String[] emojiIds) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter("oId", FilterOperator.IN,  Arrays.asList(emojiIds))
        );
        return getList(query);
    }

    /**
     * Gets emoji by id.
     *
     * @param emojiId the emoji id
     * @return emoji object
     * @throws RepositoryException repository exception
     */
    public JSONObject getById(final String emojiId) throws RepositoryException {
        return get(emojiId);
    }


    // 通过url 获取表情
    public JSONObject getByUrl(final String url) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter(Emoji.EMOJI_URL, FilterOperator.EQUAL, url)
        );
        return getFirst(query);
    }

}
