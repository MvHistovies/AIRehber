package me.mrhistories.airehber;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RehberCommand implements CommandExecutor {

    private final AIGuidePlugin plugin;

    private static final Pattern SERVER_NAME_LIKE =
            Pattern.compile("(?i)\\b([a-z0-9_]{3,20}mc)\\b");

    public RehberCommand(AIGuidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komut sadece oyuncular tarafından kullanılabilir.");
            return true;
        }

        // /rehber reload
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("airehber.admin")) {
                player.sendMessage(TextUtil.color(msg("messages.no-permission", "&cİznin yok.")));
                return true;
            }
            plugin.reloadAll();
            player.sendMessage(TextUtil.color(msg("messages.reloaded", "&aYenilendi.")));
            return true;
        }

        // /rehber stats
        if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
            sendStats(player);
            return true;
        }

        if (!player.hasPermission("airehber.use")) {
            player.sendMessage(TextUtil.color(msg("messages.no-permission", "&cBu komutu kullanmak için iznin yok.")));
            return true;
        }

        if (!plugin.getConfig().getBoolean("ai.enabled", true)) {
            player.sendMessage(TextUtil.color("&cAI rehber şu anda devre dışı."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(TextUtil.color(msg("messages.no-question",
                    "&cLütfen bir soru yaz: &e/rehber {server} hakkında bilgi ver")));
            return true;
        }

        String question = String.join(" ", args);

        // Sunucu filtresi (config’e göre)
        if (isAboutOtherServer(question)) {
            player.sendMessage(TextUtil.color(msg("messages.unknown-server",
                    "&c✖ &f{server} dışında bir sunucu hakkında bilgim yok.")));
            return true;
        }

        // Cooldown + günlük limit
        UsageManager.CheckResult cr = plugin.getUsageManager().tryConsume(player);
        if (cr.type == UsageManager.CheckResult.Type.COOLDOWN) {
            String m = msg("messages.cooldown", "&e⏳ &fLütfen &a{seconds}&f saniye bekle.")
                    .replace("{seconds}", String.valueOf(cr.cooldownSeconds));
            player.sendMessage(TextUtil.color(m));
            return true;
        }
        if (cr.type == UsageManager.CheckResult.Type.DAILY_LIMIT) {
            String m = msg("messages.daily-limit", "&c✖ &fGünlük hakkın bitti. (&e{used}&f/&e{limit}&f)")
                    .replace("{used}", String.valueOf(cr.used))
                    .replace("{limit}", String.valueOf(cr.limit));
            player.sendMessage(TextUtil.color(m));
            return true;
        }

        player.sendMessage(TextUtil.color(msg("messages.processing", "&7Sorun işleniyor, lütfen bekle...")));
        plugin.getLogger().info("Soru | " + player.getName() + " => " + question);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String instructions = buildInstructions();
                String answer = plugin.getOpenAIClient().ask(instructions, question);

                int maxChars = plugin.getConfig().getInt("ai.max-answer-chars", 900);
                answer = TextUtil.safeTrim(answer, maxChars);

                List<String> lines = TextUtil.formatForChat(answer);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String server = getServerName();
                    player.sendMessage(TextUtil.color("&6✦✦✦ &e" + server + " Rehberi &6✦✦✦"));
                    player.sendMessage(" ");
                    for (String line : lines) player.sendMessage(line);
                });

            } catch (Exception e) {
                plugin.getLogger().warning("AI hata: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(TextUtil.color(msg("messages.error", "&cBir hata oluştu.")))
                );
            }
        });

        return true;
    }

    private void sendStats(Player p) {
        String server = getServerName();
        int used = plugin.getUsageManager().getUsedToday(p);
        int limit = plugin.getUsageManager().getDailyLimit(p);
        int remaining = Math.max(0, limit - used);
        int cd = plugin.getUsageManager().getCooldownRemaining(p);

        p.sendMessage(TextUtil.color(applyServerPlaceholders(
                plugin.getConfig().getString("messages.stats.header", "&6Stats")
        )));
        p.sendMessage(TextUtil.color(applyServerPlaceholders(
                plugin.getConfig().getString("messages.stats.line1",
                                "&7Günlük kullanım: &e{used}&7/&e{limit} &8( Kalan: &a{remaining}&8 )")
                        .replace("{used}", String.valueOf(used))
                        .replace("{limit}", String.valueOf(limit))
                        .replace("{remaining}", String.valueOf(remaining))
        )));
        p.sendMessage(TextUtil.color(
                plugin.getConfig().getString("messages.stats.line2", "&7Cooldown: &e{cooldown}s")
                        .replace("{cooldown}", String.valueOf(cd))
        ));

        if (p.hasPermission("airehber.admin")) {
            int total = plugin.getUsageManager().getTotalToday();
            String adminLine = plugin.getConfig().getString("messages.stats.adminTotal", "&7Bugünkü toplam istek: &e{total}")
                    .replace("{total}", String.valueOf(total));
            p.sendMessage(TextUtil.color(adminLine));
        }
    }

    private String msg(String path, String fallback) {
        String raw = plugin.getConfig().getString(path, fallback);
        return applyServerPlaceholders(raw);
    }

    private String applyServerPlaceholders(String s) {
        if (s == null) return "";
        return s.replace("{server}", getServerName());
    }

    private String getServerName() {
        return plugin.getConfig().getString("server.name", "Sunucu").trim();
    }

    private List<String> getAllowedServerTokensLower() {
        List<String> tokens = new ArrayList<>();
        String name = getServerName();
        if (!name.isBlank()) tokens.add(name.toLowerCase(Locale.ROOT));

        List<String> aliases = plugin.getConfig().getStringList("server.aliases");
        for (String a : aliases) {
            if (a == null) continue;
            String t = a.trim();
            if (!t.isBlank()) tokens.add(t.toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private boolean questionMentionsOurServer(String question) {
        String q = (question == null ? "" : question).toLowerCase(Locale.ROOT);
        for (String token : getAllowedServerTokensLower()) {
            if (token.isBlank()) continue;
            if (q.contains(token)) return true;
        }
        return false;
    }

    private String buildInstructions() {
        String base = plugin.getConfig().getString("ai.system-prompt", "").trim();
        if (base.isEmpty()) {
            base = "Sen sadece {server} sunucusunu bilen bir rehbersin. Türkçe ve kısa cevap ver.";
        }
        base = applyServerPlaceholders(base);

        boolean include = plugin.getConfig().getBoolean("ai.include-server-knowledge", true);
        if (!include) return base;

        String knowledge = plugin.getKnowledgeStore().getKnowledgeText();
        if (knowledge.isBlank()) return base;

        int maxKnowledge = plugin.getConfig().getInt("ai.max-knowledge-chars", 6000);
        knowledge = TextUtil.safeTrim(knowledge, maxKnowledge);

        return base
                + "\n\n---\nSUNUCU BILGILERI (Referans):\n"
                + knowledge
                + "\n---\n"
                + applyServerPlaceholders("{server} dışındaki sunucular için bilgi verme. Bilgi yoksa bunu belirt ve eksik bilgiyi sor.");
    }

    private boolean isAboutOtherServer(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return false;

        String lower = q.toLowerCase(Locale.ROOT);

        // Bizim sunucu geçiyorsa serbest
        if (questionMentionsOurServer(q)) return false;

        // "xxxmc" geçiyorsa blokla
        Matcher m = SERVER_NAME_LIKE.matcher(q);
        if (m.find()) return true;

        // açıkça başka sunucu soruyorsa
        if (lower.contains("başka sunucu") || lower.contains("başka server")
                || lower.contains("diğer sunucu") || lower.contains("farklı sunucu")) {
            return true;
        }

        // "sunucu" kelimesi var ama bizim sunucu yoksa güvenli tarafta kal
        if (lower.contains(" sunucu") || lower.contains(" sunucusu") || lower.contains(" server")) {
            return true;
        }

        return false;
    }
}
