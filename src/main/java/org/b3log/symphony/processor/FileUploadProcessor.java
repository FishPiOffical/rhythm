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

import com.idrsolutions.image.png.PngCompressor;
import com.qiniu.cdn.CdnManager;
import com.qiniu.cdn.CdnResult;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import jodd.net.MimeTypes;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.latke.util.URLs;
import org.b3log.symphony.Server;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.UploadRepository;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.service.LogsService;
import org.b3log.symphony.util.*;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.b3log.symphony.util.Symphonys.QN_ENABLED;

/**
 * File upload to local.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 3.0.0.0, Feb 11, 2020
 * @since 1.4.0
 */
@Singleton
public class FileUploadProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(FileUploadProcessor.class);

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    @Inject
    private UploadRepository uploadRepository;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();

        final FileUploadProcessor fileUploadProcessor = beanManager.getReference(FileUploadProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        Dispatcher.get("/upload/{yyyy}/{MM}/{file}", fileUploadProcessor::getFile);
        Dispatcher.post("/upload", fileUploadProcessor::uploadFile, loginCheck::handle);
        // Dispatcher.post("/fetch-upload", fileUploadProcessor::fetchUpload);
    }

    /**
     * Gets file by the specified URL.
     *
     * @param context the specified context
     */
    public void getFile(final RequestContext context) {
        if (QN_ENABLED) {
            return;
        }

        final Response response = context.getResponse();

        final String uri = context.requestURI();
        String key = StringUtils.substringAfter(uri, "/upload/");
        key = StringUtils.substringBeforeLast(key, "?"); // Erase Qiniu template
        key = StringUtils.substringBeforeLast(key, "?"); // Erase Qiniu template

        String path = Symphonys.UPLOAD_LOCAL_DIR + key;
        path = URLs.decode(path);

        try {
            if (!FileUtil.isExistingFile(new File(path)) ||
                    !FileUtil.isExistingFolder(new File(Symphonys.UPLOAD_LOCAL_DIR)) ||
                    !new File(path).getCanonicalPath().startsWith(new File(Symphonys.UPLOAD_LOCAL_DIR).getCanonicalPath())) {
                context.sendError(404);
                return;
            }

            final byte[] data = IOUtils.toByteArray(new FileInputStream(path));

            final String ifNoneMatch = context.header("If-None-Match");
            final String etag = "\"" + DigestUtils.md5Hex(new String(data)) + "\"";

            context.setHeader("Cache-Control", "public, max-age=31536000");
            context.setHeader("ETag", etag);
            context.setHeader("Server", "Sym File Server (v" + Server.VERSION + ")");
            context.setHeader("Access-Control-Allow-Origin", "*");
            final String ext = StringUtils.substringAfterLast(path, ".");
            final String mimeType = MimeTypes.getMimeType(ext);
            context.addHeader("Content-Type", mimeType);

            if (etag.equals(ifNoneMatch)) {
                context.addHeader("If-None-Match", "false");
                context.setStatus(304);
            } else {
                context.addHeader("If-None-Match", "true");
            }

            response.sendBytes(data);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Gets a file failed", e);
        }
    }

    /**
     * Uploads file.
     *
     * @param context the specified context
     */
    final private static SimpleCurrentLimiter uploadLimiter = new SimpleCurrentLimiter(60 * 60 * 24, 200);
    public synchronized void uploadFile(final RequestContext context) {
        final JSONObject result = Results.newFail();
        context.renderJSONPretty(result);

        final Request request = context.getRequest();
        final int maxSize = (int) Symphonys.UPLOAD_FILE_MAX;

        final Map<String, String> succMap = new HashMap<>();
        final List<FileUpload> allFiles = request.getFileUploads("file[]");
        final List<FileUpload> files = new ArrayList<>();
        String fileName;

        Auth auth;
        UploadManager uploadManager = null;
        String uploadToken = null;
        if (QN_ENABLED) {
            auth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);
            uploadToken = auth.uploadToken(Symphonys.UPLOAD_QINIU_BUCKET);
            Configuration cfg = new Configuration(Region.autoRegion());
            uploadManager = new UploadManager(cfg);
        }

        final JSONObject data = new JSONObject();
        final List<String> errFiles = new ArrayList<>();

        boolean checkFailed = false;
        String suffix = "";
        final String[] allowedSuffixArray = Symphonys.UPLOAD_SUFFIX.split(",");
        for (final FileUpload file : allFiles) {
            suffix = Headers.getSuffix(file);
            if (!Strings.containsIgnoreCase(suffix, allowedSuffixArray)) {
                checkFailed = true;
                break;
            }

            if (maxSize < file.getData().length) {
                continue;
            }

            files.add(file);
        }

        if (checkFailed) {
            for (final FileUpload file : allFiles) {
                fileName = file.getFilename();
                errFiles.add(fileName);
            }

            data.put("errFiles", errFiles);
            data.put("succMap", succMap);
            result.put(Common.DATA, data);
            result.put(Keys.CODE, 1);
            String msg = langPropsService.get("invalidFileSuffixLabel");
            msg = StringUtils.replace(msg, "${suffix}", suffix);
            result.put(Keys.MSG, msg);
            return;
        }

        final List<byte[]> fileBytes = new ArrayList<>();
        final String[] staticPictureSuffixArray = {"jpg", "jpeg", "png"};
        final String[] animatePictureSuffixArray = {"gif"};
        final String[] audioSuffixArray = {"mp3"};
        final String[] videoSuffixArray = {"mp4"};
        for (final FileUpload file : files) {
            byte[] bytes = file.getData();
            int before = bytes.length;
            suffix = Headers.getSuffix(file);
            if (Strings.contains(suffix, staticPictureSuffixArray)) {
                // 静态图片处理
                long start = System.currentTimeMillis();
                try {
                    if (suffix.equals("png")) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        PngCompressor.compress(new ByteArrayInputStream(bytes), byteArrayOutputStream);
                        bytes = byteArrayOutputStream.toByteArray();
                    } else {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        Thumbnails.of(new ByteArrayInputStream(bytes))
                                .scale(1f)
                                .outputQuality(0.35f)
                                .toOutputStream(byteArrayOutputStream);
                        bytes = byteArrayOutputStream.toByteArray();
                    }
                    LOGGER.log(Level.INFO, "Compressed " + file.getFilename() + " as a static picture, before: " + before / 1024 + "KB, after: " + bytes.length / 1024 + "KB, time: " + (System.currentTimeMillis() - start) + "ms");
                } catch (Exception e) {
                    LOGGER.log(Level.ERROR, "Unable to compress " + file.getFilename() + " as a static picture", e);
                }
            }

            if (Strings.contains(suffix, animatePictureSuffixArray)) {
                // 动态图片处理
                try {
                    //bytes = GifUtils.gifScale(bytes, 0.7, 0.7);
                    LOGGER.log(Level.INFO, "Compressed " + file.getFilename() + " as an animate picture, before: " + before / 1024 + "KB, after: " + bytes.length / 1024 + "KB");
                } catch (Exception e) {
                    LOGGER.log(Level.ERROR, "Unable to compress " + file.getFilename() + " as an animate picture", e);
                }
            }

            if (Strings.contains(suffix, audioSuffixArray)) {
                // 音频处理
                // LOGGER.log(Level.INFO, "Compressed " + file.getFilename() + " as an audio, before: " + before / 1024 + "KB, after: " + bytes.length / 1024 + "KB");
            }

            if (Strings.contains(suffix, videoSuffixArray)) {
                // 视频处理
                // LOGGER.log(Level.INFO, "Compressed " + file.getFilename() + " as a video, before: " + before / 1024 + "KB, after: " + bytes.length / 1024 + "KB");
            }
            fileBytes.add(bytes);
        }

        final CountDownLatch countDownLatch = new CountDownLatch(files.size());
        for (int i = 0; i < files.size(); i++) {
            // 检查该文件是否已经上传过
            String md5 = MD5Calculator.calculateMd5(fileBytes.get(i));
            final Query query = new Query().setFilter(new PropertyFilter("md5", FilterOperator.EQUAL, md5));
            try {
                final List<JSONObject> md5s = uploadRepository.getList(query);
                if (!md5s.isEmpty()) {
                    JSONObject md5in1 = md5s.get(0);
                    String url = md5in1.optString("path");
                    String[] filename = url.split("/");
                    String originalName = url.split("/")[filename.length - 1];
                    succMap.put(originalName, url);
                    countDownLatch.countDown();
                    LOGGER.log(Level.INFO, "Same MD5 " + originalName + " gives: " + url);
                    continue;
                }
            } catch (RepositoryException ignored) {
            }
            JSONObject user = Sessions.getUser();
            try {
                user = ApiProcessor.getUserByKey(context.param("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userName = user.optString(User.USER_NAME);
            // 没有重复文件，正常上传
            final FileUpload file = files.get(i);
            final String originalName = fileName = Escapes.sanitizeFilename(file.getFilename());
            // 检查上传次数
            if (!uploadLimiter.access(userName)) {
                errFiles.add(originalName);
                countDownLatch.countDown();
                LOGGER.log(Level.INFO, "Out of upload limit " + originalName + " userName: " + userName);
                continue;
            }
            try {
                String url;
                byte[] bytes;
                suffix = Headers.getSuffix(file);
                String name = StringUtils.substringBeforeLast(fileName, ".");
                final String uuid = StringUtils.substring(UUID.randomUUID().toString().replaceAll("-", ""), 0, 8);
                String regex = "[a-zA-Z0-9\\u4e00-\\u9fa5]";
                Matcher matcher = Pattern.compile(regex).matcher(name);
                StringBuilder stringBuilder = new StringBuilder();
                while (matcher.find()) {
                    stringBuilder.append(matcher.group());
                }
                name = stringBuilder.toString();
                fileName = name + '-' + uuid + "." + suffix;
                fileName = genFilePath(fileName);
                if (QN_ENABLED) {
                    bytes = fileBytes.get(i);
                    final String contentType = file.getContentType();
                    com.qiniu.http.Response response = uploadManager.put(bytes, fileName, uploadToken, null, contentType, false);
                    // 解析上传成功的结果
                    JSONObject putRet = new JSONObject(response.bodyString());
                    countDownLatch.countDown();
                    url = Symphonys.UPLOAD_QINIU_DOMAIN + "/" + putRet.optString("key");

                    // AI 图片审核（如果启用且是图片文件）
                    if (!CensorFactory.isImageCallbackMode() && isImageFile(suffix)) {
                        CensorResult censorResult = CensorFactory.getImageCensor().censor(url);
                        if (censorResult != null && censorResult.isBlocked()) {
                            // 违规图片，从七牛删除并刷新 CDN
                            LOGGER.log(Level.INFO, "[AI图片审核] 图片被拦截: " + url + ", 原因: " + censorResult.getType());
                            try {
                                Auth deleteAuth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);
                                BucketManager bucketManager = new BucketManager(deleteAuth, new Configuration(Region.autoRegion()));
                                bucketManager.delete(Symphonys.UPLOAD_QINIU_BUCKET, fileName);
                                // 刷新 CDN 缓存
                                CdnManager cdnManager = new CdnManager(deleteAuth);
                                cdnManager.refreshUrls(new String[]{url});
                                LOGGER.log(Level.INFO, "[AI图片审核] 已从七牛删除: " + fileName);
                            } catch (Exception deleteEx) {
                                LOGGER.log(Level.WARN, "[AI图片审核] 删除失败: " + fileName, deleteEx);
                            }
                            // 发送警告通知（不扣积分）
                            ChatChannel.sendAdminMsg(userName, "【AI审查】您上传的图片可能违规，已被拦截删除。\n如有疑问请在此处回复我。\n原因：" + censorResult.getType());
                            LogsService.censorLog(context, userName, "用户：" + userName + " 上传违规图片被AI拦截，原因：" + censorResult.getType());
                            errFiles.add(originalName);
                            continue;
                        }
                    }

                    succMap.put(originalName, url);
                } else {
                    bytes = fileBytes.get(i);
                    final Path path = Paths.get(Symphonys.UPLOAD_LOCAL_DIR, fileName);
                    path.getParent().toFile().mkdirs();
                    try (final OutputStream output = new FileOutputStream(path.toFile())) {
                        IOUtils.write(bytes, output);
                        countDownLatch.countDown();
                    }
                    url = Latkes.getServePath() + "/upload/" + fileName;

                    // AI 图片审核（如果启用且是图片文件）
                    if (!CensorFactory.isImageCallbackMode() && isImageFile(suffix)) {
                        CensorResult censorResult = CensorFactory.getImageCensor().censor(url);
                        if (censorResult != null && censorResult.isBlocked()) {
                            // 违规图片，删除本地文件并记录
                            LOGGER.log(Level.INFO, "[AI图片审核] 图片被拦截: " + url + ", 原因: " + censorResult.getType());
                            try {
                                path.toFile().delete();
                                LOGGER.log(Level.INFO, "[AI图片审核] 已删除本地文件: " + path);
                            } catch (Exception deleteEx) {
                                LOGGER.log(Level.WARN, "[AI图片审核] 删除失败: " + path, deleteEx);
                            }
                            // 发送警告通知（不扣积分）
                            ChatChannel.sendAdminMsg(userName, "【AI审查】您上传的图片可能违规，已被拦截删除。\n如有疑问请在此处回复我。\n原因：" + censorResult.getType());
                            LogsService.censorLog(context, userName, "用户：" + userName + " 上传违规图片被AI拦截，原因：" + censorResult.getType());
                            errFiles.add(originalName);
                            continue;
                        }
                    }

                    succMap.put(originalName, url);
                }
                // 记录到Upload表
                String ip = Requests.getRemoteAddr(request);
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String scene = context.param("scene");
                boolean isPublic = !"chat".equals(scene);
                // 写入数据库
                final Transaction transaction = uploadRepository.beginTransaction();
                uploadRepository.add(suffix, userName, ip, time, url, md5, isPublic);
                transaction.commit();
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Uploads file failed", e);

                errFiles.add(originalName);
            }
        }

        try {
            countDownLatch.await(1, TimeUnit.MINUTES);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Count down latch failed", e);
        }

        data.put("errFiles", errFiles);
        data.put("succMap", succMap);
        result.put(Common.DATA, data);
        result.put(Keys.CODE, StatusCodes.SUCC);
        result.put(Keys.MSG, "");
    }

    /**
     * Fetches the remote file and upload it.
     *
     * @param context the specified context
     */
    public void fetchUpload(final RequestContext context) {
        final JSONObject result = Results.newFail();
        context.renderJSONPretty(result);
        final JSONObject data = new JSONObject();

        final JSONObject requestJSONObject = context.requestJSON();
        final String originalURL = requestJSONObject.optString(Common.URL);
        if (!Strings.isURL(originalURL) || !StringUtils.startsWithIgnoreCase(originalURL, "http")) {
            return;
        }

        byte[] bytes;
        String contentType;
        try {
            final String host = new URL(originalURL).getHost();
            final String hostIp = InetAddress.getByName(host).getHostAddress();
            if (Networks.isInnerAddress(hostIp)) {
                return;
            }

            final HttpRequest req = HttpRequest.get(originalURL).header(Common.USER_AGENT, Symphonys.USER_AGENT_BOT);
            final HttpResponse res = req.connectionTimeout(3000).timeout(5000).send();
            res.close();
            if (200 != res.statusCode()) {
                return;
            }

            bytes = res.bodyBytes();
            contentType = res.contentType();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Fetch file [url=" + originalURL + "] failed", e);
            return;
        }

        final String suffix = Headers.getSuffix(contentType);
        final String[] allowedSuffixArray = Symphonys.UPLOAD_SUFFIX.split(",");
        if (!Strings.containsIgnoreCase(suffix, allowedSuffixArray)) {
            String msg = langPropsService.get("invalidFileSuffixLabel");
            msg = StringUtils.replace(msg, "${suffix}", suffix);
            result.put(Keys.MSG, msg);
            return;
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;

        if (Symphonys.QN_ENABLED) {
            final Auth auth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);
            final UploadManager uploadManager = new UploadManager(new Configuration());

            try {
                uploadManager.put(bytes, "e/" + fileName, auth.uploadToken(Symphonys.UPLOAD_QINIU_BUCKET), null, contentType, false);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Uploads to Qiniu failed", e);
            }

            data.put(Common.URL, Symphonys.UPLOAD_QINIU_DOMAIN + "/e/" + fileName);
            data.put("originalURL", originalURL);
        } else {
            fileName = FileUploadProcessor.genFilePath(fileName);
            final Path path = Paths.get(Symphonys.UPLOAD_LOCAL_DIR, fileName);
            path.getParent().toFile().mkdirs();
            try (final OutputStream output = new FileOutputStream(Symphonys.UPLOAD_LOCAL_DIR + fileName)) {
                IOUtils.write(bytes, output);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Writes output stream failed", e);
            }

            data.put(Common.URL, Latkes.getServePath() + "/upload/" + fileName);
            data.put("originalURL", originalURL);
        }

        result.put(Common.DATA, data);
        result.put(Keys.CODE, StatusCodes.SUCC);
        result.put(Keys.MSG, "");
    }

    /**
     * Generates upload file path for the specified file name.
     *
     * @param fileName the specified file name
     * @return "yyyy/MM/fileName"
     */
    public static String genFilePath(final String fileName) {
        final String date = DateFormatUtils.format(System.currentTimeMillis(), "yyyy/MM");

        return date + "/" + fileName;
    }

    /**
     * 判断文件后缀是否为图片类型
     *
     * @param suffix 文件后缀
     * @return 是否为图片文件
     */
    private static boolean isImageFile(final String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        final String[] imageSuffixes = {"jpg", "jpeg", "png", "gif", "webp", "bmp"};
        return Strings.containsIgnoreCase(suffix, imageSuffixes);
    }
}
