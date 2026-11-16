package org.b3log.symphony.service;

import java.security.SecureRandom;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.PetBrawlAccountRepository;
import org.b3log.symphony.repository.PetBrawlMatchRepository;
import org.b3log.symphony.util.ApiV3PetBattle;
import org.b3log.symphony.util.ApiV3PetBattle.Setup;
import org.b3log.symphony.util.ApiV3PetBattle.Skill;
import org.b3log.symphony.util.ApiV3PetBattle.Weapon;
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

/** 由宠物服务在已取得本人用户锁的事务内调用。 */
@Service
public class ApiV3PetBrawl {
    static final int MIN_LEVEL = 10;
    private static final int[] REWARDS = {5, 5, 10};
    private static final Setup[] OPPONENTS = {
            new Setup(Weapon.SPOON, Skill.SPLASH, 0),
            new Setup(Weapon.GUARD, Skill.CURL, 1),
            new Setup(Weapon.FAN, Skill.BREATHE, 2)
    };
    static final int STAGES = OPPONENTS.length;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Inject private PetBrawlAccountRepository accounts;
    @Inject private PetBrawlMatchRepository matches;

    JSONObject state(final String owner, final JSONObject pet, final long now) throws RepositoryException {
        final JSONObject account = account(owner);
        final String today = ApiV3PetRules.today(now);
        final JSONArray growth = new JSONArray();
        for (int level = 0; level <= ApiV3PetBattle.COST.length; level++)
            growth.put(setup(new Setup(Weapon.SPOON, Skill.SPLASH, level)));
        return new JSONObject().put("petId", pet == null ? JSONObject.NULL : pet.getString("oId"))
                .put("species", pet == null ? JSONObject.NULL : pet.getString("speciesId"))
                .put("level", pet == null ? 0 : ApiV3PetRules.level(pet.getInt("experience")))
                .put("minLevel", MIN_LEVEL).put("version", ApiV3PetBattle.VERSION)
                .put("materials", account.getInt("materials")).put("stages", OPPONENTS.length)
                .put("unlocked", Math.min(STAGES, account.getInt("unlocked")))
                .put("cleared", account.getInt("unlocked") - 1).put("rewardCount", rewardCount(account, today))
                .put("levels", new JSONObject(account.getString("levelsJson")))
                .put("growth", growth).put("costs", new JSONArray(ApiV3PetBattle.COST)).put("rewards", new JSONArray(REWARDS));
    }

    JSONObject fight(final String owner, final JSONObject pet, final Weapon weapon, final Skill skill,
                     final int stage, final boolean practice, final long now) throws RepositoryException {
        final JSONObject account = account(owner);
        if (!practice && stage > account.getInt("unlocked"))
            throw ApiV3PetRules.conflict("brawl_stage_locked", "先通过上一关");
        final int level = practice ? 0 : new JSONObject(account.getString("levelsJson")).getInt(weapon.name());
        final Setup opponent = OPPONENTS[stage - 1];
        final Setup player = new Setup(weapon, skill, level);
        final Setup target = practice ? new Setup(opponent.weapon(), opponent.skill(), 0) : opponent;
        final long seed = RANDOM.nextLong();
        final var result = ApiV3PetBattle.resolve(player, target, seed);
        final String today = ApiV3PetRules.today(now);
        final int count = rewardCount(account, today);
        final int reward = !practice && count < REWARDS.length ? REWARDS[count] : 0;
        if (reward > 0) {
            if (today.compareTo(account.getString("rewardDate")) > 0) account.put("rewardDate", today);
            account.put("rewardCount", count + 1).put("materials", Math.addExact(account.getInt("materials"), reward));
        }
        if (!practice && result.winner() == 0 && stage == account.getInt("unlocked"))
            account.put("unlocked", stage + 1);
        if (!practice) save(account);

        final String id = Ids.genTimeMillisId();
        final JSONArray turns = new JSONArray();
        for (var turn : result.turns()) {
            turns.put(new JSONObject().put("round", turn.round()).put("actor", turn.actor())
                    .put("damage", turn.damage()).put("healing", turn.healing())
                    .put("playerHp", turn.playerHp()).put("opponentHp", turn.opponentHp()));
        }
        final JSONObject report = new JSONObject().put("id", id).put("createdAt", now)
                .put("version", ApiV3PetBattle.VERSION).put("petId", pet.getString("oId"))
                .put("species", pet.getString("speciesId")).put("stage", stage).put("practice", practice)
                .put("player", setup(player)).put("opponent", setup(target))
                .put("winner", result.winner()).put("reward", reward).put("turns", turns);
        matches.add(new JSONObject().put("oId", id).put("userId", owner).put("createdAt", now)
                .put("snapshotJson", new JSONObject().put("seed", seed).put("report", report).toString()));
        return new JSONObject().put("matchId", id);
    }

    JSONObject upgrade(final String owner, final Weapon weapon, final int expectedLevel) throws RepositoryException {
        final JSONObject account = account(owner);
        final JSONObject levels = new JSONObject(account.getString("levelsJson"));
        final int level = levels.getInt(weapon.name());
        if (level != expectedLevel || level >= ApiV3PetBattle.COST.length)
            throw ApiV3PetRules.conflict("brawl_stale_level", "装备等级已变化，请重新读取");
        final int cost = ApiV3PetBattle.COST[level];
        if (account.getInt("materials") < cost)
            throw ApiV3PetRules.conflict("brawl_materials", "历练材料不足");
        account.put("materials", account.getInt("materials") - cost)
                .put("levelsJson", levels.put(weapon.name(), level + 1).toString());
        save(account);
        return new JSONObject().put("weapon", weapon.name()).put("level", level + 1);
    }

    JSONObject history(final String owner, final String before) throws RepositoryException {
        final String query = "SELECT oId,createdAt,snapshotJson FROM `" + matches.getName() + "` WHERE userId=?";
        final var rows = before == null
                ? matches.select(query + " ORDER BY oId DESC LIMIT 21", owner)
                : matches.select(query + " AND oId<? ORDER BY oId DESC LIMIT 21", owner, before);
        final JSONArray list = new JSONArray();
        for (int i = 0; i < Math.min(20, rows.size()); i++) {
            final JSONObject report = report(rows.get(i));
            list.put(new JSONObject().put("id", report.getString("id")).put("createdAt", report.getLong("createdAt"))
                    .put("stage", report.getInt("stage")).put("practice", report.getBoolean("practice"))
                    .put("winner", report.getInt("winner")).put("reward", report.getInt("reward")));
        }
        return new JSONObject().put("list", list)
                .put("next", rows.size() > 20 ? rows.get(19).getString("oId") : JSONObject.NULL);
    }

    JSONObject detail(final String owner, final String id) throws RepositoryException {
        final var rows = matches.select("SELECT snapshotJson FROM `" + matches.getName() + "` WHERE oId=? AND userId=?", id, owner);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        return report(rows.getFirst());
    }

    private JSONObject account(final String owner) throws RepositoryException {
        final JSONObject row = accounts.get(owner);
        if (row != null) return row;
        final JSONObject levels = new JSONObject();
        for (Weapon weapon : Weapon.values()) levels.put(weapon.name(), 0);
        return new JSONObject().put("oId", owner).put("materials", 0).put("rewardCount", 0)
                .put("rewardDate", "").put("unlocked", 1).put("levelsJson", levels.toString());
    }

    private void save(final JSONObject account) throws RepositoryException {
        final String id = account.getString("oId");
        if (accounts.get(id) == null) accounts.add(account);
        else accounts.update(id, account, "materials", "rewardCount", "rewardDate", "unlocked", "levelsJson");
    }

    private static int rewardCount(final JSONObject account, final String today) {
        // 时钟回拨时不重开额度，避免同一天重复获取材料。
        return today.compareTo(account.getString("rewardDate")) <= 0 ? account.getInt("rewardCount") : 0;
    }

    private static JSONObject setup(final Setup setup) {
        return new JSONObject().put("weapon", setup.weapon().name()).put("skill", setup.skill().name())
                .put("level", setup.level()).put("maxHp", setup.maxHp()).put("attack", setup.attack());
    }

    private static JSONObject report(final JSONObject row) {
        return new JSONObject(row.getString("snapshotJson")).getJSONObject("report");
    }
}
