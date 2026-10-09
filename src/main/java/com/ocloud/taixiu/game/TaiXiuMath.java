package com.ocloud.taixiu.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class TaiXiuMath {
    private static final List<DiceRoll> TAI_ROLLS = rollsFor(Side.TAI);
    private static final List<DiceRoll> XIU_ROLLS = rollsFor(Side.XIU);
    private static final List<DiceRoll> TRIPLE_ROLLS = rollsFor(null);

    private TaiXiuMath() {
    }

    public record DiceRoll(int first, int second, int third, Side side) {
    }

    public enum Side {
        TAI, XIU;

        public static Side parse(String raw) {
            if (raw == null) return null;
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals("tai") || normalized.equals("tài")) return TAI;
            if (normalized.equals("xiu") || normalized.equals("xỉu")) return XIU;
            return null;
        }

        public String displayName() {
            return this == TAI ? "Tài" : "Xỉu";
        }
    }

    public static boolean isTriple(int first, int second, int third) {
        return first == second && second == third;
    }

    public static Side result(int first, int second, int third, boolean triplesHouseWins) {
        validateDie(first);
        validateDie(second);
        validateDie(third);
        if (triplesHouseWins && isTriple(first, second, third)) return null;
        int total = first + second + third;
        return total >= 11 ? Side.TAI : Side.XIU;
    }

    public static DiceRoll rollFair(RandomGenerator random, boolean triplesHouseWins) {
        int first = random.nextInt(1, 7);
        int second = random.nextInt(1, 7);
        int third = random.nextInt(1, 7);
        return new DiceRoll(first, second, third, result(first, second, third, triplesHouseWins));
    }

    public static DiceRoll rollWeighted(RandomGenerator random, int taiWeight, int xiuWeight, int tripleWeight) {
        if (taiWeight < 0 || xiuWeight < 0 || tripleWeight < 0) {
            throw new IllegalArgumentException("outcome weights must be nonnegative");
        }
        int total = Math.addExact(Math.addExact(taiWeight, xiuWeight), tripleWeight);
        if (total == 0) throw new IllegalArgumentException("at least one outcome weight is required");
        int choice = random.nextInt(total);
        List<DiceRoll> rolls = choice < taiWeight ? TAI_ROLLS
                : choice < taiWeight + xiuWeight ? XIU_ROLLS : TRIPLE_ROLLS;
        return rolls.get(random.nextInt(rolls.size()));
    }

    private static List<DiceRoll> rollsFor(Side side) {
        List<DiceRoll> rolls = new ArrayList<>();
        for (int first = 1; first <= 6; first++) {
            for (int second = 1; second <= 6; second++) {
                for (int third = 1; third <= 6; third++) {
                    if (result(first, second, third, true) == side) {
                        rolls.add(new DiceRoll(first, second, third, side));
                    }
                }
            }
        }
        return List.copyOf(rolls);
    }

    public static long tax(long stake, int taxPercent) {
        if (stake <= 0L || taxPercent <= 0) return 0L;
        int safePercent = Math.min(100, taxPercent);
        return Math.min(stake, Math.round(stake * (safePercent / 100.0d)));
    }

    public static long percentageOf(long amount, int percent) {
        if (amount <= 0L || percent <= 0) return 0L;
        int safePercent = Math.min(100, percent);
        return (amount / 100L) * safePercent + ((amount % 100L) * safePercent) / 100L;
    }

    public static long payout(long stake, int taxPercent) {
        if (stake <= 0L) return 0L;
        return Math.subtractExact(Math.multiplyExact(stake, 2L), tax(stake, taxPercent));
    }

    private static void validateDie(int value) {
        if (value < 1 || value > 6) throw new IllegalArgumentException("die must be between 1 and 6");
    }
}
