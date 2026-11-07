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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONObject;

/** Resource-bound socket credentials. Caller identity must come from the v3 Bearer boundary. */
@Service
public class ApiV3SocketTicketService {
    public static final long TTL_MILLIS = 60_000;
    public static final String SCOPE = "notifications.read";
    @Inject private ApiV3SocketTicketRepository tickets;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3ClientRepository clients;
    @Inject private UserRepository users;
    @Inject private ApiV3RealtimeTargetService targets;
    public record Issued(String ticket, long expiresIn) { }
    public record Grant(String userId, String parentTokenId, long expiresAt, String channel, String targetId) {
        public Grant(String userId, String parentTokenId, long expiresAt) {
            this(userId, parentTokenId, expiresAt, ApiV3SocketTicket.CHANNEL_NOTIFICATIONS, "");
        }
    }
    protected long now() { return System.currentTimeMillis(); }
    public void requireNoTransaction(){if(tickets.hasTransactionBegun())throw new IllegalStateException("socket publication before transaction end");}
    /** Small committed batches; keep expired grant hashes for one day, never plaintext credentials. */
    public int removeExpired() throws Exception {
        requireNoTransaction();
        final var tx=tickets.beginTransaction();
        try {
            final var rows=tickets.expiredBatchForUpdate(now()-86_400_000L);
            for(final var row:rows)tickets.remove(row.getString(Keys.OBJECT_ID));
            tx.commit();return rows.size();
        }finally{if(tx.isActive())tx.rollback();}
    }

    public Issued issue(final String actor, final String parentTokenId) throws Exception {
        return issue(actor, parentTokenId, ApiV3SocketTicket.CHANNEL_NOTIFICATIONS, "");
    }
    public Issued issueArticle(final String actor, final String parentTokenId, final String articleId) throws Exception {
        return issue(actor, parentTokenId, ApiV3SocketTicket.CHANNEL_ARTICLE, articleId);
    }
    public Issued issueChat(final String actor, final String parentTokenId) throws Exception {
        return issue(actor, parentTokenId, ApiV3SocketTicket.CHANNEL_CHAT, "");
    }
    public Issued issueMessages(final String actor,final String parentTokenId)throws Exception {
        return issue(actor,parentTokenId,ApiV3SocketTicket.CHANNEL_MESSAGES,"");
    }
    private Issued issue(final String actor, final String parentTokenId, final String channel, final String targetId) throws Exception {
        if (tickets.hasTransactionBegun()) throw new IllegalStateException("socket grant transaction already active");
        final var tx = tickets.beginTransaction();
        try {
            final long now = now();
            final JSONObject parent = parent(actor, parentTokenId, now, true, scope(channel, targetId));
            if (null == parent) throw unauthorized();
            if (!targetVisible(actor, channel, targetId)) throw new ApiV3Exception(404, "not_found", "文章不存在或不可访问");
            final long expires = Math.min(Math.addExact(now, TTL_MILLIS), parent.getLong(ApiV3Token.EXPIRES_AT));
            if (expires - now < 1000) throw unauthorized();
            final String ticket = ApiV3TokenUtil.generate();
            tickets.add(new JSONObject().put(Keys.OBJECT_ID, ApiV3TokenUtil.hash(ticket))
                    .put(ApiV3SocketTicket.USER_ID, actor).put(ApiV3SocketTicket.PARENT_TOKEN_ID, parentTokenId)
                    .put(ApiV3SocketTicket.CHANNEL, channel).put(ApiV3SocketTicket.TARGET_ID, targetId)
                    .put(ApiV3SocketTicket.CREATED_AT, now).put(ApiV3SocketTicket.EXPIRES_AT, expires)
                    .put(ApiV3SocketTicket.CONSUMED_AT, 0L));
            tx.commit();
            return new Issued(ticket, (expires - now) / 1000);
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    public Grant consume(final String ticket) throws Exception {
        if (!ApiV3TokenUtil.isValidFormat(ticket)) throw unauthorized();
        if (tickets.hasTransactionBegun()) throw new IllegalStateException("socket grant transaction already active");
        final var tx = tickets.beginTransaction();
        try {
            final long now = now();
            final JSONObject row = tickets.getForUpdate(ApiV3TokenUtil.hash(ticket));
            if (null == row || row.getLong(ApiV3SocketTicket.CONSUMED_AT) != 0
                    || row.getLong(ApiV3SocketTicket.EXPIRES_AT) <= now) throw unauthorized();
            final String actor = row.getString(ApiV3SocketTicket.USER_ID);
            final String parentId = row.getString(ApiV3SocketTicket.PARENT_TOKEN_ID);
            final String channel = row.optString(ApiV3SocketTicket.CHANNEL, ApiV3SocketTicket.CHANNEL_NOTIFICATIONS);
            final String targetId = row.optString(ApiV3SocketTicket.TARGET_ID, "");
            final JSONObject parent = parent(actor, parentId, now, true, scope(channel, targetId));
            if (null == parent || !targetVisible(actor, channel, targetId)) throw unauthorized();
            tickets.update(row.getString(Keys.OBJECT_ID), new JSONObject().put(ApiV3SocketTicket.CONSUMED_AT, now), ApiV3SocketTicket.CONSUMED_AT);
            final Grant grant = new Grant(actor, parentId, parent.getLong(ApiV3Token.EXPIRES_AT), channel, targetId);
            tx.commit();return grant;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    /** Recheck before delivering data and on heartbeat: no cached user or client status. */
    public boolean authorized(final Grant grant) throws Exception {
        if (null == grant || grant.expiresAt() <= now()) return false;
        final String scope = scope(grant.channel(), grant.targetId());
        return scope != null && null != parent(grant.userId(), grant.parentTokenId(), now(), false, scope)
                && targetVisible(grant.userId(), grant.channel(), grant.targetId());
    }

    private static String scope(final String channel, final String targetId) {
        if (ApiV3SocketTicket.CHANNEL_NOTIFICATIONS.equals(channel) && "".equals(targetId)) return SCOPE;
        if (ApiV3SocketTicket.CHANNEL_ARTICLE.equals(channel) && targetId != null && targetId.matches("[0-9]{1,32}")) return "content.read";
        if (ApiV3SocketTicket.CHANNEL_CHAT.equals(channel) && "".equals(targetId)) return "content.read";
        if (ApiV3SocketTicket.CHANNEL_MESSAGES.equals(channel) && "".equals(targetId)) return "messages.read";
        return null;
    }
    private boolean targetVisible(final String actor, final String channel, final String targetId) throws Exception {
        return scope(channel, targetId) != null && (!ApiV3SocketTicket.CHANNEL_ARTICLE.equals(channel) || targets.articleVisible(actor, targetId));
    }
    private JSONObject parent(final String actor, final String parentId, final long now, final boolean lock, final String scope) throws Exception {
        if (scope == null || null == actor || !actor.matches("[0-9]{1,32}") || null == parentId || !parentId.matches("[0-9]{1,32}")) return null;
        final var rows = tokens.select("SELECT t.oId,t.userId,t.scope,t.expiresAt FROM `" + tokens.getName()
                + "` t JOIN `" + clients.getName() + "` c ON c.clientId=t.clientId JOIN `" + users.getName()
                + "` u ON u.oId=t.userId WHERE t.oId=? AND t.userId=? AND t.tokenType=? AND t.state=? AND t.expiresAt>?"
                + " AND u.userStatus=? AND c.status=? LIMIT 1" + (lock ? " FOR UPDATE" : ""),
                parentId, actor, ApiV3Token.TYPE_ACCESS, ApiV3Token.STATE_ACTIVE, now, UserExt.USER_STATUS_C_VALID, ApiV3Client.STATUS_ACTIVE);
        if (rows.isEmpty() || !ApiV3TokenMgmtService.hasScope(rows.getFirst().optString(ApiV3Token.SCOPE), scope)) return null;
        return rows.getFirst();
    }
    private static ApiV3Exception unauthorized() { return new ApiV3Exception(401, "unauthorized", "实时连接凭据无效"); }
}
