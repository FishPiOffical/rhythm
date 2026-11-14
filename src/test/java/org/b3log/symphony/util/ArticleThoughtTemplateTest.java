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

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Actual FTL playback blocks; emitted fixtures are checked as HTML/JavaScript by thought-render-test.mjs. */
public class ArticleThoughtTemplateTest {
    public static void main(final String[] args) throws Exception {
        final var configuration = new Configuration(Configuration.VERSION_2_3_30);
        final var fixtures = new JSONArray();
        for (final String skin : List.of("pc", "mobile")) {
            final String source = Files.readString(Path.of("src/main/resources/skins/classic", skin, "article.ftl"));
            new Template(skin + "-full", new StringReader(source), configuration);
            final var match = Pattern.compile("(?s)<#if 3 == article\\.articleType && thoughtPlaybackContent\\?\\?>.*?</#if>").matcher(source);
            Assert.assertTrue(match.find());
            final var template = new Template(skin, new StringReader(match.group()), configuration);
            final var absent = new StringWriter(); template.process(Map.of("article", Map.of("articleType", 3)), absent);
            Assert.assertTrue(absent.toString().isBlank());
            for (final String raw : List.of("text\u001f1\u001f0-0\u001f0-0\u001e", "\\'; globalInjected=1; //\n\"quotes\"", "</script><img src=x onerror='globalInjected=1'>", "unicode\u2028line\u2029end")) {
                final var output = new StringWriter();
                template.process(Map.of("article", Map.of("articleType", 3), "thoughtPlaybackContent", raw), output);
                fixtures.put(new JSONObject().put("skin", skin).put("raw", raw).put("script", output.toString()));
            }
        }
        Files.writeString(Path.of(args[0]), fixtures.toString());
        System.out.println("PASS real PC/mobile thought FTL encoding and absent/blocked content guard; JS round-trip fixtures emitted");
    }
}
