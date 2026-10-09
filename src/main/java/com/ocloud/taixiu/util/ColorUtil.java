package com.ocloud.taixiu.util;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("(?i)<gradient:#([0-9a-f]{6}):#([0-9a-f]{6})>(.*?)</gradient>");
    private static final Pattern SINGLE_HEX_TAG_PATTERN = Pattern.compile("(?i)<#([0-9a-f]{6})>(.*?)</#([0-9a-f]{6})>");
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern HEX_PATTERN_PLAIN = Pattern.compile("(?i)#([0-9a-f]{6})");

    private ColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String output = applyGradient(input);
        output = applyHex(output);
        output = ChatColor.translateAlternateColorCodes('&', output);
        return output;
    }

    private static String applyGradient(String input) {
        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (gradientMatcher.find()) {
            String start = gradientMatcher.group(1);
            String end = gradientMatcher.group(2);
            String text = gradientMatcher.group(3);
            String colored = applyGradientToText(start, end, text);
            gradientMatcher.appendReplacement(buffer, Matcher.quoteReplacement(colored));
        }
        gradientMatcher.appendTail(buffer);

        Matcher singleHexMatcher = SINGLE_HEX_TAG_PATTERN.matcher(buffer.toString());
        StringBuilder singleBuffer = new StringBuilder();
        while (singleHexMatcher.find()) {
            String hex = singleHexMatcher.group(1);
            String text = singleHexMatcher.group(2);
            String colored = applySolidHex(hex, text);
            singleHexMatcher.appendReplacement(singleBuffer, Matcher.quoteReplacement(colored));
        }
        singleHexMatcher.appendTail(singleBuffer);
        return singleBuffer.toString();
    }

    private static String applyHex(String input) {
        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            hexMatcher.appendReplacement(buffer, toHexColor(hex));
        }
        hexMatcher.appendTail(buffer);

        Matcher plainMatcher = HEX_PATTERN_PLAIN.matcher(buffer.toString());
        StringBuffer plainBuffer = new StringBuffer();
        while (plainMatcher.find()) {
            String hex = plainMatcher.group(1);
            plainMatcher.appendReplacement(plainBuffer, toHexColor(hex));
        }
        plainMatcher.appendTail(plainBuffer);
        return plainBuffer.toString();
    }

    private static String applySolidHex(String hex, String text) {
        if (text.isEmpty()) {
            return "";
        }
        return toHexColor(hex) + text;
    }

    private static String applyGradientToText(String startHex, String endHex, String text) {
        if (text.isEmpty()) {
            return "";
        }
        int[] start = hexToRgb(startHex);
        int[] end = hexToRgb(endHex);
        int length = countVisibleCharacters(text);
        if (length == 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '&' && i + 1 < text.length()) {
                builder.append(current).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (current == '\u00A7' && i + 1 < text.length()) {
                builder.append(current).append(text.charAt(i + 1));
                i++;
                continue;
            }
            double ratio = length == 1 ? 0 : (double) index / (length - 1);
            int red = interpolate(start[0], end[0], ratio);
            int green = interpolate(start[1], end[1], ratio);
            int blue = interpolate(start[2], end[2], ratio);
            builder.append(toHexColor(String.format(Locale.ROOT, "%02x%02x%02x", red, green, blue)));
            builder.append(current);
            index++;
        }
        return builder.toString();
    }

    private static int[] hexToRgb(String hex) {
        int red = Integer.parseInt(hex.substring(0, 2), 16);
        int green = Integer.parseInt(hex.substring(2, 4), 16);
        int blue = Integer.parseInt(hex.substring(4, 6), 16);
        return new int[]{red, green, blue};
    }

    private static int interpolate(int start, int end, double ratio) {
        return (int) Math.round(start + (end - start) * ratio);
    }

    private static int countVisibleCharacters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '&' || current == '\u00A7') && i + 1 < text.length()) {
                i++;
                continue;
            }
            count++;
        }
        return count;
    }

    private static String toHexColor(String hex) {
        StringBuilder builder = new StringBuilder("\u00A7x");
        for (char c : hex.toCharArray()) {
            builder.append('\u00A7').append(c);
        }
        return builder.toString();
    }
}
