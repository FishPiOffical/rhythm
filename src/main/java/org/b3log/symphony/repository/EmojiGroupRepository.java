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
import org.b3log.symphony.model.EmojiGroup;
import org.json.JSONObject;

import java.util.List;

/**
 * Emoji group repository.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Repository
public class EmojiGroupRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public EmojiGroupRepository() {
        super(EmojiGroup.EMOJI_GROUP);
    }

    /**
     * Gets user's all groups.
     *
     * @param userId the user id
     * @return group list
     * @throws RepositoryException repository exception
     */
    public List<JSONObject> getByUserId(final String userId) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter(EmojiGroup.EMOJI_GROUP_USER_ID, FilterOperator.EQUAL, userId)
        ).addSort(EmojiGroup.EMOJI_GROUP_SORT, SortDirection.ASCENDING);
        return getList(query);
    }

    /**
     * Gets user's "all" group (default group).
     *
     * @param userId the user id
     * @return "all" group object, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getAllGroup(final String userId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
            new PropertyFilter(EmojiGroup.EMOJI_GROUP_USER_ID, FilterOperator.EQUAL, userId),
            new PropertyFilter(EmojiGroup.EMOJI_GROUP_TYPE, FilterOperator.EQUAL, EmojiGroup.EMOJI_GROUP_TYPE_ALL)
        ));
        return getFirst(query);
    }


    /**
     * 根据用户ID和分组名称获取表情分组
     *
     * @param userId 用户ID
     * @param name   分组名称
     * @return 匹配的表情分组JSON对象，如果没有匹配则返回null
     * @throws RepositoryException 如果数据库操作失败
     */
    public JSONObject getByUserIdAndName(final String userId, final String name) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
            new PropertyFilter(EmojiGroup.EMOJI_GROUP_USER_ID, FilterOperator.EQUAL, userId),
            new PropertyFilter(EmojiGroup.EMOJI_GROUP_NAME, FilterOperator.EQUAL, name)
        ));
        return getFirst(query);
    }


    /**
     * Gets group by id.
     *
     * @param groupId the group id
     * @return group object
     * @throws RepositoryException repository exception
     */
    public JSONObject getById(final String groupId) throws RepositoryException {
        return get(groupId);
    }
}
