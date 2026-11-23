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
package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;

import java.io.IOException;

/** 提供登录后可读的 API 开发文档。 */
@Singleton
public class ApiDocumentationProcessor {

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var page = beans.getReference(ApiDocumentationProcessor.class);
        final var login = beans.getReference(LoginCheckMidware.class);
        Dispatcher.get("/api-docs", page::index, login::handle);
        Dispatcher.get("/api-docs/bootstrap", page::bootstrap, login::handle);
        Dispatcher.get("/api-docs/v2", page::v2, login::handle);
        Dispatcher.get("/api-docs/v3", page::v3, login::handle);
        Dispatcher.get("/api-docs/v2-reference", page::v2Reference, login::handle);
        Dispatcher.get("/api-docs/v3-reference", page::v3Reference, login::handle);
    }

    public void index(final RequestContext context) {
        send(context, "index.html", "text/html");
    }

    public void bootstrap(final RequestContext context) {
        send(context, "reference.js", "text/javascript");
    }

    public void v2(final RequestContext context) {
        send(context, "v2.json", "application/json");
    }

    public void v3(final RequestContext context) {
        send(context, "v3.json", "application/json");
    }

    public void v2Reference(final RequestContext context) {
        send(context, "v2.md", "text/plain");
    }

    public void v3Reference(final RequestContext context) {
        send(context, "v3.md", "text/plain");
    }

    private void send(final RequestContext context, final String resource, final String contentType) {
        final var response = context.getResponse();
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self' blob:; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://file.fishpi.cn; "
                + "font-src 'self' https://file.fishpi.cn/ada/fonts/; "
                + "base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        // 只读取各处理方法指定的构建产物，资源路径不接受请求参数。
        try (final var input = ApiDocumentationProcessor.class.getResourceAsStream("/api-docs/" + resource)) {
            if (input == null) {
                context.sendError(503);
                return;
            }
            response.setContentType(contentType + "; charset=UTF-8");
            response.sendBytes(input.readAllBytes());
        } catch (final IOException e) {
            context.sendError(503);
        }
    }
}
