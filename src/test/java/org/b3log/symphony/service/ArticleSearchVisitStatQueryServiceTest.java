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

import org.b3log.latke.repository.Query;
import org.b3log.symphony.model.ArticleSearchVisitStat;
import org.b3log.symphony.repository.ArticleSearchVisitStatRepository;
import org.b3log.symphony.util.ClientVisitSources;
import org.b3log.symphony.util.SearchEngines;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章访问来源展示回归：只用内存夹具，不连接数据库，也不模拟蜘蛛核验通过。
 */
@Test(singleThreaded = true, timeOut = 60_000)
public class ArticleSearchVisitStatQueryServiceTest {

    private static final Map<SearchEngines.Engine, String> ENGINE_ICONS = new LinkedHashMap<>();

    static {
        ENGINE_ICONS.put(SearchEngines.Engine.GOOGLE, "visitSourceGoogle");
        ENGINE_ICONS.put(SearchEngines.Engine.BAIDU, "visitSourceBaidu");
        ENGINE_ICONS.put(SearchEngines.Engine.BING, "visitSourceBing");
        ENGINE_ICONS.put(SearchEngines.Engine.SOGOU, "visitSourceSogou");
        ENGINE_ICONS.put(SearchEngines.Engine.YANDEX, "visitSourceYandex");
        ENGINE_ICONS.put(SearchEngines.Engine.SO360, "visitSourceSo360");
        ENGINE_ICONS.put(SearchEngines.Engine.DUCKDUCKGO, "visitSourceDuckDuckGo");
        ENGINE_ICONS.put(SearchEngines.Engine.OAI_SEARCH, "visitSourceOpenAI");
        ENGINE_ICONS.put(SearchEngines.Engine.CLAUDE_SEARCH, "visitSourceClaude");
        ENGINE_ICONS.put(SearchEngines.Engine.PERPLEXITY, "visitSourcePerplexity");
        ENGINE_ICONS.put(SearchEngines.Engine.APPLE, "visitSourceApple");
        ENGINE_ICONS.put(SearchEngines.Engine.MISTRAL_INDEX, "visitSourceMistral");
    }

    public void everyEngineIsListedIndependentlyWithItsOwnIcon() throws Exception {
        Assert.assertEquals(ENGINE_ICONS.size(), SearchEngines.Engine.values().length, "新增引擎需补图标映射");
        final List<JSONObject> rows = new ArrayList<>();
        int i = 1;
        for (final SearchEngines.Engine engine : SearchEngines.Engine.values()) {
            rows.add(row(engine.key(), i, 0));
            i++;
        }

        final List<JSONObject> stats = service(rows).getStats("a1");

        Assert.assertEquals(stats.size(), SearchEngines.Engine.values().length);
        i = 1;
        for (final SearchEngines.Engine engine : SearchEngines.Engine.values()) {
            final JSONObject stat = stats.get(i - 1);
            Assert.assertEquals(stat.getString(ArticleSearchVisitStat.SOURCE_KEY), "search:" + engine.key());
            Assert.assertEquals(stat.getString(ArticleSearchVisitStat.SOURCE_CSS), engine.key());
            Assert.assertEquals(stat.getString(ArticleSearchVisitStat.SOURCE_ICON), ENGINE_ICONS.get(engine));
            Assert.assertEquals(stat.getString(ArticleSearchVisitStat.SOURCE_TYPE), "search");
            Assert.assertEquals(stat.getInt(ArticleSearchVisitStat.VISIT_CNT), i);
            Assert.assertEquals(stat.getString(ArticleSearchVisitStat.SOURCE_NAME), engine.displayName() + "：抓取 " + i);
            i++;
        }
    }

    public void visitCountSumsCrawlerAndRefererAndLabelsBoth() throws Exception {
        final List<JSONObject> stats = service(List.of(
                row("perplexity", 0, 4),
                row("google", 3, 2))).getStats("a1");

        Assert.assertEquals(stats.size(), 2);
        Assert.assertEquals(stats.get(0).getString(ArticleSearchVisitStat.SOURCE_NAME), "Google：抓取 3，来源 2");
        Assert.assertEquals(stats.get(0).getInt(ArticleSearchVisitStat.VISIT_CNT), 5);
        Assert.assertEquals(stats.get(1).getString(ArticleSearchVisitStat.SOURCE_NAME), "PerplexityBot：来源 4");
        Assert.assertEquals(stats.get(1).getInt(ArticleSearchVisitStat.VISIT_CNT), 4);
    }

    public void zeroCountsAndEmptyResultsStayHidden() throws Exception {
        Assert.assertTrue(service(List.of()).getStats("a1").isEmpty());
        Assert.assertTrue(service(null).getStats("a1").isEmpty());
        final List<JSONObject> stats = service(List.of(
                row("oai-search", 0, 0),
                row("claude-search", 0, 0),
                row("mistral-index", 1, 0),
                row("unknown-engine", 9, 9))).getStats("a1");

        Assert.assertEquals(stats.size(), 1);
        Assert.assertEquals(stats.get(0).getString(ArticleSearchVisitStat.SOURCE_KEY), "search:mistral-index");
    }

    public void everyRenderedIconExistsInSymbolDefs() throws Exception {
        final String defs = Files.readString(Path.of("src/main/resources/js/symbol-defs.js"), StandardCharsets.UTF_8);
        final Matcher matcher = Pattern.compile("<symbol id=\"([A-Za-z0-9_-]+)\"").matcher(defs);
        final List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }

        final List<JSONObject> rows = new ArrayList<>();
        for (final SearchEngines.Engine engine : SearchEngines.Engine.values()) {
            rows.add(row(engine.key(), 1, 0));
        }
        for (final ClientVisitSources.Source source : ClientVisitSources.Source.values()) {
            rows.add(row(source.key(), 0, 1));
        }
        final List<JSONObject> stats = service(rows).getStats("a1");

        Assert.assertEquals(stats.size(), rows.size());
        for (final JSONObject stat : stats) {
            final String icon = stat.getString(ArticleSearchVisitStat.SOURCE_ICON);
            Assert.assertTrue(ids.contains(icon), "symbol-defs.js 缺少图标 " + icon);
        }
    }

    private static ArticleSearchVisitStatQueryService service(final List<JSONObject> rows) throws Exception {
        final ArticleSearchVisitStatQueryService service = new ArticleSearchVisitStatQueryService();
        final var field = ArticleSearchVisitStatQueryService.class.getDeclaredField("statRepository");
        field.setAccessible(true);
        field.set(service, new ArticleSearchVisitStatRepository() {
            @Override
            public List<JSONObject> getList(final Query query) {
                return rows;
            }
        });
        return service;
    }

    private static JSONObject row(final String key, final int crawler, final int referer) {
        return new JSONObject()
                .put(ArticleSearchVisitStat.ARTICLE_ID, "a1")
                .put(ArticleSearchVisitStat.SEARCH_ENGINE, key)
                .put(ArticleSearchVisitStat.CRAWLER_VISIT_CNT, crawler)
                .put(ArticleSearchVisitStat.REFERER_VISIT_CNT, referer);
    }
}
