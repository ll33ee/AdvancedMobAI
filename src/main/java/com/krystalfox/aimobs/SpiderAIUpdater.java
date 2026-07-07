package com.krystalfox.aimobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Spider;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SpiderAIUpdater
extends BukkitRunnable {
    private final AdvancedMobAI plugin;
    private final boolean webShotEnabled;
    private final double webShotRangeSq;
    private final long webShotCooldownTimeMs;
    private final boolean particlesOnShot;
    private Particle particleTypeOnShot;
    private final Map<UUID, Long> spiderCooldown = new HashMap<UUID, Long>();
    public static final NamespacedKey WEB_SHOT_KEY = new NamespacedKey("advancedmobai", "spider_web_shot");
    public static final NamespacedKey SHOOTER_UUID_KEY = new NamespacedKey("advancedmobai", "shooter_uuid");
    private final int speedEffectDurationTicks = 60;
    private final int speedEffectAmplifier = 1;

    public SpiderAIUpdater(AdvancedMobAI plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.webShotEnabled = config.getBoolean("spider_ai.web_shot.enabled", false);
        double webShotRange = config.getDouble("spider_ai.web_shot.range", 15.0);
        this.webShotRangeSq = webShotRange * webShotRange;
        this.webShotCooldownTimeMs = config.getLong("spider_ai.web_shot.cooldown_seconds", 7L) * 1000L;
        this.particlesOnShot = config.getBoolean("spider_ai.web_shot.particles_on_shot", true);
        String particleNameShot = config.getString("spider_ai.web_shot.particle_type_on_shot", "SPIT");
        try {
            this.particleTypeOnShot = Particle.valueOf((String)particleNameShot.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SpiderAI] Tipo de part\u00edcula inv\u00e1lido en config para disparo: " + particleNameShot + ". Usando SPIT por defecto.");
            this.particleTypeOnShot = Particle.SPIT;
        }
        plugin.getLogger().info("[SpiderAI] Web Shot AI: " + (this.webShotEnabled ? "Activado" : "Desactivado"));
        if (this.webShotEnabled) {
            plugin.getLogger().info("[SpiderAI] Rango de disparo: " + webShotRange);
            plugin.getLogger().info("[SpiderAI] Cooldown: " + this.webShotCooldownTimeMs / 1000L + "s");
            plugin.getLogger().info("[SpiderAI] Part\u00edculas al disparar: " + this.particleTypeOnShot.name());
        }
        plugin.getLogger().info("[SpiderAI] Enhanced Attack: Always Enabled (Targeting & Speed Boost)");
    }

    public void run() {
        if (!this.webShotEnabled) {
            return;
        }
        List<Spider> spiders = this.plugin.getServer().getWorlds().stream().flatMap(world -> world.getEntitiesByClass(Spider.class).stream()).collect(Collectors.toList());
        for (Spider spider : spiders) {
            Player targetPlayer;
            if (spider == null || spider.isDead() || !spider.isValid()) continue;
            long lastShotTime = this.spiderCooldown.getOrDefault(spider.getUniqueId(), 0L);
            if (System.currentTimeMillis() - lastShotTime < this.webShotCooldownTimeMs || (targetPlayer = this.findPlayerTarget(spider)) == null) continue;
            this.shootWebShot(spider, targetPlayer);
            this.spiderCooldown.put(spider.getUniqueId(), System.currentTimeMillis());
        }
    }

    private Player findPlayerTarget(Spider spider) {
        World world = spider.getWorld();
        Location spiderLoc = spider.getLocation();
        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || (this.plugin.isIgnoreInvulnerablePlayers() && player.isInvulnerable()) || spiderLoc.distanceSquared(player.getLocation()) > this.webShotRangeSq || !spider.hasLineOfSight((Entity)player)) continue;
            return player;
        }
        return null;
    }

    private void shootWebShot(Spider spider, Player target) {
        Location spiderEyeLoc = spider.getEyeLocation();
        Location targetLoc = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
        Vector direction = targetLoc.toVector().subtract(spiderEyeLoc.toVector()).normalize();
        double speed = 1.8;
        direction.add(new Vector(0.0, 0.1, 0.0));
        direction.normalize().multiply(speed);
        Snowball snowball = (Snowball)spider.launchProjectile(Snowball.class, direction);
        PersistentDataContainer snowballData = snowball.getPersistentDataContainer();
        snowballData.set(WEB_SHOT_KEY, PersistentDataType.BOOLEAN, true);
        snowballData.set(SHOOTER_UUID_KEY, PersistentDataType.STRING, spider.getUniqueId().toString());
        snowball.setShooter((ProjectileSource)spider);
        spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.5f, 2.0f);
        if (this.particlesOnShot && this.particleTypeOnShot != null) {
            spider.getWorld().spawnParticle(this.particleTypeOnShot, spiderEyeLoc, 5, 0.2, 0.2, 0.2, 0.01);
        }
    }

    public void onWebShotHitPlayer(UUID shooterUUID, Player hitPlayer) {
        Spider spider = (Spider)this.plugin.getServer().getEntity(shooterUUID);
        if (spider != null && !spider.isDead() && spider.isValid()) {
            spider.setTarget((LivingEntity)hitPlayer);
            spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, true));
            this.plugin.getLogger().fine("[SpiderAI] Ara\u00f1a " + String.valueOf(spider.getUniqueId()) + " targete\u00f3 y recibi\u00f3 velocidad al impactar telara\u00f1a en " + hitPlayer.getName());
        }
    }
}

