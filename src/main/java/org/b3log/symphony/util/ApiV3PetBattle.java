package org.b3log.symphony.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 纯战斗规则，所有生命和伤害以百分之一 HP 计。 */
public final class ApiV3PetBattle {
    public static final int LEGACY_VERSION = 1;
    public static final int VERSION = 2;
    public static final int[] COST = {3, 4, 6, 7, 8, 9, 11, 12, 14, 18, 22, 26, 28, 32, 36, 44, 46, 52, 58, 64};
    public static final int MAX_UPGRADE = COST.length;
    public static final int LEVELS_PER_MILESTONE = 4;
    private static final int[] HP_BONUS = {0, 80, 180, 300, 440, 600};
    private static final int[] ATTACK_BONUS = {0, 8, 16, 26, 38, 50};
    public enum Weapon {
        SPOON(null, "小木勺", "前 8 回合各加伤 1"),
        GUARD(null, "软垫护腕", "前 2 回合各减伤 4"),
        FAN(null, "折纸扇", "第 3、6 回合各加伤 4"),
        PEBBLE("momota", "卵石拍", "第 3、7 回合各加伤 3.8"),
        SHELL("momota", "贝壳盾", "前 2 回合各减伤 3.8"),
        LEAF("hexiaotun", "荷叶伞", "前 3 回合各减伤 2.6"),
        LOTUS("hexiaotun", "莲蓬锤", "第 5、8 回合各加伤 3.8"),
        CLAW("yubingmiao", "鱼饼爪套", "前 6 回合各加伤 1.3"),
        SLING("yubingmiao", "小鱼弹弓", "第 4 回合加伤 3.8，首次攻击半血或以下的对手再加伤 3.8"),
        FIN("paixiaoyu", "浪花尾鳍", "第 2 回合减伤 3.8，第 3 回合加伤 3.8"),
        BUBBLE("paixiaoyu", "泡泡炮", "第 3、5 回合各加伤 3.6，并各穿透最多 1.2 减伤");

        public final String species;
        public final String label;
        public final String effect;

        Weapon(final String species, final String label, final String effect) {
            this.species = species;
            this.label = label;
            this.effect = effect;
        }

        public boolean availableTo(final String speciesId) {
            return species == null || species.equals(speciesId);
        }
    }

    public enum Skill {
        SPLASH(null, "拍水花", "第 4 回合加伤 8"),
        CURL(null, "卷成一团", "第 5 回合减伤 8"),
        BREATHE(null, "深呼吸", "第 4 回合最多回复 8"),
        COUNTER("momota", "翻身拍浪", "第 5 回合加伤 7.5"),
        DIVE("momota", "抱石潜水", "第 4 回合减伤 7.5"),
        SHELTER("hexiaotun", "荷叶挡雨", "第 5 回合减伤 7.5"),
        CHEW("hexiaotun", "嚼叶回血", "第 4 回合最多回复 7.5"),
        DOUBLE("yubingmiao", "两连拍", "第 4、5 回合各加伤 3.8"),
        RUSH("yubingmiao", "叼鱼突袭", "第 4、7 回合各加伤 3.8"),
        SLIDE("paixiaoyu", "摆尾滑步", "第 5 回合减伤 7.5"),
        BURST("paixiaoyu", "泡泡破阵", "第 4 回合加伤 7.5，并穿透最多 1.2 减伤");

        public final String species;
        public final String label;
        public final String effect;

        Skill(final String species, final String label, final String effect) {
            this.species = species;
            this.label = label;
            this.effect = effect;
        }

        public boolean availableTo(final String speciesId) {
            return species == null || species.equals(speciesId);
        }
    }
    public record Setup(Weapon weapon, Skill skill, int level, int gearHp, int gearAttack, boolean fixedNpc) {
        public Setup(final Weapon weapon, final Skill skill, final int level) {
            this(weapon, skill, level, 0, 0);
        }
        public Setup(final Weapon weapon, final Skill skill, final int level, final int gearHp, final int gearAttack) {
            this(weapon, skill, level, gearHp, gearAttack, false);
        }
        public Setup {
            if (weapon == null || skill == null || level < 0 || level > MAX_UPGRADE
                    || gearHp < 0 || gearHp > 40 || gearAttack < 0 || gearAttack > 10
                    || fixedNpc && (level % LEVELS_PER_MILESTONE != 0 || level > 8 || gearHp != 0 || gearAttack != 0))
                throw new IllegalArgumentException("无效战斗配置");
        }

        public static Setup npc(final Weapon weapon, final Skill skill, final int oldLevel) {
            return new Setup(weapon, skill, oldLevel * LEVELS_PER_MILESTONE, 0, 0, true);
        }

        public int maxHp() { return 10_000 + (fixedNpc ? 60 * (level / LEVELS_PER_MILESTONE) : interpolate(HP_BONUS, level)) + gearHp; }
        public int attack() { return 900 + (fixedNpc ? 5 * (level / LEVELS_PER_MILESTONE) : interpolate(ATTACK_BONUS, level)) + gearAttack; }
    }

    private static int interpolate(final int[] milestones, final int level) {
        final int index = level / LEVELS_PER_MILESTONE;
        final int step = level % LEVELS_PER_MILESTONE;
        if (step == 0) return milestones[index];
        // 每四级之间线性插值，四舍五入到战斗使用的百分之一单位。
        return milestones[index] + ((milestones[index + 1] - milestones[index]) * step + 2) / LEVELS_PER_MILESTONE;
    }
    // 按出手方和受击方记录伤害构成，战报翻转时可原样使用。
    public record Effects(int baseDamage, int criticalBonus, int weaponBonus, int skillBonus, int followUp,
                          int weaponGuard, int skillGuard, int penetrated, int minimumBonus) {}
    public record Turn(int round, int actor, int damage, int healing, int playerHp, int opponentHp, Effects effects) {}
    public record Result(int winner, List<Turn> turns) {}

    private ApiV3PetBattle() {}

    public static Result resolve(final Setup player, final Setup opponent, final long seed) {
        final Setup[] setups = {player, opponent};
        final int[] hp = {player.maxHp(), opponent.maxHp()};
        final boolean[] slingUsed = new boolean[2];
        final Random random = new Random(seed);
        final List<Turn> turns = new ArrayList<>();
        for (int round = 1; round <= 12; round++) {
            final int first = random.nextInt(2);
            final int[] jitter = {random.nextInt(5) - 2, random.nextInt(5) - 2};
            final boolean[] critical = {random.nextInt(100) < 8, random.nextInt(100) < 8};
            for (int position = 0; position < 2; position++) {
                final int actor = first ^ position, target = 1 - actor;
                final Setup attacker = setups[actor], defender = setups[target];
                final int healing = round == 4
                        ? Math.min(healingPower(attacker.skill), attacker.maxHp() - hp[actor]) : 0;
                hp[actor] += healing;
                final int baseDamage = attacker.attack() + jitter[actor] * 100;
                final int criticalBonus = critical[actor] ? baseDamage / 2 : 0;
                final int weaponBonus = weaponDamage(attacker.weapon, round);
                final int skillBonus = skillDamage(attacker.skill, round);
                // 小鱼弹弓的追击只触发一次，半血判断使用本次攻击前的生命。
                final boolean followUpTriggered = attacker.weapon == Weapon.SLING && !slingUsed[actor]
                        && hp[target] * 2 <= defender.maxHp();
                final int followUp = followUpTriggered ? 380 : 0;
                if (followUpTriggered) slingUsed[actor] = true;
                final int weaponGuard = weaponReduction(defender.weapon, round);
                final int skillGuard = skillReduction(defender.skill, round);
                final int penetrated = Math.min(weaponGuard + skillGuard, pierce(attacker, round));
                final int reducedDamage = baseDamage + criticalBonus + weaponBonus + skillBonus + followUp
                        - weaponGuard - skillGuard + penetrated;
                final int damage = Math.max(100, reducedDamage);
                hp[target] = Math.max(0, hp[target] - damage);
                final Effects effects = new Effects(baseDamage, criticalBonus, weaponBonus, skillBonus, followUp,
                        weaponGuard, skillGuard, penetrated, damage - reducedDamage);
                turns.add(new Turn(round, actor, damage, healing, hp[0], hp[1], effects));
                if (hp[target] == 0) return new Result(actor, List.copyOf(turns));
            }
        }
        final int difference = Integer.compare(hp[0] * opponent.maxHp(), hp[1] * player.maxHp());
        return new Result(difference == 0 ? -1 : difference > 0 ? 0 : 1, List.copyOf(turns));
    }

    private static int weaponDamage(final Weapon weapon, final int round) {
        return switch (weapon) {
            case SPOON -> round <= 8 ? 100 : 0;
            case FAN -> round == 3 || round == 6 ? 400 : 0;
            case PEBBLE -> round == 3 || round == 7 ? 380 : 0;
            case LOTUS -> round == 5 || round == 8 ? 380 : 0;
            case CLAW -> round <= 6 ? 130 : 0;
            case SLING -> round == 4 ? 380 : 0;
            case FIN -> round == 3 ? 380 : 0;
            case BUBBLE -> round == 3 || round == 5 ? 360 : 0;
            default -> 0;
        };
    }

    private static int skillDamage(final Skill skill, final int round) {
        return switch (skill) {
            case SPLASH -> round == 4 ? 800 : 0;
            case COUNTER -> round == 5 ? 750 : 0;
            case DOUBLE -> round == 4 || round == 5 ? 380 : 0;
            case RUSH -> round == 4 || round == 7 ? 380 : 0;
            case BURST -> round == 4 ? 750 : 0;
            default -> 0;
        };
    }

    private static int weaponReduction(final Weapon weapon, final int round) {
        return switch (weapon) {
            case GUARD -> round <= 2 ? 400 : 0;
            case SHELL -> round <= 2 ? 380 : 0;
            case LEAF -> round <= 3 ? 260 : 0;
            case FIN -> round == 2 ? 380 : 0;
            default -> 0;
        };
    }

    private static int skillReduction(final Skill skill, final int round) {
        return switch (skill) {
            case CURL -> round == 5 ? 800 : 0;
            case DIVE -> round == 4 ? 750 : 0;
            case SHELTER, SLIDE -> round == 5 ? 750 : 0;
            default -> 0;
        };
    }

    private static int healingPower(final Skill skill) {
        return switch (skill) {
            case BREATHE -> 800;
            case CHEW -> 750;
            default -> 0;
        };
    }

    private static int pierce(final Setup attacker, final int round) {
        int amount = attacker.weapon == Weapon.BUBBLE && (round == 3 || round == 5) ? 120 : 0;
        if (attacker.skill == Skill.BURST && round == 4) amount += 120;
        return amount;
    }
}
