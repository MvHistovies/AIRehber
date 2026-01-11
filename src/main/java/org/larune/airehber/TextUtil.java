package me.mrhistories.airehber;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class TextUtil {

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String safeTrim(String text, int maxChars) {
        if (text == null) return "";
        String t = text.trim();
        if (maxChars <= 0) return t;
        if (t.length() <= maxChars) return t;
        return t.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    public static List<String> formatForChat(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) return lines;

        String[] split = raw.split("\n");
        for (String line : split) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.matches("^\\d+\\.\\s+.*")) {
                String clean = line.replaceFirst("^\\d+\\.\\s+", "").replace("**", "").trim();
                if (clean.endsWith(":")) clean = clean.substring(0, clean.length() - 1).trim();
                lines.add(color("&e➤ &a" + clean));
                continue;
            }

            line = line.replaceAll("(/\\S+)", "&b$1&7");
            lines.add(color("&7  " + line));
        }

        return lines;
    }
}
