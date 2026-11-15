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

import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiV3ProfessionInputsTest {
    @Test
    public void rejectsCoercionBeforeSharedMapper() {
        for (final String json : new String[]{"{\"professionId\":123}", "{\"configJson\":{}}",
                "{\"levels\":[1]}", "{\"levels\":[{\"sortOrder\":\"1\"}]}",
                "{\"levels\":[{\"requiredTotalExperience\":1.5}]}",
                "{\"levels\":[{\"isTopLevel\":\"false\"}]}", "{\"professionIds\":[1]}",
                "{\"configJson\":\"{\\\"a\\\":1,\\\"a\\\":2}\"}"}) {
            try { ApiV3ProfessionInputs.validate(new JSONObject(json)); Assert.fail("错误类型应被拒绝"); }
            catch (final ApiV3Exception expected) { Assert.assertEquals(expected.status, 422); }
        }
        ApiV3ProfessionInputs.validate(new JSONObject("{\"levels\":[{\"sortOrder\":1,\"requiredTotalExperience\":9223372036854775807,\"isTopLevel\":false}]}"));
    }
    public static void main(final String[] args) {
        new ApiV3ProfessionInputsTest().rejectsCoercionBeforeSharedMapper();
        System.out.println("PASS: profession types, nested JSON and numeric bounds");
    }
}
