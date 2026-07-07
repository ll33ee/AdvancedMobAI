package com.krystalfox.aimobs;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class AdvancedMobAI
extends JavaPlugin {
    private static AdvancedMobAI instance;
    private CreeperAIUpdater creeperAIUpdaterTask;
    private BukkitTask creeperTaskHandle;
    private EndermanAIUpdater endermanAIUpdaterTask;
    private BukkitTask endermanTaskHandle;
    private SpiderAIUpdater spiderAIUpdaterTask;
    private BukkitTask spiderTaskHandle;
    private WitchAIUpdater witchAIUpdaterTask;
    private BukkitTask witchTaskHandle;
    private Set<Material> creeperResistantMaterials = new HashSet<Material>();
    private Set<Material> endermanResistantMaterials = new HashSet<Material>();
    private boolean creeperExplosionFriendlyFire;
    private boolean witchSupportiveAIEnabled;
    private double witchMaxPlayerDistance;
    private double witchMinPlayerDistance;
    private double witchAllySearchRadius;
    private long witchCheckIntervalTicks;
    private int witchCooldownSeconds;
    private List<Map<?, ?>> witchEffectsConfig;
    private List<String> witchTargetMobs;
    private boolean ignoreInvulnerablePlayers;
    private boolean debugLogProjectileShooter;

    public void onEnable() {
        instance = this;
        this.getLogger().info("------------------------------------------");
        this.getLogger().info("   AdvancedMobAI v" + this.getPluginMeta().getVersion() + " Enabled");
        this.getLogger().info("------------------------------------------");
        this.saveDefaultConfig();
        this.loadConfigValues();
        this.getServer().getPluginManager().registerEvents((Listener)new DamageListener(this), (Plugin)this);
        this.getLogger().info("Damage Listener registered.");
        this.startSpiderTask();
        this.getServer().getPluginManager().registerEvents((Listener)new ProjectileListener(this), (Plugin)this);
        this.getLogger().info("Projectile Listener registered.");
        this.startCreeperTask();
        this.startEndermanTask();
        this.startWitchTask();
        this.getLogger().info("AdvancedMobAI loaded successfully.");
    }

    public void onDisable() {
        this.getLogger().info("------------------------------------------");
        this.getLogger().info("   AdvancedMobAI Disabled");
        this.getLogger().info("------------------------------------------");
        if (this.creeperTaskHandle != null && !this.creeperTaskHandle.isCancelled()) {
            this.creeperTaskHandle.cancel();
        }
        if (this.endermanTaskHandle != null && !this.endermanTaskHandle.isCancelled()) {
            this.endermanTaskHandle.cancel();
        }
        if (this.spiderTaskHandle != null && !this.spiderTaskHandle.isCancelled()) {
            this.spiderTaskHandle.cancel();
        }
        if (this.witchTaskHandle != null && !this.witchTaskHandle.isCancelled()) {
            this.witchTaskHandle.cancel();
        }
        this.getLogger().info("Tasks stopped.");
    }

    public void loadConfigValues() {
        this.reloadConfig();
        this.getLogger().info("Reloading AdvancedMobAI configuration...");
        this.getLogger().info("Loading Creeper configuration...");
        this.creeperResistantMaterials.clear();
        List<String> creeperResistNames = this.getConfig().getStringList("creeper.wall_breaching.resistant_blocks");
        if (creeperResistNames != null) {
            for (String name : creeperResistNames) {
                try {
                    this.creeperResistantMaterials.add(Material.valueOf((String)name.toUpperCase()));
                }
                catch (IllegalArgumentException e) {
                    this.getLogger().warning("[Config Creeper] Invalid resistant material: " + name);
                }
            }
        }
        this.addAlwaysResistant(this.creeperResistantMaterials);
        this.getLogger().info("[Config Creeper] Resistant blocks loaded: " + this.creeperResistantMaterials.size());
        this.logEnabledStatus("Creeper Wall Breaching", this.getConfig().getBoolean("creeper.wall_breaching.enabled", false));
        this.logEnabledStatus("Creeper Proactive Targeting", this.getConfig().getBoolean("creeper.proactive_targeting.enabled", false));
        this.logEnabledStatus("Creeper Pillar Explosion", this.getConfig().getBoolean("creeper.pillar_explosion.enabled", false));
        this.getLogger().info("Loading Enderman configuration...");
        this.endermanResistantMaterials.clear();
        List<String> endermanResistNames = this.getConfig().getStringList("enderman.dismantler_ai.resistant_blocks");
        if (endermanResistNames != null) {
            for (String name : endermanResistNames) {
                try {
                    this.endermanResistantMaterials.add(Material.valueOf((String)name.toUpperCase()));
                }
                catch (IllegalArgumentException e) {
                    this.getLogger().warning("[Config Enderman] Invalid resistant material: " + name);
                }
            }
        }
        this.addAlwaysResistant(this.endermanResistantMaterials);
        this.getLogger().info("[Config Enderman] Resistant blocks loaded: " + this.endermanResistantMaterials.size());
        this.logEnabledStatus("Enderman Dismantler AI", this.getConfig().getBoolean("enderman.dismantler_ai.enabled", false));
        this.creeperExplosionFriendlyFire = this.getConfig().getBoolean("creeper.creeper_explosions.friendly_fire", true);
        this.logEnabledStatus("Creeper Explosion Friendly Fire (Normal Creepers)", this.getConfig().getBoolean("creeper.creeper_explosions.friendly_fire", true));
        this.getLogger().info("Loading Spider configuration...");
        this.logEnabledStatus("Spider Web Shot AI", this.getConfig().getBoolean("spider_ai.web_shot.enabled", false));
        this.getLogger().info("Loading Witch configuration...");
        this.witchSupportiveAIEnabled = this.getConfig().getBoolean("witch_ai.supportive_ai.enabled", false);
        this.witchMaxPlayerDistance = this.getConfig().getDouble("witch_ai.supportive_ai.max_player_distance", 25.0);
        this.witchMinPlayerDistance = this.getConfig().getDouble("witch_ai.supportive_ai.min_player_distance", 8.0);
        this.witchAllySearchRadius = this.getConfig().getDouble("witch_ai.supportive_ai.ally_search_radius", 6.0);
        this.witchCheckIntervalTicks = this.getConfig().getLong("witch_ai.supportive_ai.check_interval_ticks", 40L);
        this.witchCooldownSeconds = this.getConfig().getInt("witch_ai.supportive_ai.cooldown_seconds", 15);
        this.witchEffectsConfig = this.getConfig().getMapList("witch_ai.supportive_ai.effects");
        this.witchTargetMobs = this.getConfig().getStringList("witch_ai.supportive_ai.target_mobs");
        this.logEnabledStatus("Witch Supportive AI", this.witchSupportiveAIEnabled);
        // Undocumented option, not present in the shipped config.yml - add it manually to enable.
        this.ignoreInvulnerablePlayers = this.getConfig().getBoolean("ignore_invulnerable_players", false);
        // Undocumented option, not present in the shipped config.yml - add it manually to enable.
        this.debugLogProjectileShooter = this.getConfig().getBoolean("debug_log_projectile_shooter", false);
        this.getLogger().info("Configuration reloaded completely.");
    }

    private void logEnabledStatus(String featureName, boolean enabled) {
        // El logger de consola no renderiza colores; solo texto plano.
        this.getLogger().info(featureName + ": " + (enabled ? "Enabled" : "Disabled"));
    }

    private void addAlwaysResistant(Set<Material> materialSet) {
        materialSet.add(Material.BEDROCK);
        materialSet.add(Material.END_PORTAL_FRAME);
        materialSet.add(Material.END_GATEWAY);
        materialSet.add(Material.BARRIER);
        materialSet.add(Material.COMMAND_BLOCK);
        materialSet.add(Material.CHAIN_COMMAND_BLOCK);
        materialSet.add(Material.REPEATING_COMMAND_BLOCK);
        materialSet.add(Material.STRUCTURE_BLOCK);
        materialSet.add(Material.JIGSAW);
    }

    public void startCreeperTask() {
        if (this.creeperTaskHandle != null && !this.creeperTaskHandle.isCancelled()) {
            this.creeperTaskHandle.cancel();
        }
        boolean wbEnabled = this.getConfig().getBoolean("creeper.wall_breaching.enabled", false);
        boolean ptEnabled = this.getConfig().getBoolean("creeper.proactive_targeting.enabled", false);
        boolean peEnabled = this.getConfig().getBoolean("creeper.pillar_explosion.enabled", false);
        if (wbEnabled || ptEnabled || peEnabled) {
            long interval = this.getConfig().getLong("creeper.wall_breaching.check_interval_ticks", 20L);
            if (interval <= 0L) {
                interval = 20L;
            }
            try {
                this.creeperAIUpdaterTask = new CreeperAIUpdater(this);
                this.creeperTaskHandle = this.creeperAIUpdaterTask.runTaskTimer((Plugin)this, 0L, interval);
                this.getLogger().info("Creeper AI Task (re)started (Interval: " + interval + " ticks).");
            }
            catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Error starting Creeper AI task:", e);
            }
        } else {
            this.getLogger().info("Creeper AI Task not started (disabled in config).");
        }
    }

    public void startEndermanTask() {
        if (this.endermanTaskHandle != null && !this.endermanTaskHandle.isCancelled()) {
            this.endermanTaskHandle.cancel();
        }
        if (this.getConfig().getBoolean("enderman.dismantler_ai.enabled", true)) {
            long interval = this.getConfig().getLong("enderman.dismantler_ai.check_interval_ticks", 30L);
            if (interval <= 0L) {
                interval = 30L;
            }
            try {
                this.endermanAIUpdaterTask = new EndermanAIUpdater(this);
                this.endermanTaskHandle = this.endermanAIUpdaterTask.runTaskTimer((Plugin)this, 0L, interval);
                this.getLogger().info("Enderman AI Task (Dismantler) (re)started (Interval: " + interval + " ticks).");
            }
            catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Error starting Enderman AI task:", e);
            }
        } else {
            this.getLogger().info("Enderman AI Task (Dismantler) not started (disabled in config).");
        }
    }

    public void startSpiderTask() {
        if (this.spiderTaskHandle != null && !this.spiderTaskHandle.isCancelled()) {
            this.spiderTaskHandle.cancel();
        }
        if (this.getConfig().getBoolean("spider_ai.web_shot.enabled", false)) {
            long interval = this.getConfig().getLong("spider_ai.web_shot.check_interval_ticks", 40L);
            if (interval <= 0L) {
                interval = 40L;
            }
            try {
                this.spiderAIUpdaterTask = new SpiderAIUpdater(this);
                this.spiderTaskHandle = this.spiderAIUpdaterTask.runTaskTimer((Plugin)this, 0L, interval);
                this.getLogger().info("Spider AI Task (Web Shot & Enhanced Attack) (re)started (Interval: " + interval + " ticks).");
            }
            catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Error starting Spider AI task:", e);
            }
        } else {
            this.getLogger().info("Spider AI Task (Web Shot & Enhanced Attack) not started (disabled in config).");
        }
    }

    public void startWitchTask() {
        if (this.witchTaskHandle != null && !this.witchTaskHandle.isCancelled()) {
            this.witchTaskHandle.cancel();
        }
        if (this.witchSupportiveAIEnabled) {
            long interval = this.witchCheckIntervalTicks;
            if (interval <= 0L) {
                interval = 40L;
            }
            try {
                this.witchAIUpdaterTask = new WitchAIUpdater(this);
                this.witchTaskHandle = this.witchAIUpdaterTask.runTaskTimer((Plugin)this, 0L, interval);
                this.getLogger().info("Witch AI Task (Supportive) started (Interval: " + interval + " ticks).");
            }
            catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Error starting Witch AI task:", e);
            }
        } else {
            this.getLogger().info("Witch AI Task (Supportive) not started (disabled in config).");
        }
    }

    public static AdvancedMobAI getInstance() {
        return instance;
    }

    public SpiderAIUpdater getSpiderAIUpdater() {
        return this.spiderAIUpdaterTask;
    }

    public boolean isCreeperExplosionFriendlyFireEnabled() {
        return this.creeperExplosionFriendlyFire;
    }

    public boolean isIgnoreInvulnerablePlayers() {
        return this.ignoreInvulnerablePlayers;
    }

    public boolean isDebugLogProjectileShooter() {
        return this.debugLogProjectileShooter;
    }

    public boolean isCreeperResistantBlock(Material material) {
        return this.creeperResistantMaterials.contains(material);
    }

    public boolean isEndermanResistantBlock(Material material) {
        return this.endermanResistantMaterials.contains(material);
    }

    public boolean isWitchSupportiveAIEnabled() {
        return this.witchSupportiveAIEnabled;
    }

    public double getWitchMaxPlayerDistance() {
        return this.witchMaxPlayerDistance;
    }

    public double getWitchMinPlayerDistance() {
        return this.witchMinPlayerDistance;
    }

    public double getWitchAllySearchRadius() {
        return this.witchAllySearchRadius;
    }

    public int getWitchCooldownSeconds() {
        return this.witchCooldownSeconds;
    }

    public List<Map<?, ?>> getWitchEffectsConfig() {
        return this.witchEffectsConfig;
    }

    public List<String> getWitchTargetMobs() {
        return this.witchTargetMobs;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("am") || label.equalsIgnoreCase("advancedmobai")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("advancedmobai.reload")) {
                    sender.sendMessage(Component.text("You don't have permission to execute this command.", NamedTextColor.RED));
                    return true;
                }
                try {
                    this.loadConfigValues();
                    this.startCreeperTask();
                    this.startEndermanTask();
                    this.startSpiderTask();
                    this.startWitchTask();
                    sender.sendMessage(Component.text("AdvancedMobAI configuration reloaded successfully!", NamedTextColor.GREEN));
                }
                catch (Exception e) {
                    sender.sendMessage(Component.text("Error reloading configuration. Check console.", NamedTextColor.RED));
                    this.getLogger().log(Level.SEVERE, "Error during reload command:", e);
                }
                return true;
            }
            sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.YELLOW));
            return true;
        }
        return false;
    }
}

