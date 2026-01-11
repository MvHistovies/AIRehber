package me.mrhistories.airehber;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class UsageManager {

    private final AIGuidePlugin plugin;
    private final File file;
    private YamlConfiguration data;

    // memory cache
    private final Map<UUID, Integer> dailyUsed = new HashMap<>();
    private final Map<UUID, Long> lastUsedMs = new HashMap<>();
    private final Map<UUID, String> lastDate = new HashMap<>();
    private int totalToday = 0;
    private String totalDate = "";

    public UsageManager(AIGuidePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "usage.yml");
        reload();
    }

    public synchronized void reload() {
        try {
            if (!file.exists()) {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            }
            data = YamlConfiguration.loadConfiguration(file);

            dailyUsed.clear();
            lastUsedMs.clear();
            lastDate.clear();

            totalDate = data.getString("total.date", "");
            totalToday = data.getInt("total.count", 0);

            ConfigurationSection users = data.getConfigurationSection("users");
            if (users != null) {
                for (String key : users.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (Exception ignore) {
                        continue;
                    }
                    int used = data.getInt("users." + key + ".used", 0);
                    long last = data.getLong("users." + key + ".lastMs", 0L);
                    String date = data.getString("users." + key + ".date", "");
                    dailyUsed.put(uuid, used);
                    lastUsedMs.put(uuid, last);
                    lastDate.put(uuid, date);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("usage.yml yüklenemedi: " + e.getMessage());
            data = new YamlConfiguration();
        }
    }

    public synchronized void save() {
        try {
            data.set("total.date", totalDate);
            data.set("total.count", totalToday);

            for (UUID uuid : dailyUsed.keySet()) {
                String key = uuid.toString();
                data.set("users." + key + ".used", dailyUsed.getOrDefault(uuid, 0));
                data.set("users." + key + ".lastMs", lastUsedMs.getOrDefault(uuid, 0L));
                data.set("users." + key + ".date", lastDate.getOrDefault(uuid, ""));
            }
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("usage.yml kaydedilemedi: " + e.getMessage());
        }
    }

    public int getCooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("limits.cooldown-seconds", 25));
    }

    public int getDailyLimit(Player p) {
        int base = Math.max(0, plugin.getConfig().getInt("limits.daily.default", 10));
        int best = base;

        List<Map<?, ?>> tiers = plugin.getConfig().getMapList("limits.daily.tiers");
        for (Map<?, ?> tier : tiers) {
            Object permObj = tier.get("permission");
            Object limitObj = tier.get("limit");
            if (permObj == null || limitObj == null) continue;

            String perm = String.valueOf(permObj).trim();
            int lim;
            try {
                lim = Integer.parseInt(String.valueOf(limitObj));
            } catch (Exception ignore) {
                continue;
            }

            if (!perm.isEmpty() && p.hasPermission(perm)) {
                best = Math.max(best, lim);
            }
        }

        return best;
    }

    private String today() {
        return LocalDate.now().toString(); // yyyy-MM-dd
    }

    private void ensureToday(UUID uuid) {
        String t = today();
        String d = lastDate.getOrDefault(uuid, "");
        if (!t.equals(d)) {
            lastDate.put(uuid, t);
            dailyUsed.put(uuid, 0);
        }

        if (!t.equals(totalDate)) {
            totalDate = t;
            totalToday = 0;
        }
    }

    public synchronized int getUsedToday(Player p) {
        ensureToday(p.getUniqueId());
        return dailyUsed.getOrDefault(p.getUniqueId(), 0);
    }

    public synchronized int getRemainingToday(Player p) {
        int limit = getDailyLimit(p);
        int used = getUsedToday(p);
        return Math.max(0, limit - used);
    }

    public synchronized int getCooldownRemaining(Player p) {
        long last = lastUsedMs.getOrDefault(p.getUniqueId(), 0L);
        int cd = getCooldownSeconds();
        long passed = System.currentTimeMillis() - last;
        long remainMs = (cd * 1000L) - passed;
        if (remainMs <= 0) return 0;
        return (int) Math.ceil(remainMs / 1000.0);
    }

    /**
     * Kullanım kontrolü. Uygunsa tüketim yapar (cooldown + günlük).
     */
    public synchronized CheckResult tryConsume(Player p) {
        ensureToday(p.getUniqueId());

        int cdRemain = getCooldownRemaining(p);
        if (cdRemain > 0) {
            return CheckResult.cooldown(cdRemain);
        }

        int limit = getDailyLimit(p);
        int used = dailyUsed.getOrDefault(p.getUniqueId(), 0);

        if (limit > 0 && used >= limit) {
            return CheckResult.dailyLimit(used, limit);
        }

        // Tüket
        dailyUsed.put(p.getUniqueId(), used + 1);
        lastUsedMs.put(p.getUniqueId(), System.currentTimeMillis());
        totalToday++;

        // Basit ve güvenli: her kullanımda kaydet
        save();

        return CheckResult.ok(used + 1, limit);
    }

    public synchronized int getTotalToday() {
        ensureToday(UUID.randomUUID()); // sadece totalDate reset mantığı için
        return totalToday;
    }

    public static class CheckResult {
        public enum Type { OK, COOLDOWN, DAILY_LIMIT }

        public final Type type;
        public final int cooldownSeconds;
        public final int used;
        public final int limit;

        private CheckResult(Type type, int cooldownSeconds, int used, int limit) {
            this.type = type;
            this.cooldownSeconds = cooldownSeconds;
            this.used = used;
            this.limit = limit;
        }

        public static CheckResult ok(int used, int limit) {
            return new CheckResult(Type.OK, 0, used, limit);
        }

        public static CheckResult cooldown(int seconds) {
            return new CheckResult(Type.COOLDOWN, seconds, 0, 0);
        }

        public static CheckResult dailyLimit(int used, int limit) {
            return new CheckResult(Type.DAILY_LIMIT, 0, used, limit);
        }
    }
}
