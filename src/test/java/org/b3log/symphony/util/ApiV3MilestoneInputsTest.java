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

import org.b3log.symphony.model.Milestone;
import org.json.JSONObject;
import org.testng.Assert;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class ApiV3MilestoneInputsTest {
    private static JSONObject valid() {
        return new JSONObject().put("title", " title ").put("date", "2026-10-17").put("content", "# Markdown\n\n**bold**");
    }

    public static void main(final String[] args) throws Exception {
        final JSONObject normalized = ApiV3MilestoneInputs.normalize(valid().put("endDate", "2026-10-18").put("mediaURL", "https://example.test/a'b.png").put("mediaType", "image"));
        Assert.assertEquals(normalized.getString(Milestone.MILESTONE_TITLE), "title");
        Assert.assertTrue(normalized.getString(Milestone.MILESTONE_MEDIA_URL).contains("%27"));
        Assert.assertEquals(normalized.getString(Milestone.MILESTONE_CONTENT), "# Markdown\n\n**bold**");
        for (final JSONObject input : List.of(valid().put("title", ""), valid().put("title", "x".repeat(256)), valid().put("content", "x".repeat(102401)),
                valid().put("date", "2026-02-30"), valid().put("date", "2026-1-1"), valid().put("date", "0000-01-01"), valid().put("endDate", "2026-10-16"),
                valid().put("mediaURL", "https://example.test/x"), valid().put("mediaType", "audio"), valid().put("link", "javascript:alert(1)"),
                valid().put("link", "//example.test/x"), valid().put("link", "https://user:pass@example.test/"), valid().put("link", "https://example.test/\\x"),
                valid().put("content", "x\u0001y"), valid().put("title", "x\ny"), valid().put("status", "approved"), valid().put("authorId", "other"), valid().put("content", 1))) {
            try {
                ApiV3MilestoneInputs.normalize(input);
                Assert.fail(input.toString());
            } catch (final ApiV3Exception expected) {
            }
        }
        System.out.println("PASS typed Markdown inputs, storage lengths, strict calendar, interval, metadata and safe URLs");
        final Configuration config = new Configuration();
        final String payload = "\"><textarea></textarea><img src=x onerror=alert(1)></script>";
        for (final String device : List.of("pc", "mobile")) {
            for (final String page : List.of("admin/milestone.ftl", "admin/milestones.ftl", "milestones.ftl")) {
                final String source = Files.readString(Path.of("src/main/resources/skins/classic/" + device + "/" + page));
                new Template(page, new StringReader(source), config); // Parse the complete real template, including all directives.
                if (page.startsWith("admin/")) {
                    final var expressions = Pattern.compile("\\$\\{(?:\\()?milestone\\.(?:milestoneTitle|milestoneContent|milestoneMediaCaption|milestoneLink|milestoneMediaUrl)[^}]*}").matcher(source);
                    int checked = 0;
                    while (expressions.find()) {
                        final var out = new StringWriter();
                        new Template("value", new StringReader(expressions.group()), config).process(Map.of("milestone", Map.of("milestoneTitle", payload, "milestoneContent", payload, "milestoneMediaCaption", payload, "milestoneLink", payload, "milestoneMediaUrl", payload)), out);
                        Assert.assertFalse(out.toString().contains("<img"));
                        Assert.assertFalse(out.toString().contains("</textarea>"));
                        checked++;
                    }
                    Assert.assertTrue(checked > 0);
                } else {
                    for (final String expression : List.of("${e.title?html?js_string}", "${e.media.caption?html?js_string}")) {
                        Assert.assertTrue(source.contains(expression));
                        final var out = new StringWriter();
                        new Template("timeline", new StringReader(expression), config).process(Map.of("e", Map.of("title", payload, "media", Map.of("caption", payload))), out);
                        Assert.assertFalse(out.toString().contains("<img"));
                        Assert.assertFalse(out.toString().contains("</script>"));
                    }
                }
            }
        }
        System.out.println("PASS six real PC/mobile templates parse and their changed user-value expressions escape hostile content");
    }
}
