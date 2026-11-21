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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.util.ApiV3NotificationTypes;
import org.b3log.symphony.util.ContentNotificationReferences;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.2.0.0, Feb 27, 2019
 * @since 0.2.5
 */
@Repository
public class NotificationRepository extends AbstractRepository {
    @Inject private ApiV3NotificationRevisionRepository revisions;

    private void requireWriteTransaction(){if(!hasTransactionBegun())throw new IllegalStateException("notification write transaction required");}
    private String ownerForUpdate(String id)throws RepositoryException {
        final var rows=select("SELECT userId FROM `"+getName()+"` WHERE oId=? FOR UPDATE",id);
        return rows.isEmpty()?null:rows.getFirst().optString(Notification.NOTIFICATION_USER_ID);
    }
    @Override public String add(JSONObject row)throws RepositoryException {
        requireWriteTransaction();final String id=insertNotification(row);revisions.touchInCurrentTransaction(row.optString(Notification.NOTIFICATION_USER_ID));return id;
    }
    @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException {
        requireWriteTransaction();final String owner=ownerForUpdate(id);updateNotification(id,row,fields);
        revisions.touchInCurrentTransaction(owner);
        // Preserve existing repository field semantics if an internal caller changes ownership.
        if(row.has(Notification.NOTIFICATION_USER_ID)&&(fields.length==0||java.util.Arrays.asList(fields).contains(Notification.NOTIFICATION_USER_ID))
                &&!java.util.Objects.equals(owner,row.optString(Notification.NOTIFICATION_USER_ID)))revisions.touchInCurrentTransaction(row.optString(Notification.NOTIFICATION_USER_ID));
    }
    @Override public void remove(String id)throws RepositoryException {
        requireWriteTransaction();final String owner=ownerForUpdate(id);removeNotification(id);revisions.touchInCurrentTransaction(owner);
    }
    protected String insertNotification(JSONObject row)throws RepositoryException{return super.add(row);}
    protected void updateNotification(String id,JSONObject row,String...fields)throws RepositoryException{super.update(id,row,fields);}
    protected void removeNotification(String id)throws RepositoryException{super.remove(id);}
    protected void removeNotifications(Query query)throws RepositoryException{super.remove(query);}
    /** Notification bulk callers use this owner-aware path to preserve invalidation markers. */
    public void removeByUserIdAndType(String owner,Integer type)throws RepositoryException {
        requireWriteTransaction();
        final Query query=new Query().setFilter(type==null?new PropertyFilter(Notification.NOTIFICATION_USER_ID,FilterOperator.EQUAL,owner)
                :CompositeFilterOperator.and(new PropertyFilter(Notification.NOTIFICATION_USER_ID,FilterOperator.EQUAL,owner),new PropertyFilter(Notification.NOTIFICATION_DATA_TYPE,FilterOperator.EQUAL,type)));
        removeNotifications(query);revisions.touchInCurrentTransaction(owner);
    }

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(NotificationRepository.class);

    /**
     * Public constructor.
     */
    public NotificationRepository() {
        super(Notification.NOTIFICATION);
    }

    /** Content references only; a colliding opposite-kind target makes old AT references ambiguous. */
    public void removeContentReferences(final String dataId, final boolean article, final String oppositeTargetTable) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("notification removal requires transaction");
        final boolean removeMentions = select(ContentNotificationReferences.oppositeTarget(oppositeTargetTable), dataId).isEmpty();
        final var selection = ContentNotificationReferences.target(getName(), dataId, article, removeMentions);
        for (final JSONObject row : select(selection.sql(), selection.args().toArray())) remove(row.getString(Keys.OBJECT_ID));
    }

    /** Payment notices reference the reward record ID, rather than the content ID. */
    public void removeRewardReferences(final String rewardId, final int rewardType) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("notification removal requires transaction");
        final int type = ContentNotificationReferences.rewardNotificationType(rewardType);
        for (final JSONObject row : select("SELECT oId FROM `" + getName()
                + "` WHERE dataId=? AND dataType=? ORDER BY oId FOR UPDATE", rewardId, type)) remove(row.getString(Keys.OBJECT_ID));
    }

    /**
     * Checks whether has sent a notification to a user specified by the given user id with the specified data id and data type.
     *
     * @param userId               the given user id
     * @param dataId               the specified the specified data id
     * @param notificationDataType the specified notification data type
     * @return {@code true} if sent, returns {@code false} otherwise
     */
    public boolean hasSentByDataIdAndType(final String userId, final String dataId, final int notificationDataType) {
        try {
            return 0 < count(new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(Notification.NOTIFICATION_USER_ID, FilterOperator.EQUAL, userId),
                    new PropertyFilter(Notification.NOTIFICATION_DATA_ID, FilterOperator.EQUAL, dataId),
                    new PropertyFilter(Notification.NOTIFICATION_DATA_TYPE, FilterOperator.EQUAL, notificationDataType))));
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Checks [" + notificationDataType + "] notification sent failed [userId=" + userId + ", dataId=" + dataId + "]", e);

            return false;
        }
    }

    /**
     * Remove notifications by the specified user id.
     *
     * @param userId the specified user id
     * @throws RepositoryException repository exception
     */
    public void removeByUserId(final String userId) throws RepositoryException {
        removeByUserIdAndType(userId,null);
    }
    /** Snapshot the unread data-reference set; subsequent batches exclude later notifications. */
    public String unreadDataCutoffForUpdate(final String userId, final String dataId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("notification transaction required");
        final var rows = select("SELECT oId FROM `" + getName() + "` WHERE userId=? AND dataId=? AND hasRead=? ORDER BY oId DESC LIMIT 1 FOR UPDATE", userId, dataId, false);
        return rows.isEmpty() ? null : rows.getFirst().optString("oId");
    }

    public List<JSONObject> unreadDataBatchForUpdate(final String userId, final String dataId, final String cutoff) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("notification transaction required");
        return select("SELECT * FROM `" + getName() + "` WHERE userId=? AND dataId=? AND hasRead=? AND oId<=? ORDER BY oId LIMIT 200 FOR UPDATE", userId, dataId, false, cutoff);
    }

    public JSONObject getOwnedForUpdate(final String userId, final String id) throws RepositoryException {
        final List<JSONObject> rows = select("SELECT * FROM " + getName() + " WHERE userId=? AND oId=? FOR UPDATE", userId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public JSONObject getOwned(final String userId, final String id) throws RepositoryException {
        final List<JSONObject> rows = select("SELECT * FROM " + getName() + " WHERE userId=? AND oId=?", userId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<JSONObject> getV3Page(final String userId, final String category, final String status,
                                      final int p, final int size) throws RepositoryException {
        final Selection where = v3Selection(userId, category, status, null);
        final List<Object> args = new ArrayList<>(where.args());
        args.add((p - 1) * size); args.add(size);
        return select("SELECT * FROM " + getName() + where.sql() + " ORDER BY hasRead ASC,oId DESC LIMIT ?,?", args.toArray());
    }

    public long countV3(final String userId, final String category, final String status) throws RepositoryException {
        final Selection where = v3Selection(userId, category, status, null);
        return select("SELECT COUNT(1) AS total FROM " + getName() + where.sql(), where.args().toArray()).get(0).optLong("total");
    }

    public List<JSONObject> countV3Unread(final String userId) throws RepositoryException {
        return select("SELECT dataType,COUNT(1) AS total FROM " + getName() + " WHERE userId=? AND hasRead=? GROUP BY dataType", userId, false);
    }

    public String getV3Cutoff(final String userId, final String category, final boolean unreadOnly) throws RepositoryException {
        final Selection where = v3Selection(userId, category, unreadOnly ? "unread" : "all", null);
        final List<JSONObject> rows = select("SELECT oId FROM " + getName() + where.sql() + " ORDER BY oId DESC LIMIT 1", where.args().toArray());
        return rows.isEmpty() ? null : rows.get(0).optString("oId");
    }

    public List<JSONObject> getV3BatchForUpdate(final String userId, final String category, final boolean unreadOnly,
                                               final String cutoff) throws RepositoryException {
        final Selection where = v3Selection(userId, category, unreadOnly ? "unread" : "all", cutoff);
        return select("SELECT * FROM " + getName() + where.sql() + " ORDER BY oId ASC LIMIT 200 FOR UPDATE", where.args().toArray());
    }

    private static Selection v3Selection(final String userId, final String category, final String status, final String cutoff) {
        if (!ApiV3NotificationTypes.FILTERS.contains(category) || !java.util.Set.of("all", "read", "unread").contains(status)) {
            throw new IllegalArgumentException("notification selection");
        }
        final StringBuilder sql = new StringBuilder(" WHERE userId=?");
        final List<Object> args = new ArrayList<>(); args.add(userId);
        if (!"all".equals(status)) { sql.append(" AND hasRead=?"); args.add("read".equals(status)); }
        if (!"all".equals(category)) {
            final boolean other = "other".equals(category);
            final List<Integer> types = other ? ApiV3NotificationTypes.KNOWN_TYPES : ApiV3NotificationTypes.CATEGORIES.get(category);
            sql.append(" AND dataType ").append(other ? "NOT IN (" : "IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(types.size(), "?"))).append(')');
            args.addAll(types);
        }
        if (null != cutoff) { sql.append(" AND oId<=?"); args.add(cutoff); }
        return new Selection(sql.toString(), args);
    }

    private record Selection(String sql, List<Object> args) { }

}
