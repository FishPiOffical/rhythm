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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.util.ArticleValidation;
import org.b3log.symphony.util.ArticleWriteQuota;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.ReservedWords;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Binds shared validation to the real moderation, tag rules and old write quota, without session identity. */
@Service
public class ArticleValidationService {
    @Inject private LangPropsService labels;
    @Inject private TagQueryService tags;
    public void validate(final JSONObject request, final JSONObject actor, final boolean adding, final Consumer<String> rejectedLog) {
        ArticleValidation.validate(request, actor, adding, new ArticleValidation.Rules() {
            public String label(final String key) { return labels.get(key); }
            public String reservedWords(final String text) { return ReservedWords.processReservedWord(text); }
            public String clearEmotions(final String text) { return Emotions.clear(text); }
            public String formatTags(final String text) { return Tag.formatTags(text); }
            public List<String> invalidTags() { return tags.getInvalidTags(); }
            public boolean whiteListedTag(final String tag) { return Tag.containsWhiteListTags(tag); }
            public boolean validTag(final String tag) { return Tag.TAG_TITLE_PATTERN.matcher(tag).matches(); }
            public int maxTagLength() { return Tag.MAX_TAG_TITLE_LENGTH; }
            public boolean reservedTag(final String tag) { return Arrays.asList(Symphonys.RESERVED_TAGS).contains(tag); }
            public boolean allowWrite(final String id) { return ArticleWriteQuota.access(id); }
            public CensorResult censor(final String text) { return CensorFactory.getTextCensor().censor(text); }
        }, rejectedLog);
    }
}
