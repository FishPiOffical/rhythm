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
package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.util.Results;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 韵图（RhyPic）图床集成：签发一次性短期上传票据。
 *
 * <p>{@code upload.channel=rhypic} 时启用。前端（Vditor 等）上传前先调用
 * {@code POST /api/rhypic/upload-ticket} 获取 Ed25519 签名 JWT，再携带
 * {@code Authorization: Bearer <ticket>} 直传图床，文件流不经过 Rhythm 中转。
 * 约定见 RhyPic《AI设计要求》§3.3。</p>
 *
 * @version 1.0.0.0, Sep 26, 2026
 * @since 3.9.0
 */
@Singleton
public class RhypicProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(RhypicProcessor.class);

    /**
     * 票据有效期（秒）。图床侧要求 1 至 5 分钟。
     */
    private static final long TICKET_TTL_SECONDS = 180;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();

        final RhypicProcessor rhypicProcessor = beanManager.getReference(RhypicProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        Dispatcher.post("/api/rhypic/upload-ticket", rhypicProcessor::uploadTicket, loginCheck::handle);
    }

    /**
     * 签发一次性短期上传票据。需登录（Session 或 apiKey）。
     *
     * @param context the specified context
     */
    public void uploadTicket(final RequestContext context) {
        final JSONObject result = Results.newFail();
        context.renderJSONPretty(result);

        if (!Symphonys.RHPIC_ENABLED) {
            result.put(Keys.MSG, "图床通道未启用");
            return;
        }
        if (StringUtils.isBlank(Symphonys.RHPIC_UPLOAD_URL)
                || StringUtils.isBlank(Symphonys.RHPIC_TICKET_PRIVATE_KEY)) {
            result.put(Keys.MSG, "图床未配置上传地址或票据私钥");
            return;
        }

        // loginCheck 已把登录用户写入 context（Sessions/apiKey 均可）
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String oId = null == user ? "" : user.optString(Keys.OBJECT_ID);
        if (StringUtils.isBlank(oId)) {
            result.put(Keys.MSG, "请先登录后再上传");
            return;
        }

        try {
            final String ticket = signTicket(oId,
                    user.optString(User.USER_NAME), user.optString(UserExt.USER_NICKNAME));

            final JSONObject data = new JSONObject();
            data.put("ticket", ticket);
            data.put("uploadURL", Symphonys.RHPIC_UPLOAD_URL);
            data.put("expiresIn", TICKET_TTL_SECONDS);
            result.put(Common.DATA, data);
            result.put(Keys.CODE, StatusCodes.SUCC);
            result.put(Keys.MSG, "");
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Signs RhyPic upload ticket failed", e);
            result.put(Keys.MSG, "签发上传票据失败，请稍后重试");
        }
    }

    /**
     * 用配置的 Ed25519 私钥（base64 PKCS#8）签发票据 JWT：
     * {@code iss=rhythm, aud=rhypic, sub=oId, scope=file:upload, name, nickname, iat, exp, jti}.
     */
    private static String signTicket(final String oId, final String name, final String nickname) throws Exception {
        final byte[] keyBytes = Base64.getDecoder().decode(Symphonys.RHPIC_TICKET_PRIVATE_KEY);
        final PrivateKey key = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        final long now = System.currentTimeMillis() / 1000;
        final JSONObject payload = new JSONObject();
        payload.put("iss", "rhythm");
        payload.put("aud", "rhypic");
        payload.put("sub", oId);
        payload.put("scope", "file:upload");
        payload.put("name", name);
        payload.put("nickname", nickname);
        payload.put("iat", now);
        payload.put("exp", now + TICKET_TTL_SECONDS);
        payload.put("jti", UUID.randomUUID().toString().replace("-", ""));

        final String signingInput = base64Url("{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}")
                + "." + base64Url(payload.toString());
        final Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature.sign());
    }

    private static String base64Url(final String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(final byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // -----------------------------------------------------------------------
    // 服务端中转上传辅助（/upload 代理、TTS 音频、注册头像、数据导出等使用）
    // -----------------------------------------------------------------------

    /**
     * 上传文件部件：原始文件名、Content-Type 与字节内容。
     */
    public static final class UploadPart {

        /**
         * 原始文件名（仅保留末段，图床侧还会再次清洗）。
         */
        private final String filename;

        /**
         * Content-Type（可为空，图床会自行嗅探）。
         */
        private final String contentType;

        /**
         * 文件字节内容。
         */
        private final byte[] data;

        public UploadPart(final String filename, final String contentType, final byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getData() {
            return data;
        }
    }

    /**
     * 为指定用户签发一次性上传票据（服务端中转上传用）。
     *
     * @param oId      Rhythm 用户 oId（票据 sub，图床据此归属文件）
     * @param name     用户名（票据 name，图床自动开通账号时使用）
     * @param nickname 昵称（可为空）
     * @return 票据 JWT
     * @throws IllegalStateException 图床未启用/未配置或签发失败，消息为用户可读文本
     */
    public static String mintUploadTicket(final String oId, final String name, final String nickname) {
        if (!Symphonys.RHPIC_ENABLED) {
            throw new IllegalStateException("图床通道未启用");
        }
        if (StringUtils.isBlank(Symphonys.RHPIC_UPLOAD_URL)
                || StringUtils.isBlank(Symphonys.RHPIC_TICKET_PRIVATE_KEY)) {
            throw new IllegalStateException("图床未配置上传地址或票据私钥");
        }
        if (StringUtils.isBlank(oId) || StringUtils.isBlank(name)) {
            throw new IllegalStateException("缺少用户信息，无法签发上传票据");
        }
        try {
            return signTicket(oId, name, nickname);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Signs RhyPic upload ticket failed", e);
            throw new IllegalStateException("签发上传票据失败，请稍后重试");
        }
    }

    /**
     * 把文件经 multipart 转发到 RhyPic 上传接口（{@code POST {uploadURL}/api/v1/files}，
     * 字段名 {@code file}，可多文件）。票据身份下图床按 Vditor 兼容结构响应。
     *
     * @param ticket 上传票据（单请求一张，覆盖本次全部文件）
     * @param parts  文件部件列表
     * @return 图床响应 JSON：成功为 {@code {code:0, msg:"", data:{succMap, errFiles}}}，
     * 失败可能为 Vditor 兼容错误（HTTP 200 + code=1）或统一错误结构 {@code {error:{code,message}}}
     * @throws IOException 网络失败或响应不是合法 JSON
     */
    public static JSONObject uploadFiles(final String ticket, final List<UploadPart> parts) throws IOException {
        if (null == parts || parts.isEmpty()) {
            throw new IOException("没有可上传的文件");
        }
        final String boundary = "----RhythmRhypic" + UUID.randomUUID().toString().replace("-", "");
        final HttpURLConnection conn = (HttpURLConnection) new URL(
                Symphonys.RHPIC_UPLOAD_URL + "/api/v1/files").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10 * 1000);
            conn.setReadTimeout(300 * 1000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Authorization", "Bearer " + ticket);
            conn.setChunkedStreamingMode(64 * 1024);

            try (final OutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
                for (final UploadPart part : parts) {
                    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                            + escapeMultipartFilename(part.getFilename()) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Type: " + (StringUtils.isBlank(part.getContentType())
                            ? "application/octet-stream" : part.getContentType()) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(part.getData());
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            final int status = conn.getResponseCode();
            final InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            final String body = null == in ? "" : IOUtils.toString(in, StandardCharsets.UTF_8);
            try {
                return new JSONObject(body);
            } catch (final Exception e) {
                throw new IOException("图床响应非法（HTTP " + status + "）");
            }
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException("连接图床失败: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 调 RhyPic 内部删除接口（{@code POST {uploadURL}/internal/v1/files/delete}，
     * Bearer 内部令牌鉴权），用于服务端清理图床文件（如 TTS 音频）。
     *
     * @param fileID 图床文件 ID
     * @return 图床 HTTP 状态码；网络失败返回 -1
     */
    public static int internalDeleteFile(final long fileID) {
        if (StringUtils.isBlank(Symphonys.RHPIC_INTERNAL_TOKEN)) {
            LOGGER.log(Level.WARN, "RhyPic internal token not configured [upload.rhypic.internalToken], skip deleting file [" + fileID + "]");
            return -1;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(
                    Symphonys.RHPIC_UPLOAD_URL + "/internal/v1/files/delete").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10 * 1000);
            conn.setReadTimeout(30 * 1000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + Symphonys.RHPIC_INTERNAL_TOKEN);
            try (final OutputStream out = conn.getOutputStream()) {
                out.write(new JSONObject().put("file_id", fileID).toString()
                        .getBytes(StandardCharsets.UTF_8));
            }
            return conn.getResponseCode();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Calls RhyPic internal delete failed [fileId=" + fileID + "]", e);
            return -1;
        } finally {
            if (null != conn) {
                conn.disconnect();
            }
        }
    }

    /**
     * 转义 multipart Content-Disposition 中的文件名（反斜杠与双引号）。
     */
    private static String escapeMultipartFilename(final String filename) {
        if (null == filename) {
            return "unnamed";
        }
        String name = filename.replace("\\", "_").replace("\"", "_");
        final int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isEmpty() ? "unnamed" : name;
    }
}
