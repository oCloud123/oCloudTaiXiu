package com.ocloud.taixiu.util;

import java.util.Locale;

public final class CompactNumberUtil {
    private static final double[] DIVISORS = {
            1_000d,
            1_000_000d,
            1_000_000_000d,
            1_000_000_000_000d,
            1_000_000_000_000_000d
    };
    private static final String[] SUFFIXES = {"k", "M", "B", "T", "Q"};

    private CompactNumberUtil() {
    }

    public static String formatMoney(double value) {
        if (!Double.isFinite(value)) return "0";
        double absolute = Math.abs(value);
        if (absolute < 1_000.0d) return trimDecimals(String.format(Locale.ROOT, "%.2f", value));

        String[] suffixes = {"K", "M", "B", "T", "Q"};
        double scaled = absolute / 1_000.0d;
        int index = 0;
        while (scaled >= 1_000.0d && index < suffixes.length - 1) {
            scaled /= 1_000.0d;
            index++;
        }
        scaled = Math.round(scaled * 10.0d) / 10.0d;
        if (scaled >= 1_000.0d && index < suffixes.length - 1) {
            scaled = 1.0d;
            index++;
        }
        String amount = trimDecimals(String.format(Locale.ROOT, "%.1f", scaled));
        return (value < 0.0d ? "-" : "") + amount + suffixes[index];
    }

    private static String trimDecimals(String text) {
        if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
        if (text.endsWith(".0")) return text.substring(0, text.length() - 2);
        if (text.endsWith("0") && text.contains(".")) return text.substring(0, text.length() - 1);
        return text;
    }

    public static String formatShard(long value) {
        if (value < 10_000L) {
            return String.valueOf(Math.max(0L, value));
        }
        double current = Math.max(0L, value);
        int index = 0;
        for (int i = DIVISORS.length - 1; i >= 0; i--) {
            if (current >= DIVISORS[i]) {
                index = i;
                break;
            }
        }
        current = current / DIVISORS[index];
        current = Math.round(current * 10.0d) / 10.0d;
        while (current >= 1000.0d && index < SUFFIXES.length - 1) {
            current = Math.round((current / 1000.0d) * 10.0d) / 10.0d;
            index++;
        }

        long whole = (long) current;
        int decimal = (int) Math.round((current - whole) * 10.0d);
        if (decimal <= 0) {
            return whole + SUFFIXES[index];
        }
        return String.format(Locale.ROOT, "%d,%d%s", whole, decimal, SUFFIXES[index]);
    }
}
