package me.mrhistories.airehber;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Set;

public class KnowledgeStore {

    private final AIGuidePlugin plugin;
    private String cachedText = "";

    public KnowledgeStore(AIGuidePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        try {
            File file = new File(plugin.getDataFolder(), "server-knowledge.yml");
            if (!file.exists()) {
                cachedText = "";
                return;
            }

            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

            StringBuilder sb = new StringBuilder();

            String name = yml.getString("server.name", "");
            String mode = yml.getString("server.mode", "");
            String lang = yml.getString("server.language", "");
            if (!name.isBlank() || !mode.isBlank() || !lang.isBlank()) {
                sb.append("Sunucu: ").append(name).append("\n");
                if (!mode.isBlank()) sb.append("Mod: ").append(mode).append("\n");
                if (!lang.isBlank()) sb.append("Dil: ").append(lang).append("\n");
                sb.append("\n");
            }

            List<String> about = yml.getStringList("about");
            if (!about.isEmpty()) {
                sb.append("Hakkında:\n");
                for (String a : about) sb.append("- ").append(a).append("\n");
                sb.append("\n");
            }

            List<String> rules = yml.getStringList("rules");
            if (!rules.isEmpty()) {
                sb.append("Kurallar:\n");
                for (String r : rules) sb.append("- ").append(r).append("\n");
                sb.append("\n");
            }

            String currency = yml.getString("economy.currency", "");
            List<String> tips = yml.getStringList("economy.tips");
            if (!currency.isBlank() || !tips.isEmpty()) {
                sb.append("Ekonomi:\n");
                if (!currency.isBlank()) sb.append("- Para birimi: ").append(currency).append("\n");
                if (!tips.isEmpty()) {
                    sb.append("- İpuçları:\n");
                    for (String t : tips) sb.append("  * ").append(t).append("\n");
                }
                sb.append("\n");
            }

            List<String> commands = yml.getStringList("commands");
            if (!commands.isEmpty()) {
                sb.append("Komutlar:\n");
                for (String c : commands) sb.append("- ").append(c).append("\n");
                sb.append("\n");
            }

            List<String> perks = yml.getStringList("vip.perks");
            if (!perks.isEmpty()) {
                sb.append("VIP:\n");
                for (String p : perks) sb.append("- ").append(p).append("\n");
                sb.append("\n");
            }

            List<String> events = yml.getStringList("events");
            if (!events.isEmpty()) {
                sb.append("Etkinlik/Zamanlama:\n");
                for (String ev : events) sb.append("- ").append(ev).append("\n");
                sb.append("\n");
            }

            if (yml.isConfigurationSection("faq")) {
                Set<String> keys = yml.getConfigurationSection("faq").getKeys(false);
                if (!keys.isEmpty()) {
                    sb.append("SSS:\n");
                    for (String k : keys) {
                        String v = yml.getString("faq." + k, "");
                        if (!v.isBlank()) {
                            sb.append("- ").append(k).append(" => ").append(v).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }

            cachedText = sb.toString().trim();

        } catch (Exception e) {
            plugin.getLogger().warning("server-knowledge.yml okunamadı: " + e.getMessage());
            cachedText = "";
        }
    }

    public String getKnowledgeText() {
        return cachedText == null ? "" : cachedText;
    }
}
