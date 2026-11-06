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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ApiV3WriteInputsTest {
    @Test
    public void strictBodyMediaTypeKeysAndParseFailuresHaveControlledStatus() {
        Assert.assertEquals(body("{\"title\":\"hello\"}", "application/json; charset=UTF-8", false).getString("title"), "hello");
        status(415, () -> body("{}", "text/plain", false));
        status(415, () -> body("{}", null, false));
        status(400, () -> body("{'title':'hello'}", "application/json", false));
        status(400, () -> body("{\"title\":1,\"title\":2}", "application/json", false));
        status(422, () -> body("{\"apiKey\":\"legacy\"}", "application/json", false));
        status(413, () -> body("x".repeat(ApiV3Json.MAX_BODY_BYTES + 1), "application/json", false));
        Assert.assertEquals(body("", null, true).length(), 0);
        status(400, () -> body("{", "application/json", true));
        status(400, () -> body("{\"title\":1,\"title\":2}", "application/json", true));
        status(400, () -> body("[]", "application/json", true));
        status(400, () -> body("   ", "application/json", true));
        status(400, () -> body("{\"title\":\"\\ud800\"}", "application/json", false));
        status(400, () -> body(new byte[]{123,34,116,105,116,108,101,34,58,34,(byte)0xc3,40,34,125}, "application/json", false));
        Assert.assertEquals(body("{\"title\":\"中文 🐟\\nline\\\\literal\"}", "application/json", false).getString("title"), "中文 🐟\nline\\literal");
    }

    @Test
    public void scalarTypesAreNotCoercedAndIntBoundsCannotOverflow() {
        status(422, () -> ApiV3WriteInputs.integer(new JSONObject().put("n", "1"), "n", 0, 0, 10));
        status(422, () -> ApiV3WriteInputs.integer(new JSONObject().put("n", 1.5), "n", 0, 0, 10));
        status(422, () -> ApiV3WriteInputs.integer(new JSONObject().put("n", Long.MAX_VALUE), "n", 0, 0, 10));
        status(422, () -> ApiV3WriteInputs.bool(new JSONObject().put("b", "true"), "b", false));
        status(422, () -> ApiV3WriteInputs.string(new JSONObject().put("s", JSONObject.NULL), "s", 10, false));
        status(422, () -> ApiV3WriteInputs.string(new JSONObject().put("s", "x" + (char) 0), "s", 10, false));
        Assert.assertEquals(ApiV3WriteInputs.integer(new JSONObject().put("n", 1.0), "n", 0, 0, 10), 1);
    }

    private JSONObject body(final String text, final String contentType, final boolean empty) {
        return body(text.getBytes(StandardCharsets.UTF_8), contentType, empty);
    }

    private JSONObject body(final byte[] bytes, final String contentType, final boolean empty) {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/v3/me/drafts");
        if (null != contentType) raw.headers().set("Content-Type", contentType);
        final Request request = new Request(null, raw) { @Override public String getRemoteAddr() { return "127.0.0.1"; } };
        raw.content().writeBytes(bytes);
        try { final var parse = Request.class.getDeclaredMethod("parseForm"); parse.setAccessible(true); parse.invoke(request); }
        catch (final ReflectiveOperationException e) { throw new AssertionError(e); }
        final Response response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        try { return ApiV3WriteInputs.body(new RequestContext(request, response), Set.of("title"), empty); }
        finally { raw.release(); }
    }

    private void status(final int expected, final Runnable call) {
        try { call.run(); Assert.fail("Expected protocol failure"); }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.status, expected); }
    }

    public static void main(final String[] args) {
        final var test = new ApiV3WriteInputsTest();
        test.strictBodyMediaTypeKeysAndParseFailuresHaveControlledStatus();
        test.scalarTypesAreNotCoercedAndIntBoundsCannotOverflow();
        System.out.println("PASS: 2 ApiV3WriteInputs tests using real Latke RequestContext");
    }
}
