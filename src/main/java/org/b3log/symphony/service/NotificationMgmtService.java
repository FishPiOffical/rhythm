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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.util.Connections;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.NotificationProcessor;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.util.NotificationContents;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Notification management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.20.0.0, Jul 15, 2018
 * @since 0.2.5
 */
@Service
public class NotificationMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(NotificationMgmtService.class);

    /**
     * Whether the custom notification content column is available.
     */
    private volatile Boolean customSysContentColumnReady;

    /**
     * Notification repository.
     */
    @Inject
    private NotificationRepository notificationRepository;

    /**
     * Adds a 'report handled' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // report handled point transfer id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addReportHandledNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_REPORT_HANDLED);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=report_handled] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Removes the specified user's notifications of the specified type.
     *
     * @param userId the specified user id
     * @param type   the specified notification type
     */
    @Transactional
    public void removeNotifications(final String userId, final int type) {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(Notification.NOTIFICATION_USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(Notification.NOTIFICATION_DATA_TYPE, FilterOperator.EQUAL, type)));
        try {
            notificationRepository.remove(query);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Removes user [id=" + userId + "]'s notifications [type=" + type + "] failed", e);
        }
    }

    /**
     * Removes a notification by the specified notification id.
     *
     * @param notificationId the specified notification id
     */
    @Transactional
    public void removeNotification(final String notificationId) {
        try {
            notificationRepository.remove(notificationId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Removes a notification [id=" + notificationId + "] failed", e);
        }
    }

    /**
     * Adds a 'red packet from sky' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // comment id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addRedPacketFromSkyNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_RED_PACKET_FROM_SKY);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=red_packet_from_sky] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'comment accept' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // comment id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addCommentAcceptNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=comment_accept] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'article vote down' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // article id-vote user id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addArticleVoteDownNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ARTICLE_VOTE_DOWN);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_vote_down] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'article vote up' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // article id-vote user id
     */
    @Transactional
    public void addArticleVoteUpNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ARTICLE_VOTE_UP);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_vote_up] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'comment vote down' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // comment id-vote user id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addCommentVoteDownNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_COMMENT_VOTE_DOWN);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=comment_vote_down] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'comment vote up' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // comment id-vote user id
     */
    @Transactional
    public void addCommentVoteUpNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_COMMENT_VOTE_UP);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=comment_vote_up] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'article new watcher' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // article id-follower user id
     */
    @Transactional
    public void addArticleNewWatcherNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ARTICLE_NEW_WATCHER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_new_watcher] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'article new follower' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // article id-follower user id
     */
    @Transactional
    public void addArticleNewFollowerNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ARTICLE_NEW_FOLLOWER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_new_follower] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'point - perfect article' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addPerfectArticleNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_PERFECT_ARTICLE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=perfect_article] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'following - article comment' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // comment id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addFollowingArticleCommentNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=following_article_comment] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'following - article update' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addFollowingArticleUpdateNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_UPDATE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=following_article_update] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'sys announce - role changed' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // oldRoleId-newRoleId
     * @throws ServiceException service exception
     */
    @Transactional
    public void addSysAnnounceRoleChangedNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_SYS_ANNOUNCE_ROLE_CHANGED);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=sys_announce_role_changed] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'invitation link used' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // invited user id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addInvitationLinkUsedNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_INVITATION_LINK_USED);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=invitation_link_used] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'new follower' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId": "",
     *                          "dataId": "" // new follower id
     */
    @Transactional
    public void addNewFollowerNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_NEW_FOLLOWER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=new_follower] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'sys announce - article' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addSysAnnounceArticleNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=sys_announce_article] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    @Transactional
    public void addSysAnnounceCustomNotification(String notification, String uid) throws ServiceException {
        try {
            final JSONObject requestJSONObject = new JSONObject();
            requestJSONObject.put(Notification.NOTIFICATION_USER_ID, uid);
            if (isCustomSysContentColumnReady()) {
                final String rawContent = NotificationContents.normalizeCustomSysContent(notification);
                final String safeContent = NotificationContents.sanitizeCustomSysContent(rawContent);
                if (StringUtils.isBlank(safeContent)) {
                    throw new ServiceException("发送失败：notification 清洗后为空，请至少保留文本或 http/https 链接。");
                }
                requestJSONObject.put(Notification.NOTIFICATION_DATA_ID, StringUtils.EMPTY);
                requestJSONObject.put(Notification.NOTIFICATION_CONTENT, rawContent);
            } else {
                if (NotificationContents.requiresDedicatedContentColumn(notification)) {
                    throw new ServiceException("当前数据库缺少 notification.content 列，请先执行 SQL：ALTER TABLE `"
                            + Latkes.getLocalProperty("jdbc.tablePrefix") + "_notification` ADD COLUMN `content` TEXT;");
                }
                requestJSONObject.put(Notification.NOTIFICATION_DATA_ID,
                        NotificationContents.normalizeCustomSysContent(notification));
            }
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_CUSTOM_SYS);

            addNotification(requestJSONObject);
        } catch (final ServiceException e) {
            throw e;
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=sys_announce_custom] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Returns whether the custom system notification content column is ready.
     *
     * @return {@code true} if ready
     * @throws ServiceException service exception
     */
    public boolean isCustomSysContentColumnReady() throws ServiceException {
        if (null != customSysContentColumnReady) {
            return customSysContentColumnReady;
        }

        synchronized (this) {
            if (null != customSysContentColumnReady) {
                return customSysContentColumnReady;
            }

            customSysContentColumnReady = detectCustomSysContentColumn();
            return customSysContentColumnReady;
        }
    }

    /**
     * Adds a 'sys announce - new user' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addSysAnnounceNewUserNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_SYS_ANNOUNCE_NEW_USER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=sys_announce_new_user] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'invitecode used' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // invited user id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addInvitecodeUsedNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_INVITECODE_USED);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=invitecode_used] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Makes the specified user's notifications of the specified type as read.
     *
     * @param userId the specified user id
     * @param type   the specified notification type
     */
    public void makeRead(final String userId, final int type) {
        final Query query = new Query().setFilter(
                CompositeFilterOperator.and(
                        new PropertyFilter(Notification.NOTIFICATION_USER_ID, FilterOperator.EQUAL, userId),
                        new PropertyFilter(Notification.NOTIFICATION_HAS_READ, FilterOperator.EQUAL, false),
                        new PropertyFilter(Notification.NOTIFICATION_DATA_TYPE, FilterOperator.EQUAL, type)));
        try {
            final List<JSONObject> notifications = notificationRepository.getList(query);
            makeRead(notifications);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Makes read failed", e);
        }
    }

    /**
     * Makes the specified user's all notifications as read.
     *
     * @param userId the specified user id
     */
    public void makeAllRead(final String userId) {
        final Query query = new Query().setFilter(
                CompositeFilterOperator.and(
                        new PropertyFilter(Notification.NOTIFICATION_USER_ID, FilterOperator.EQUAL, userId),
                        new PropertyFilter(Notification.NOTIFICATION_HAS_READ, FilterOperator.EQUAL, false)));
        try {
            final List<JSONObject> notifications = notificationRepository.getList(query);
            makeRead(notifications);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Makes read failed", e);
        }
    }

    /**
     * Makes the specified user to article, comments notifications as read.
     *
     * @param userId     the specified user id
     * @param articleId  the specified article id
     * @param commentIds the specified comment ids
     */
    public void makeRead(final String userId, final String articleId, final List<String> commentIds) {
        final List<String> dataIds = new ArrayList<>(commentIds);
        dataIds.add(articleId);

        makeRead(userId, dataIds);
    }

    /**
     * Makes the specified user to comments notifications as read.
     *
     * @param userId     the specified user id
     * @param commentIds the specified comment ids
     */
    public void makeRead(final String userId, final List<String> commentIds) {
        final Query query = new Query().setFilter(
                CompositeFilterOperator.and(
                        new PropertyFilter(Notification.NOTIFICATION_USER_ID, FilterOperator.EQUAL, userId),
                        new PropertyFilter(Notification.NOTIFICATION_HAS_READ, FilterOperator.EQUAL, false),
                        new PropertyFilter(Notification.NOTIFICATION_DATA_ID, FilterOperator.IN, commentIds)));
        try {
            final List<JSONObject> notifications = notificationRepository.getList(query);
            makeRead(notifications);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Makes read failed", e);
        }
    }

    /**
     * Makes the specified notifications have been read.
     *
     * @param notifications the specified notifications
     */
    @Transactional
    public void makeRead(final Collection<JSONObject> notifications) {
        String userId = null;
        for (final JSONObject notification : notifications) {
            makeRead(notification);
            userId = notification.optString(Notification.NOTIFICATION_USER_ID);
        }
        if (userId != null && notifications.size() != 0) {
            try {
                String finalUserId = userId;
                Symphonys.EXECUTOR_SERVICE.submit(() -> {
                    final JSONObject cmd = new JSONObject();
                    cmd.put(UserExt.USER_T_ID, finalUserId);
                    cmd.put(Common.COMMAND, "refreshNotification");
                    Map<String, Object> dataModel = new HashMap<>();
                    final BeanManager beanManager = BeanManager.getInstance();
                    final NotificationProcessor notificationProcessor = beanManager.getReference(NotificationProcessor.class);
                    notificationProcessor.fillNotificationCount(finalUserId, dataModel);
                    cmd.put("count", Integer.parseInt(dataModel.get(Common.UNREAD_NOTIFICATION_CNT).toString()));

                    UserChannel.sendCmd(cmd);
                });
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Makes the specified notification have been read.
     *
     * @param notification the specified notification, return directly if this notification has been read
     *                     (notification.hasRead equals to {@code true})
     */
    @Transactional
    public void makeRead(final JSONObject notification) {
        if (notification.optBoolean(Notification.NOTIFICATION_HAS_READ)) {
            return;
        }

        final String id = notification.optString(Keys.OBJECT_ID);

        try {
            final JSONObject record = notificationRepository.get(id);
            if (null == record) {
                return;
            }

            record.put(Notification.NOTIFICATION_HAS_READ, true);

            notificationRepository.update(id, record);
        } catch (final RepositoryException e) {
            final String msg = "Makes notification as read failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'broadcast' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addBroadcastNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_BROADCAST);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=broadcast] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'point charge' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // transfer record id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addPointChargeNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_CHARGE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=point_charge] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'abuse point deduct' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // transfer record id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addAbusePointDeductNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ABUSE_POINT_DEDUCT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=abuse_point_deduct] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'point exchange' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // transfer record id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addPointExchangeNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_EXCHANGE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=point_exchange] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'point transfer' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // transfer record id
     */
    @Transactional
    public void addPointTransferNotification(final JSONObject requestJSONObject) {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_TRANSFER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=point_transfer] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * Adds a 'article reward' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // reward id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addArticleRewardNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_reward] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'article thank' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // thank id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addArticleThankNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_ARTICLE_THANK);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article_thank] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'comment thank' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // reward id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addCommentThankNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_POINT_COMMENT_THANK);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=comment_thank] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'comment' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addCommentNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_COMMENT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=comment] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'at' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addAtNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_AT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=at] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'at' type in chat room notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addChatRoomAtNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_CHAT_ROOM_AT);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=chat room at] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'red packet' type in chat room notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addRedPacketNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_RED_PACKET);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=chat room at] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'article' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addArticleNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_ARTICLE);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=article] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'following - user' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "" // article id
     * @throws ServiceException service exception
     */
    @Transactional
    public void addFollowingUserNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_FOLLOWING_USER);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=following_user] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'commented' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addCommentedNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_COMMENTED);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=commented] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a 'reply' type notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": ""
     * @throws ServiceException service exception
     */
    @Transactional
    public void addReplyNotification(final JSONObject requestJSONObject) throws ServiceException {
        try {
            requestJSONObject.put(Notification.NOTIFICATION_DATA_TYPE, Notification.DATA_TYPE_C_REPLY);

            addNotification(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds a notification [type=reply] failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Adds a notification with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "userId"; "",
     *                          "dataId": "",
     *                          "dataType": int
     * @throws RepositoryException repository exception
     */
    private void addNotification(final JSONObject requestJSONObject) throws RepositoryException {
        final JSONObject notification = new JSONObject();

        notification.put(Notification.NOTIFICATION_HAS_READ, false);
        notification.put(Notification.NOTIFICATION_USER_ID, requestJSONObject.optString(Notification.NOTIFICATION_USER_ID));
        notification.put(Notification.NOTIFICATION_DATA_ID, requestJSONObject.optString(Notification.NOTIFICATION_DATA_ID));
        notification.put(Notification.NOTIFICATION_DATA_TYPE, requestJSONObject.optInt(Notification.NOTIFICATION_DATA_TYPE));
        if (requestJSONObject.has(Notification.NOTIFICATION_CONTENT)) {
            notification.put(Notification.NOTIFICATION_CONTENT, requestJSONObject.optString(Notification.NOTIFICATION_CONTENT));
        }

        notificationRepository.add(notification);

        Symphonys.EXECUTOR_SERVICE.submit(() -> {
            final JSONObject cmd = new JSONObject();
            cmd.put(UserExt.USER_T_ID, requestJSONObject.optString(Notification.NOTIFICATION_USER_ID));
            cmd.put(Common.COMMAND, "refreshNotification");

            UserChannel.sendCmd(cmd);
        });
    }

    /**
     * Detects whether the custom content column exists on the notification table.
     *
     * @return {@code true} if the column exists
     * @throws ServiceException service exception
     */
    private boolean detectCustomSysContentColumn() throws ServiceException {
        final String tableName = Latkes.getLocalProperty("jdbc.tablePrefix") + "_" + Notification.NOTIFICATION;
        try (Connection connection = Connections.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getColumns(connection.getCatalog(), null, tableName, Notification.NOTIFICATION_CONTENT)) {
            return resultSet.next();
        } catch (final SQLException e) {
            LOGGER.log(Level.ERROR, "Detects notification.content column failed", e);
            throw new ServiceException("检测 notification.content 列失败，请检查数据库连接。");
        }
    }
}
