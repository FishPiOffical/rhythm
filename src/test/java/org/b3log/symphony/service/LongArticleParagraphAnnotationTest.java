package org.b3log.symphony.service;

import org.b3log.symphony.model.Comment;
import org.jsoup.Jsoup;
import org.testng.Assert;
import java.util.HashSet;

/** Real parser and project Flexmark; controlled sanitization/emotion/media adapters. */
public class LongArticleParagraphAnnotationTest {
    public static void main(final String[] args) throws Exception {
        final var configuration = org.b3log.latke.Latkes.class.getDeclaredField("latkeProps");
        configuration.setAccessible(true); configuration.set(null, new java.util.Properties());
        org.b3log.latke.Latkes.setServerHost("paragraph.source.test");
        final var service = new LongArticleParagraphService();
        final var links = LongArticleParagraphService.class.getDeclaredField("shortLinkQueryService"); links.setAccessible(true);
        links.set(service, new ShortLinkQueryService());
        final String html = "<h2>中文标题</h2><p>第一段 <strong>正文</strong></p><p>重复段落</p><p>重复段落</p>"
                + "<ul><li>列表一</li><li>列表二</li></ul><pre><code>code\nline two</code></pre>"
                + "<table><tbody><tr><td>单元格</td></tr></tbody></table><p><img src='/example.png' alt='配图'></p>";
        final var raw = service.extractRendered("10", html);
        final var legacy = service.annotateRenderedContent("10", html);
        final var annotated = Jsoup.parseBodyFragment(legacy.getString("html"));
        final var anchors = annotated.select("[data-long-paragraph-id]");
        Assert.assertTrue(raw.size() >= 8); Assert.assertEquals(anchors.size(), raw.size());
        Assert.assertEquals(legacy.getJSONArray("paragraphs").length(), raw.size());
        final var ids = new HashSet<String>();
        for (int i = 0; i < raw.size(); i++) {
            final var paragraph = raw.get(i);
            Assert.assertTrue(paragraph.getId().matches("[0-9a-f]{64}")); Assert.assertTrue(ids.add(paragraph.getId()));
            Assert.assertEquals(paragraph.getIndex(), i);
            Assert.assertEquals(anchors.get(i).attr("data-long-paragraph-id"), paragraph.getId());
            Assert.assertEquals(legacy.getJSONArray("paragraphs").getJSONObject(i).getString(Comment.COMMENT_PARAGRAPH_ID), paragraph.getId());
            Assert.assertEquals(service.findRenderedParagraph("10", legacy.getString("html"), paragraph.getId()).getId(), paragraph.getId());
        }
        Assert.assertNotEquals(service.extractRendered("11", html).getFirst().getId(), raw.getFirst().getId());
        System.out.println("PASS real mixed-content parser/legacy annotations preserve deterministic article-bound unique anchors and indices");

        final var rendered = service.annotateMarkdown("10", html);
        Assert.assertEquals(rendered.html().strip(), legacy.getString("html").strip());
        Assert.assertEquals(rendered.paragraphs().size(), raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Assert.assertEquals(rendered.paragraphs().get(i).getId(), raw.get(i).getId());
            Assert.assertEquals(service.extractMarkdown("10", html).get(i).getId(), raw.get(i).getId());
            Assert.assertEquals(service.findParagraph("10", html, raw.get(i).getId()).getSnapshot(), raw.get(i).getSnapshot());
        }
        try { rendered.paragraphs().clear(); Assert.fail(); } catch (final UnsupportedOperationException expected) { }
        System.out.println("PASS typed annotated content and publishing lookup share exact parser IDs; result list cannot be mutated");

        Assert.assertTrue(service.annotateMarkdown("10", "").paragraphs().isEmpty());
        final var longText = service.extractRendered("10", "<p>" + "长".repeat(2000) + "</p>").getFirst();
        Assert.assertEquals(longText.getSnapshot().length(), 1024);
        Assert.assertNull(service.findParagraph("10", html, "invalid-anchor"));
        Assert.assertNull(service.findParagraph("10", html, "0".repeat(64)));
        System.out.println("PASS empty content, bounded snapshots and missing/invalid anchors retain existing parser rules");

        final var shortLinks = new ShortLinkQueryService();
        final String base = org.b3log.latke.Latkes.getServePath();
        final String publicId = "1700000000001", privateId = "1700000000002";
        final String publicUrl = base + "/article/" + publicId, privateUrl = base + "/article/" + privateId;
        final java.util.function.Function<String, String> publicTitles = id -> publicId.equals(id) ? "Public $5 \\ title" : null;
        final String expanded = shortLinks.linkArticle("before " + publicUrl + " then " + privateUrl + " after", publicTitles);
        Assert.assertTrue(expanded.contains("[Public $5 \\ title](" + publicUrl + ")"));
        Assert.assertTrue(expanded.contains(privateUrl)); Assert.assertTrue(expanded.endsWith(" after"));
        Assert.assertFalse(expanded.contains("SECRET"));
        Assert.assertEquals(shortLinks.linkArticle(privateUrl, id -> null), privateUrl);
        try { shortLinks.linkArticle(publicUrl, id -> { throw new IllegalStateException("lookup unavailable"); }); Assert.fail(); }
        catch (final IllegalStateException expected) { Assert.assertEquals(expected.getMessage(), "lookup unavailable"); }
        System.out.println("PASS actual short-link callback never falls back to unrestricted lookup; dollars/backslashes remain literal and failures propagate");

        final String body = privateUrl + "\n\n" + publicUrl;
        final var safe = service.annotateMarkdown("10", body, publicTitles);
        final var safeRows = service.extractMarkdown("10", body, publicTitles);
        Assert.assertEquals(safeRows.size(), 2);
        Assert.assertTrue(safeRows.getFirst().getSnapshot().contains(privateUrl));
        Assert.assertFalse(safeRows.getFirst().getSnapshot().contains("SECRET"));
        for (int i = 0; i < safeRows.size(); i++) {
            Assert.assertEquals(safe.paragraphs().get(i).getId(), safeRows.get(i).getId());
            Assert.assertEquals(service.findParagraph("10", body, safeRows.get(i).getId(), publicTitles).getId(), safeRows.get(i).getId());
        }
        final var legacyExpanded = service.extractMarkdown("10", body,
                id -> privateId.equals(id) ? "SECRET-restricted-title" : publicTitles.apply(id));
        Assert.assertNotEquals(legacyExpanded.getFirst().getId(), safeRows.getFirst().getId());
        System.out.println("PASS public-title paragraph render, anchor enumeration and publication lookup remain consistent for restricted links");

        final String protectedHTML = "<p>Before <a href='" + privateUrl + "'><strong>SECRET-private</strong></a> after</p><p>line one<br>line two</p>";
        final String safeHTML = "<p>Before<a href='" + privateUrl + "'>" + privateUrl + "</a>after</p><p>line one<br>line two</p>";
        final var compatibility = service.compatibleRenderedContent("10", protectedHTML, safeHTML);
        final var oldRows = service.extractRendered("10", protectedHTML);
        final var publicRows = service.extractRendered("10", safeHTML);
        Assert.assertEquals(compatibility.paragraphs().size(), 3);
        Assert.assertEquals(compatibility.paragraphs().getFirst().getId(), oldRows.getFirst().getId());
        Assert.assertEquals(compatibility.canonicalId(publicRows.getFirst().getId()), oldRows.getFirst().getId());
        Assert.assertEquals(compatibility.idsFor(oldRows.getFirst().getId()).size(), 2);
        Assert.assertTrue(compatibility.paragraphs().getFirst().getSnapshot().contains(privateUrl));
        Assert.assertFalse(compatibility.html().contains("SECRET-private"));
        final var compatibilityDOM = Jsoup.parseBodyFragment(compatibility.html());
        for (int i = 0; i < oldRows.size(); i++) {
            Assert.assertEquals(compatibilityDOM.select("[data-long-paragraph-id]").get(i).attr("data-long-paragraph-id"), oldRows.get(i).getId());
        }
        Assert.assertEquals(compatibility.find(publicRows.getFirst().getId()).getId(), oldRows.getFirst().getId());
        try { compatibility.aliases().clear(); Assert.fail(); } catch (final UnsupportedOperationException expected) { }
        System.out.println("PASS compatible view preserves legacy hashes and multiline anchors but exposes only public HTML/snapshots; provisional IDs resolve");

        conflict(() -> service.compatibleRenderedContent("10", "<p>SECRET outside link</p>", "<p>Different outside link</p>"));
        conflict(() -> service.compatibleRenderedContent("10", protectedHTML, safeHTML + "<p>extra</p>"));
        conflict(() -> service.compatibleRenderedContent("10", protectedHTML, safeHTML.replace(privateUrl, publicUrl)));
        final String duplicate = "<p><a href='" + privateUrl + "'>" + privateUrl + "</a></p>";
        conflict(() -> service.compatibleRenderedContent("10", "<p><a href='" + privateUrl + "'>SECRET</a></p>" + duplicate, duplicate + duplicate));
        System.out.println("PASS mismatched ordinary text, structure, link destination and overlapping alias identities fail closed with generic 409");

        final var titleReads = new java.util.concurrent.atomic.AtomicInteger();
        final var paired = service.compatibleMarkdown("10", body, id -> {
            titleReads.incrementAndGet();
            return new LongArticleParagraphService.TitleSnapshot(privateId.equals(id) ? "SECRET-title" : "Public title", !privateId.equals(id));
        });
        Assert.assertEquals(titleReads.get(), 2);
        Assert.assertFalse(paired.html().contains("SECRET-title"));
        Assert.assertEquals(paired.paragraphs().getFirst().getId(), service.extractMarkdown("10", body,
                id -> privateId.equals(id) ? "SECRET-title" : "Public title").getFirst().getId());
        System.out.println("PASS paired rendering memoizes one title snapshot per linked article and never exposes restricted text");

        final String initialHost = org.b3log.latke.Latkes.getServerHost();
        try {
            org.b3log.latke.Latkes.setServerHost("local-paragraph-proxy.invalid");
            final String previousIdentity = service.annotateMarkdown("10", body, id -> privateId.equals(id) ? "SECRET-proxy-title" : "Public title").html();
            final String previousSafe = service.annotateMarkdown("10", body, id -> privateId.equals(id) ? null : "Public title").html();
            conflict(() -> service.compatibleRenderedContent("10", previousIdentity, previousSafe));
            final var proxied = service.compatibleMarkdown("10", body, id ->
                    new LongArticleParagraphService.TitleSnapshot(privateId.equals(id) ? "SECRET-proxy-title" : "Public title", !privateId.equals(id)));
            Assert.assertFalse(proxied.html().contains("SECRET-proxy-title"));
            Assert.assertTrue(proxied.paragraphs().getFirst().getSnapshot().contains(privateUrl));
            Assert.assertEquals(proxied.idsFor(proxied.paragraphs().getFirst().getId()).size(), 2);
            Assert.assertTrue(proxied.html().contains("href=\"" + privateUrl + "\""));
            Assert.assertFalse(proxied.html().contains("local-paragraph-proxy.invalid"));
            Assert.assertTrue(previousIdentity.contains("local-paragraph-proxy.invalid"));
            Assert.assertEquals(proxied.paragraphs().getFirst().getId(), service.extractRendered("10", previousIdentity).getFirst().getId());
        } finally { org.b3log.latke.Latkes.setServerHost(initialHost); }
        System.out.println("PASS restricted URL labels retain the exact paired link destination across request host changes without exposing titles");

        final String detailedUrl = privateUrl + "?p=2&sort=oldest#comment7";
        final var pairedLabels = new java.util.ArrayList<String>();
        final String kept = shortLinks.linkArticleWithUrlTitles(detailedUrl, (id, originalUrl) -> {
            Assert.assertEquals(id, privateId); pairedLabels.add(originalUrl); return originalUrl;
        });
        Assert.assertEquals(pairedLabels, java.util.List.of(detailedUrl));
        Assert.assertTrue(kept.contains("[" + detailedUrl + "](" + detailedUrl + ")"));
        Assert.assertEquals(shortLinks.linkArticleWithUrlTitles(privateUrl, (id, originalUrl) -> null), privateUrl);
        final var missing = service.compatibleMarkdown("10", privateUrl, id -> new LongArticleParagraphService.TitleSnapshot(null, false));
        Assert.assertEquals(missing.paragraphs().size(), 1);
        Assert.assertEquals(missing.paragraphs().getFirst().getSnapshot(), privateUrl);
        Assert.assertEquals(missing.idsFor(missing.paragraphs().getFirst().getId()).size(), 1);
        Assert.assertEquals(shortLinks.linkArticleWithUrlTitles("`" + privateUrl + "`", (id, originalUrl) -> { throw new AssertionError("inline code lookup"); }), "`" + privateUrl + "`");
        System.out.println("PASS safe URL label receives original query/fragment; missing titles and inline code retain existing fallback without database lookup");
        final String spoof = privateUrl.replace("paragraph.source.test", "paragraphXsourceYtest");
        Assert.assertEquals(shortLinks.linkArticleWithUrlTitles(spoof, (id, originalUrl) -> { throw new AssertionError("lookalike origin lookup"); }), spoof);
        System.out.println("PASS URL-preserving paired renderer requires literal configured source origin and cannot expand regex lookalike hosts");
    }
    private static void conflict(final Runnable operation) {
        try { operation.run(); Assert.fail("Expected ambiguous mapping rejection"); }
        catch (final org.b3log.symphony.util.ApiV3Exception expected) {
            Assert.assertEquals(expected.status, 409); Assert.assertFalse(expected.getMessage().contains("SECRET"));
        }
    }
}
