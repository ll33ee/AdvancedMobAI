package com.krystalfox.aimobs;

import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

public class ProjectileListener
implements Listener {
    private final AdvancedMobAI plugin;
    private final boolean webShotEnabled;
    private final int webDurationTicks;
    private final boolean applyEffectOnCapture;
    private PotionEffectType effectType;
    private final int effectDurationTicks;
    private final int effectAmplifier;
    private final boolean particlesOnImpact;
    private Particle particleTypeOnImpact;
    private final int effectCheckDelayTicks = 6;

    public ProjectileListener(AdvancedMobAI plugin) {
        this.plugin = plugin;
        this.webShotEnabled = plugin.getConfig().getBoolean("spider_ai.web_shot.enabled", false);
        this.webDurationTicks = plugin.getConfig().getInt("spider_ai.web_shot.web_duration_seconds", 5) * 20;
        this.applyEffectOnCapture = plugin.getConfig().getBoolean("spider_ai.web_shot.apply_effect_on_capture", true);
        this.effectDurationTicks = plugin.getConfig().getInt("spider_ai.web_shot.effect_duration_seconds", 4) * 20;
        this.effectAmplifier = plugin.getConfig().getInt("spider_ai.web_shot.effect_amplifier", 0);
        this.particlesOnImpact = plugin.getConfig().getBoolean("spider_ai.web_shot.particles_on_impact", true);
        String particleNameImpact = plugin.getConfig().getString("spider_ai.web_shot.particle_type_on_impact", "BLOCK");
        try {
            this.particleTypeOnImpact = Particle.valueOf((String)particleNameImpact.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SpiderAI] Invalid particle type in config for impact: " + particleNameImpact + ". Using BLOCK by default.");
            this.particleTypeOnImpact = Particle.BLOCK;
        }
        String effectTypeName = plugin.getConfig().getString("spider_ai.web_shot.effect_type", "POISON");
        try {
            this.effectType = PotionEffectType.getByName((String)effectTypeName.toUpperCase());
            if (this.effectType == null) {
                plugin.getLogger().warning("[SpiderAI] Invalid potion effect type in config: " + effectTypeName + ". Using POISON by default.");
                this.effectType = PotionEffectType.POISON;
            }
        }
        catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[SpiderAI] Error loading potion effect type: " + effectTypeName + ". Using POISON by default.", e);
            this.effectType = PotionEffectType.POISON;
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Location impactLoc;
        if (!this.webShotEnabled) {
            return;
        }
        Projectile projectile = event.getEntity();
        PersistentDataContainer projectileData = projectile.getPersistentDataContainer();
        if (!(projectile instanceof Snowball) || !projectileData.has(SpiderAIUpdater.WEB_SHOT_KEY, PersistentDataType.BOOLEAN)) {
            return;
        }
        Player hitPlayer = null;
        if (event.getHitEntity() instanceof Player) {
            hitPlayer = (Player)event.getHitEntity();
            impactLoc = hitPlayer.getLocation().getBlock().getLocation();
            if (this.plugin.getSpiderAIUpdater() != null && projectileData.has(SpiderAIUpdater.SHOOTER_UUID_KEY, PersistentDataType.STRING)) {
                UUID shooterUUID = UUID.fromString(projectileData.get(SpiderAIUpdater.SHOOTER_UUID_KEY, PersistentDataType.STRING));
                if (this.plugin.isDebugLogProjectileShooter()) {
                    this.logShooterDiagnostics(projectile, shooterUUID);
                }
                this.plugin.getSpiderAIUpdater().onWebShotHitPlayer(shooterUUID, hitPlayer);
            }
        } else if (event.getHitBlock() != null) {
            Block hitBlock = event.getHitBlock();
            impactLoc = hitBlock.getLocation().add(event.getHitBlockFace().getDirection());
        } else {
            return;
        }
        this.createWebAndApplyEffect(impactLoc, hitPlayer);
        projectile.remove();
    }

    private void createWebAndApplyEffect(Location location, final Player player) {
        boolean isReplaceable;
        Block targetBlock = location.getBlock();
        boolean bl = isReplaceable = !targetBlock.getType().isSolid() || targetBlock.getType() == Material.WATER || targetBlock.getType() == Material.LAVA || targetBlock.getType().name().contains("GRASS") || targetBlock.getType().name().contains("FERN") || targetBlock.getType().name().contains("FLOWER") || targetBlock.getType().name().contains("MUSHROOM") || targetBlock.getType() == Material.VINE || targetBlock.getType() == Material.SNOW || targetBlock.getType() == Material.COBWEB;
        if (isReplaceable) {
            final BlockData originalBlockData = targetBlock.getBlockData();
            final Material originalMaterial = targetBlock.getType();
            targetBlock.setType(Material.COBWEB);
            targetBlock.getWorld().playSound(targetBlock.getLocation(), Sound.ENTITY_SPIDER_DEATH, 0.8f, 0.5f);
            if (this.particlesOnImpact && this.particleTypeOnImpact != null) {
                if (this.particleTypeOnImpact == Particle.BLOCK) {
                    targetBlock.getWorld().spawnParticle(this.particleTypeOnImpact, targetBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.2, 0.2, 0.2, (Object)Material.COBWEB.createBlockData());
                } else {
                    targetBlock.getWorld().spawnParticle(this.particleTypeOnImpact, targetBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.2, 0.2, 0.2, 0.01);
                }
            }
            final Location finalLocation = targetBlock.getLocation();
            new BukkitRunnable(){
                public void run() {
                    if (finalLocation.getBlock().getType() == Material.COBWEB) {
                        if (originalMaterial != Material.AIR && !originalMaterial.name().contains("AIR") && originalMaterial != Material.CAVE_AIR && originalMaterial != Material.VOID_AIR) {
                            finalLocation.getBlock().setBlockData(originalBlockData);
                        } else {
                            finalLocation.getBlock().setType(Material.AIR);
                        }
                        finalLocation.getWorld().playSound(finalLocation, Sound.BLOCK_WOOL_BREAK, 0.5f, 1.5f);
                        if (particlesOnImpact && particleTypeOnImpact != null) {
                            if (particleTypeOnImpact == Particle.BLOCK) {
                                finalLocation.getWorld().spawnParticle(particleTypeOnImpact, finalLocation.add(0.5, 0.5, 0.5), 10, 0.1, 0.1, 0.1, (Object)Material.COBWEB.createBlockData());
                            } else {
                                finalLocation.getWorld().spawnParticle(particleTypeOnImpact, finalLocation.add(0.5, 0.5, 0.5), 10, 0.1, 0.1, 0.1, 0.01);
                            }
                        }
                    } else {
                        plugin.getLogger().fine("[SpiderAI] Web not removed at " + String.valueOf(finalLocation) + " - block is no longer COBWEB.");
                    }
                }
            }.runTaskLater((Plugin)this.plugin, (long)this.webDurationTicks);
            if (player != null && this.applyEffectOnCapture && this.effectType != null) {
                new BukkitRunnable(){
                    public void run() {
                        Block playerFeetBlock = player.getLocation().getBlock();
                        Block playerLegsBlock = player.getLocation().add(0.0, 1.0, 0.0).getBlock();
                        if (playerFeetBlock.getType() == Material.COBWEB || playerLegsBlock.getType() == Material.COBWEB) {
                            try {
                                player.removePotionEffect(effectType);
                                player.addPotionEffect(new PotionEffect(effectType, effectDurationTicks, effectAmplifier, false, true));
                                plugin.getLogger().fine("[SpiderAI] Potion effect applied to " + player.getName() + " (caught in web).");
                            }
                            catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "[SpiderAI] Error applying potion effect to " + player.getName(), e);
                            }
                        } else {
                            plugin.getLogger().fine("[SpiderAI] No effect applied to " + player.getName() + " (not caught in web).");
                        }
                    }
                }.runTaskLater((Plugin)this.plugin, 6L);
            }
        } else {
            this.plugin.getLogger().fine("[SpiderAI] Could not place web at " + String.valueOf(location) + " (Type: " + targetBlock.getType().name() + ") - block not replaceable.");
        }
    }

    // Diagnostic only - disabled unless 'debug_log_projectile_shooter: true' is manually added to config.yml.
    // Compares the native Projectile#getShooter() reference against a fresh, UUID-based live lookup, to check
    // whether getShooter() can return a stale/invalid entity after its chunk unloads and reloads mid-flight.
    private void logShooterDiagnostics(Projectile projectile, UUID storedUUID) {
        ProjectileSource shooterSource = projectile.getShooter();
        Entity liveEntity = this.plugin.getServer().getEntity(storedUUID);
        boolean liveEntityFound = liveEntity != null;
        if (shooterSource instanceof Entity) {
            Entity shooterEntity = (Entity) shooterSource;
            boolean sameUUID = liveEntityFound && shooterEntity.getUniqueId().equals(liveEntity.getUniqueId());
            boolean shooterValid = shooterEntity.isValid() && !shooterEntity.isDead();
            this.plugin.getLogger().info("[DEBUG getShooter] storedUUID=" + storedUUID
                + " getShooter().uuid=" + shooterEntity.getUniqueId()
                + " sameUUID=" + sameUUID
                + " getShooterValid=" + shooterValid
                + " liveLookupFound=" + liveEntityFound);
        } else {
            this.plugin.getLogger().info("[DEBUG getShooter] storedUUID=" + storedUUID
                + " getShooter()=" + (shooterSource == null ? "null" : shooterSource.getClass().getName())
                + " liveLookupFound=" + liveEntityFound);
        }
    }
}

