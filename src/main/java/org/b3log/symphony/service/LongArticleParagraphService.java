/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.symphony.service;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Latkes;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.MediaPlayers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses, annotates and migrates long-article paragraph anchors.
 */
@Service
public class LongArticleParagraphService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final int SNAPSHOT_MAX_LENGTH = 1024;

    private static final double FUZZY_MATCH_THRESHOLD = 0.82D;

    private static final int FUZZY_MATCH_CANDIDATE_LIMIT = 32;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private ShortLinkQueryService shortLinkQueryService;

    public static final class Paragraph {
        private String id;
        private final String kind;
        private int index;
        private final String normalizedText;
        private final String snapshot;
        private Element element;

        private Paragraph(final String kind, final String normalizedText, final String snapshot,
                          final Element element) {
            this.kind = kind;
            this.normalizedText = normalizedText;
            this.snapshot = snapshot;
            this.element = element;
        }

        public String getId() {
            return id;
        }

        public String getKind() {
            return kind;
        }

        public int getIndex() {
            return index;
        }

        public String getSnapshot() {
            return snapshot;
        }

        private JSONObject toJSON() {
            final JSONObject ret = new JSONObject();
            ret.put(Comment.COMMENT_PARAGRAPH_ID, id);
            ret.put(Comment.COMMENT_PARAGRAPH_KIND, kind);
            ret.put(Comment.COMMENT_PARAGRAPH_INDEX, index);
            ret.put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, snapshot);
            return ret;
        }
    }

    public JSONObject annotateRenderedContent(final String articleId, final String renderedHTML) {
        final Document document = parse(renderedHTML);
        final List<Paragraph> paragraphs = extract(articleId, document, true);
        final List<JSONObject> paragraphJSON = new ArrayList<>();
        for (final Paragraph paragraph : paragraphs) {
            paragraphJSON.add(paragraph.toJSON());
        }
        final Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        document.outputSettings(outputSettings);
        final JSONObject ret = new JSONObject();
        ret.put("html", document.body().html());
        ret.put("paragraphs", paragraphJSON);
        return ret;
    }

    public Paragraph findParagraph(final String articleId, final String markdown, final String paragraphId) {
        if (!isValidParagraphId(paragraphId)) {
            return null;
        }
        for (final Paragraph paragraph : extractMarkdown(articleId, markdown)) {
            if (paragraphId.equals(paragraph.id)) {
                return paragraph;
            }
        }
        return null;
    }

    public Paragraph findRenderedParagraph(final String articleId, final String renderedHTML,
                                           final String paragraphId) {
        if (!isValidParagraphId(paragraphId)) {
            return null;
        }
        for (final Paragraph paragraph : extract(articleId, parse(renderedHTML), false)) {
            if (paragraphId.equals(paragraph.id)) {
                return paragraph;
            }
        }
        return null;
    }

    public List<Paragraph> extractRendered(final String articleId, final String renderedHTML) {
        return extract(articleId, parse(renderedHTML), false);
    }

    public boolean isValidParagraphId(final String paragraphId) {
        return null != paragraphId && paragraphId.matches("[0-9a-f]{64}");
    }

    public List<Paragraph> extractMarkdown(final String articleId, final String markdown) {
        String content = shortLinkQueryService.linkArticle(StringUtils.defaultString(markdown));
        String html = Markdowns.toHTML(Emotions.convert(content));
        html = Markdowns.clean(html, Latkes.getServePath() + "/article/" + articleId);
        html = MediaPlayers.renderAudio(html);
        html = MediaPlayers.renderVideo(html);
        return extract(articleId, parse(html), false);
    }

    public void migrateCommentsInCurrentTransaction(final String articleId, final String oldMarkdown,
                                                     final String newMarkdown) throws RepositoryException {
        final List<JSONObject> comments = getParagraphComments(articleId);
        if (comments.isEmpty()) {
            return;
        }

        final List<Paragraph> oldParagraphs = extractMarkdown(articleId, oldMarkdown);
        final List<Paragraph> newParagraphs = extractMarkdown(articleId, newMarkdown);
        final Map<String, Paragraph> oldById = indexById(oldParagraphs);
        final Map<String, Paragraph> newById = indexById(newParagraphs);
        final Map<String, Paragraph> mapped = matchParagraphs(oldParagraphs, newParagraphs);
        final Set<String> claimedNewIds = new HashSet<>();
        for (final Paragraph paragraph : mapped.values()) {
            claimedNewIds.add(paragraph.id);
        }

        final Map<String, List<JSONObject>> commentsByParagraph = new LinkedHashMap<>();
        for (final JSONObject comment : comments) {
            commentsByParagraph.computeIfAbsent(comment.optString(Comment.COMMENT_PARAGRAPH_ID), key -> new ArrayList<>()).add(comment);
        }

        for (final Map.Entry<String, List<JSONObject>> entry : commentsByParagraph.entrySet()) {
            final String oldId = entry.getKey();
            Paragraph target = newById.get(oldId);
            if (null == target && oldById.containsKey(oldId)) {
                target = mapped.get(oldId);
            }
            if (null == target) {
                target = matchOrphan(entry.getValue().get(0), newParagraphs, claimedNewIds);
            }
            if (null != target) {
                claimedNewIds.add(target.id);
            }
            updateCommentParagraphs(entry.getValue(), target);
        }
    }

    private List<JSONObject> getParagraphComments(final String articleId) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(
                new PropertyFilter(Comment.COMMENT_ON_ARTICLE_ID, FilterOperator.EQUAL, articleId),
                new PropertyFilter(Comment.COMMENT_TYPE, FilterOperator.EQUAL, Comment.COMMENT_TYPE_C_PARAGRAPH));
        return commentRepository.getList(new Query().setPage(1, Integer.MAX_VALUE).setPageCount(1).setFilter(filter));
    }

    private void updateCommentParagraphs(final List<JSONObject> comments, final Paragraph target)
            throws RepositoryException {
        for (final JSONObject comment : comments) {
            if (null == target) {
                comment.put(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ORPHANED);
            } else {
                comment.put(Comment.COMMENT_PARAGRAPH_ID, target.id);
                comment.put(Comment.COMMENT_PARAGRAPH_KIND, target.kind);
                comment.put(Comment.COMMENT_PARAGRAPH_INDEX, target.index);
                comment.put(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE);
            }
            commentRepository.update(comment.optString(Keys.OBJECT_ID), comment);
        }
    }

    private Map<String, Paragraph> matchParagraphs(final List<Paragraph> oldParagraphs,
                                                   final List<Paragraph> newParagraphs) {
        final Map<String, Paragraph> ret = new HashMap<>();
        final Map<String, Paragraph> newById = indexById(newParagraphs);
        final Set<String> used = new HashSet<>();
        for (final Paragraph oldParagraph : oldParagraphs) {
            final Paragraph exact = newById.get(oldParagraph.id);
            if (null != exact) {
                ret.put(oldParagraph.id, exact);
                used.add(exact.id);
            }
        }
        for (final Paragraph oldParagraph : oldParagraphs) {
            if (ret.containsKey(oldParagraph.id) || oldParagraph.normalizedText.length() < 12) {
                continue;
            }
            final Paragraph matched = bestMatch(oldParagraph.kind, oldParagraph.normalizedText,
                    oldParagraph.index, newParagraphs, used);
            if (null != matched) {
                ret.put(oldParagraph.id, matched);
                used.add(matched.id);
            }
        }
        return ret;
    }

    private Paragraph matchOrphan(final JSONObject comment, final List<Paragraph> paragraphs,
                                  final Set<String> used) {
        final String snapshot = normalize(comment.optString(Comment.COMMENT_PARAGRAPH_SNAPSHOT));
        if (snapshot.length() < 12) {
            return null;
        }
        return bestMatch(comment.optString(Comment.COMMENT_PARAGRAPH_KIND), snapshot,
                comment.optInt(Comment.COMMENT_PARAGRAPH_INDEX, -1), paragraphs, used);
    }

    private Paragraph bestMatch(final String kind, final String text, final int oldIndex,
                                final List<Paragraph> candidates, final Set<String> used) {
        final String comparableText = StringUtils.substring(text, 0, SNAPSHOT_MAX_LENGTH);
        return candidates.stream().
                filter(candidate -> kind.equals(candidate.kind) && !used.contains(candidate.id)).
                sorted(Comparator.comparingInt(candidate -> oldIndex < 0
                        ? candidate.index : Math.abs(oldIndex - candidate.index))).
                limit(FUZZY_MATCH_CANDIDATE_LIMIT).
                map(candidate -> new Match(candidate, similarity(comparableText,
                        StringUtils.substring(candidate.normalizedText, 0, SNAPSHOT_MAX_LENGTH)),
                        Math.abs(oldIndex - candidate.index))).
                filter(match -> match.similarity >= FUZZY_MATCH_THRESHOLD).
                sorted(Comparator.comparingDouble((Match match) -> match.similarity).reversed().
                        thenComparingInt(match -> match.distance)).
                map(match -> match.paragraph).
                findFirst().orElse(null);
    }

    private static final class Match {
        private final Paragraph paragraph;
        private final double similarity;
        private final int distance;

        private Match(final Paragraph paragraph, final double similarity, final int distance) {
            this.paragraph = paragraph;
            this.similarity = similarity;
            this.distance = distance;
        }
    }

    private double similarity(final String left, final String right) {
        final int maxLength = Math.max(left.length(), right.length());
        if (0 == maxLength) {
            return 1D;
        }
        final int[] previous = new int[right.length() + 1];
        final int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                final int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            System.arraycopy(current, 0, previous, 0, current.length);
        }
        return 1D - ((double) previous[right.length()] / maxLength);
    }

    private Map<String, Paragraph> indexById(final List<Paragraph> paragraphs) {
        final Map<String, Paragraph> ret = new HashMap<>();
        for (final Paragraph paragraph : paragraphs) {
            ret.put(paragraph.id, paragraph);
        }
        return ret;
    }

    private List<Paragraph> extract(final String articleId, final Document document, final boolean annotate) {
        final List<Paragraph> paragraphs = new ArrayList<>();
        extractChildren(document.body(), paragraphs, annotate);
        final Map<String, Integer> duplicateCounts = new HashMap<>();
        int index = 0;
        for (final Paragraph paragraph : paragraphs) {
            paragraph.index = index++;
            final String duplicateKey = paragraph.kind + "\n" + paragraph.normalizedText;
            final int duplicateIndex = duplicateCounts.getOrDefault(duplicateKey, 0);
            duplicateCounts.put(duplicateKey, duplicateIndex + 1);
            paragraph.id = sha256(articleId + "\n" + duplicateKey + "\n" + duplicateIndex);
            if (annotate && null != paragraph.element) {
                paragraph.element.attr("data-long-paragraph-id", paragraph.id);
                paragraph.element.attr("data-long-paragraph-kind", paragraph.kind);
                paragraph.element.attr("data-long-paragraph-index", String.valueOf(paragraph.index));
            }
        }
        return paragraphs;
    }

    private void extractChildren(final Element parent, final List<Paragraph> paragraphs, final boolean annotate) {
        for (final Element element : new ArrayList<>(parent.children())) {
            final String tag = element.tagName();
            if ("p".equals(tag)) {
                extractParagraph(element, paragraphs, annotate);
            } else if (tag.matches("h[1-6]")) {
                addParagraph("heading", element, paragraphs);
            } else if ("ul".equals(tag) || "ol".equals(tag)) {
                for (final Element child : element.children()) {
                    if ("li".equals(child.tagName())) {
                        addParagraph("list-item", child, paragraphs);
                    }
                }
            } else if ("blockquote".equals(tag)) {
                addParagraph("blockquote", element, paragraphs);
            } else if ("pre".equals(tag)) {
                addParagraph("code", element, paragraphs);
            } else if ("table".equals(tag)) {
                addParagraph("table", element, paragraphs);
            } else if ("figure".equals(tag) || isMediaTag(tag)) {
                addParagraph("media", element, paragraphs);
            } else if ("div".equals(tag) || "section".equals(tag) || "article".equals(tag)
                    || "main".equals(tag) || "header".equals(tag) || "footer".equals(tag)) {
                extractChildren(element, paragraphs, annotate);
            }
        }
    }

    private void extractParagraph(final Element paragraph, final List<Paragraph> paragraphs,
                                  final boolean annotate) {
        if (containsMedia(paragraph)) {
            addParagraph("media", paragraph, paragraphs);
            return;
        }
        boolean hasDirectBreak = false;
        for (final Node child : paragraph.childNodes()) {
            if (child instanceof Element && "br".equals(((Element) child).tagName())) {
                hasDirectBreak = true;
                break;
            }
        }
        if (!hasDirectBreak) {
            addParagraph("paragraph", paragraph, paragraphs);
            return;
        }

        final List<Node> nodes = new ArrayList<>();
        for (final Node node : paragraph.childNodes()) {
            nodes.add(node.clone());
        }
        if (annotate) {
            paragraph.empty();
            Element line = paragraph.appendElement("span").addClass("long-article-paragraph-line");
            for (final Node node : nodes) {
                if (node instanceof Element && "br".equals(((Element) node).tagName())) {
                    addParagraph("paragraph", line, paragraphs);
                    line = paragraph.appendElement("span").addClass("long-article-paragraph-line");
                } else {
                    line.appendChild(node);
                }
            }
            addParagraph("paragraph", line, paragraphs);
            return;
        }

        final Element holder = new Element(Tag.valueOf("span"), "");
        Element line = holder.appendElement("span");
        for (final Node node : nodes) {
            if (node instanceof Element && "br".equals(((Element) node).tagName())) {
                addParagraph("paragraph", line, paragraphs);
                line = holder.appendElement("span");
            } else {
                line.appendChild(node.clone());
            }
        }
        addParagraph("paragraph", line, paragraphs);
    }

    private void addParagraph(final String kind, final Element element, final List<Paragraph> paragraphs) {
        final String snapshot = snapshot(kind, element);
        final String normalized = normalize(snapshot);
        if (StringUtils.isBlank(normalized)) {
            return;
        }
        paragraphs.add(new Paragraph(kind, normalized, StringUtils.substring(snapshot, 0, SNAPSHOT_MAX_LENGTH), element));
    }

    private String snapshot(final String kind, final Element element) {
        String text = element.text();
        if ("media".equals(kind) && StringUtils.isBlank(text)) {
            final Element media = findMedia(element);
            if (null != media) {
                text = StringUtils.defaultIfBlank(media.attr("alt"),
                        StringUtils.defaultIfBlank(media.attr("title"), media.attr("src")));
            }
            if (StringUtils.isBlank(text)) {
                text = "媒体";
            }
        }
        return normalize(text);
    }

    private static boolean isMediaTag(final String tag) {
        return "img".equals(tag) || "video".equals(tag) || "audio".equals(tag)
                || "iframe".equals(tag) || "picture".equals(tag);
    }

    private static boolean containsMedia(final Element element) {
        return null != findMedia(element);
    }

    private static Element findMedia(final Element element) {
        for (final Element media : element.select("img,video,audio,iframe,picture")) {
            if (!"img".equals(media.tagName()) || !media.hasClass("emoji")) {
                return media;
            }
        }
        return null;
    }

    private static Document parse(final String html) {
        final Document document = Jsoup.parseBodyFragment(StringUtils.defaultString(html));
        final Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        document.outputSettings(outputSettings);
        return document;
    }

    private static String normalize(final String value) {
        final String withoutInvisible = INVISIBLE.matcher(StringUtils.defaultString(value)).replaceAll("");
        return WHITESPACE.matcher(withoutInvisible).replaceAll(" ").trim();
    }

    private static String sha256(final String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder ret = new StringBuilder(64);
            for (final byte b : digest) {
                ret.append(String.format("%02x", b & 0xff));
            }
            return ret.toString();
        } catch (final Exception e) {
            throw new IllegalStateException("Generates paragraph id failed", e);
        }
    }
}
