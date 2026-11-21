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
import org.b3log.symphony.repository.AppearanceProductRepository;
import org.b3log.symphony.repository.ChatBubbleStyleRepository;
import org.b3log.symphony.util.ApiV3BubbleStyle;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ApiV3BubbleStyleService {
    @Inject private ChatBubbleStyleRepository styles;
    @Inject private AppearanceProductRepository products;
    @Inject private ApiV3AdminService admin;

    public record Published(String id, JSONObject config, int revision, boolean active) {
        String name() { return config.getString("name"); }
        String access() { return config.getString("access"); }
        JSONObject style() { return config.getJSONObject("style"); }
    }

    public JSONObject available() throws RepositoryException {
        final var list = new JSONArray();
        for (final var row : styles.select("SELECT oId,publishedJson,publishedRevision,active FROM `" + styles.getName()
                + "` WHERE active=1 ORDER BY oId")) {
            final var bubble = publishedRow(row);
            if (bubble != null && !"points".equals(bubble.access())) {
                list.put(new JSONObject().put("id", bubble.id()).put("name", bubble.name()).put("access", bubble.access())
                        .put("style", bubble.style()).put("bubbleRevision", bubble.revision()));
            }
        }
        return new JSONObject().put("list", list);
    }

    JSONArray market() throws RepositoryException {
        final var list = new JSONArray();
        for (final var row : styles.select("SELECT b.oId,b.publishedJson,b.publishedRevision,b.active,p.price7,p.price30,p.enabled,p.revision"
                + " FROM `" + styles.getName() + "` b JOIN `" + products.getName() + "` p ON p.oId=b.oId"
                + " WHERE b.publishedRevision>0 ORDER BY b.oId")) {
            final var bubble = publishedRow(row);
            if (!"points".equals(bubble.access())) continue;
            ApiV3AppearanceShopService.validatePrice(row, true);
            list.put(new JSONObject().put("id", bubble.id()).put("styleId", bubble.id()).put("kind", "bubble")
                    .put("name", bubble.name()).put("version", 1).put("enabled", bubble.active() && row.getInt("enabled") == 1)
                    .put("revision", row.getLong("revision")).put("price7", row.getInt("price7")).put("price30", row.getInt("price30"))
                    .put("bubbleStyle", bubble.style()).put("bubbleRevision", bubble.revision()));
        }
        return list;
    }

    public JSONObject adminList(JSONObject identity, int page, int size) throws RepositoryException {
        final var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            final String table = "`" + styles.getName() + "`";
            final long total = styles.select("SELECT COUNT(*) AS total FROM " + table).getFirst().getLong("total");
            final var list = new JSONArray();
            for (final var row : styles.select("SELECT * FROM " + table + " ORDER BY oId LIMIT ? OFFSET ?", size, (page - 1L) * size)) {
                list.put(adminRow(row));
            }
            tx.commit();
            return ApiV3Requests.pagination(list, page, size, total);
        } finally {
            ApiV3AdminService.rollbackIfActive(tx);
        }
    }

    public JSONObject update(JSONObject identity, String id, JSONObject body, String key, Request request) throws Exception {
        if (!ApiV3BubbleStyle.customId(id) || !(body.opt("action") instanceof String action)) throw ApiV3WriteInputs.invalid();
        final boolean save = "save".equals(action);
        if (!Set.of("save", "publish", "unpublish").contains(action)
                || !body.keySet().equals(save ? Set.of("action", "revision", "draft") : Set.of("action", "revision"))) {
            throw ApiV3WriteInputs.invalid();
        }
        final int revision = ApiV3BubbleStyle.integer(body, "revision", 0, Integer.MAX_VALUE - 1);
        final JSONObject draft = save ? draft(body.optJSONObject("draft")) : null;
        final var input = new JSONObject().put("action", action).put("revision", revision);
        if (save) input.put("draft", draft);
        final var actor = new AtomicReference<ApiV3AdminService.AuthorizedActor>();
        return ApiV3IdempotencyMgmtService.runAtomic(admin.guardedWrite(identity, null, true, actor),
                ApiV3AdminService.actorId(identity), "admin.chat.bubble:" + id, key, input, () -> {
                    final JSONObject current = row(id, true);
                    if (current == null ? revision != 0 || !save : current.getInt("revision") != revision) {
                        throw new ApiV3Exception(409, "bubble_revision_changed", "气泡配置已变化，请重新读取");
                    }
                    final JSONObject next = current == null ? new JSONObject().put("oId", id)
                            .put("publishedJson", "").put("publishedRevision", 0).put("active", 0) : current;
                    final Published last = publishedRow(next);
                    if (save) {
                        if (last != null && !last.access().equals(draft.getString("access"))) {
                            throw new ApiV3Exception(409, "bubble_access_locked", "已发布气泡的获取方式固定，可以复制为新气泡");
                        }
                        next.put("draftJson", draft.toString());
                    } else if ("publish".equals(action)) {
                        final JSONObject ready = draft(new JSONObject(next.getString("draftJson")));
                        next.put("publishedJson", ready.toString()).put("active", 1)
                                .put("publishedRevision", Math.addExact(next.getInt("publishedRevision"), 1));
                        if ("points".equals(ready.getString("access"))) syncProduct(id, ready, true);
                    } else {
                        if (last == null || !last.active()) throw new ApiV3Exception(409, "bubble_not_active", "当前气泡未上架");
                        next.put("active", 0);
                        if ("points".equals(last.access())) syncProduct(id, last.config(), false);
                    }
                    next.put("revision", revision + 1);
                    if (current == null) styles.add(next);
                    else styles.update(id, next, "draftJson", "publishedJson", "active", "publishedRevision", "revision");
                    final int code = switch (action) {
                        case "publish" -> Operation.OPERATION_CODE_C_PUBLISH_CHAT_BUBBLE;
                        case "unpublish" -> Operation.OPERATION_CODE_C_UNPUBLISH_CHAT_BUBBLE;
                        default -> Operation.OPERATION_CODE_C_SAVE_CHAT_BUBBLE;
                    };
                    admin.recordOperation(request, code, id, actor.get().user());
                    return adminRow(next);
                });
    }

    private void syncProduct(String id, JSONObject config, boolean active) throws RepositoryException {
        final var rows = products.select("SELECT revision FROM `" + products.getName() + "` WHERE oId=? FOR UPDATE", id);
        final var price = new JSONObject().put("price7", config.getInt("price7")).put("price30", config.getInt("price30"))
                .put("enabled", active ? 1 : 0).put("revision", rows.isEmpty() ? 1L : Math.addExact(rows.getFirst().getLong("revision"), 1L));
        if (rows.isEmpty()) products.add(price.put("oId", id));
        else products.update(id, price, "price7", "price30", "enabled", "revision");
    }

    Published published(String id, boolean lock) throws RepositoryException {
        return ApiV3BubbleStyle.customId(id) ? publishedRow(row(id, lock)) : null;
    }

    static Published publishedRow(JSONObject row) {
        if (row == null || row.optInt("publishedRevision") == 0) return null;
        return new Published(row.getString("oId"), new JSONObject(row.getString("publishedJson")), row.getInt("publishedRevision"), row.getInt("active") == 1);
    }

    private JSONObject row(String id, boolean lock) throws RepositoryException {
        final var rows = styles.select("SELECT * FROM `" + styles.getName() + "` WHERE oId=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static JSONObject adminRow(JSONObject row) {
        final JSONObject currentDraft = new JSONObject(row.getString("draftJson"));
        final Published published = publishedRow(row);
        return new JSONObject().put("id", row.getString("oId")).put("revision", row.getInt("revision"))
                .put("publishedRevision", row.getInt("publishedRevision")).put("active", row.getInt("active") == 1)
                .put("draft", currentDraft).put("hasDraftChanges",
                        published == null || !ApiV3Json.canonical(currentDraft).equals(ApiV3Json.canonical(published.config())));
    }

    private static JSONObject draft(JSONObject input) {
        if (input == null || !input.keySet().equals(Set.of("name", "access", "price7", "price30", "style"))
                || !(input.opt("access") instanceof String access) || !Set.of("free", "vip", "points").contains(access)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String name = ApiV3WriteInputs.string(input, "name", 48, false).strip();
        if (name.isEmpty() || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw ApiV3WriteInputs.invalid();
        final boolean paid = "points".equals(access);
        return new JSONObject().put("name", name).put("access", access)
                .put("price7", ApiV3BubbleStyle.integer(input, "price7", paid ? 1 : 0, paid ? 100000000 : 0))
                .put("price30", ApiV3BubbleStyle.integer(input, "price30", 0, paid ? 100000000 : 0))
                .put("style", ApiV3BubbleStyle.validate(input.optJSONObject("style")));
    }
}
