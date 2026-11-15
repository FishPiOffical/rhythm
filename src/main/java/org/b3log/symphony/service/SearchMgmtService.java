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

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.net.MimeTypes;
import okio.Utf8;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;

/**
 * Search management service.
 * <p>
 * Uses <a href="https://www.elastic.co/products/elasticsearch">Elasticsearch</a> or
 * <a href="https://www.algolia.com">Algolia</a> as the underlying engine.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.3.2.9, Aug 31, 2018
 * @since 1.4.0
 */
@Service
public class SearchMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(SearchMgmtService.class);

    /**
     * Elasticsearch index name.
     */
    public static final String ES_INDEX_NAME = "symphony";

    /**
     * Rebuilds ES index.
     */
    public void rebuildESIndex() {
        try {
            HttpRequest.delete(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME).timeout(3000).send();
            HttpRequest.put(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME).timeout(3000).send();

            final JSONObject mapping = new JSONObject();
            final JSONObject article = new JSONObject();
            mapping.put(Article.ARTICLE, article);
            final JSONObject properties = new JSONObject();
            article.put("properties", properties);
            final JSONObject title = new JSONObject();
            properties.put(Article.ARTICLE_TITLE, title);
            title.put("type", "string");
            title.put("analyzer", "ik_smart");
            title.put("search_analyzer", "ik_smart");
            final JSONObject content = new JSONObject();
            properties.put(Article.ARTICLE_CONTENT, content);
            content.put("type", "string");
            content.put("analyzer", "ik_smart");
            content.put("search_analyzer", "ik_smart");

            HttpRequest.post(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME + "/" + Article.ARTICLE + "/_mapping").bodyText(mapping.toString()).timeout(3000).contentTypeJson();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Removes index failed", e);
        }
    }

    /**
     * Rebuilds Algolia index.
     */
    public void rebuildAlgoliaIndex() {
        final int maxRetries = 3;
        int retries = 1;

        final String appId = Symphonys.ALGOLIA_APP_ID;
        final String index = Symphonys.ALGOLIA_INDEX;
        final String key = Symphonys.ALGOLIA_ADMIN_KEY;

        while (retries <= maxRetries) {
            String host = appId + "-" + retries + ".algolianet.com";

            try {
                final HttpResponse response = HttpRequest.post("https://" + host + "/1/indexes/" + index + "/clear").
                        header("X-Algolia-API-Key", key).
                        header("X-Algolia-Application-Id", appId).timeout(5000).send();
                if (200 != response.statusCode()) {
                    LOGGER.warn(response.toString());
                }
                break;
            } catch (final Exception e) {
                LOGGER.log(Level.WARN, "Clear index failed", e);

                retries++;
                if (retries > maxRetries) {
                    LOGGER.log(Level.ERROR, "Clear index failed", e);
                }
            }
        }
    }

    /**
     * Updates/Adds indexing the specified document in ES.
     *
     * @param doc  the specified document
     * @param type the specified document type
     */
    public void updateESDocument(final JSONObject doc, final String type) {
        try {
            final JSONObject payload = new JSONObject();
            payload.put("doc", doc);
            payload.put("upsert", doc);

            HttpRequest.post(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME + "/" + type + "/" + doc.optString(Keys.OBJECT_ID) + "/_update").
                    bodyText(payload.toString()).contentTypeJson().timeout(5000).sendAsync();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Updates doc failed", e);
        }
    }

    /** 后台维护任务使用可观察失败的同步版本，不能调用吞错的历史异步入口。 */
    public void updateAdminArticleIndex(final JSONObject article) {
        if (!Symphonys.ES_ENABLED && !Symphonys.ALGOLIA_ENABLED) {
            throw new IllegalStateException("未启用搜索索引");
        }
        if (Symphonys.ES_ENABLED) {
            final JSONObject payload = new JSONObject().put("doc", article).put("upsert", article);
            requireSearchSuccess(HttpRequest.post(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME + "/"
                    + Article.ARTICLE + "/" + article.optString(Keys.OBJECT_ID) + "/_update")
                    .bodyText(payload.toString()).contentTypeJson().connectionTimeout(5000).timeout(5000).send());
        }
        if (Symphonys.ALGOLIA_ENABLED) {
            final JSONObject doc = new JSONObject(article.toString());
            String content = Jsoup.parse(Markdowns.toHTML(doc.optString(Article.ARTICLE_CONTENT))).text();
            doc.put(Article.ARTICLE_CONTENT, content);
            while (Utf8.size(doc.toString()) >= 9000 && !content.isEmpty()) {
                int end = Math.max(0, content.length() - 128);
                if (end > 0 && Character.isHighSurrogate(content.charAt(end - 1))) end--;
                content = content.substring(0, end);
                doc.put(Article.ARTICLE_CONTENT, content);
            }
            final HttpResponse response = algoliaRequest("PUT", "/" + doc.optString(Keys.OBJECT_ID))
                    .bodyText(doc.toString()).contentTypeJson().send();
            awaitAlgoliaTask(response);
        }
    }

    /** 清空与建映射也在后台任务线程执行；非成功 HTTP 响应必须令任务失败。 */
    public void rebuildAdminArticleIndex() {
        if (!Symphonys.ES_ENABLED && !Symphonys.ALGOLIA_ENABLED) {
            throw new IllegalStateException("未启用搜索索引");
        }
        if (Symphonys.ES_ENABLED) {
            final String url = Symphonys.ES_SERVER + "/" + ES_INDEX_NAME;
            final HttpResponse deleted = HttpRequest.delete(url).connectionTimeout(3000).timeout(3000).send();
            if (deleted.statusCode() != 404) requireSearchSuccess(deleted);
            requireSearchSuccess(HttpRequest.put(url).connectionTimeout(3000).timeout(3000).send());
            final JSONObject field = new JSONObject().put("type", "string").put("analyzer", "ik_smart")
                    .put("search_analyzer", "ik_smart");
            final JSONObject mapping = new JSONObject().put(Article.ARTICLE, new JSONObject().put("properties",
                    new JSONObject().put(Article.ARTICLE_TITLE, field).put(Article.ARTICLE_CONTENT, field)));
            requireSearchSuccess(HttpRequest.post(url + "/" + Article.ARTICLE + "/_mapping")
                    .bodyText(mapping.toString()).contentTypeJson().connectionTimeout(3000).timeout(3000).send());
        }
        if (Symphonys.ALGOLIA_ENABLED) awaitAlgoliaTask(algoliaRequest("POST", "/clear").send());
    }

    private static HttpRequest algoliaRequest(final String method, final String suffix) {
        final String url = "https://" + Symphonys.ALGOLIA_APP_ID + "-1.algolianet.com/1/indexes/"
                + Symphonys.ALGOLIA_INDEX + suffix;
        final HttpRequest request = "PUT".equals(method) ? HttpRequest.put(url)
                : "POST".equals(method) ? HttpRequest.post(url) : HttpRequest.get(url);
        return request.header("X-Algolia-API-Key", Symphonys.ALGOLIA_ADMIN_KEY)
                .header("X-Algolia-Application-Id", Symphonys.ALGOLIA_APP_ID)
                .connectionTimeout(5000).timeout(5000);
    }

    private static void requireSearchSuccess(final HttpResponse response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("搜索服务返回 HTTP " + response.statusCode());
        }
    }

    private static void awaitAlgoliaTask(final HttpResponse response) {
        requireSearchSuccess(response);
        final Object task = new JSONObject(response.bodyText()).opt("taskID");
        if (task == null || !task.toString().matches("[0-9]+")) {
            throw new IllegalStateException("搜索服务未返回任务标识");
        }
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        do {
            final HttpResponse status = algoliaRequest("GET", "/task/" + task).send();
            requireSearchSuccess(status);
            if ("published".equals(new JSONObject(status.bodyText()).optString("status"))) return;
            try { Thread.sleep(200); }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("搜索任务已中断", e);
            }
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("搜索任务结果尚未确认");
    }

    /**
     * Removes the specified document in ES.
     *
     * @param doc  the specified document
     * @param type the specified document type
     */
    public void removeESDocument(final JSONObject doc, final String type) {
        try {
            HttpRequest.delete(Symphonys.ES_SERVER + "/" + ES_INDEX_NAME + "/" + type + "/" + doc.optString(Keys.OBJECT_ID)).timeout(5000).sendAsync();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Updates doc failed", e);
        }
    }

    /**
     * Updates/Adds indexing the specified document in Algolia.
     *
     * @param doc the specified document
     */
    public void updateAlgoliaDocument(final JSONObject doc) {
        final int maxRetries = 3;
        int retries = 1;

        final String appId = Symphonys.ALGOLIA_APP_ID;
        final String index = Symphonys.ALGOLIA_INDEX;
        final String key = Symphonys.ALGOLIA_ADMIN_KEY;

        while (retries <= maxRetries) {
            String host = appId + "-" + retries + ".algolianet.com";

            try {
                final String id = doc.optString(Keys.OBJECT_ID);

                String content = doc.optString(Article.ARTICLE_CONTENT);
                content = Markdowns.toHTML(content);
                content = Jsoup.parse(content).text();
                doc.put(Article.ARTICLE_CONTENT, content);

                final long dataLength = Utf8.size(doc.toString());
                final int maxLength = 9000; // Essential plan is 20000, Community plan is 10000
                if (dataLength >= maxLength) {
                    LOGGER.log(Level.INFO, "This article [id=" + id + "] is too big [length=" + dataLength + "], so cuts it");

                    final int length = content.length();
                    int idx = length;
                    int continueCnt = 0;
                    while (idx > 0) {
                        idx -= 128;
                        content = content.substring(0, idx);
                        if (Utf8.size(content) < maxLength) {
                            continueCnt++;
                        }

                        if (3 < continueCnt) {
                            break;
                        }
                    }

                    doc.put(Article.ARTICLE_CONTENT, content);
                }

                final byte[] data = doc.toString().getBytes(StandardCharsets.UTF_8);
                final HttpResponse response = HttpRequest.put("https://" + host + "/1/indexes/" + index + "/" + id).
                        header("X-Algolia-API-Key", key).
                        header("X-Algolia-Application-Id", appId).body(data, MimeTypes.MIME_APPLICATION_JSON).
                        connectionTimeout(5000).timeout(5000).send();
                response.charset("UTF-8");
                if (200 != response.statusCode()) {
                    LOGGER.warn(response.bodyText());
                }
                break;
            } catch (final Exception e) {
                LOGGER.log(Level.WARN, "Index failed", e);

                retries++;
                if (retries > maxRetries) {
                    LOGGER.log(Level.ERROR, "Index failed [doc=" + doc + "]", e);
                }
            }

            try {
                Thread.sleep(100);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Sleep failed", e);
            }
        }
    }

    /**
     * Removes the specified document in Algolia.
     *
     * @param doc the specified document
     */
    public void removeAlgoliaDocument(final JSONObject doc) {
        final int maxRetries = 3;
        int retries = 1;

        final String appId = Symphonys.ALGOLIA_APP_ID;
        final String index = Symphonys.ALGOLIA_INDEX;
        final String key = Symphonys.ALGOLIA_ADMIN_KEY;

        while (retries <= maxRetries) {
            String host = appId + "-" + retries + ".algolianet.com";

            try {
                final String id = doc.optString(Keys.OBJECT_ID);
                final HttpResponse response = HttpRequest.delete("https://" + host + "/1/indexes/" + index + "/" + id).
                        header("X-Algolia-API-Key", key).
                        header("X-Algolia-Application-Id", appId).bodyText(doc.toString()).contentTypeJson().timeout(5000).send();
                if (200 != response.statusCode()) {
                    LOGGER.warn(response.toString());
                }
                break;
            } catch (final Exception e) {
                LOGGER.log(Level.WARN, "Remove object failed", e);

                retries++;
                if (retries > maxRetries) {
                    LOGGER.log(Level.ERROR, "Remove object failed", e);
                }
            }
        }
    }
}
