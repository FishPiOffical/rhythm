package org.b3log.symphony.service;

import java.util.Properties;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.PetBrawlAccountRepository;
import org.b3log.symphony.repository.PetBrawlGearRepository;
import org.b3log.symphony.repository.PetBrawlMatchRepository;
import org.b3log.symphony.repository.PetBrawlPairRepository;
import org.b3log.symphony.repository.PetBrawlProfileRepository;
import org.b3log.symphony.util.ApiV3PetBattle;
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONObject;
import org.testng.Assert;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

public final class ApiV3PetBrawlTest {
    static final String PATH = "/api/v3/me/pet/brawl";

    static final class Fixture extends ApiV3PetTest.Fixture {
        Fixture() throws Exception {
            sql(db, "CREATE TABLE pet_brawl_account(oId TEXT PRIMARY KEY,materials INT,rewardDate TEXT,rewardCount INT,unlocked INT,levelsJson TEXT)");
            sql(db, "CREATE TABLE pet_brawl_match(oId TEXT PRIMARY KEY,userId TEXT,createdAt BIGINT,snapshotJson TEXT)");
            final var brawl = new ApiV3PetBrawl();
            inject(pets, "brawl", brawl);
            sql(db, "CREATE TABLE pet_brawl_gear(oId TEXT PRIMARY KEY,snapshotJson TEXT)");
            inject(brawl, "gears", new PetBrawlGearRepository() {
                public JSONObject get(final String id) throws RepositoryException {
                    return selectSQL("SELECT * FROM pet_brawl_gear WHERE oId=?", id).stream().findFirst().orElse(null);
                }
            });
            sql(db, "CREATE TABLE pet_brawl_profile(oId TEXT PRIMARY KEY,petId TEXT,enabled INT,weapon TEXT,skill TEXT,revision TEXT,version INT)");
            sql(db, "CREATE TABLE pet_brawl_pair(oId TEXT PRIMARY KEY,day TEXT,initiator TEXT,recipient TEXT,pairLow TEXT,pairHigh TEXT)");
            inject(brawl, "profiles", new PetBrawlProfileRepository() {
                public JSONObject get(final String id) throws RepositoryException {
                    return selectSQL("SELECT * FROM pet_brawl_profile WHERE oId=?", id).stream().findFirst().orElse(null);
                }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_brawl_profile", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    modify("pet_brawl_profile", id, row, fields);
                }
            });
            inject(brawl, "pairs", new PetBrawlPairRepository() {
                public String getName() { return "pet_brawl_pair"; }
                public java.util.List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                    return selectSQL(query, args);
                }
            });
            inject(brawl, "accounts", new PetBrawlAccountRepository() {
                public JSONObject get(final String id) throws RepositoryException {
                    return selectSQL("SELECT * FROM pet_brawl_account WHERE oId=?", id).stream().findFirst().orElse(null);
                }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_brawl_account", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    modify("pet_brawl_account", id, row, fields);
                }
            });
            inject(brawl, "matches", new PetBrawlMatchRepository() {
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_brawl_match", row); }
                public java.util.List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                    return selectSQL(query.replace("symphony_pet_brawl_match", "pet_brawl_match"), args);
                }
                public String getName() { return "pet_brawl_match"; }
            });
        }
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var f = new Fixture()) {
            final String pet = f.adopt();
            final JSONObject command = new JSONObject().put("petId", pet).put("weapon", "SPOON")
                    .put("skill", "SPLASH").put("stage", 1).put("practice", false).put("version", ApiV3PetBattle.VERSION);
            Assert.assertEquals(f.pets.brawlState(identity()).getInt("materials"), 0);
            Assert.assertEquals(f.count("pet_brawl_account"), 0);
            sql(f.db, "UPDATE user_pet SET experience=899 WHERE oId=?", pet);
            error(409, () -> f.pets.brawlFight(identity(), command, "locked"));
            sql(f.db, "UPDATE user_pet SET experience=900 WHERE oId=?", pet);
            error(409, () -> f.pets.brawlFight(identity(), new JSONObject(command.toString()).put("stage", 3), "stage"));
            final JSONObject practice = new JSONObject(command.toString()).put("practice", true);
            final String practiceId = f.pets.brawlFight(identity(), practice, "practice").getString("matchId");
            Assert.assertEquals(f.pets.brawlDetail(identity(), practiceId).getInt("reward"), 0);
            String first = "";
            for (int i = 0; i < 4; i++) {
                final String key = "story-" + i;
                final String id = f.pets.brawlFight(identity(), command, key).getString("matchId");
                if (i == 0) first = id;
                Assert.assertEquals(f.pets.brawlDetail(identity(), id).getInt("reward"), new int[]{5, 5, 10, 0}[i]);
                Assert.assertEquals(f.pets.brawlFight(identity(), command, key).getString("matchId"), id);
            }
            Assert.assertEquals(f.count("pet_brawl_match"), 5);
            Assert.assertEquals(f.pets.brawlState(identity()).getInt("materials"), 20);
            final JSONObject upgrade = new JSONObject().put("petId", pet).put("weapon", "SPOON").put("level", 0).put("version", ApiV3PetBattle.VERSION);
            Assert.assertEquals(f.pets.brawlUpgrade(identity(), upgrade, "upgrade").getInt("level"), 1);
            Assert.assertEquals(f.pets.brawlUpgrade(identity(), upgrade, "upgrade").getInt("level"), 1);
            error(409, () -> f.pets.brawlUpgrade(identity(), upgrade, "stale-upgrade"));
            Assert.assertEquals(f.pets.brawlState(identity()).getInt("materials"), 17);
            f.clock += 24 * ApiV3PetRules.HOUR;
            Assert.assertEquals(f.pets.brawlFight(identity(), command, "story-0").getString("matchId"), first);
            Assert.assertEquals(f.pets.brawlState(identity()).getInt("rewardCount"), 0);
            f.fault = "ack";
            error(503, () -> f.pets.brawlFight(identity(), command, "new-day"));
            f.fault = "";
            final String next = f.pets.brawlFight(identity(), command, "new-day").getString("matchId");
            Assert.assertEquals(f.count("pet_brawl_match"), 6);
            final JSONObject report = f.pets.brawlDetail(identity(), next);
            Assert.assertEquals(report.getInt("reward"), 5);
            Assert.assertEquals(report.getJSONObject("player").getInt("attack"), 902);
            Assert.assertFalse(report.has("seed"));
            final JSONObject firstAction = report.getJSONArray("turns").getJSONObject(0);
            final JSONObject effects = firstAction.getJSONObject("effects");
            Assert.assertEquals(effects.getInt("baseDamage") + effects.getInt("criticalBonus") + effects.getInt("weaponBonus")
                    + effects.getInt("skillBonus") + effects.getInt("followUp") - effects.getInt("weaponGuard")
                    - effects.getInt("skillGuard") + effects.getInt("penetrated") + effects.getInt("minimumBonus"),
                    firstAction.getInt("damage"));

            final JSONObject oldReport = f.pets.brawlDetail(identity(), first);
            final int oldDamage = oldReport.getJSONArray("turns").getJSONObject(0).getInt("damage");
            for (Object turn : oldReport.getJSONArray("turns")) ((JSONObject) turn).remove("effects");
            sql(f.db, "UPDATE pet_brawl_match SET snapshotJson=? WHERE oId=?",
                    new JSONObject().put("report", oldReport).toString(), first);
            final JSONObject oldAction = f.pets.brawlDetail(identity(), first).getJSONArray("turns").getJSONObject(0);
            Assert.assertFalse(oldAction.has("effects"));
            Assert.assertEquals(oldAction.getInt("damage"), oldDamage);
            ApiV3PetProcessorRoutes.verify(f, next);
            final JSONObject enrollment = new JSONObject().put("petId", pet).put("weapon", "SPOON")
                    .put("skill", "SPLASH").put("enabled", true).put("revision", "").put("version", ApiV3PetBattle.VERSION);
            f.pets.brawlEnroll(identity(), enrollment, "enroll");
            f.pets.brawlEnroll(identity(), enrollment, "enroll");
            Assert.assertTrue(f.pets.brawlState(identity()).getJSONObject("pvp").getBoolean("enabled"));
            error(409, () -> f.pets.brawlEnroll(identity(), enrollment, "stale-enroll"));
            sql(f.db, "UPDATE pet_account SET coins=512");
            final String replacement = f.pets.readopt(identity(), ApiV3PetProgressionTest.replacement(pet), "brawl-readopt").getString("petId");
            Assert.assertNotEquals(replacement, pet);
            Assert.assertEquals(f.pets.brawlState(identity()).getJSONObject("levels").getInt("SPOON"), 1);
            Assert.assertEquals(f.pets.brawlState(identity()).getInt("materials"), 22);
            Assert.assertFalse(f.pets.brawlState(identity()).getJSONObject("pvp").getBoolean("enabled"));
            Assert.assertEquals(f.pets.brawlDetail(identity(), next).getString("petId"), pet);
            error(404, () -> f.pets.brawlFight(identity(), command, "old-pet"));
            sql(f.db, "UPDATE user_pet SET experience=3900 WHERE oId=?", replacement);
            sql(f.db, "UPDATE pet_brawl_account SET levelsJson=?", new JSONObject().put("SPOON", 5).put("FAN", 3).toString());
            final JSONObject migrated = f.pets.brawlState(identity());
            Assert.assertEquals(migrated.getInt("level"), 40);
            Assert.assertEquals(migrated.getJSONObject("levels").getInt("SPOON"), 20);
            Assert.assertEquals(migrated.getJSONObject("levels").getInt("FAN"), 12);
            Assert.assertFalse(migrated.getJSONObject("levels").has("_version"));
            final JSONObject legacyWrite = new JSONObject(upgrade.toString()).put("petId", replacement).put("version", ApiV3PetBattle.LEGACY_VERSION);
            error(409, () -> f.pets.brawlUpgrade(identity(), legacyWrite, "stale-version"));
            final JSONObject upgradeFan = new JSONObject(upgrade.toString()).put("petId", replacement).put("weapon", "FAN").put("level", 12);
            sql(f.db, "UPDATE pet_brawl_account SET materials=28");
            Assert.assertEquals(f.pets.brawlUpgrade(identity(), upgradeFan, "migrate-and-upgrade").getInt("level"), 13);
            final JSONObject afterMigration = f.pets.brawlState(identity()).getJSONObject("levels");
            Assert.assertEquals(afterMigration.getInt("FAN"), 13);
            Assert.assertEquals(afterMigration.getInt("SPOON"), 20);
            Assert.assertEquals(f.coins(), 0);
        }
        final var base = new ApiV3PetBattle.Setup(ApiV3PetBattle.Weapon.SPOON, ApiV3PetBattle.Skill.SPLASH, 0);
        final var result = ApiV3PetBattle.resolve(base, base, 87);
        Assert.assertEquals(result, ApiV3PetBattle.resolve(base, base, 87));
        Assert.assertTrue(result.turns().size() <= 24);
        System.out.println("PASS Lv.10、固定种子、V3鉴权、材料额度、原键重放、强化、换宠与历史");
    }

    private static final class ApiV3PetProcessorRoutes {
        static void verify(final Fixture f, final String id) throws Exception {
            org.b3log.symphony.processor.ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            Assert.assertEquals(f.httpPet("GET", PATH, null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("GET", PATH, "owner", null, null).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("GET", PATH + "/matches", "owner", null, null).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("GET", PATH + "/season", null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("POST", PATH + "/season/1/claim", "scope", "season", "{}").attr("test.status"), 403);
            Assert.assertEquals(f.httpPet("POST", PATH + "/matches", null, "anonymous", "{}").attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("POST", PATH + "/upgrade", "scope", "scope", "{}").attr("test.status"), 403);
            Assert.assertEquals(f.httpPet("GET", PATH + "/matches/" + id, "reviewer", null, null).attr("test.status"), 404);
            Assert.assertEquals(f.httpPet("GET", PATH + "/matches/" + id, "owner", null, null).attr("test.status"), 200);
        }
    }
}
