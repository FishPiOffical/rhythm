package org.b3log.symphony.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.b3log.symphony.util.ApiV3PetBattle.Setup;
import org.b3log.symphony.util.ApiV3PetBattle.Skill;
import org.b3log.symphony.util.ApiV3PetBattle.Weapon;
import org.testng.Assert;

public final class ApiV3PetBattleSpeciesTest {
    private static final List<String> SPECIES = List.of("momota", "hexiaotun", "yubingmiao", "paixiaoyu");

    private record Build(int species, Weapon weapon, Skill skill) {
        Setup setup(final boolean strengthened) {
            return strengthened ? new Setup(weapon, skill, ApiV3PetBattle.MAX_UPGRADE, 40, 10) : new Setup(weapon, skill, 0);
        }
    }

    public static void main(final String[] args) {
        checkGrowth();
        final List<Build> builds = new ArrayList<>();
        for (int i = 0; i < SPECIES.size(); i++) {
            final String species = SPECIES.get(i);
            final int before = builds.size();
            for (Weapon weapon : Weapon.values()) {
                if (!weapon.availableTo(species)) continue;
                for (Skill skill : Skill.values()) {
                    if (skill.availableTo(species)) builds.add(new Build(i, weapon, skill));
                }
            }
            Assert.assertEquals(builds.size() - before, 25);
        }
        final int samples = args.length == 0 ? 512 : Integer.parseInt(args[0]);
        final long[] seeds = new Random(20261117L).longs(samples).toArray();
        checkTier(builds, seeds, false);
        checkTier(builds, seeds, true);
        double advantage = 0;
        double fullVsTwelve = 0;
        double fullVsSixteen = 0;
        for (Build build : builds) {
            final Setup full = new Setup(build.weapon, build.skill, 20);
            advantage += score(full, build.setup(false), seeds);
            fullVsTwelve += score(full, new Setup(build.weapon, build.skill, 12), seeds);
            fullVsSixteen += score(full, new Setup(build.weapon, build.skill, 16), seeds);
        }
        final double average = advantage / builds.size();
        Assert.assertTrue(average >= .83 && average <= .90, "强化优势超出预期：" + average);
        Assert.assertTrue(fullVsTwelve / builds.size() >= .67 && fullVsTwelve / builds.size() <= .75);
        Assert.assertTrue(fullVsSixteen / builds.size() >= .57 && fullVsSixteen / builds.size() <= .65);
        System.out.printf("PASS 同一配装 +20 对 +0/+12/+16 的平均得分 %.2f%% / %.2f%% / %.2f%%%n",
                average * 100, fullVsTwelve * 100 / builds.size(), fullVsSixteen * 100 / builds.size());
    }

    private static void checkGrowth() {
        final int[] expenses = {0, 20, 60, 140, 280, 500};
        final int[] health = {10000, 10080, 10180, 10300, 10440, 10600};
        final int[] attack = {900, 908, 916, 926, 938, 950};
        int total = 0;
        Setup previous = null;
        for (int level = 0; level <= 20; level++) {
            final Setup current = new Setup(Weapon.SPOON, Skill.SPLASH, level);
            if (level > 0) total += ApiV3PetBattle.COST[level - 1];
            if (level % 4 == 0) {
                Assert.assertEquals(total, expenses[level / 4]);
                Assert.assertEquals(current.maxHp(), health[level / 4]);
                Assert.assertEquals(current.attack(), attack[level / 4]);
            }
            if (previous != null) {
                Assert.assertTrue(current.maxHp() > previous.maxHp());
                Assert.assertTrue(current.attack() > previous.attack());
            }
            previous = current;
        }
        Assert.assertEquals(Setup.npc(Weapon.FAN, Skill.BREATHE, 2).maxHp(), 10120);
        Assert.assertEquals(Setup.npc(Weapon.FAN, Skill.BREATHE, 2).attack(), 910);
    }

    private static void checkTier(final List<Build> builds, final long[] seeds, final boolean strengthened) {
        final double[] speciesScores = new double[SPECIES.size()];
        final int[] speciesGames = new int[SPECIES.size()];
        double lowest = 1;
        for (int i = 0; i < builds.size(); i++) {
            final Build left = builds.get(i);
            final Setup player = left.setup(strengthened);
            for (int j = i + 1; j < builds.size(); j++) {
                final Build right = builds.get(j);
                final double result = score(player, right.setup(strengthened), seeds);
                lowest = Math.min(lowest, Math.min(result, 1 - result));
                speciesScores[left.species] += result;
                speciesScores[right.species] += 1 - result;
                speciesGames[left.species]++;
                speciesGames[right.species]++;
            }
        }
        Assert.assertTrue(lowest >= .35, "同投入配装克制超出预期：" + lowest);
        double low = 1;
        double high = 0;
        for (int i = 0; i < SPECIES.size(); i++) {
            final double score = speciesScores[i] / speciesGames[i];
            Assert.assertTrue(score >= .47 && score <= .53, SPECIES.get(i) + " 平均得分超出预期：" + score);
            low = Math.min(low, score);
            high = Math.max(high, score);
        }
        System.out.printf("PASS %s：物种平均得分 %.2f%%–%.2f%%，最偏组合 %.2f%%–%.2f%%%n",
                strengthened ? "双方满强化" : "双方零强化", low * 100, high * 100, lowest * 100, (1 - lowest) * 100);
    }

    private static double score(final Setup a, final Setup b, final long[] seeds) {
        double points = 0;
        for (long seed : seeds) {
            points += points(ApiV3PetBattle.resolve(a, b, seed).winner(), 0);
            points += points(ApiV3PetBattle.resolve(b, a, seed).winner(), 1);
        }
        return points / (2 * seeds.length);
    }

    private static double points(final int winner, final int side) {
        return winner < 0 ? .5 : winner == side ? 1 : 0;
    }
}
