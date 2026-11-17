package org.b3log.symphony.service;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PetBrawlAttemptRepository;
import org.b3log.symphony.repository.PetBrawlInviteRepository;
import org.b3log.symphony.repository.PetBrawlMatchRepository;
import org.b3log.symphony.repository.PetBrawlOrderRepository;
import org.b3log.symphony.repository.PetBrawlPairRepository;
import org.b3log.symphony.repository.PetRiskAuditRepository;
import org.b3log.symphony.util.ApiV3PostExportFiles;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 管理员玩法分析。埋点独立于乱斗事务，导出只投影玩法字段和用途隔离的匿名编号。 */
@Service
public class PetAnalyticsService {
    private static final Logger LOGGER = LogManager.getLogger(PetAnalyticsService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int ROWS = 25_000;
    private static final long DAY = 86_400_000L;
    private static final long ZIP_BUDGET = 8L * 1024 * 1024;
    private final AtomicLong lastWarning = new AtomicLong();
    private final AtomicLong lastCleanup = new AtomicLong();

    @Inject private PetBrawlAttemptRepository attempts;
    @Inject private PetBrawlMatchRepository matches;
    @Inject private PetBrawlPairRepository pairs;
    @Inject private PetBrawlInviteRepository invites;
    @Inject private PetBrawlOrderRepository orders;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PetRiskAuditRepository risk;

    void recordMatch(final String owner, final String key, final ApiV3PetBrawl.MatchTrace trace, final String result) {
        Transaction transaction = null;
        try {
            final String id = ApiV3TokenUtil.hash("request-key\0" + owner + "\0pet.brawl-match\0" + key);
            transaction = ApiV3Transactions.strict(attempts.beginTransaction());
            final var previous = attempts.select("SELECT result FROM " + table(attempts) + " WHERE oId=? FOR UPDATE", id);
            final JSONObject row = new JSONObject().put("oId", id).put("userId", owner).put("createdAt", trace.startedAt)
                    .put("result", result).put("weaponLevel", trace.weaponLevel)
                    .put("nearCount", trace.candidateCounts[0]).put("midCount", trace.candidateCounts[1])
                    .put("wideCount", trace.candidateCounts[2]).put("matchedTier", trace.matchedTier).put("weaponGap", trace.weaponGap);
            if (previous.isEmpty()) attempts.add(row);
            else if ("matched".equals(result) && !"matched".equals(previous.getFirst().getString("result")))
                attempts.update(id, row, "createdAt", "result", "weaponLevel", "nearCount", "midCount", "wideCount", "matchedTier", "weaponGap");
            transaction.commit();
        } catch (Exception error) {
            rollback(transaction);
            final long now = System.currentTimeMillis(), last = lastWarning.get();
            if (now - last >= 60_000 && lastWarning.compareAndSet(last, now))
                LOGGER.warn("乱斗匹配观测暂不可用 ({})", error.getClass().getSimpleName());
            return;
        }
        trim(trace.startedAt);
    }

    private void trim(final long now) {
        final long last = lastCleanup.get();
        if (now - last < 3_600_000 || !lastCleanup.compareAndSet(last, now)) return;
        Transaction transaction = null;
        try {
            transaction = ApiV3Transactions.strict(attempts.beginTransaction());
            // 每次清理固定上限，避免历史观测阻塞匹配请求。
            for (JSONObject row : attempts.select("SELECT oId FROM " + table(attempts)
                    + " WHERE createdAt<? ORDER BY createdAt,oId LIMIT 100", now - 90 * DAY))
                attempts.remove(row.getString("oId"));
            transaction.commit();
        } catch (Exception error) {
            rollback(transaction);
            LOGGER.warn("清理乱斗匹配观测失败 ({})", error.getClass().getSimpleName());
        }
    }

    private static void rollback(final Transaction transaction) {
        try {
            if (transaction != null && transaction.isActive()) transaction.rollback();
        } catch (RuntimeException error) {
            LOGGER.warn("回滚宠物分析事务失败 ({})", error.getClass().getSimpleName());
        }
    }

    public byte[] export(final LocalDate from, final LocalDate to) throws Exception {
        final String key = System.getenv("PET_ANALYTICS_EXPORT_KEY");
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("请先配置宠物分析导出密钥");
        final long start = from.atStartOfDay(ZONE).toInstant().toEpochMilli();
        final long end = to.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        final var encoder = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, key);
        final var bytes = new ByteArrayOutputStream();
        // 文件构造成功后再返回，超出预算时整次缩小日期重试。
        try (var zip = new ZipOutputStream(ApiV3PostExportFiles.bounded(bytes, ZIP_BUDGET), StandardCharsets.UTF_8)) {
            write(zip, "battles.csv", "time,mode,version,stage,practice,player,opponent,winner,rounds,actions,materials,"
                    + "player_species,player_weapon,player_skill,player_upgrade,player_artifact,player_soul,player_soul_rank,player_rune,player_rune_rank,"
                    + "opponent_species,opponent_weapon,opponent_skill,opponent_upgrade,opponent_artifact,opponent_soul,opponent_soul_rank,opponent_rune,opponent_rune_rank",
                    matches.select("SELECT m.createdAt,m.userId,m.snapshotJson,COALESCE(p.recipient,i.recipient,'') AS opponent"
                            + " FROM " + table(matches) + " m LEFT JOIN " + table(pairs) + " p ON p.oId=m.oId"
                            + " LEFT JOIN " + table(invites) + " i ON i.matchId=m.oId"
                            + " WHERE m.createdAt>=? AND m.createdAt<? ORDER BY m.createdAt,m.oId" + limit(), start, end), row -> {
                        final JSONObject report = new JSONObject(row.getString("snapshotJson")).getJSONObject("report");
                        final var turns = report.getJSONArray("turns");
                        return csv(row.getLong("createdAt"), tag(report.optString("mode", "npc")), report.getInt("version"),
                                report.getInt("stage"), report.getBoolean("practice"), anon(encoder, "player", row.getString("userId")),
                                anon(encoder, "player", row.getString("opponent")), report.getInt("winner"),
                                turns.getJSONObject(turns.length() - 1).getInt("round"), turns.length(),
                                report.getInt("reward")) + "," + gear(report.optString("species"), report.getJSONObject("player"))
                                + "," + gear(report.optString("opponentSpecies"), report.getJSONObject("opponent"));
                    });
            write(zip, "match_attempts.csv", "time,player,attempt,result,weapon_level,near_count,mid_count,wide_count,matched_tier,weapon_gap",
                    attempts.select("SELECT * FROM " + table(attempts)
                            + " WHERE createdAt>=? AND createdAt<? ORDER BY createdAt,oId" + limit(), start, end),
                    row -> csv(row.getLong("createdAt"), anon(encoder, "player", row.getString("userId")), anon(encoder, "attempt", row.getString("oId")),
                            tag(row.getString("result")), row.getInt("weaponLevel"), known(row, "nearCount"), known(row, "midCount"),
                            known(row, "wideCount"), known(row, "matchedTier"), known(row, "weaponGap")));

            final String paidAt = "CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshotJson,'$.completedAt')) AS UNSIGNED)";
            write(zip, "orders.csv", "completed_at,player,product,weapon,previous_level,next_level,points_paid,materials_used,version",
                    orders.select("SELECT userId,snapshotJson FROM " + table(orders)
                            + " WHERE JSON_UNQUOTE(JSON_EXTRACT(snapshotJson,'$.status'))='completed' AND "
                            + paidAt + ">=? AND " + paidAt + "<? ORDER BY " + paidAt + ",oId" + limit(), start, end), row -> {
                        final JSONObject order = new JSONObject(row.getString("snapshotJson"));
                        return csv(order.getLong("completedAt"), anon(encoder, "player", row.getString("userId")),
                                tag(order.optString("product", "WEAPON")), tag(order.getString("weapon")), order.getInt("level"),
                                order.getInt("level") + 1, order.getInt("points"), order.getInt("materials"), order.getInt("version"));
                    });
            write(zip, "invitations.csv", "created_at,expires_at,sender,recipient,status",
                    invites.select("SELECT createdAt,expiresAt,sender,recipient,status FROM " + table(invites)
                            + " WHERE createdAt>=? AND createdAt<? ORDER BY createdAt,oId" + limit(), start, end), row -> {
                        final String state = row.getString("status");
                        return csv(row.getLong("createdAt"), row.getLong("expiresAt"), anon(encoder, "player", row.getString("sender")),
                                anon(encoder, "player", row.getString("recipient")),
                                tag("pending".equals(state) && row.getLong("expiresAt") <= System.currentTimeMillis() ? "expired" : state));
                    });
            final Map<String, Signals> signals = new LinkedHashMap<>();
            write(zip, "pet_commands.csv", "time,player,attempt,operation,action",
                    requests.select("SELECT oId,userId,createdAt,operation,"
                            + " JSON_UNQUOTE(JSON_EXTRACT(responseJson,'$.operation')) AS action FROM " + table(requests)
                            + " WHERE operation LIKE 'pet.%' AND createdAt>=? AND createdAt<? ORDER BY createdAt,oId" + limit(), start, end),
                    row -> {
                        final String user = row.getString("userId");
                        final long time = row.getLong("createdAt");
                        final String operation = tag(row.getString("operation").substring(4));
                        final String action = tag(row.optString("action"));
                        if (!operation.startsWith("brawl-"))
                            signals.computeIfAbsent(user, unused -> new Signals()).command(time, action);
                        return csv(time, anon(encoder, "player", user), anon(encoder, "attempt", row.getString("oId")), operation, action);
                    });
            write(zip, "pet_risk.csv", "time,player,attempt,operation,reason",
                    risk.select("SELECT userId,commandHash,operation,reason,MIN(occurredAt) AS occurredAt FROM " + table(risk)
                            + " WHERE occurredAt>=? AND occurredAt<? GROUP BY userId,commandHash,operation,reason"
                            + " ORDER BY occurredAt,commandHash,reason" + limit(), start, end), row -> {
                        final String user = row.getString("userId");
                        final String reason = tag(row.getString("reason"));
                        final String operation = tag(row.getString("operation"));
                        if (!operation.startsWith("brawl-"))
                            signals.computeIfAbsent(user, unused -> new Signals()).risk(reason);
                        return csv(row.getLong("occurredAt"), anon(encoder, "player", user),
                                anon(encoder, "attempt", row.getString("commandHash")), operation, reason);
                    });
            if (signals.size() > ROWS) throw new IllegalArgumentException("记录过多，请缩小导出日期范围");
            final var summary = new StringBuilder("player,commands,active_hours,distinct_actions,peak_per_minute,median_gap_ms,rate_limited,verification_required,verification_failed").append('\n');
            signals.forEach((user, signal) -> summary.append(csv(anon(encoder, "player", user), signal.count, signal.hours.size(), signal.actions.size(),
                    signal.peak, signal.medianGap(), signal.limited, signal.challenge, signal.failed)).append('\n'));
            entry(zip, "pet_signals.csv", summary.toString());
            entry(zip, "manifest.json",
                    new JSONObject().put("version", 1).put("zone", ZONE.getId()).put("from", from.toString()).put("to", to.toString())
                            .put("timestampUnit", "milliseconds").put("snapshotAt", System.currentTimeMillis())
                            .put("matchedTiers", new JSONObject().put("0", "0-4").put("1", "5-8").put("2", "9+"))
                            .put("candidateSampleLimit", 32).put("riskDeduplication", "attempt_and_reason_in_selected_window")
                            .put("petSignalsIncludeBrawl", false).put("medianGapMaxMs", 300_000).toString());
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface Projection { String project(JSONObject row); }

    private static void write(final ZipOutputStream zip, final String file, final String header, final List<JSONObject> rows,
                              final Projection project) throws IOException {
        if (rows.size() > ROWS) throw new IllegalArgumentException("记录过多，请缩小导出日期范围");
        final var text = new StringBuilder(header).append('\n');
        for (JSONObject row : rows) text.append(project.project(row)).append('\n');
        entry(zip, file, text.toString());
    }

    private static void entry(final ZipOutputStream zip, final String filename, final String text) throws IOException {
        final var file = new ZipEntry(filename);
        file.setTime(0);
        zip.putNextEntry(file);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 汇总时序和保护事件，供人工复核，不生成风险结论。 */
    private static final class Signals {
        private final Set<Long> hours = new HashSet<>();
        private final Set<String> actions = new HashSet<>();
        private final ArrayDeque<Long> recent = new ArrayDeque<>();
        private final List<Long> gaps = new ArrayList<>();
        private long last = -1;
        private int count, peak, limited, challenge, failed;

        void command(final long time, final String action) {
            count++;
            hours.add(time / 3_600_000);
            if (!action.isEmpty()) actions.add(action);
            if (last >= 0 && time - last <= 300_000) gaps.add(time - last);
            last = time;
            while (!recent.isEmpty() && time - recent.getFirst() >= 60_000) recent.removeFirst();
            recent.addLast(time);
            peak = Math.max(peak, recent.size());
        }

        void risk(final String reason) {
            switch (reason) {
                case "rate_limited" -> limited++;
                case "verification_required" -> challenge++;
                case "verification_failed" -> failed++;
                default -> { }
            }
        }

        String medianGap() {
            if (gaps.isEmpty()) return "";
            gaps.sort(Long::compareTo);
            final int middle = gaps.size() / 2;
            return Long.toString((gaps.get((gaps.size() - 1) / 2) + gaps.get(middle)) / 2);
        }
    }

    private static String gear(final String species, final JSONObject setup) {
        final JSONObject gear = setup.optJSONObject("gear");
        return csv(tag(species), tag(setup.optString("weapon")), tag(setup.optString("skill")), setup.getInt("level"),
                gear == null ? 0 : gear.optInt("artifact"), gear == null ? "" : tag(gear.optString("soul")),
                gear == null ? "" : knownRank(gear, "soulLevel"), gear == null ? "" : tag(gear.optString("rune")),
                gear == null ? "" : knownRank(gear, "runeLevel"));
    }

    private static String table(final AbstractRepository repository) { return "`" + repository.getName() + "`"; }
    private static String limit() { return " LIMIT " + (ROWS + 1); }
    private static String tag(final String value) { return value.matches("[A-Za-z0-9_-]{1,64}") ? value : ""; }
    private static String known(final JSONObject row, final String key) {
        final int value = row.getInt(key);
        return value < 0 ? "" : Integer.toString(value);
    }
    private static String knownRank(final JSONObject row, final String key) {
        return row.has(key) ? known(row, key) : "";
    }
    private static String anon(final HmacUtils encoder, final String kind, final String value) {
        return value.isEmpty() ? "" : kind.charAt(0) + "_" + encoder.hmacHex("pet-analytics-v1\0" + kind + "\0" + value).substring(0, 32);
    }

    /** 所有字符串都经过白名单或 HMAC，允许出的标点只来自固定列定义。 */
    private static String csv(final Object... cells) {
        final var result = new StringBuilder();
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) result.append(',');
            result.append(cells[index]);
        }
        return result.toString();
    }
}
