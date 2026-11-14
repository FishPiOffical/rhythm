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

import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.apache.commons.codec.digest.DigestUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Query;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Report;
import org.b3log.symphony.repository.LogsRepository;
import org.b3log.symphony.repository.ReportRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class ReportContentSafetyTest {
    private static final String MESSAGE_ID = "1800000000001";
    private static final String REPORT_ID = "1800000000002";
    private static final String IMAGE_URL = "https://images.example.test/photo.png?expires=42&signature=a%2Bb%3D";
    private static final String NORMAL = "正常文字 & 引号\"\n第二行\n\n**粗体** *斜体* ~~删除~~\n\n"
            + "[安全链接](https://example.test/read?q=hello&lang=zh)\n\n"
            + "![图片](" + IMAGE_URL + ")\n\n:huaji: :smile:\n\n"
            + "`<script>inline()</script>`\n\n```html\n<script>code()</script>\n```";
    private static final String ATTACK = "正文前\n\n<script>window.reportXss=1</script>\n\n"
            + "<img src=\"https://images.example.test/safe.png\" onerror=\"alert(1)\" onload=\"alert(2)\">\n\n"
            + "<a href=\"javascript:alert(3)\" onclick=\"alert(4)\">危险链接</a> "
            + "<a href=\"jav&#x61;script:alert(5)\">实体协议</a> "
            + "<a href=\"data:text/html,<script>alert(6)</script>\">数据链接</a>\n\n"
            + "<img src=\"data:image/svg+xml,<svg onload='alert(7)'></svg>\">\n\n"
            + "</div></li></ul><svg onload=\"alert(8)\"></svg>"
            + "<iframe srcdoc=\"<script>alert(9)</script>\"></iframe>"
            + "<form action=\"https://example.test/steal\"><input autofocus onfocus=\"alert(10)\"></form>\n\n正文后";
    private static final String HOSTILE_NAME = "</p></div><img src=x onerror=alert(11)>[伪链接](javascript:alert(12))&\"";
    private static final String HOSTILE_ID = "42\" onclick=\"alert(13)\"><script>alert(14)</script>&x=1#fragment %_!";
    private final Map<String, Object> previous = new HashMap<>();
    private Map<String, JSONObject> markdownCache;
    private Map<String, JSONObject> previousCache;

    @BeforeClass
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        properties.setProperty("serverScheme", "http");
        properties.setProperty("serverHost", "localhost");
        properties.setProperty("serverPort", "8080");
        for (final String name : List.of("latkeProps", "localProps", "contextPath")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            previous.put(name, field.get(null));
            field.set(null, "contextPath".equals(name) ? "" : properties);
        }

        // 在真实 Markdown 类初始化前禁用远程引擎探测；正文仍由生产 flexmark 和 Jsoup 处理。
        final var configField = Symphonys.class.getDeclaredField("CFG");
        configField.setAccessible(true);
        final Properties configuration = (Properties) configField.get(null);
        final Object oldEngine = configuration.put("lute.engine.url", "isolated-test:no-network");
        try {
            Class.forName(Markdowns.class.getName(), true, Markdowns.class.getClassLoader());
        } finally {
            if (oldEngine == null) configuration.remove("lute.engine.url");
            else configuration.put("lute.engine.url", oldEngine);
        }
        Assert.assertFalse(Markdowns.LUTE_AVAILABLE, "隔离测试不得使用远程 Markdown 引擎");
        final var cacheField = Markdowns.class.getDeclaredField("MD_CACHE");
        cacheField.setAccessible(true);
        markdownCache = (Map<String, JSONObject>) cacheField.get(null);
        previousCache = new HashMap<>(markdownCache);
        markdownCache.clear();
    }

    @AfterClass(alwaysRun = true)
    public void restore() throws Exception {
        if (markdownCache != null && previousCache != null) {
            markdownCache.clear();
            markdownCache.putAll(previousCache);
        }
        for (final var entry : previous.entrySet()) {
            final var field = Latkes.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    private static final class Fixture {
        private final ReportQueryService service = new ReportQueryService();
        private final List<JSONObject> records = new ArrayList<>();
        private final Map<String, JSONObject> messages = new HashMap<>();
        private final Map<String, JSONObject> preciseLogs = new HashMap<>();
        private final Map<String, JSONObject> historicalLogs = new HashMap<>();
        private final List<String> logQueries = new ArrayList<>();

        private Fixture() throws Exception {
            inject("reportRepository", new ReportRepository() {
                @Override
                public JSONObject get(final Query query) {
                    return new JSONObject().put(Keys.RESULTS, (Object) records)
                            .put(Pagination.PAGINATION, new JSONObject().put(Pagination.PAGINATION_PAGE_COUNT, 3));
                }
            });
            inject("userRepository", new UserRepository() {
                @Override
                public JSONObject get(final String id) {
                    return new JSONObject().put(User.USER_NAME, "fixture-reporter");
                }
            });
            inject("chatRoomService", new ChatRoomService() {
                @Override
                public JSONObject getChatMsg(final String id) {
                    return messages.get(id);
                }
            });
            inject("logsRepository", new LogsRepository() {
                @Override
                public List<JSONObject> select(final String sql, final Object... args) {
                    logQueries.add(sql);
                    final JSONObject log = sql.contains("key3=?")
                            ? preciseLogs.get(args[1].toString()) : historicalLogs.get(args[1].toString());
                    return log == null ? List.of() : List.of(log);
                }
            });
            inject("langPropsService", new LangPropsService() {
                @Override
                public String get(final String key) {
                    return key;
                }
            });
        }

        private void inject(final String name, final Object value) throws Exception {
            final var field = ReportQueryService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        }

        private JSONObject report(final String id, final String target, final int handled) {
            final JSONObject record = new JSONObject().put(Keys.OBJECT_ID, id)
                    .put(Report.REPORT_USER_ID, "10001").put(Report.REPORT_DATA_ID, target)
                    .put(Report.REPORT_DATA_TYPE, Report.REPORT_DATA_TYPE_C_CHAT)
                    .put(Report.REPORT_TYPE, Report.REPORT_TYPE_C_OTHER)
                    .put(Report.REPORT_MEMO, "").put(Report.REPORT_HANDLED, handled);
            records.add(record);
            return record;
        }

        private JSONObject message(final String user, final String text) {
            return new JSONObject().put(Keys.OBJECT_ID, MESSAGE_ID).put(Common.CONTENT,
                    new JSONObject().put(User.USER_NAME, user).put(Common.CONTENT, text).toString());
        }

        private JSONObject log(final String user, final String text) {
            return new JSONObject().put("data", message(user, text).toString());
        }

        @SuppressWarnings("unchecked")
        private List<JSONObject> read() {
            final JSONObject result = service.getReports(new JSONObject()
                    .put(Pagination.PAGINATION_CURRENT_PAGE_NUM, 1)
                    .put(Pagination.PAGINATION_PAGE_SIZE, 20)
                    .put(Pagination.PAGINATION_WINDOW_SIZE, 5));
            Assert.assertNotNull(result);
            Assert.assertEquals(result.getJSONObject(Pagination.PAGINATION).getInt(Pagination.PAGINATION_PAGE_COUNT), 3);
            return (List<JSONObject>) result.get(Report.REPORTS);
        }
    }

    @Test(timeOut = 60000)
    public void currentAndBothSnapshotPathsRenderSafeContentWithoutChangingEvidence() throws Exception {
        for (final String source : List.of("current", "precise", "historical")) {
            final Fixture fixture = new Fixture();
            final JSONObject row = fixture.report(REPORT_ID, MESSAGE_ID, 0);
            // 聊天分支不得把举报备注或私有聊天正文送入公共 Markdown 缓存。
            row.put(Report.REPORT_MEMO, "不应被解析的举报备注 " + source);
            final JSONObject evidence;
            if ("current".equals(source)) {
                evidence = fixture.message(HOSTILE_NAME, NORMAL + "\n\n" + ATTACK);
                fixture.messages.put(MESSAGE_ID, evidence);
            } else {
                evidence = fixture.log(HOSTILE_NAME, NORMAL + "\n\n" + ATTACK);
                if ("precise".equals(source)) fixture.preciseLogs.put(MESSAGE_ID, evidence);
                else fixture.historicalLogs.put("%\"oId\":\"" + MESSAGE_ID + "\"%", evidence);
            }
            final String rowBefore = row.toString();
            final String evidenceBefore = evidence.toString();
            final Map<String, JSONObject> cacheBefore = new HashMap<>(markdownCache);
            final List<JSONObject> reports = fixture.read();
            Assert.assertEquals(reports.size(), 1, source);
            final String html = reports.getFirst().getString(Report.REPORT_MEMO);
            final Document content = Jsoup.parseBodyFragment(html);
            assertSafe(content.body());
            assertNormalContent(content.body());
            Assert.assertTrue(content.text().contains(HOSTILE_NAME), "用户名应完整显示为文字：" + source);
            Assert.assertEquals(content.text().contains("此为快照记录"), !"current".equals(source));
            Assert.assertTrue(content.text().contains("正文前"));
            Assert.assertTrue(content.text().contains("正文后"));
            Assert.assertEquals(row.toString(), rowBefore, "举报原始记录不得被修改");
            Assert.assertEquals(evidence.toString(), evidenceBefore, "聊天/日志原文不得被修改");
            Assert.assertEquals(markdownCache, cacheBefore, "聊天正文不得进入公共 Markdown 缓存");
            Assert.assertEquals(fixture.logQueries.size(), "current".equals(source) ? 0 : "precise".equals(source) ? 1 : 2);
            assertJumpLink(reports.getFirst(), MESSAGE_ID);
        }
    }

    @Test(timeOut = 60000)
    public void emptyBodiesAndExistingHtmlKeepAuthorRowsAndMarkupOnBothSkins() throws Exception {
        final String existingHtml = "<p>已有 HTML &amp; 文字<br>下一行 <strong>加粗</strong> <em>倾斜</em></p>"
                + "<p><a href=\"https://example.test/legacy?a=1&amp;b=2\">原有链接</a>"
                + "<img alt=\"已有图片\" src=\"" + IMAGE_URL.replace("&", "&amp;") + "\"></p>"
                + "<blockquote><p>引用文字</p></blockquote>"
                + "<pre><code>&lt;script&gt;legacy()&lt;/script&gt;</code></pre>";
        for (final boolean revoked : List.of(false, true)) {
            for (final String body : List.of("", " \t\n\r\n ", existingHtml)) {
                final Fixture fixture = new Fixture();
                final JSONObject row = fixture.report(REPORT_ID, MESSAGE_ID, 0);
                row.put(Report.REPORT_MEMO, "空正文不得回退到举报备注");
                final JSONObject evidence = revoked ? fixture.log("existing-author", body)
                        : fixture.message("existing-author", body);
                if (revoked) fixture.preciseLogs.put(MESSAGE_ID, evidence);
                else fixture.messages.put(MESSAGE_ID, evidence);
                final String evidenceBefore = evidence.toString();
                final List<JSONObject> reports = fixture.read();
                Assert.assertEquals(reports.size(), 1, "空正文仍须保留举报行");
                for (final String skin : List.of("pc", "mobile")) {
                    final Document document = Jsoup.parse(render(skin, reports));
                    Assert.assertEquals(document.select("ul.notification > li").size(), 1, skin);
                    final Element memo = document.selectFirst(".vditor-reset");
                    Assert.assertNotNull(memo);
                    assertSafe(memo);
                    Assert.assertTrue(memo.text().contains("[existing-author]"), skin);
                    Assert.assertEquals(memo.text().contains("此为快照记录"), revoked, skin);
                    Assert.assertFalse(memo.text().contains("空正文不得回退到举报备注"), skin);
                    if (body.isBlank()) {
                        Assert.assertEquals(memo.select("p").size(), 1, skin);
                        Assert.assertEquals(memo.text(), (revoked ? "[该数据已被撤回或删除，此为快照记录] " : "")
                                + "[existing-author]", skin);
                    } else {
                        Assert.assertTrue(memo.text().contains("已有 HTML & 文字"), skin);
                        Assert.assertEquals(memo.select("br").size(), 1, skin);
                        Assert.assertEquals(memo.select("strong").text(), "加粗", skin);
                        Assert.assertEquals(memo.select("em").text(), "倾斜", skin);
                        Assert.assertEquals(memo.select("a").attr("href"), "https://example.test/legacy?a=1&b=2", skin);
                        Assert.assertEquals(memo.select("img[alt=已有图片]").attr("src"), IMAGE_URL, skin);
                        Assert.assertEquals(memo.select("blockquote p").text(), "引用文字", skin);
                        Assert.assertEquals(memo.select("pre code").text(), "<script>legacy()</script>", skin);
                        Assert.assertFalse(memo.text().contains("<p>"), "已有 HTML 不得显示为标签文本：" + skin);
                    }
                }
                Assert.assertEquals(evidence.toString(), evidenceBefore, "原始正文不得被修改");
            }
        }
    }

    @Test(timeOut = 60000)
    public void missingAndMalformedLogsEscapeIdsAndKeepEncodedJumpLinks() throws Exception {
        for (final String logData : new String[]{null, "{broken", "{\"content\":\"{broken\"}"}) {
            final Fixture fixture = new Fixture();
            fixture.report(REPORT_ID, HOSTILE_ID, 0);
            if (logData != null) fixture.preciseLogs.put(HOSTILE_ID, new JSONObject().put("data", logData));
            final List<JSONObject> reports = fixture.read();
            Assert.assertEquals(reports.size(), 1);
            final Document content = Jsoup.parseBodyFragment(reports.getFirst().getString(Report.REPORT_MEMO));
            assertSafe(content.body());
            Assert.assertTrue(content.text().contains("日志中无记录"));
            Assert.assertTrue(content.text().contains(HOSTILE_ID));
            assertJumpLink(reports.getFirst(), HOSTILE_ID);
        }
    }

    @Test(timeOut = 60000)
    public void ordinaryReportMemoKeepsPublicCacheAndCleaningPath() throws Exception {
        final Fixture fixture = new Fixture();
        final JSONObject record = fixture.report(REPORT_ID, "10002", 0);
        record.put(Report.REPORT_DATA_TYPE, Report.REPORT_DATA_TYPE_C_USER);
        final String memo = "普通举报备注缓存测试";
        record.put(Report.REPORT_MEMO, memo);
        final String key = DigestUtils.md5Hex(memo);
        // 仅固定普通 toHTML 的缓存命中结果，实际 getReports 仍必须调用原 clean 去除危险内容。
        markdownCache.put(key, new JSONObject().put(Common.DATA,
                "<p><strong>普通举报备注</strong><img src=\"https://images.example.test/safe.png\" onerror=\"alert(1)\"></p><script>alert(2)</script>"));
        try {
            final String before = record.toString();
            final List<JSONObject> reports = fixture.read();
            Assert.assertEquals(reports.size(), 1);
            final Document content = Jsoup.parseBodyFragment(reports.getFirst().getString(Report.REPORT_MEMO));
            assertSafe(content.body());
            Assert.assertEquals(content.select("strong").text(), "普通举报备注");
            Assert.assertEquals(record.toString(), before);
        } finally {
            markdownCache.remove(key);
        }
    }

    @Test(timeOut = 60000)
    public void actualPcAndMobileTemplatesPreserveSafeMarkupControlsAndPagination() throws Exception {
        final Fixture fixture = new Fixture();
        fixture.report(REPORT_ID, HOSTILE_ID, 0);
        fixture.messages.put(HOSTILE_ID, fixture.message(HOSTILE_NAME, NORMAL + "\n\n" + ATTACK));
        fixture.report("1800000000003", MESSAGE_ID, 1);
        fixture.preciseLogs.put(MESSAGE_ID, fixture.log("snapshot-user", "**已处理的快照**"));
        fixture.report("1800000000004", "1800000000005", 2);
        final List<JSONObject> reports = fixture.read();
        Assert.assertEquals(reports.size(), 3);
        for (final String skin : List.of("pc", "mobile")) {
            final String html = render(skin, reports);
            final Document document = Jsoup.parse(html);
            Assert.assertEquals(document.select("ul.notification > li").size(), 3, skin);
            Assert.assertEquals(document.select("ul.notification > li.read").size(), 2, skin);
            Assert.assertEquals(document.select(".vditor-reset").size(), 3, skin);
            for (final Element memo : document.select(".vditor-reset")) assertSafe(memo);
            assertNormalContent(document.selectFirst(".vditor-reset"));
            Assert.assertEquals(document.select("button[onclick^=AdminReportHandled]").size(), 1, skin);
            Assert.assertEquals(document.select("button[onclick^=AdminReportCancel]").size(), 1, skin);
            Assert.assertEquals(document.select(".ft-green").text(), "处理");
            Assert.assertEquals(document.select(".ft-fade").text(), "忽略");
            Assert.assertEquals(document.select(".pagination").size(), 1, skin);
            Assert.assertFalse(document.select(".pagination a[href$='?p=2']").isEmpty(), skin);
            Assert.assertTrue(document.selectFirst(".vditor-reset").text().contains(HOSTILE_NAME));
            Assert.assertTrue(document.select("script, svg, iframe, form").isEmpty(), skin);
            for (final Element element : document.getAllElements()) {
                for (final var attribute : element.attributes()) {
                    if (attribute.getKey().toLowerCase(Locale.ROOT).startsWith("on")) {
                        Assert.assertEquals(element.tagName(), "button", skin);
                        Assert.assertTrue(attribute.getValue().matches("AdminReport(Handled|Cancel)\\(this, '" + REPORT_ID + "'\\)"), skin);
                    }
                }
            }
            final String outputDirectory = System.getenv("REPORT_CONTENT_HTML_DIR");
            if (outputDirectory != null && !outputDirectory.isBlank()) {
                final Path directory = Path.of(outputDirectory).toAbsolutePath();
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("reports-" + skin + ".html"), html, StandardCharsets.UTF_8);
            }
        }
    }

    private static void assertJumpLink(final JSONObject report, final String id) {
        final Document document = Jsoup.parseBodyFragment(report.getString(Report.REPORT_T_DATA));
        assertSafe(document.body());
        Assert.assertEquals(document.select("a").size(), 1);
        final String href = document.selectFirst("a").attr("href");
        final String marker = "/cr?oId=";
        Assert.assertTrue(href.contains(marker));
        final String encodedId = href.substring(href.indexOf(marker) + marker.length());
        Assert.assertFalse(encodedId.contains("&"));
        Assert.assertFalse(encodedId.contains("#"));
        Assert.assertEquals(URLDecoder.decode(encodedId, StandardCharsets.UTF_8), id);
    }

    private static void assertSafe(final Element root) {
        Assert.assertTrue(root.select("script, svg, iframe, object, embed, form, input, style, link, meta").isEmpty(), root.html());
        for (final Element element : root.getAllElements()) {
            for (final var attribute : element.attributes()) {
                final String key = attribute.getKey().toLowerCase(Locale.ROOT);
                Assert.assertFalse(key.startsWith("on"), element.outerHtml());
                if (List.of("href", "src", "data", "action", "xlink:href").contains(key)) {
                    final String value = attribute.getValue().replaceAll("[\\x00-\\x20]", "").toLowerCase(Locale.ROOT);
                    Assert.assertFalse(value.startsWith("javascript:") || value.startsWith("data:") || value.startsWith("vbscript:"), element.outerHtml());
                }
            }
        }
    }

    private static void assertNormalContent(final Element root) {
        Assert.assertTrue(root.text().contains("正常文字 & 引号\""));
        Assert.assertFalse(root.select("br").isEmpty(), "换行应保留");
        Assert.assertEquals(root.select("strong").first().text(), "粗体");
        Assert.assertEquals(root.select("em").text(), "斜体");
        Assert.assertEquals(root.select("del").text(), "删除");
        Assert.assertEquals(root.select("a[href^='https://example.test/read']").attr("href"), "https://example.test/read?q=hello&lang=zh");
        Assert.assertEquals(root.select("img[alt=图片]").attr("src"), IMAGE_URL);
        Assert.assertEquals(root.select("img.emoji[alt=huaji]").size(), 1);
        Assert.assertTrue(root.text().contains("😄"));
        Assert.assertEquals(root.select("pre code").text(), "<script>code()</script>");
        Assert.assertTrue(root.select("code").stream().anyMatch(code -> code.text().equals("<script>inline()</script>")));
        Assert.assertFalse(root.html().contains("&lt;strong&gt;"), "安全标签不得被二次转义");
    }

    private static String render(final String skin, final List<JSONObject> reports) throws Exception {
        final Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
        configuration.setDefaultEncoding("UTF-8");
        final StringTemplateLoader chrome = new StringTemplateLoader();
        chrome.putTemplate("admin/macro-admin.ftl", "<#macro admin page><!doctype html><html><head><meta charset='UTF-8'><title>举报安全隔离测试</title></head><body><#nested></body></html></#macro>");
        // 只替换站点外壳；两端举报模板和分页宏均从真实源码加载。
        configuration.setTemplateLoader(new MultiTemplateLoader(new freemarker.cache.TemplateLoader[]{chrome,
                new FileTemplateLoader(Path.of("src/main/resources/skins/classic", skin).toFile())}));
        final Map<String, Object> model = new HashMap<>();
        model.put("reports", reports.stream().map(JSONObject::toMap).toList());
        model.put("reportLabel", "举报");
        model.put("processLabel", "处理");
        model.put("ignoreLabel", "忽略");
        model.put("servePath", "http://localhost:8080");
        model.put("paginationCurrentPageNum", 1);
        model.put("paginationPageCount", 3);
        model.put("paginationFirstPageNum", 1);
        model.put("paginationLastPageNum", 3);
        model.put("paginationPageNums", List.of(1, 2, 3));
        final StringWriter output = new StringWriter();
        configuration.getTemplate("admin/reports.ftl").process(model, output);
        return output.toString();
    }
}
