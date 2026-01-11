package me.mrhistories.airehber;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class AIGuidePlugin extends JavaPlugin {

    private OpenAIClient openAIClient;
    private KnowledgeStore knowledgeStore;
    private UsageManager usageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureServerKnowledgeFile();

        this.knowledgeStore = new KnowledgeStore(this);
        this.knowledgeStore.reload();

        this.usageManager = new UsageManager(this);

        this.openAIClient = new OpenAIClient(this);

        getCommand("rehber").setExecutor(new RehberCommand(this));

        getLogger().info("AIRehber aktif edildi.");
    }

    @Override
    public void onDisable() {
        if (usageManager != null) usageManager.save();
    }

    private void ensureServerKnowledgeFile() {
        File f = new File(getDataFolder(), "server-knowledge.yml");
        if (!f.exists()) {
            saveResource("server-knowledge.yml", false);
        }
    }

    public void reloadAll() {
        reloadConfig();
        knowledgeStore.reload();
        usageManager.reload();
    }

    public OpenAIClient getOpenAIClient() {
        return openAIClient;
    }

    public KnowledgeStore getKnowledgeStore() {
        return knowledgeStore;
    }

    public UsageManager getUsageManager() {
        return usageManager;
    }
}
