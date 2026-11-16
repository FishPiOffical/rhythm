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

import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.repository.ClientDownloadRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ApiV3ClientDownloadService {
    private static final Set<String> GROUPS = Set.of("desktop", "mobile", "tools", "terminal", "more");
    private static final Set<String> STATUSES = Set.of("active", "retired", "comingSoon");
    private static final Set<String> ICON_HOSTS = Set.of("fishpi.cn", "file.fishpi.cn", "pic.fishpi.cn");

    @Inject private ClientDownloadRepository clients;
    @Inject private ApiV3AdminService admin;

    public JSONObject directory() throws RepositoryException {
        final var list = new JSONArray();
        for (final var row : clients.select("SELECT oId,configJson FROM " + table() + " ORDER BY sortOrder,oId")) {
            final JSONObject item = project(row);
            if (!"active".equals(item.getString("status"))) {
                item.remove("links");
                item.put("recommended", false);
            }
            list.put(item);
        }
        return new JSONObject().put("list", list);
    }

    public JSONObject adminList(JSONObject identity, int page, int size) throws RepositoryException {
        final var transaction = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            final long total = clients.select("SELECT COUNT(*) AS total FROM " + table()).getFirst().getLong("total");
            final var rows = clients.select("SELECT * FROM " + table() + " ORDER BY sortOrder,oId LIMIT ? OFFSET ?",
                    size, (page - 1L) * size);
            final var list = new JSONArray();
            for (final var row : rows) list.put(project(row).put("revision", row.getInt("revision")));
            transaction.commit();
            return ApiV3Requests.pagination(list, page, size, total);
        } finally {
            ApiV3AdminService.rollbackIfActive(transaction);
        }
    }

    public JSONObject save(JSONObject identity, String id, JSONObject body, String key, Request request) throws Exception {
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || id.length() > 48
                || !body.keySet().equals(Set.of("revision", "client"))) throw ApiV3WriteInputs.invalid();
        final int revision = ApiV3AdminService.requireExactInt(body, "revision", 0, Integer.MAX_VALUE - 1);
        final JSONObject client = normalize(body.optJSONObject("client"));
        final var actor = new AtomicReference<ApiV3AdminService.AuthorizedActor>();
        final var command = new JSONObject().put("revision", revision).put("client", client);
        return ApiV3IdempotencyMgmtService.runAtomic(admin.guardedWrite(identity, null, true, actor),
                ApiV3AdminService.actorId(identity), "admin.clients.save:" + id, key, command, () -> {
                    final var rows = clients.select("SELECT revision FROM " + table() + " WHERE oId=? FOR UPDATE", id);
                    if (rows.isEmpty() ? revision != 0 : revision != rows.getFirst().getInt("revision")) {
                        throw new ApiV3Exception(409, "client_revision_changed", "客户端配置已更新，请重新读取");
                    }
                    final var updated = new JSONObject().put("configJson", client.toString())
                            .put("sortOrder", client.getInt("sortOrder")).put("revision", revision + 1);
                    if (rows.isEmpty()) clients.add(updated.put("oId", id));
                    else clients.update(id, updated, "configJson", "sortOrder", "revision");
                    admin.recordOperation(request, Operation.OPERATION_CODE_C_SAVE_CLIENT_DOWNLOAD, id, actor.get().user());
                    return new JSONObject().put("clientId", id).put("revision", revision + 1);
                });
    }

    static JSONObject normalize(JSONObject input) {
        if (input == null || !input.keySet().equals(Set.of("name", "group", "status", "icon", "authors", "links", "sortOrder", "recommended"))
                || !(input.opt("recommended") instanceof Boolean recommended)) throw ApiV3WriteInputs.invalid();
        final String name = field(input, "name", 80, true);
        final String group = field(input, "group", 16, true);
        final String status = field(input, "status", 16, true);
        final String icon = field(input, "icon", 512, false);
        final int order = ApiV3AdminService.requireExactInt(input, "sortOrder", 0, 999999);
        if (!GROUPS.contains(group) || !STATUSES.contains(status) || !icon.isEmpty() && !safeUrl(icon, true))
            throw ApiV3WriteInputs.invalid();

        final var authors = new JSONArray();
        final var usernames = new HashSet<String>();
        final var sourceAuthors = input.optJSONArray("authors");
        final var sourceLinks = input.optJSONArray("links");
        if (sourceAuthors == null || sourceAuthors.length() > 4 || sourceLinks == null || sourceLinks.length() > 5)
            throw ApiV3WriteInputs.invalid();
        for (final Object value : sourceAuthors) {
            if (!(value instanceof JSONObject author) || !author.keySet().equals(Set.of("name", "userName")))
                throw ApiV3WriteInputs.invalid();
            final String userName = field(author, "userName", 64, true);
            if (!userName.matches("[a-zA-Z0-9_-]+") || !usernames.add(userName)) throw ApiV3WriteInputs.invalid();
            authors.put(new JSONObject().put("userName", userName).put("name", field(author, "name", 64, true)));
        }
        final var links = new JSONArray();
        final var addresses = new HashSet<String>();
        for (final Object value : sourceLinks) {
            if (!(value instanceof JSONObject link) || !link.keySet().equals(Set.of("label", "href")))
                throw ApiV3WriteInputs.invalid();
            final String href = field(link, "href", 512, true);
            if (!safeUrl(href, false) || !addresses.add(href)) throw ApiV3WriteInputs.invalid();
            links.put(new JSONObject().put("href", href).put("label", field(link, "label", 64, true)));
        }
        final boolean active = "active".equals(status);
        if (active && links.isEmpty()) throw new ApiV3Exception(422, "download_url_required", "上架客户端至少需要一个下载链接");
        final var projected = new JSONObject().put("name", name).put("group", group).put("status", status).put("icon", icon)
                .put("authors", authors).put("links", links).put("sortOrder", order).put("recommended", active && recommended);
        if (projected.toString().length() > 4096) throw ApiV3WriteInputs.invalid();
        return projected;
    }

    private static String field(JSONObject input, String key, int size, boolean required) {
        if (!(input.opt(key) instanceof String text) || text.length() > size
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                        || Character.getType(c) == Character.SURROGATE)) throw ApiV3WriteInputs.invalid();
        final String cleaned = text.strip();
        if (required && cleaned.isEmpty()) throw ApiV3WriteInputs.invalid();
        return cleaned;
    }

    private static boolean safeUrl(String text, boolean icon) {
        if (!icon && text.matches("/article/[0-9]{1,19}")) return true;
        try {
            final URI uri = URI.create(text);
            final String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && uri.getRawUserInfo() == null && uri.getPort() == -1
                    && (!icon || ICON_HOSTS.contains(host) && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getRawPath().matches("/[A-Za-z0-9/_-][A-Za-z0-9/_.-]*[.](?:png|jpg|jpeg|webp|avif|ico)")
                    && !uri.getRawPath().contains("..")
                    && (!host.equals("fishpi.cn") || uri.getRawPath().startsWith("/images/clients/")));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static JSONObject project(JSONObject row) {
        return normalize(new JSONObject(row.getString("configJson"))).put("id", row.getString("oId"));
    }

    private String table() {
        return "`" + clients.getName() + "`";
    }
}
