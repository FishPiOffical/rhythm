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
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.GameWalletRepository;
import org.b3log.symphony.repository.GameWalletLedgerRepository;
import org.b3log.symphony.repository.PetIncomeAccountRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.GameWalletRules;
import org.json.JSONObject;

import java.time.LocalDate;

import static org.b3log.symphony.util.GameWalletRules.DAILY_LIMIT;

/** 游戏命令须先锁定用户并完成幂等校验；钱包、流水及游戏状态共用调用方事务。 */
@Service
public class GameWalletService {
    @Inject private GameWalletRepository wallets;
    @Inject private GameWalletLedgerRepository ledger;
    @Inject private PetIncomeAccountRepository income;

    private void requireTransaction() {
        if (!wallets.hasTransactionBegun()) throw new IllegalStateException("游园币操作需要已开启的账号事务");
    }

    private JSONObject row(final String actor) throws RepositoryException {
        requireTransaction();
        final var rows = wallets.select("SELECT * FROM `" + wallets.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || rows.getFirst().getLong("coins") < 0) throw unavailable();
        return rows.getFirst();
    }

    /** 首次参与某个游戏时，在同一个账号锁内开户；已有钱包及其他游戏的收入均保留。 */
    public void open(final String actor) throws RepositoryException {
        if (row(actor) == null) wallets.add(new JSONObject().put("oId", actor).put("coins", 0L));
    }

    public Long balance(final String actor) throws RepositoryException {
        final JSONObject wallet = row(actor);
        return wallet == null ? null : wallet.getLong("coins");
    }

    /** 正数用于首奖和补给，负数用于消费；有日额度的奖励必须走 creditEarned。 */
    public void change(final String actor, final int delta, final String operation, final String reference, final long now)
            throws RepositoryException {
        if (delta == 0) return;
        final JSONObject wallet = row(actor);
        if (wallet == null) throw unavailable();
        final long next = Math.addExact(wallet.getLong("coins"), delta);
        if (next < 0) throw new ApiV3Exception(409, "not_enough_coins", "游园币不足");
        wallets.update(actor, wallet.put("coins", next), "coins");
        ledger.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", actor)
                .put("operation", operation).put("referenceId", reference).put("coinDelta", delta).put("createdAt", now));
    }

    public JSONObject dailyIncome(final String actor, final long now) throws RepositoryException {
        return incomeState(incomeRow(actor), GameWalletRules.today(now));
    }

    public int availableIncome(final String actor, final long now) throws RepositoryException {
        final JSONObject budget = incomeRow(actor);
        checkIncomeClock(budget, now);
        return incomeState(budget, GameWalletRules.today(now)).getInt("remaining");
    }

    private JSONObject incomeRow(final String actor) throws RepositoryException {
        requireTransaction();
        final var rows = income.select("SELECT * FROM `" + income.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw unavailable();
        final JSONObject row = rows.getFirst();
        if (!(row.opt("earned") instanceof Number earned) || earned.longValue() < 0 || earned.longValue() > DAILY_LIMIT
                || earned.doubleValue() != earned.intValue()) throw unavailable();
        try {
            final String date = row.getString("date");
            if (!LocalDate.parse(date).toString().equals(date) || row.getLong("lastSettlementAt") < 0
                    || !GameWalletRules.today(row.getLong("lastSettlementAt")).equals(date)) throw unavailable();
        } catch (Exception error) {
            throw unavailable();
        }
        return row;
    }

    private JSONObject incomeState(final JSONObject budget, final String today) {
        final boolean current = budget != null && today.compareTo(budget.getString("date")) <= 0;
        final int earned = current ? budget.getInt("earned") : 0;
        return new JSONObject().put("date", current ? budget.getString("date") : today).put("earned", earned)
                .put("limit", DAILY_LIMIT).put("remaining", Math.max(0, DAILY_LIMIT - earned));
    }

    private void checkIncomeClock(final JSONObject budget, final long current) {
        if (budget != null && (current < budget.getLong("lastSettlementAt")
                || GameWalletRules.today(current).compareTo(budget.getString("date")) < 0))
            throw new ApiV3Exception(409, "clock_changed", "时间正在校准，请稍后再试");
    }

    public int creditEarned(final String actor, final int planned, final String operation, final String reference, final long current) throws RepositoryException {
        if (planned < 0) throw new IllegalArgumentException("收入不能为负");
        final JSONObject previous = incomeRow(actor);
        checkIncomeClock(previous, current);
        final String date = GameWalletRules.today(current);
        final JSONObject state = incomeState(previous, date);
        final int credited = Math.min(planned, state.getInt("remaining"));
        final JSONObject next = new JSONObject().put("oId", actor).put("date", date)
                .put("earned", Math.addExact(state.getInt("earned"), credited)).put("lastSettlementAt", current);
        // 上限按领取日与账号计算，不随换宠物重置；满额仍允许领取已开始工作的其他成果并释放活动槽。
        if (previous == null) income.add(next);
        else income.update(actor, next, "date", "earned", "lastSettlementAt");
        change(actor, credited, operation, reference, current);
        return credited;
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "游园币钱包暂不可用");
    }
}
