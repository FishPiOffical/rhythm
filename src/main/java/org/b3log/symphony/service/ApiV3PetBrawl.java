package org.b3log.symphony.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Callable;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.PetBrawlAccountRepository;
import org.b3log.symphony.repository.PetBrawlOrderRepository;
import org.b3log.symphony.repository.PetBrawlGearRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3PetGear.Product;
import org.b3log.symphony.repository.PetBrawlMatchRepository;
import org.b3log.symphony.repository.PetBrawlPairRepository;
import org.b3log.symphony.repository.PetBrawlProfileRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.UserRepository;
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
            Setup.npc(Weapon.SPOON, Skill.SPLASH, 0),
            Setup.npc(Weapon.GUARD, Skill.CURL, 1),
            Setup.npc(Weapon.FAN, Skill.BREATHE, 2)
    };
    static final int STAGES = OPPONENTS.length;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Inject private ApiV3PetSeasons seasons;
    @Inject private PetBrawlAccountRepository accounts;
    @Inject private PetBrawlOrderRepository orders;
    @Inject private PetBrawlGearRepository gears;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRedcRepository transferIndex;
    private static final int POINTS_PER_MATERIAL = 10;
    private static final long QUOTE_TTL_MS = 10 * 60 * 1000L;
    @Inject private PetBrawlMatchRepository matches;
    @Inject private PetBrawlPairRepository pairs;
    @Inject private PetBrawlProfileRepository profiles;
    @Inject private UserPetRepository pets;
    @Inject private UserRepository users;
    private static final int ACTIVE_LIMIT = 3;
    private static final int PASSIVE_LIMIT = 6;
    private static final int MAX_LEVEL_GAP = 5;
    private static final int MAX_MATCH_LEVEL = 30;
    private static final String LEVELS_VERSION = "_version";
    private static final int CANDIDATES = 32;

    JSONObject state(final String owner, final JSONObject pet, final long now) throws RepositoryException {
        final JSONObject account = account(owner);
        final String today = ApiV3PetRules.today(now);
        final String species = pet == null ? null : pet.getString("speciesId");
        final JSONObject levels = new JSONObject(account.getString("levelsJson"));
        levels.remove(LEVELS_VERSION);
        final JSONArray weapons = new JSONArray();
        final JSONArray skills = new JSONArray();
        for (Weapon weapon : Weapon.values()) {
            if (!levels.has(weapon.name())) levels.put(weapon.name(), 0);
            if (weapon.availableTo(species)) weapons.put(option(weapon.name(), weapon.label, weapon.effect, weapon.species));
        }
        for (Skill skill : Skill.values()) {
            if (skill.availableTo(species)) skills.put(option(skill.name(), skill.label, skill.effect, skill.species));
        }
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
                .put("levels", levels)
                .put("equipmentCatalog", new JSONObject().put("weapons", weapons).put("skills", skills))
                .put("pvp", pvpState(owner, pet, today))
                .put("gear", gearState(owner))
                .put("growth", growth).put("costs", new JSONArray(ApiV3PetBattle.COST)).put("rewards", new JSONArray(REWARDS));
    }

    private static JSONObject option(final String id, final String name, final String effect, final String species) {
        return new JSONObject().put("id", id).put("name", name).put("effect", effect)
                .put("exclusive", species != null);
    }

    JSONObject fight(final String owner, final JSONObject pet, final Weapon weapon, final Skill skill,
                     final int stage, final boolean practice, final long now) throws RepositoryException {
        requireWeapon(pet, weapon);
        requireSkill(pet, skill);
        final JSONObject account = account(owner);
        if (!practice && stage > account.getInt("unlocked"))
            throw ApiV3PetRules.conflict("brawl_stage_locked", "先通过上一关");
        final int level = practice ? 0 : weaponLevel(account, weapon);
        final Setup opponent = OPPONENTS[stage - 1];
        final Setup player = practice ? new Setup(weapon, skill, 0) : equipment(weapon, skill, level, gear(owner)).setup();
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

        final JSONObject report = resolve(pet, player, target, now, result)
                .put("stage", stage).put("practice", practice).put("reward", reward);
        return store(owner, report, seed);
    }

    private JSONObject pvpState(final String owner, final JSONObject pet, final String day) throws RepositoryException {
        final JSONObject profile = profiles.get(owner);
        final boolean active = pet != null && currentProfile(profile, pet);
        return new JSONObject().put("enabled", active)
                .put("revision", profile == null ? "" : profile.getString("revision"))
                .put("weapon", active ? profile.getString("weapon") : JSONObject.NULL)
                .put("skill", active ? profile.getString("skill") : JSONObject.NULL)
                .put("remaining", Math.max(0, ACTIVE_LIMIT - count(owner, day, true)))
                .put("received", count(owner, day, false)).put("receivedLimit", PASSIVE_LIMIT);
    }

    void enroll(final String owner, final JSONObject pet, final Weapon weapon, final Skill skill,
                final boolean enabled, final String revision) throws RepositoryException {
        requireWeapon(pet, weapon);
        requireSkill(pet, skill);
        final JSONObject old = profiles.get(owner);
        if (!(old == null ? "" : old.getString("revision")).equals(revision))
            throw ApiV3PetRules.conflict("brawl_profile_changed", "报名配置已变化，请重新读取");
        final JSONObject row = new JSONObject().put("oId", owner).put("petId", pet.getString("oId"))
                .put("weapon", weapon.name()).put("skill", skill.name()).put("enabled", enabled ? 1 : 0)
                .put("version", ApiV3PetBattle.VERSION).put("revision", Ids.genTimeMillisId());
        if (old == null) profiles.add(row);
        else profiles.update(owner, row, "petId", "weapon", "skill", "enabled", "version", "revision");
    }

    JSONObject match(final String owner, final JSONObject pet, final String revision, final java.util.function.LongSupplier time) throws RepositoryException {
        final JSONObject profile = profiles.get(owner);
        if (!currentProfile(profile, pet) || !revision.equals(profile.getString("revision")))
            throw ApiV3PetRules.conflict("brawl_profile_changed", "请确认当前报名配置");
        final var round = seasons.beginRound(time);
        final long now = round.startedAt();
        final String day = ApiV3PetRules.today(now);
        if (count(owner, day, true) >= ACTIVE_LIMIT)
            throw ApiV3PetRules.conflict("brawl_pvp_limit", "今天的主动挑战次数已用完");
        final Equipped player = equipped(owner, profile);
        final String profileColumns = "SELECT userName,userNickname FROM `" + users.getName() + "` WHERE oId=?";
        final int level = matchLevel(pet);
        final String pairTable = "`" + pairs.getName() + "`";
        final int floor = (Math.max(MIN_LEVEL, level - MAX_LEVEL_GAP) - 1) * ApiV3PetRules.EXPERIENCE_PER_LEVEL;
        final int ceiling = level + MAX_LEVEL_GAP >= MAX_MATCH_LEVEL ? Integer.MAX_VALUE
                : (level + MAX_LEVEL_GAP) * ApiV3PetRules.EXPERIENCE_PER_LEVEL - 1;
        final var candidates = profiles.select("SELECT r.oId FROM `" + profiles.getName() + "` r"
                        + " JOIN `" + pets.getName() + "` p ON p.oId=r.petId AND p.userId=r.oId"
                        + " JOIN `" + users.getName() + "` u ON u.oId=r.oId AND u.userStatus=0"
                        + " WHERE r.enabled=1 AND r.version IN (?,?) AND r.oId<>? AND p.experience BETWEEN ? AND ?"
                        + " AND NOT EXISTS(SELECT 1 FROM " + pairTable + " d WHERE d.day=?"
                        + " AND d.pairLow=LEAST(?,r.oId) AND d.pairHigh=GREATEST(?,r.oId))"
                        + " AND (SELECT COUNT(*) FROM " + pairTable + " d WHERE d.recipient=r.oId AND d.day=?)<?"
                        + " ORDER BY ABS(LEAST(?, 1+FLOOR(p.experience/?))-?),r.oId LIMIT " + CANDIDATES,
                ApiV3PetBattle.LEGACY_VERSION, ApiV3PetBattle.VERSION, owner, floor, ceiling, day, owner, owner, day, PASSIVE_LIMIT,
                MAX_MATCH_LEVEL, ApiV3PetRules.EXPERIENCE_PER_LEVEL, level);
        for (JSONObject candidate : candidates) {
            final String opponent = candidate.getString("oId");
            // 本人授权先持有第一方客户端锁。同类写入共享该顺序，遇到外部事务占用的对手账号直接跳过。
            final var opponentProfile = users.select(profileColumns + " AND userStatus=0 FOR UPDATE SKIP LOCKED", opponent);
            if (opponentProfile.isEmpty()) continue;
            final JSONObject enrolled = profiles.get(opponent);
            final var current = pets.select("SELECT oId,speciesId,experience,name FROM `" + pets.getName() + "` WHERE userId=? FOR UPDATE", opponent);
            if (current.size() != 1 || !currentProfile(enrolled, current.getFirst())) continue;
            final JSONObject targetPet = current.getFirst();
            if (Math.abs(matchLevel(targetPet) - level) > MAX_LEVEL_GAP) continue;
            final Equipped target = equipped(opponent, enrolled);
            final long seed = RANDOM.nextLong();
            final JSONObject report = resolve(pet, player.setup(), target.setup(), now, ApiV3PetBattle.resolve(player.setup(), target.setup(), seed))
                    .put("mode", "pvp").put("stage", 0).put("practice", false).put("reward", 0)
                    .put("season", round.season())
                    .put("opponentPetId", targetPet.getString("oId")).put("opponentSpecies", targetPet.getString("speciesId"));
            report.getJSONObject("player").put("gear", player.snapshot());
            report.getJSONObject("opponent").put("gear", target.snapshot());
            // 昵称随出战配置保存，被挑战方读取时跟随出战配置一起翻转。
            final JSONObject ownerProfile = users.select(profileColumns, owner).getFirst();
            addFighterNames(report.getJSONObject("player"), pet, ownerProfile);
            addFighterNames(report.getJSONObject("opponent"), targetPet, opponentProfile.getFirst());
            final String low = owner.compareTo(opponent) < 0 ? owner : opponent;
            final String high = low.equals(owner) ? opponent : owner;
            pairs.add(new JSONObject().put("oId", report.getString("id")).put("day", day)
                    .put("initiator", owner).put("recipient", opponent).put("pairLow", low).put("pairHigh", high));
            seasons.record(round, owner, opponent, report.getInt("winner"));
            return store(owner, report, seed).put("createdAt", now);
        }
        throw ApiV3PetRules.conflict("brawl_no_opponent", "暂时没有符合条件的对手，可以稍后再匹配");
    }

    private static int matchLevel(final JSONObject pet) {
        return Math.min(MAX_MATCH_LEVEL, ApiV3PetRules.level(pet.getInt("experience")));
    }

    private static void addFighterNames(final JSONObject fighter, final JSONObject pet, final JSONObject owner) {
        final String nickname = owner.optString("userNickname");
        fighter.put("petName", pet.getString("name"));
        fighter.put("ownerNickname", nickname.isBlank() ? owner.getString("userName") : nickname);
    }

    private int count(final String owner, final String day, final boolean active) throws RepositoryException {
        final String field = active ? "initiator" : "recipient";
        return pairs.select("SELECT COUNT(*) AS total FROM `" + pairs.getName() + "` WHERE " + field + "=? AND day=?",
                owner, day).getFirst().getInt("total");
    }

    private static boolean currentProfile(final JSONObject profile, final JSONObject pet) {
        return profile != null && profile.getInt("enabled") == 1
                && (profile.getInt("version") == ApiV3PetBattle.LEGACY_VERSION || profile.getInt("version") == ApiV3PetBattle.VERSION)
                && profile.getString("petId").equals(pet.getString("oId"))
                && ApiV3PetRules.level(pet.getInt("experience")) >= MIN_LEVEL
                && Weapon.valueOf(profile.getString("weapon")).availableTo(pet.getString("speciesId"))
                && Skill.valueOf(profile.getString("skill")).availableTo(pet.getString("speciesId"));
    }

    static void requireWeapon(final JSONObject pet, final Weapon weapon) {
        if (!weapon.availableTo(pet.getString("speciesId")))
            throw ApiV3PetRules.conflict("brawl_equipment_unavailable", "这把武器不适用于当前宠物");
    }

    private static void requireSkill(final JSONObject pet, final Skill skill) {
        if (!skill.availableTo(pet.getString("speciesId")))
            throw ApiV3PetRules.conflict("brawl_equipment_unavailable", "这个招式不适用于当前宠物");
    }

    private Equipped equipped(final String owner, final JSONObject profile) throws RepositoryException {
        final Weapon weapon = Weapon.valueOf(profile.getString("weapon"));
        final Skill skill = Skill.valueOf(profile.getString("skill"));
        return equipment(weapon, skill, weaponLevel(account(owner), weapon), gear(owner));
    }

    private record Equipped(Setup setup, JSONObject snapshot) {}

    private static Equipped equipment(final Weapon weapon, final Skill skill, final int level, final JSONObject gear) {
        final JSONObject slots = weaponGear(gear, weapon);
        final JSONObject ranks = gear.getJSONObject("ranks");
        int hp = slots.getInt("artifact") * Product.ARTIFACT.hp;
        int attack = slots.getInt("artifact") * Product.ARTIFACT.attack;
        final JSONObject snapshot = new JSONObject(slots.toString());
        for (String slot : List.of("soul", "rune")) {
            final String value = slots.getString(slot);
            if (value.isEmpty()) continue;
            final Product item = Product.valueOf(value);
            final int rank = ranks.getInt(value);
            hp += item.hp * rank;
            attack += item.attack * rank;
            snapshot.put(slot + "Level", rank);
        }
        return new Equipped(new Setup(weapon, skill, level, hp, attack), snapshot);
    }

    JSONObject gearState(final String owner) throws RepositoryException {
        final JSONObject data = gear(owner);
        final JSONArray catalog = new JSONArray();
        for (Product product : Product.values()) catalog.put(product.json());
        final JSONObject bonuses = new JSONObject();
        for (Weapon weapon : Weapon.values()) {
            final Setup projected = equipment(weapon, Skill.SPLASH, 0, data).setup();
            bonuses.put(weapon.name(), new JSONObject().put("hp", projected.gearHp()).put("attack", projected.gearAttack()));
        }
        return data.put("catalog", catalog).put("bonuses", bonuses);
    }

    void socket(final String owner, final JSONObject pet, final Weapon weapon, final String slot,
                final String item, final int revision) throws RepositoryException {
        requireWeapon(pet, weapon);
        final JSONObject gear = gear(owner);
        if (gear.getInt("revision") != revision)
            throw ApiV3PetRules.conflict("brawl_gear_changed", "配装已变化，请刷新");
        final JSONObject selected = weaponGear(gear, weapon);
        if (!item.isEmpty()) {
            final Product product = Product.valueOf(item);
            if (gear.getJSONObject("ranks").optInt(product.name()) < 1)
                throw ApiV3PetRules.conflict("brawl_gear_unowned", "请先打造该镶嵌物");
            if ("rune".equals(slot) && selected.getInt("slot") == 0)
                throw ApiV3PetRules.conflict("brawl_slot_locked", "请先解锁第二槽");
            for (Weapon other : Weapon.values()) {
                if (other == weapon) continue;
                final JSONObject previous = weaponGear(gear, other);
                if (!item.equals(previous.getString(slot))) continue;
                if (other.availableTo(pet.getString("speciesId")))
                    throw ApiV3PetRules.conflict("brawl_gem_in_use", "请先从另一件武器拆下该镶嵌物");
                // 换宠后旧专属武器不在配装列表，允许本次明确选择将原镶嵌转移过来。
                previous.put(slot, "");
            }
        }
        if (item.equals(selected.getString(slot))) return;
        selected.put(slot, item);
        saveGear(owner, gear);
    }

    private JSONObject gear(final String owner) throws RepositoryException {
        final JSONObject row = gears.get(owner);
        if (row != null) return new JSONObject(row.getString("snapshotJson"));
        final JSONObject all = new JSONObject();
        for (Weapon weapon : Weapon.values()) {
            all.put(weapon.name(), new JSONObject().put("artifact", 0).put("slot", 0).put("soul", "").put("rune", ""));
        }
        return new JSONObject().put("revision", 0).put("weapons", all).put("ranks", new JSONObject());
    }

    private static JSONObject weaponGear(final JSONObject gear, final Weapon weapon) {
        final JSONObject weapons = gear.getJSONObject("weapons");
        if (!weapons.has(weapon.name()))
            weapons.put(weapon.name(), new JSONObject().put("artifact", 0).put("slot", 0).put("soul", "").put("rune", ""));
        return weapons.getJSONObject(weapon.name());
    }

    private void saveGear(final String owner, final JSONObject gear) throws RepositoryException {
        gear.put("revision", Math.addExact(gear.getInt("revision"), 1));
        final JSONObject row = new JSONObject().put("oId", owner).put("snapshotJson", gear.toString());
        if (gears.get(owner) == null) gears.add(row);
        else gears.update(owner, row, "snapshotJson");
    }

    private static int checkGearUpgrade(final JSONObject account, final JSONObject gear, final Weapon weapon,
                                        final Product product, final int expected) {
        if (product == Product.ARTIFACT && weaponLevel(account, weapon) < ApiV3PetBattle.MAX_UPGRADE)
            throw ApiV3PetRules.conflict("brawl_artifact_locked", "武器强化到 +20 后才能进阶");
        final int level = product.weaponBound() ? weaponGear(gear, weapon).getInt(product == Product.ARTIFACT ? "artifact" : "slot")
                : gear.getJSONObject("ranks").optInt(product.name());
        if (level != expected || expected >= product.maxLevel())
            throw ApiV3PetRules.conflict("brawl_gear_changed", "当前进阶等级已变化，请重新报价");
        return product.cost(level);
    }

    private void applyGearUpgrade(final String owner, final JSONObject gear, final Weapon weapon,
                                  final Product product, final int level) throws RepositoryException {
        if (product.weaponBound()) weaponGear(gear, weapon).put(product == Product.ARTIFACT ? "artifact" : "slot", level + 1);
        else gear.getJSONObject("ranks").put(product.name(), level + 1);
        saveGear(owner, gear);
    }

    private JSONObject store(final String owner, final JSONObject report, final long seed) throws RepositoryException {
        final String id = report.getString("id");
        matches.add(new JSONObject().put("oId", id).put("userId", owner).put("createdAt", report.getLong("createdAt"))
                .put("snapshotJson", new JSONObject().put("seed", seed).put("report", report).toString()));
        return new JSONObject().put("matchId", id);
    }

    private static JSONObject resolve(final JSONObject pet, final Setup player, final Setup target,
                                      final long now, final ApiV3PetBattle.Result result) {
        final JSONArray turns = new JSONArray();
        for (var turn : result.turns()) {
            final var effects = turn.effects();
            turns.put(new JSONObject().put("round", turn.round()).put("actor", turn.actor())
                    .put("damage", turn.damage()).put("healing", turn.healing())
                    .put("playerHp", turn.playerHp()).put("opponentHp", turn.opponentHp())
                    .put("effects", new JSONObject().put("baseDamage", effects.baseDamage())
                            .put("criticalBonus", effects.criticalBonus()).put("weaponBonus", effects.weaponBonus())
                            .put("skillBonus", effects.skillBonus()).put("followUp", effects.followUp())
                            .put("weaponGuard", effects.weaponGuard()).put("skillGuard", effects.skillGuard())
                            .put("penetrated", effects.penetrated()).put("minimumBonus", effects.minimumBonus())));
        }
        return new JSONObject().put("id", Ids.genTimeMillisId()).put("createdAt", now)
                .put("version", ApiV3PetBattle.VERSION).put("petId", pet.getString("oId"))
                .put("species", pet.getString("speciesId")).put("player", setup(player)).put("opponent", setup(target))
                .put("winner", result.winner()).put("turns", turns);
    }

    JSONObject lockPoints(final String owner, final Callable<JSONObject> command) throws Exception {
        return points.executeWithTransferLocks(owner, List.of(Pointtransfer.ID_C_SYS), command);
    }

    JSONObject quote(final String owner, final String petId, final Weapon weapon, final int level,
                     final int materials, final Product product, final long now) throws RepositoryException {
        final JSONObject account = account(owner);
        final int cost = product == null ? checkUpgrade(account, weapon, level)
                : checkGearUpgrade(account, gear(owner), weapon, product, level);
        checkMaterials(account, materials, cost);
        final String id = Ids.genTimeMillisId();
        final JSONObject quote = new JSONObject().put("id", id).put("petId", petId).put("weapon", weapon.name())
                .put("level", level).put("materials", materials).put("points", (cost - materials) * POINTS_PER_MATERIAL)
                .put("version", ApiV3PetBattle.VERSION).put("createdAt", now).put("expiresAt", now + QUOTE_TTL_MS)
                .put("status", "pending");
        if (product != null) quote.put("product", product.name());
        orders.add(new JSONObject().put("oId", id).put("userId", owner).put("snapshotJson", quote.toString()));
        return quote;
    }

    JSONObject order(final String owner, final String id, final boolean lock) throws RepositoryException {
        final var rows = orders.select("SELECT snapshotJson FROM `" + orders.getName() + "` WHERE userId=? AND oId=?"
                + (lock ? " FOR UPDATE" : ""), owner, id);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        return new JSONObject(rows.getFirst().getString("snapshotJson"));
    }

    JSONObject orderPage(final String owner, final String before, final long now) throws RepositoryException {
        final String base = "SELECT oId,snapshotJson FROM `" + orders.getName() + "` WHERE userId=?";
        final var rows = before == null ? orders.select(base + " ORDER BY oId DESC LIMIT 21", owner)
                : orders.select(base + " AND oId<? ORDER BY oId DESC LIMIT 21", owner, before);
        final JSONArray list = new JSONArray();
        for (int i = 0; i < Math.min(rows.size(), 20); i++) {
            list.put(projectOrder(new JSONObject(rows.get(i).getString("snapshotJson")), now));
        }
        return new JSONObject().put("list", list).put("balance", balance(owner))
                .put("next", rows.size() > 20 ? rows.get(19).getString("oId") : JSONObject.NULL);
    }

    JSONObject orderDetail(final String owner, final String id, final long now) throws RepositoryException {
        return projectOrder(order(owner, id, false), now).put("balance", balance(owner));
    }

    private static JSONObject projectOrder(final JSONObject quote, final long now) {
        if (!"completed".equals(quote.getString("status"))
                && (now >= quote.getLong("expiresAt") || quote.getInt("version") != ApiV3PetBattle.VERSION))
            quote.put("status", "expired");
        return quote;
    }

    void settle(final String owner, final JSONObject quote, final long now) throws RepositoryException {
        if (now >= quote.getLong("expiresAt"))
            throw ApiV3PetRules.conflict("brawl_quote_expired", "报价已过期，请重新报价");
        if (quote.getInt("version") != ApiV3PetBattle.VERSION)
            throw ApiV3PetRules.conflict("brawl_version_changed", "乱斗规则已更新，请重新报价");
        final Weapon weapon = Weapon.valueOf(quote.getString("weapon"));
        final int level = quote.getInt("level"), materials = quote.getInt("materials");
        final JSONObject account = account(owner);
        final Product product = quote.has("product") ? Product.valueOf(quote.getString("product")) : null;
        final JSONObject gear = product == null ? null : gear(owner);
        final int cost = product == null ? checkUpgrade(account, weapon, level) : checkGearUpgrade(account, gear, weapon, product, level);
        checkMaterials(account, materials, cost);
        final int price = (cost - materials) * POINTS_PER_MATERIAL;
        if (price != quote.getInt("points"))
            throw ApiV3PetRules.conflict("brawl_quote_changed", "报价已变更，请重新报价");
        if (price > 0 && balance(owner) < price)
            throw ApiV3PetRules.conflict("insufficient_points", "积分余额不足");
        final String id = quote.getString("id");
        if (price > 0) {
            final String label = weapon.label;
            final String transfer = points.transferInCurrentTransaction(owner, Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_PET_BRAWL_UPGRADE, price, (product == null ? label + "强化至 +" + (level + 1) : product.label + " · " + (product.weaponBound() ? label : "等级 " + (level + 1))) + " · 订单 " + id,
                    now, "", new PointtransferSource("", "", "pet.brawl.upgrade", id), false);
            if (transfer == null) throw new ApiV3Exception(503, "temporarily_unavailable", "积分结算暂不可用");
            for (String user : List.of(owner, Pointtransfer.ID_C_SYS)) {
                transferIndex.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", user).put("rawId", transfer));
            }
        }
        if (product == null) applyUpgrade(account, weapon, level, materials);
        else {
            account.put("materials", account.getInt("materials") - materials);
            save(account);
            applyGearUpgrade(owner, gear, weapon, product, level);
        }
        quote.put("status", "completed").put("completedAt", now);
        orders.update(id, new JSONObject().put("snapshotJson", quote.toString()), "snapshotJson");
    }

    private int balance(final String owner) throws RepositoryException {
        final var rows = users.select("SELECT userPoint FROM `" + users.getName() + "` WHERE oId=?", owner);
        if (rows.size() != 1) throw ApiV3Requests.forbidden();
        return rows.getFirst().getInt("userPoint");
    }

    JSONObject upgrade(final String owner, final Weapon weapon, final int expectedLevel) throws RepositoryException {
        final JSONObject account = account(owner);
        final int cost = checkUpgrade(account, weapon, expectedLevel);
        checkMaterials(account, cost, cost);
        applyUpgrade(account, weapon, expectedLevel, cost);
        return new JSONObject().put("weapon", weapon.name()).put("level", expectedLevel + 1);
    }

    private static int checkUpgrade(final JSONObject account, final Weapon weapon, final int expectedLevel) {
        final int level = weaponLevel(account, weapon);
        if (level != expectedLevel || level >= ApiV3PetBattle.COST.length)
            throw ApiV3PetRules.conflict("brawl_stale_level", "装备等级已变化，请重新读取");
        return ApiV3PetBattle.COST[level];
    }

    private static int weaponLevel(final JSONObject account, final Weapon weapon) {
        final JSONObject levels = new JSONObject(account.getString("levelsJson"));
        return levels.has(weapon.name()) ? levels.getInt(weapon.name()) : 0;
    }

    private static void checkMaterials(final JSONObject account, final int materials, final int cost) {
        if (materials < 0 || materials > cost) throw ApiV3Requests.invalid();
        if (account.getInt("materials") < materials)
            throw ApiV3PetRules.conflict("brawl_materials", "历练材料不足");
    }

    private void applyUpgrade(final JSONObject account, final Weapon weapon, final int level, final int materials) throws RepositoryException {
        final JSONObject levels = new JSONObject(account.getString("levelsJson"));
        account.put("materials", account.getInt("materials") - materials)
                .put("levelsJson", levels.put(weapon.name(), level + 1).toString());
        save(account);
    }

    JSONObject history(final String owner, final String before, final boolean includePvp) throws RepositoryException {
        final String matchTable = "`" + matches.getName() + "`";
        final String pairTable = "`" + pairs.getName() + "`";
        final String cursor = before == null ? "" : " AND oId<?";
        final String npcOnly = includePvp ? "" : " AND NOT EXISTS(SELECT 1 FROM " + pairTable + " p WHERE p.oId=own.oId)";
        final String entries = "SELECT oId,0 AS mirrored FROM (SELECT oId FROM " + matchTable
                + " own WHERE userId=?" + cursor + npcOnly + " ORDER BY oId DESC LIMIT 21) sent"
                + (includePvp ? " UNION ALL SELECT oId,1 AS mirrored FROM (SELECT oId FROM " + pairTable
                    + " WHERE recipient=?" + cursor + " ORDER BY oId DESC LIMIT 21) received" : "");
        final String query = "SELECT m.oId,m.snapshotJson,l.mirrored FROM (" + entries + ") l JOIN "
                + matchTable + " m ON m.oId=l.oId ORDER BY l.oId DESC LIMIT 21";
        final Object[] params;
        if (includePvp) params = before == null ? new Object[]{owner, owner} : new Object[]{owner, before, owner, before};
        else params = before == null ? new Object[]{owner} : new Object[]{owner, before};
        final var rows = matches.select(query, params);
        final JSONArray list = new JSONArray();
        for (int i = 0; i < Math.min(20, rows.size()); i++) {
            final JSONObject row = rows.get(i), report = report(row);
            final int winner = report.getInt("winner");
            final boolean mirrored = row.getInt("mirrored") == 1;
            final String mode = report.optString("mode", "npc");
            final JSONObject summary = new JSONObject().put("id", report.getString("id")).put("createdAt", report.getLong("createdAt"))
                    .put("stage", report.getInt("stage")).put("practice", report.getBoolean("practice"))
                    .put("mode", mode)
                    .put("winner", mirrored && winner >= 0 ? 1 - winner : winner).put("reward", report.getInt("reward"));
            if ("pvp".equals(mode)) {
                // 被挑战方视角里的对手是原发起方，直接取战报快照，不查询当前昵称。
                final JSONObject opponent = report.getJSONObject(mirrored ? "player" : "opponent");
                summary.put("opponentPetId", report.opt(mirrored ? "petId" : "opponentPetId"));
                summary.put("opponentSpecies", report.opt(mirrored ? "species" : "opponentSpecies"));
                summary.put("opponentPetName", opponent.opt("petName"));
                summary.put("opponentOwnerNickname", opponent.opt("ownerNickname"));
            }
            list.put(summary);
        }
        return new JSONObject().put("list", list)
                .put("next", rows.size() > 20 ? rows.get(19).getString("oId") : JSONObject.NULL);
    }

    JSONObject detail(final String owner, final String id) throws RepositoryException {
        final var rows = matches.select("SELECT m.snapshotJson,m.userId FROM `" + matches.getName() + "` m"
                + " LEFT JOIN `" + pairs.getName() + "` p ON p.oId=m.oId"
                + " WHERE m.oId=? AND (m.userId=? OR p.recipient=?)", id, owner, owner);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        final JSONObject row = rows.getFirst(), report = report(row);
        if (!owner.equals(row.getString("userId"))) mirror(report);
        return report;
    }

    private static void mirror(final JSONObject report) {
        // 同一战报 ID 按当前查看者翻转双方，回合顺序与实际结算一致。
        swap(report, "petId", "opponentPetId");
        swap(report, "species", "opponentSpecies");
        swap(report, "player", "opponent");
        final int winner = report.getInt("winner");
        if (winner >= 0) report.put("winner", 1 - winner);
        for (Object value : report.getJSONArray("turns")) {
            final JSONObject turn = (JSONObject) value;
            turn.put("actor", 1 - turn.getInt("actor"));
            swap(turn, "playerHp", "opponentHp");
        }
    }

    private static void swap(final JSONObject row, final String left, final String right) {
        final Object value = row.get(left);
        row.put(left, row.get(right)).put(right, value);
    }

    private JSONObject account(final String owner) throws RepositoryException {
        final JSONObject row = accounts.get(owner);
        if (row != null) {
            final JSONObject levels = new JSONObject(row.getString("levelsJson"));
            if (!levels.has(LEVELS_VERSION)) {
                // 旧账号投影等价档位；下次强化或闯关落库时，同事务写入版本标记，避免重复换算。
                for (Weapon weapon : Weapon.values()) {
                    if (levels.has(weapon.name()))
                        levels.put(weapon.name(), Math.multiplyExact(levels.getInt(weapon.name()), ApiV3PetBattle.LEVELS_PER_MILESTONE));
                }
                row.put("levelsJson", levels.put(LEVELS_VERSION, ApiV3PetBattle.VERSION).toString());
            }
            return row;
        }
        final JSONObject levels = new JSONObject().put(LEVELS_VERSION, ApiV3PetBattle.VERSION);
        for (Weapon weapon : Weapon.values()) levels.put(weapon.name(), 0);
        return new JSONObject().put("oId", owner).put("materials", 0).put("rewardCount", 0)
                .put("rewardDate", "").put("unlocked", 1).put("levelsJson", levels.toString());
    }

    void creditSeasonMaterials(final String owner, final int amount) throws RepositoryException {
        final var rows = accounts.select("SELECT * FROM `" + accounts.getName() + "` WHERE oId=? FOR UPDATE", owner);
        final JSONObject account = rows.isEmpty() ? account(owner) : rows.getFirst();
        account.put("materials", Math.addExact(account.getInt("materials"), amount));
        if (rows.isEmpty()) accounts.add(account);
        else accounts.update(owner, account, "materials");
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
                .put("level", setup.level()).put("maxHp", setup.maxHp()).put("attack", setup.attack())
                .put("gearHp", setup.gearHp()).put("gearAttack", setup.gearAttack());
    }

    private static JSONObject report(final JSONObject row) {
        return new JSONObject(row.getString("snapshotJson")).getJSONObject("report");
    }
}
