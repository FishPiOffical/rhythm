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

import org.b3log.latke.Keys;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.EmojiGroupItem;
import org.json.JSONObject;

import java.util.List;

/**
 * Emoji group item repository.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Repository
public class EmojiGroupItemRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public EmojiGroupItemRepository() {
        super(EmojiGroupItem.EMOJI_GROUP_ITEM);
    }

    /**
     * Gets all emojis in a group.
     *
     * @param groupId the group id
     * @return emoji group item list
     * @throws RepositoryException repository exception
     */
    public List<JSONObject> getByGroupId(final String groupId) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, FilterOperator.EQUAL, groupId)
        ).addSort(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, SortDirection.ASCENDING);
        return getList(query);
    }

    /**
     * Removes an emoji from a group.
     *
     * @param groupId the group id
     * @param emojiId the emoji id
     * @throws RepositoryException repository exception
     */
    public void removeByGroupIdAndEmojiId(final String groupId, final String emojiId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, FilterOperator.EQUAL, groupId),
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID, FilterOperator.EQUAL, emojiId)
        ));
        remove(query);
    }

    /**
     * Checks if an emoji is in a group.
     *
     * @param groupId the group id
     * @param emojiId the emoji id
     * @return emoji group item object, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByGroupIdAndEmojiId(final String groupId, final String emojiId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, FilterOperator.EQUAL, groupId),
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID, FilterOperator.EQUAL, emojiId)
        ));
        return getFirst(query);
    }

    public JSONObject getByItemId(final String itemId) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter(Keys.OBJECT_ID, FilterOperator.EQUAL, itemId)
        );
        return getFirst(query);
    }

    public JSONObject getByGroupIdAndItemId(final String groupId,final String itemId) throws RepositoryException {
        final Query query = new Query().setFilter(
                CompositeFilterOperator.and(
                        new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, FilterOperator.EQUAL, groupId),
                        new PropertyFilter(Keys.OBJECT_ID, FilterOperator.EQUAL, itemId)
                )
        );
        return getFirst(query);
    }

    /**
     * Removes all items in a group.
     *
     * @param groupId the group id
     * @throws RepositoryException repository exception
     */
    public void removeByGroupId(final String groupId) throws RepositoryException {
        final Query query = new Query().setFilter(
            new PropertyFilter(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, FilterOperator.EQUAL, groupId)
        );
        remove(query);
    }


}
