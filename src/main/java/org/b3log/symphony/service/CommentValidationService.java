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
import org.b3log.symphony.util.CommentValidation;
import org.b3log.symphony.util.CommentWriteQuota;
import org.b3log.symphony.util.ReservedWords;
import org.json.JSONObject;
import java.util.function.Consumer;

/** Session-independent adapter; v3 callers must supply only their Bearer actor. */
@Service
public class CommentValidationService {
    @Inject private LangPropsService labels;
    private CommentValidation.Rules rules() {
        return new CommentValidation.Rules() {
            public boolean allowWrite(final String actor, final boolean adding) { return CommentWriteQuota.access(actor, adding); }
            public String reservedWords(final String text) { return ReservedWords.processReservedWord(text); }
            public String label(final String key) { return labels.get(key); }
            public CensorResult censor(final String text) { return CensorFactory.getTextCensor().censor(text); }
        };
    }
    public void prepare(final JSONObject request, final JSONObject actor, final boolean adding) {
        CommentValidation.prepare(request, actor, adding, rules());
    }
    public void moderate(final JSONObject request, final Consumer<CensorResult> rejectedLog) {
        CommentValidation.moderate(request, rules(), rejectedLog);
    }
}
