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

import com.qiniu.cdn.CdnManager;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.repository.AdminOperationTaskRepository;
import org.b3log.symphony.repository.OperationRepository;
import org.b3log.symphony.repository.UploadRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 新旧图片审核共用审核权、云端执行阶段和本地结算。 */
@Service
public class AdminImageReviewService {
    private static final Logger LOGGER = LogManager.getLogger(AdminImageReviewService.class);
    private static final String KIND = "IMAGE_REVIEW";
    public static final long WINDOW = TimeUnit.DAYS.toMillis(45);

    @Inject private UploadRepository uploads;
    @Inject private AdminOperationTaskRepository tasks;
    @Inject private UserRepository users;
    @Inject private OperationRepository operations;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;

    public JSONObject reserveLegacy(final String id, final String decision, final Request request,
                                    final JSONObject actor) throws Exception {
        final var tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final JSONObject accepted = reserveInCurrentTransaction(id, decision, request, actor, true);
            tx.commit();
            return accepted;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public JSONObject reserveInCurrentTransaction(final String id, final String decision,
                                                  final Request request, final JSONObject actor) throws Exception {
        return reserveInCurrentTransaction(id, decision, request, actor, false);
    }

    private JSONObject reserveInCurrentTransaction(final String id, final String decision, final Request request,
                                                   final JSONObject actor, final boolean legacy) throws Exception {
        if (!tasks.hasTransactionBegun()) throw new IllegalStateException("图片审核需要活动事务");
        if (id == null || !id.matches("[1-9][0-9]{0,18}")
                || !("normal".equals(decision) || "illegal".equals(decision))) throw ApiV3WriteInputs.invalid();
        final long imageTime;
        try {
            imageTime = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw ApiV3WriteInputs.invalid();
        }
        final long now = System.currentTimeMillis();
        final JSONObject image = readImageForUpdate(id);
        if (image == null) throw ApiV3Requests.notFound();
        if (!isPublic(image)) {
            throw new ApiV3Exception(409, "image_already_reviewed", "该图片已完成审核");
        }
        final String actorId = actor.getString(Keys.OBJECT_ID);
        final JSONObject prior = tasks.lock(taskId(id));
        if (prior != null) {
            final JSONObject payload = payload(prior);
            if (!actorId.equals(prior.getString("actorId")) || !decision.equals(payload.getString("decision"))) {
                throw new ApiV3Exception(409, "image_review_pending", "该图片已有审核任务，请核对原任务");
            }
            return receipt(id, decision);
        }
        if (imageTime < now - WINDOW) {
            throw new ApiV3Exception(422, "image_review_expired", "此图片已超过45天，无法审核");
        }
        final JSONObject payload = new JSONObject().put("decision", decision).put("actorName", actor.getString(User.USER_NAME));
        if ("illegal".equals(decision)) {
            final String path = image.getString("path");
            final String domain = Symphonys.UPLOAD_QINIU_DOMAIN;
            final String prefix = domain == null || domain.isBlank() ? "" : domain.replaceAll("/+$", "") + "/";
            if (!Symphonys.QN_ENABLED || prefix.isEmpty() || !path.startsWith(prefix)
                    || path.length() <= prefix.length()) {
                throw new ApiV3Exception(422, "image_storage_unsupported", "此图片不支持云端删除");
            }
            final var uploaders = users.select("SELECT oId FROM `" + users.getName()
                    + "` WHERE userName=? LIMIT 1", image.optString("userName"));
            if (uploaders.isEmpty()) throw new ApiV3Exception(409, "image_uploader_missing", "上传者不存在，请先核对");
            final JSONObject uploader = uploaders.getFirst();
            final String auditData = legacy ? image.toString() : id;
            payload.put("path", path).put("key", path.substring(prefix.length()))
                    .put("uploaderId", uploader.getString(Keys.OBJECT_ID)).put("uploaderName", image.getString("userName"))
                    .put("audit", Operation.newOperation(request, Operation.OPERATION_CODE_C_DELETE_PICTURE,
                            auditData.substring(0, Math.min(auditData.length(), 254)), actor));
        }
        tasks.add(new JSONObject().put(Keys.OBJECT_ID, taskId(id)).put("actorId", actorId).put("kind", KIND)
                .put("status", "illegal".equals(decision) ? "IMAGE_QUEUED" : "IMAGE_READY")
                .put("payload", payload.toString()).put("result", "{}").put("createdAt", now).put("updatedAt", now));
        return receipt(id, decision);
    }

    private JSONObject readImageForUpdate(final String id) throws RepositoryException {
        final var rows = uploads.select("SELECT * FROM `" + uploads.getName()
                + "` WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject complete(final JSONObject accepted, final String actorId) throws Exception {
        final String id = accepted.getString("oId");
        if (accepted.optInt("reviewed") == 1) return accepted; // 兼容旧版成功回执
        JSONObject task = advance(id, actorId, "IMAGE_QUEUED", "IMAGE_DELETING");
        if (task != null) {
            final JSONObject data = payload(task);
            try {
                deleteCloudObject(data.getString("key"));
            } catch (Exception failure) {
                suspend(id, actorId, "IMAGE_DELETING");
                return status(accepted, "UNKNOWN");
            }
            advance(id, actorId, "IMAGE_DELETING", "IMAGE_DELETED");
        }
        task = advance(id, actorId, "IMAGE_DELETED", "IMAGE_REFRESHING");
        if (task != null) {
            try {
                refreshCdn(payload(task).getString("path"));
            } catch (Exception failure) {
                suspend(id, actorId, "IMAGE_REFRESHING");
                return status(accepted, "UNKNOWN");
            }
            advance(id, actorId, "IMAGE_REFRESHING", "IMAGE_READY");
        }
        task = current(id, actorId);
        final String phase = task.getString("status");
        if ("IMAGE_DONE".equals(phase)) return status(accepted, "SUCCEEDED").put("reviewed", 1);
        if (!"IMAGE_READY".equals(phase)) return status(accepted, "UNKNOWN");
        final JSONObject data = payload(task);
        final String uploaderId = data.optString("uploaderId");
        final var destinations = uploaderId.isEmpty() ? List.of(actorId) : List.of(actorId, uploaderId);
        final Settlement settlement = points.executeWithTransferLocks(Pointtransfer.ID_C_SYS, destinations,
                () -> settle(id, actorId));
        if (settlement != null) {
            try {
                afterSettlement(actorId, settlement, data);
            } catch (final RuntimeException e) {
                LOGGER.warn("图片已结算，提交后发布失败，imageId={}", id, e);
            }
        }
        return status(accepted, "SUCCEEDED").put("reviewed", 1);
    }

    protected void deleteCloudObject(final String key) throws Exception {
        final Auth auth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);
        final var response = new BucketManager(auth, new Configuration(Region.autoRegion()))
                .delete(Symphonys.UPLOAD_QINIU_BUCKET, key);
        if (!response.isOK()) throw new IllegalStateException("云端删除未确认");
    }

    protected void refreshCdn(final String path) throws Exception {
        final Auth auth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);
        final var result = new CdnManager(auth).refreshUrls(new String[]{path});
        if (result.code != 200) throw new IllegalStateException("CDN 刷新未受理");
    }

    private record Settlement(String reward, String penalty, String uploaderId) { }

    private Settlement settle(final String id, final String actorId) throws Exception {
        final var tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final JSONObject image = readImageForUpdate(id);
            final JSONObject task = checkedTask(id, actorId);
            if ("IMAGE_DONE".equals(task.getString("status"))) return null;
            if (!"IMAGE_READY".equals(task.getString("status")) || image == null || !isPublic(image)) {
                throw new IllegalStateException("图片审核状态已变化");
            }
            final JSONObject data = payload(task);
            final boolean illegal = "illegal".equals(data.getString("decision"));
            final long now = System.currentTimeMillis();
            final String reward = points.transferAdminInCurrentTransaction(Pointtransfer.ID_C_SYS, actorId,
                    Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT, illegal ? 128 : 2, id, now,
                    illegal ? "参与图片审核奖励：违规图片" : "参与图片审核奖励：正常图片");
            final String uploaderId = data.optString("uploaderId");
            String penalty = "";
            if (illegal) {
                penalty = points.transferAdminPenaltyInCurrentTransaction(uploaderId, 500, "机器人罚单-上传违规文件", now);
                notifications.addAbusePointDeductNotificationInCurrentTransaction(uploaderId, penalty);
                uploads.update(id, new JSONObject().put("public", false)
                        .put("md5", "delete by " + data.getString("actorName")), "public", "md5");
                operations.add(data.getJSONObject("audit"));
            } else {
                uploads.update(id, new JSONObject().put("public", false), "public");
            }
            setStage(task, "IMAGE_DONE");
            tx.commit();
            return new Settlement(reward, penalty, uploaderId);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    private void afterSettlement(final String actorId, final Settlement result, final JSONObject data) {
        users.evictSettingsCaches(actorId);
        points.indexTransferAsync(Pointtransfer.ID_C_SYS, actorId, result.reward());
        if (!result.penalty().isEmpty()) {
            users.evictSettingsCaches(result.uploaderId());
            points.indexTransferAsync(result.uploaderId(), Pointtransfer.ID_C_SYS, result.penalty());
            notifications.publishRefreshNotification(result.uploaderId());
            ChatRoomBot.sendBotMsg("犯罪嫌疑人 @" + data.getString("uploaderName")
                    + "  由于上传违法文件/图片，被处以 500 积分的处罚，请引以为戒。\n@"
                    + data.getString("actorName") + "  处理人\n@adlered  审批人");
        }
    }

    private JSONObject current(final String id, final String actorId) throws RepositoryException {
        final var tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final JSONObject task = checkedTask(id, actorId);
            tx.commit();
            return task;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    private JSONObject advance(final String id, final String actorId, final String from, final String to) throws RepositoryException {
        final var tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final JSONObject task = checkedTask(id, actorId);
            final boolean matched = from.equals(task.getString("status"));
            if (matched) setStage(task, to);
            tx.commit();
            return matched ? task : null;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    private void suspend(final String id, final String actorId, final String phase) throws RepositoryException {
        LOGGER.warn("图片云端审核结果待核对，imageId={} phase={}", id, phase);
        final var tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final JSONObject task = checkedTask(id, actorId);
            if (phase.equals(task.getString("status"))) {
                task.put("result", new JSONObject().put("uncertainPhase", phase).toString());
                tasks.update(task.getString(Keys.OBJECT_ID), task, "result");
                setStage(task, "IMAGE_UNKNOWN");
            }
            tx.commit();
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    private void setStage(final JSONObject task, final String stage) throws RepositoryException {
        task.put("status", stage).put("updatedAt", System.currentTimeMillis());
        tasks.update(task.getString(Keys.OBJECT_ID), task, "status", "updatedAt");
    }

    private JSONObject checkedTask(final String id, final String actorId) throws RepositoryException {
        final JSONObject task = tasks.lock(taskId(id));
        if (task == null || !KIND.equals(task.optString("kind")) || !actorId.equals(task.optString("actorId"))) {
            throw new ApiV3Exception(409, "image_review_pending", "图片审核任务需要原操作员核对");
        }
        return task;
    }

    private static boolean isPublic(final JSONObject image) {
        return image.optBoolean("public") || image.optInt("public") == 1;
    }

    private static JSONObject payload(final JSONObject task) {
        return new JSONObject(task.getString("payload"));
    }

    private static String taskId(final String id) {
        return "image-review:" + id;
    }

    private static JSONObject receipt(final String id, final String decision) {
        return new JSONObject().put("oId", id).put("decision", decision).put("reviewed", 0);
    }

    private static JSONObject status(final JSONObject receipt, final String status) {
        return new JSONObject(receipt.toString()).put("status", status);
    }
}
