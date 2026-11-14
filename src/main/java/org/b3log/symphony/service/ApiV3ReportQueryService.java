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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3ReportInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 只公开已有的举报分类，隐藏目标数据和举报人历史。
 */
@Service
public class ApiV3ReportQueryService {
    @Inject
    private UserRepository users;

    public JSONObject options(final String actor) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        try {
            final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=?", actor);
            if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId"))
                    || !(rows.getFirst().opt("userStatus") instanceof Number status)
                    || status.doubleValue() != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
            final JSONArray targets = new JSONArray();
            targets.put(target(0, "文章"));
            targets.put(target(1, "评论"));
            targets.put(target(2, "用户"));
            targets.put(target(3, "聊天室消息"));
            targets.put(target(4, "复读内容"));
            return new JSONObject().put("targets", targets);
        } catch (final RepositoryException e) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "举报选项暂不可用");
        }
    }

    private static JSONObject target(final int type, final String label) {
        final JSONArray values = new JSONArray();
        for (final int reason : ApiV3ReportInputs.reasonsFor(type))
            values.put(new JSONObject().put("reason", reason).put("label", switch (reason) {
                case 0 -> "垃圾广告";
                case 1 -> "低俗色情";
                case 2 -> "违法违规";
                case 3 -> "涉嫌侵权";
                case 4 -> "人身攻击";
                case 5 -> "冒充账号";
                case 6 -> "垃圾广告账号";
                case 7 -> "个人信息违规";
                case 49 -> "其他";
                default -> throw new IllegalArgumentException();
            }));
        return new JSONObject().put("targetType", type).put("label", label).put("reasons", values);
    }
}
