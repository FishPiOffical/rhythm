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

import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3RepeaterPublicationInputs;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RepeaterContentModerationServiceTest {

    @Test(timeOut = 60000)
    public void approvalRequiresExplicitPass() {
        final RepeaterContentModerationService service = service(new JSONObject().put("action", "pass").put("do", "pass").put("type", "正常"));
        service.requireApproved("plain text");

        final RepeaterContentModerationService review = service(new JSONObject().put("action", "review").put("do", "pass").put("type", "待审核"));
        final ApiV3Exception rejected = Assert.expectThrows(ApiV3Exception.class, () -> review.requireApproved("plain text"));
        Assert.assertEquals(rejected.status, 422);
    }

    @Test(timeOut = 60000)
    public void reviewAndMalformedResultsFailClosed() {
        final RepeaterContentModerationService unknown = service(new JSONObject().put("action", "review").put("do", "pass").put("type", "未知"));
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class, () -> unknown.requireApproved("text")).status, 503);

        final RepeaterContentModerationService malformed = service(new JSONObject());
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class, () -> malformed.requireApproved("text")).status, 503);

        final RepeaterContentModerationService failed = new RepeaterContentModerationService() {
            @Override
            protected JSONObject censor(final String content) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class, () -> failed.requireApproved("text")).status, 503);
    }

    @Test(timeOut = 60000)
    public void normalizedTextKeepsLineBreaksAndRemovesMarkup() {
        final JSONObject normalized = ApiV3RepeaterPublicationInputs.normalize(new JSONObject()
                .put("type", "joke").put("content", " first\r\nsecond\rthird <script>alert(1)</script> "));
        Assert.assertEquals(normalized.getString("content"), "first\nsecond\nthird");
        Assert.assertEquals(ApiV3RepeaterPublicationInputs.normalize(new JSONObject().put("type", "joke")
                .put("content", "a &amp; b\n&lt;hello&gt;")).getString("content"), "a & b\n<hello>");
    }

    @Test(timeOut = 60000)
    public void inputBoundsAndUnicodeAreValidated() {
        for (String text : new String[]{"a", "x".repeat(501), "a\u0001b", "x\ud800"}) {
            Assert.expectThrows(ApiV3Exception.class, () -> ApiV3RepeaterPublicationInputs.normalize(
                    new JSONObject().put("type", "joke").put("content", text)));
        }
        final String text = "a  b\n\tsecond line";
        Assert.assertEquals(ApiV3RepeaterPublicationInputs.normalize(new JSONObject()
                .put("type", "joke").put("content", text)).getString("content"), text);
    }

    private RepeaterContentModerationService service(final JSONObject result) {
        return new RepeaterContentModerationService() {
            @Override
            protected JSONObject censor(final String content) {
                return result;
            }
        };
    }
}
