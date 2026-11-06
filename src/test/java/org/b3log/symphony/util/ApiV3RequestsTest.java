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

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiV3RequestsTest {
    @Test
    public void textTrimsButDoesNotMutateSearchMeaning() {
        Assert.assertEquals(ApiV3Requests.text("  中文 100%_'!  ", 100, true), "中文 100%_'!");
    }

    @Test(expectedExceptions = ApiV3Requests.ApiException.class)
    public void blankRequiredTextFails() { ApiV3Requests.text(" \t ", 100, true); }

    @Test(expectedExceptions = ApiV3Requests.ApiException.class)
    public void excessiveTextFails() { ApiV3Requests.text("x".repeat(101), 100, true); }

    @Test(expectedExceptions = ApiV3Requests.ApiException.class)
    public void controlCharactersFail() { ApiV3Requests.text("hello" + (char) 0 + "world", 100, true); }

    @Test
    public void emptyAndLargePaginationCountsRemainExact() {
        final JSONObject empty = ApiV3Requests.pagination(new JSONArray(), 1, 20, 0).getJSONObject("pagination");
        Assert.assertEquals(empty.getLong("pageCount"), 0L);
        final JSONObject large = ApiV3Requests.pagination(new JSONArray(), 1, 50, 3000000000L).getJSONObject("pagination");
        Assert.assertEquals(large.getLong("pageCount"), 60000000L);
    }
}
