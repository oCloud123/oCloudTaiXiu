package com.ocloud.taixiu.game;

import java.util.Locale;
import java.util.random.RandomGenerator;

public enum NextOutcome {
    TAI, XIU, TRIPLE;

    public static NextOutcome parse(String text) {
        if (text == null || text.isBlank()) return null;
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "tai", "tài" -> TAI;
            case "xiu", "xỉu" -> XIU;
            case "triple", "boba", "bộba", "bo-ba" -> TRIPLE;
            default -> null;
        };
    }

    TaiXiuMath.DiceRoll roll(RandomGenerator random, boolean triplesHouseWins) {
        var roll = TaiXiuMath.rollWeighted(random, this == TAI ? 1 : 0, this == XIU ? 1 : 0, this == TRIPLE ? 1 : 0);
        return new TaiXiuMath.DiceRoll(roll.first(), roll.second(), roll.third(),
                TaiXiuMath.result(roll.first(), roll.second(), roll.third(), triplesHouseWins));
    }
}
