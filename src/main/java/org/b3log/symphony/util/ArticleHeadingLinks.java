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
package org.b3log.symphony.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ArticleHeadingLinks {
    private ArticleHeadingLinks() { }

    public static String prepare(final Document doc, final Elements headings) {
        final boolean sidebar = headings.size() >= 3;
        final Element list = new Element("ul").addClass("article-toc");
        final Map<String, String> aliases = new HashMap<>();
        final Set<String> targets = new HashSet<>();
        final Set<String> occupied = new HashSet<>();
        final Set<Element> headingElements = new HashSet<>(headings);
        for (Element other : doc.select("[id]")) {
            if (!headingElements.contains(other)) occupied.add(other.id());
        }
        for (int index = 0; index < headings.size(); index++) {
            final Element heading = headings.get(index);
            final String text = heading.text();
            final String previous = heading.id();
            final String id = sidebar || previous.isEmpty() ? "toc_" + heading.tagName() + "_" + index : previous;
            heading.attr("id", id);
            targets.add(id);
            if (!previous.isEmpty()) {
                aliases.merge(previous, id, ArticleHeadingLinks::unique);
            }
            final String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N} _-]", "").replace(' ', '-');
            aliases.merge(slug, id, ArticleHeadingLinks::unique);
            if (sidebar) {
                list.appendElement("li").addClass("toc-" + heading.tagName()).appendElement("a")
                        .attr("data-id", id).attr("href", "#" + id).text(text);
            }
        }
        for (Element link : doc.select("a[href^=#]")) {
            final String href = link.attr("href");
            final String fragment;
            try {
                fragment = URI.create(href.replace(" ", "%20")).getFragment();
            } catch (IllegalArgumentException malformedFragment) {
                continue;
            }
            if (fragment.isEmpty() || occupied.contains(fragment)) continue;
            final String id = targets.contains(fragment) ? fragment : aliases.get(fragment);
            if (id != null && !id.isEmpty()) {
                link.attr("href", "#" + id).removeAttr("target");
            }
        }
        return sidebar ? list.outerHtml() : "";
    }

    private static String unique(final String first, final String second) {
        return first.equals(second) ? first : "";
    }
}
