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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 通过 Bearer 身份发送普通文本，所有持久化写入与重放回执一起提交。 */
@Service
public class ApiV3ChatPublicationService {
    @Inject private UserRepository users;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ChatMessageValidationService validation;
    @Inject private ChatMessageRestrictionQueryService restrictions;
    @Inject private ChatMessagePenaltyMgmtService penalties;
    @Inject private ChatMessageMgmtService messages;
    @Inject private CloudService cloud;
    @Inject private NotificationMgmtService notifications;
    @Inject private ChatroomProcessor delivery;
    @Inject private ChatMessageNoticeService notices;
    protected long now() { return System.currentTimeMillis(); }
    private record Published(ApiV3IdempotencyMgmtService.Execution execution, JSONObject message,
                             ChatMessageAbuseState.Repeat repeat, ChatMessagePenaltyMgmtService.Result penalty, boolean card) { }
    private record RepeatDecision(String name, String content, ChatMessageAbuseState.Repeat decision) { }
    public JSONObject publishCard(final JSONObject viewer, final String kind, final JSONObject body, final String key) throws Exception {
        final JSONObject card = switch (kind) {
            case "music" -> ApiV3ChatCardInputs.music(body);
            case "weather" -> ApiV3ChatCardInputs.weather(body);
            default -> throw ApiV3WriteInputs.invalid();
        };
        return publishInternal(viewer, "", "", key, kind, card);
    }
    public JSONObject publish(final JSONObject viewer, final String content, final String client, final String key) throws Exception {
        ChatMessageText.requireOrdinary(content);
        return publishInternal(viewer, content, client, key, null, null);
    }
    private JSONObject publishInternal(final JSONObject viewer, final String content, final String client, final String key,
                                      final String kind, final JSONObject card) throws Exception {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        if (!actor.matches("[0-9]{1,19}")) throw new ApiV3Exception(401, "unauthorized", "登录凭据无效");
        if (client == null || client.length() > 128 || client.indexOf(0) >= 0) throw ApiV3WriteInputs.invalid();
        final var saved = new AtomicReference<JSONObject>();
        final var penalty = new AtomicReference<ChatMessagePenaltyMgmtService.Result>();
        final var repeat = new AtomicReference<RepeatDecision>();
        final var confirmed = new AtomicReference<String>();
        final Published result = points.executeWithTransferLocks(actor, List.of(Pointtransfer.ID_C_SYS), () -> {
            try (final var caches = ArticlePublicationCaches.open(users::hasTransactionBegun)) {
                ArticlePublicationCaches.afterTransaction("chat-user:" + actor, () -> {
                    try { users.refreshSettingsCache(actor); }
                    catch (final Exception e) { users.evictSettingsCaches(actor); }
                });
                final ApiV3IdempotencyMgmtService.Mutation mutation = () -> {
                    final JSONObject current = users.getForUpdate(actor);
                    if (current == null || current.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID) {
                        throw new ApiV3Exception(404, "not_found", "用户不存在或不可用");
                    }
                    if (current.optString("userPhone").isBlank()) throw new ApiV3Exception(403, "phone_required", "请先绑定手机号码");
                    final String prepared;
                    try { prepared = card == null ? validation.prepare(current, content)
                            : ApiV3ChatCardInputs.content(kind, ApiV3ChatCardInputs.prepare(kind, card, text -> validation.prepare(current, text))); }
                    catch (final ChatMessageValidation.Rejected e) { throw new ApiV3Exception(e.status, e.code, e.getMessage()); }
                    if (card == null) ChatMessageText.requireOrdinary(prepared);
                    final long time = now();
                    ChatMessageRestrictions.check(restrictions.readInCurrentTransaction(actor),
                            DataModelService.hasPermission(current.optString(User.USER_ROLE), 3), prepared, time, () -> {
                                if (prepared.equals(confirmed.get())) return true;
                                final boolean value = ChatMessageAbuseState.confirmRisk(actor, prepared);
                                if (value) confirmed.set(prepared);
                                return value;
                            });
                    // 存储层自动重试复用同一次风控判定，已成功请求的回放不进入此回调。
                    final String name = current.getString(User.USER_NAME);
                    if (repeat.get() == null || !name.equals(repeat.get().name()) || !prepared.equals(repeat.get().content())) {
                        repeat.set(new RepeatDecision(name, prepared, ChatMessageAbuseState.repeated(name, prepared)));
                    }
                    penalty.set(repeat.get().decision() == ChatMessageAbuseState.Repeat.PENALIZE ? penalties.applyInCurrentTransaction(actor, time) : null);
                    final JSONObject envelope = new JSONObject().put("userName", current.getString(User.USER_NAME))
                            .put("userNickname", current.optString(UserExt.USER_NICKNAME)).put("userAvatarURL", current.optString(UserExt.USER_AVATAR_URL))
                            .put("userOId", Long.parseLong(actor)).put("content", prepared).put("time", time)
                            .put("sysMetal", cloud.getEnabledMedal(actor));
                    final String mark = ChatMessageClients.normalize(client);
                    if (!mark.isEmpty()) envelope.put("client", mark);
                    final JSONObject message = messages.saveInCurrentTransaction(envelope, actor);
                    users.updateColumnsInCurrentTransaction(actor, new JSONObject().put(UserExt.USER_LATEST_CMT_TIME, time));
                    saved.set(message);
                    return new JSONObject().put("id", message.getString(Keys.OBJECT_ID)).put("createdAt", time);
                };
                final ApiV3IdempotencyMgmtService.Execution outcome;
                if (card == null) outcome = idempotency.executeWithOutcome(actor, "chat.messages.create", key,
                        new JSONObject().put("content", content).put("client", client), mutation);
                else {
                    // 先锁定当前用户，再读取回执；回放成功请求也遵循此顺序。
                    final ApiV3MutationStore store = new ApiV3MutationStore() {
                        public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                        public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                        public JSONObject getForUpdate(String id) throws RepositoryException {
                            final JSONObject current = users.getForUpdate(actor);
                            if (current == null || current.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
                            return requests.getForUpdate(id);
                        }
                        public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
                        public void update(String id, JSONObject row, String... columns) throws RepositoryException { requests.update(id, row, columns); }
                    };
                    outcome = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, actor, "chat." + kind + ".create", key, card, mutation);
                }
                return new Published(outcome, saved.get(), repeat.get() == null ? null : repeat.get().decision(), penalty.get(), card != null);
            }
        });
        if (result.execution().applied()) afterPublished(actor, result);
        return result.execution().result();
    }
    private void afterPublished(final String actor, final Published result) {
        if (users.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("chat delivery before transaction end");
        ChatMessageAbuseState.accepted(result.message().getString("content"));
        afterSharedAdmission(actor,result.message().getString("userName"),result.repeat(),result.penalty());
        safely(() -> {
            if (result.card()) delivery.afterApiCardPublished(result.message());
            else delivery.afterApiMessagePublished(result.message());
        });
    }
    /** Shared topic/ordinary-message admission effects, strictly after their owner transaction. */
    public void afterSharedAdmission(final String actor,final String name,final ChatMessageAbuseState.Repeat repeat,final ChatMessagePenaltyMgmtService.Result penalty){
        if(users.hasTransactionBegun()||ArticlePublicationCaches.active())throw new IllegalStateException("chat admission delivery before transaction end");
        if (penalty != null && !penalty.transferId().isEmpty()) {
            safely(() -> points.indexTransferAsync(actor, Pointtransfer.ID_C_SYS, penalty.transferId()));
            safely(() -> notifications.publishReadStateRefresh(actor));
        }
        if (repeat == ChatMessageAbuseState.Repeat.WARN) {
            safely(() -> notices.send("监测到 @" + name + " 疑似使用自动复读机插件，请不要频繁复读。"));
        } else if (penalty != null) {
            safely(() -> notices.send("由于 @" + name + " 频繁复读，现处以禁言15分钟"
                    + (penalty.transferId().isEmpty() ? "。积分不足，本次未扣分。" : "、扣除积分30的处罚。")));
        }
    }
    @FunctionalInterface private interface Effect { void run() throws Exception; }
    private static void safely(final Effect work) {
        try { work.run(); }
        catch (final Exception e) { LogManager.getLogger(ApiV3ChatPublicationService.class).error("Deliver committed chat message failed", e); }
    }
}
