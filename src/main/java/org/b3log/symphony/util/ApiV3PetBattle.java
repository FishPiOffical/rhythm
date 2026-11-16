package org.b3log.symphony.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 纯战斗规则，所有生命和伤害以百分之一 HP 计。 */
public final class ApiV3PetBattle {
    public static final int VERSION = 1;
    public static final int[] COST = {20, 40, 80, 140, 220};
    public enum Weapon { SPOON, GUARD, FAN }
    public enum Skill { SPLASH, CURL, BREATHE }
    public record Setup(Weapon weapon, Skill skill, int level) {
        public Setup {
            if (weapon == null || skill == null || level < 0 || level > COST.length)
                throw new IllegalArgumentException("无效战斗配置");
        }

        public int maxHp() { return 10_000 + 60 * level; }
        public int attack() { return 900 + 5 * level; }
    }
    public record Turn(int round, int actor, int damage, int healing, int playerHp, int opponentHp) {}
    public record Result(int winner, List<Turn> turns) {}

    private ApiV3PetBattle() {}

    public static Result resolve(final Setup player, final Setup opponent, final long seed) {
        final Setup[] setups = {player, opponent};
        final int[] hp = {player.maxHp(), opponent.maxHp()};
        final Random random = new Random(seed);
        final List<Turn> turns = new ArrayList<>();
        for (int round = 1; round <= 12; round++) {
            final int first = random.nextInt(2);
            final int[] jitter = {random.nextInt(5) - 2, random.nextInt(5) - 2};
            final boolean[] critical = {random.nextInt(100) < 8, random.nextInt(100) < 8};
            for (int position = 0; position < 2; position++) {
                final int actor = first ^ position, target = 1 - actor;
                final Setup attacker = setups[actor], defender = setups[target];
                final int healing = round == 4 && attacker.skill == Skill.BREATHE
                        ? Math.min(800, attacker.maxHp() - hp[actor]) : 0;
                hp[actor] += healing;
                int damage = attacker.attack() + jitter[actor] * 100;
                if (critical[actor]) damage = damage * 3 / 2;
                if (attacker.weapon == Weapon.SPOON && round <= 8) damage += 100;
                if (attacker.weapon == Weapon.FAN && (round == 3 || round == 6)) damage += 400;
                if (attacker.skill == Skill.SPLASH && round == 4) damage += 800;
                if (defender.weapon == Weapon.GUARD && round <= 2) damage -= 400;
                if (defender.skill == Skill.CURL && round == 5) damage -= 800;
                damage = Math.max(100, damage);
                hp[target] = Math.max(0, hp[target] - damage);
                turns.add(new Turn(round, actor, damage, healing, hp[0], hp[1]));
                if (hp[target] == 0) return new Result(actor, List.copyOf(turns));
            }
        }
        final int difference = Integer.compare(hp[0] * opponent.maxHp(), hp[1] * player.maxHp());
        return new Result(difference == 0 ? -1 : difference > 0 ? 0 : 1, List.copyOf(turns));
    }
}
