package org.b3log.symphony.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArticleHeadingLinksTest {
    private static String prepare(Document doc) {
        return ArticleHeadingLinks.prepare(doc, doc.select("body > :is(h1,h2,h3,h4,h5,h6)"));
    }

    public static void main(String[] args) throws Exception {
        Document doc = Jsoup.parse("""
                <h1>API v3</h1><h2 id="before">账号与授权</h2><h2>重复</h2><h2>重复</h2>
                <a href="#api-v3" target="_blank">版本</a><a href="#%E8%B4%A6%E5%8F%B7%E4%B8%8E%E6%8E%88%E6%9D%83">账号</a>
                <a href="#before">原编号</a><a href="#toc_h2_3">既有目录编号</a><a href="#重复">重名</a>
                <a href="https://example.com/#before" target="_blank">外链</a><a href="#comments">评论</a>
                """);
        Document sidebar = Jsoup.parse(prepare(doc));
        String[] expected = {"#toc_h1_0", "#toc_h2_1", "#toc_h2_1", "#toc_h2_3", "#重复", "https://example.com/#before", "#comments"};
        for (int index = 0; index < expected.length; index++) {
            if (!doc.select("a").get(index).attr("href").equals(expected[index])) throw new AssertionError(index);
        }
        if (doc.select("a[target]").size() != 1 || !doc.select("a[target]").text().equals("外链")) throw new AssertionError("target");
        if (sidebar.select("a[data-id]").size() != 4) throw new AssertionError("目录项数");
        for (var entry : sidebar.select("a")) {
            String id = entry.attr("data-id");
            if (!entry.attr("href").equals("#" + id) || !doc.getElementById(id).text().equals(entry.text())) throw new AssertionError(id);
        }
        Document shortDoc = Jsoup.parse("<h2 id='old'>短文</h2><a href='#短文'>短文</a>");
        if (!prepare(shortDoc).isEmpty() || !shortDoc.selectFirst("a").attr("href").equals("#old")) throw new AssertionError("短文");
        if (args.length > 0) {
            Document api = Jsoup.parse(Files.readString(Path.of(args[0])));
            var links = api.select("a[href^=#]");
            if (links.isEmpty()) throw new AssertionError("没有找到文档目录");
            prepare(api);
            for (var link : links) {
                String id = link.attr("href").substring(1);
                if (!id.startsWith("toc_") || api.getElementById(id) == null || link.hasAttr("target")) throw new AssertionError(link.text());
            }
            System.out.println("API.md fragment links: " + links.size());
        }
    }
}
