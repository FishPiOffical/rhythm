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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.PetActivityRepository;
import org.b3log.symphony.repository.PetJournalRepository;
import org.b3log.symphony.repository.PetRiskAuditRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/** 管理员日志查询只投影已存记录的必要字段。 */
@Service
public class PetAdminLogQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);
    private static final int PAGE_SIZE = 30;
    public static final int MAX_PAGE = 1000;
    private static final Map<String, String> ACTIVITY_TYPES = Map.ofEntries(
            entry("outing", "外出"), entry("course", "上课"), entry("work", "兼职"),
            entry("feed", "喂食"), entry("water", "饮水"), entry("pet", "抚摸"), entry("play", "玩耍"),
            entry("rest", "休息"), entry("wake", "醒来"), entry("purchase", "购买"),
            entry("craft", "制作"), entry("story-claim", "领取事件奖励"), entry("readoption", "重新领养"));
    private static final Map<String, String> RISK_TYPES = Map.of(
            "adoption", "领养", "readoption", "重新领养", "story-claim", "领取事件奖励", "name", "改名",
            "action", "照顾", "supplies", "领取补给", "purchase", "购买", "craft", "制作",
            "activity-start", "开始活动", "activity-claim", "领取活动");
    private static final Map<String, String> RISK_REASONS = Map.of(
            "rate_limited", "账号限流", "verification_required", "要求验证", "challenge_issued", "已发起验证",
            "verification_passed", "验证通过", "verification_failed", "验证失败",
            "verification_unavailable", "验证服务不可用", "idempotency_conflict", "幂等键冲突");

    @Inject private PetActivityRepository activities;
    @Inject private PetJournalRepository journal;
    @Inject private PetRiskAuditRepository riskAudit;
    @Inject private UserRepository users;

    public record Filters(String tab, String user, String from, String to, String operation, int page) {
    }

    public record Page(List<Map<String, String>> rows, boolean hasNext) {
    }

    public static Map<String, String> operations(final String tab) {
        return "risk".equals(tab) ? RISK_TYPES : ACTIVITY_TYPES;
    }

    public static Filters filters(final String tab, final String user, final String from, final String to,
                                  final String operation, final String page, final LocalDate today) {
        final String selectedTab = tab == null || tab.isEmpty() ? "activity" : tab;
        final String account = user == null ? "" : user.strip();
        final String action = operation == null ? "" : operation;
        if (!List.of("activity", "risk").contains(selectedTab) || account.length() > 64
                || account.codePoints().anyMatch(Character::isISOControl)
                || (!action.isEmpty() && !operations(selectedTab).containsKey(action))) {
            throw new IllegalArgumentException("用户或操作类型无效。");
        }
        if (page != null && !page.matches("[1-9][0-9]{0,3}")) {
            throw new IllegalArgumentException("页码无效。");
        }
        final int pageNumber = page == null ? 1 : Integer.parseInt(page);
        if (pageNumber > MAX_PAGE) {
            throw new IllegalArgumentException("请缩小查询范围。");
        }
        final LocalDate end = date(to, today);
        final LocalDate start = date(from, end.minusDays(6));
        if (start.isAfter(end) || end.isAfter(start.plusDays(30))) {
            throw new IllegalArgumentException("请选择有效的日期范围，每次最多查询 31 天。");
        }
        return new Filters(selectedTab, account, start.toString(), end.toString(), action, pageNumber);
    }

    public Page list(final Filters filters) throws RepositoryException {
        String userId = filters.user();
        if (!userId.isEmpty() && !userId.matches("[1-9][0-9]{0,18}")) {
            // 纯数字用户名可加 @，与直接按 UID 查询区分。
            final String username = userId.startsWith("@") ? userId.substring(1) : userId;
            final JSONObject user = users.getByName(username);
            if (user == null) return new Page(List.of(), false);
            userId = user.getString(Keys.OBJECT_ID);
        }
        final long from = LocalDate.parse(filters.from()).atStartOfDay(ZONE).toInstant().toEpochMilli();
        final long until = LocalDate.parse(filters.to()).plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        final List<Object> params = new ArrayList<>();
        final List<String> sources = new ArrayList<>();
        if ("risk".equals(filters.tab())) {
            sources.add(source(riskAudit.getName(), "occurredAt", "operation", "reason", "''", "0", "0", "risk",
                    userId, filters.operation(), from, until, params));
        } else {
            final String action = filters.operation();
            if (action.isEmpty() || List.of("outing", "course", "work").contains(action)) {
                sources.add(source(activities.getName(), "startedAt", "kind", "entryId", "petId", "endsAt", "claimedAt", "activity",
                        userId, action, from, until, params));
            }
            if (action.isEmpty() || !List.of("outing", "course", "work").contains(action)) {
                sources.add(source(journal.getName(), "occurredAt", "action", "''", "petId", "0", "0", "event",
                        userId, action, from, until, params));
            }
        }
        // 多读一条判断下一页，避免对所有符合条件的记录做额外计数。
        params.add(PAGE_SIZE + 1);
        params.add((filters.page() - 1) * PAGE_SIZE);
        final String query = "SELECT * FROM (" + String.join(" UNION ALL ", sources)
                + ") pet_logs ORDER BY eventTime DESC,oId DESC,recordType DESC LIMIT ? OFFSET ?";
        final List<JSONObject> records = journal.select(query, params.toArray());
        final List<Map<String, String>> rows = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final JSONObject row : records.subList(0, Math.min(PAGE_SIZE, records.size()))) {
            final String type = row.getString("recordType");
            final String action = row.getString("operation");
            final String detail = row.getString("detail");
            final String status = "activity".equals(type) ? activityStatus(row, now) : "";
            rows.add(Map.of(
                    "time", TIME.format(Instant.ofEpochMilli(row.getLong("eventTime"))),
                    "userId", row.getString("userId"), "petId", row.getString("petId"),
                    "operation", operations(filters.tab()).getOrDefault(action, action),
                    "detail", "risk".equals(type) ? RISK_REASONS.getOrDefault(detail, detail) : detail,
                    "status", status));
        }
        return new Page(rows, records.size() > PAGE_SIZE && filters.page() < MAX_PAGE);
    }

    private static String activityStatus(final JSONObject row, final long now) {
        if (row.getLong("claimedAt") > 0) {
            return "已领取 · " + TIME.format(Instant.ofEpochMilli(row.getLong("claimedAt")));
        }
        return row.getLong("endsAt") <= now ? "待领取" : "进行中";
    }

    private static String source(final String table, final String time, final String operation, final String detail,
                                 final String pet, final String ends, final String claimed, final String type,
                                 final String userId, final String action, final long from, final long until,
                                 final List<Object> params) {
        // 表名和列名来自仓库与固定投影，筛选值一律绑定参数。
        final StringBuilder sql = new StringBuilder("SELECT oId,userId,")
                .append(time).append(" AS eventTime,").append(operation).append(" AS operation,")
                .append(detail).append(" AS detail,").append(pet).append(" AS petId,")
                .append(ends).append(" AS endsAt,").append(claimed).append(" AS claimedAt,'").append(type)
                .append("' AS recordType FROM `").append(table).append("` WHERE ").append(time)
                .append(">=? AND ").append(time).append("<?");
        params.add(from);
        params.add(until);
        if (!userId.isEmpty()) {
            sql.append(" AND userId=?");
            params.add(userId);
        }
        if (!action.isEmpty()) {
            sql.append(" AND ").append(operation).append("=?");
            params.add(action);
        }
        return sql.toString();
    }

    private static LocalDate date(final String value, final LocalDate fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("请输入有效日期。");
    }
}
