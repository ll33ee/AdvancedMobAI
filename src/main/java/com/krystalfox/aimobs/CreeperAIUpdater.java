package com.krystalfox.aimobs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class CreeperAIUpdater
extends BukkitRunnable {
    private final AdvancedMobAI plugin;
    private final boolean wallBreachingEnabled;
    private final double maxCheckDistance;
    private final double proximityThresholdSquared;
    private final boolean proactiveTargetingEnabled;
    private final double proactiveTargetingRange;
    private final double proactiveTargetingRangeSq;
    private final boolean pillarExplosionEnabled;
    private final double pillarMaxHorizDistSq;
    private final double pillarMinVertDist;
    private final double pillarMaxVertDist;
    private final Map<UUID, Long> creeperCooldown = new HashMap<UUID, Long>();
    private final long COOLDOWN_TIME_MS;
    private long lastErrorLog = 0L;
    private static final long ERROR_LOG_COOLDOWN = 30000L;

    public CreeperAIUpdater(AdvancedMobAI plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.wallBreachingEnabled = config.getBoolean("creeper.wall_breaching.enabled", true);
        this.maxCheckDistance = config.getDouble("creeper.wall_breaching.max_check_distance", 5.0);
        double proximityThreshold = config.getDouble("creeper.wall_breaching.proximity_threshold", 1.8);
        this.proximityThresholdSquared = proximityThreshold * proximityThreshold;
        this.proactiveTargetingEnabled = config.getBoolean("creeper.proactive_targeting.enabled", true);
        this.proactiveTargetingRange = config.getDouble("creeper.proactive_targeting.range", 8.0);
        this.proactiveTargetingRangeSq = this.proactiveTargetingRange * this.proactiveTargetingRange;
        this.pillarExplosionEnabled = config.getBoolean("creeper.pillar_explosion.enabled", true);
        double pillarMaxHorizDist = config.getDouble("creeper.pillar_explosion.max_horizontal_distance", 3.5);
        this.pillarMaxHorizDistSq = pillarMaxHorizDist * pillarMaxHorizDist;
        this.pillarMinVertDist = config.getDouble("creeper.pillar_explosion.min_vertical_distance", 2.0);
        this.pillarMaxVertDist = config.getDouble("creeper.pillar_explosion.max_vertical_distance", 5.0);
        this.COOLDOWN_TIME_MS = config.getLong("creeper.wall_breaching.cooldown_seconds", 5L) * 1000L;
    }

    public void run() {
        try {
            long now = System.currentTimeMillis();
            for (World world : this.plugin.getServer().getWorlds()) {
                for (Creeper creeper : world.getEntitiesByClass(Creeper.class)) {
                    try {
                        this.processCreeper(creeper, now);
                    }
                    catch (Exception e) {
                        this.handleCreeperError(creeper, e, now);
                    }
                }
            }
            this.creeperCooldown.entrySet().removeIf(entry -> this.plugin.getServer().getEntity((UUID)entry.getKey()) == null || this.plugin.getServer().getEntity((UUID)entry.getKey()).isDead());
        }
        catch (Exception globalEx) {
            this.handleGlobalError(globalEx);
        }
    }

    private void processCreeper(Creeper creeper, long now) {
        Location cLoc;
        Location pLoc;
        if (this.proactiveTargetingEnabled && creeper.getTarget() == null) {
            Player closestPlayer = null;
            double minDistanceSqFound = this.proactiveTargetingRangeSq;
            for (Entity entity : creeper.getNearbyEntities(this.proactiveTargetingRange, this.proactiveTargetingRange, this.proactiveTargetingRange)) {
                double distSq;
                Player p;
                if (!(entity instanceof Player) || !(p = (Player)entity).getWorld().equals((Object)creeper.getWorld()) || p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || (this.plugin.isIgnoreInvulnerablePlayers() && p.isInvulnerable()) || !((distSq = creeper.getLocation().distanceSquared(p.getLocation())) <= minDistanceSqFound)) continue;
                minDistanceSqFound = distSq;
                closestPlayer = p;
            }
            if (closestPlayer != null) {
                creeper.setTarget(closestPlayer);
            }
        }
        if (creeper.isIgnited()) {
            return;
        }
        LivingEntity target = creeper.getTarget();
        if (target == null || !(target instanceof Player) || target.isDead()) {
            return;
        }
        Player player = (Player)target;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (this.plugin.isIgnoreInvulnerablePlayers() && player.isInvulnerable()) {
            return;
        }
        if (!creeper.getWorld().equals((Object)player.getWorld())) {
            return;
        }
        UUID creeperId = creeper.getUniqueId();
        if (this.creeperCooldown.containsKey(creeperId) && now - this.creeperCooldown.get(creeperId) < this.COOLDOWN_TIME_MS) {
            return;
        }
        boolean specialActionTaken = false;
        if (this.pillarExplosionEnabled) {
            pLoc = player.getLocation();
            cLoc = creeper.getLocation();
            double dx = pLoc.getX() - cLoc.getX();
            double dy = pLoc.getY() - cLoc.getY();
            double dz = pLoc.getZ() - cLoc.getZ();
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq <= this.pillarMaxHorizDistSq && dy >= this.pillarMinVertDist && dy <= this.pillarMaxVertDist && dy > 1.6) {
                creeper.ignite();
                this.creeperCooldown.put(creeperId, now);
                specialActionTaken = true;
            }
        }
        if (!specialActionTaken && this.wallBreachingEnabled) {
            double distanceToWallSquared;
            Location wallLocationDetected;
            pLoc = player.getLocation();
            cLoc = creeper.getLocation();
            double distanceToPlayerSquared = cLoc.distanceSquared(pLoc);
            if (distanceToPlayerSquared <= this.maxCheckDistance * this.maxCheckDistance && !creeper.hasLineOfSight((Entity)player) && (wallLocationDetected = this.checkWall(creeper, player)) != null && (distanceToWallSquared = cLoc.distanceSquared(wallLocationDetected.clone().add(0.5, 0.0, 0.5))) <= this.proximityThresholdSquared) {
                creeper.ignite();
                this.creeperCooldown.put(creeperId, now);
            }
        }
    }

    private Location checkWall(Creeper creeper, Player player) {
        Location checkLoc;
        Location playerLoc;
        Location creeperLoc = creeper.getEyeLocation();
        if (creeperLoc == null) {
            creeperLoc = creeper.getLocation();
        }
        if ((playerLoc = player.getEyeLocation()) == null) {
            playerLoc = player.getLocation();
        }
        Vector direction = playerLoc.toVector().subtract(creeperLoc.toVector()).normalize();
        for (double d = 1.0; d <= this.maxCheckDistance && (checkLoc = creeperLoc.clone().add(direction.clone().multiply(d))).getChunk().isLoaded(); d += 0.5) {
            Block checkBlock = checkLoc.getBlock();
            Material blockType = checkBlock.getType();
            if (!blockType.isSolid() || !blockType.isOccluding()) {
                if (!(checkLoc.distanceSquared(playerLoc) < 2.25)) continue;
                break;
            }
            if (this.plugin.isCreeperResistantBlock(blockType)) {
                return null;
            }
            Location nextLoc = checkLoc.clone().add(direction.clone().multiply(1.0));
            if (!nextLoc.getChunk().isLoaded()) break;
            Block nextBlock = nextLoc.getBlock();
            Material nextBlockType = nextBlock.getType();
            if (!nextBlockType.isSolid() || !nextBlockType.isOccluding()) {
                return checkBlock.getLocation();
            }
            if (this.plugin.isCreeperResistantBlock(nextBlockType)) {
                return null;
            }
            Location thirdLoc = nextLoc.clone().add(direction.clone().multiply(1.0));
            if (!thirdLoc.getChunk().isLoaded()) break;
            Block thirdBlock = thirdLoc.getBlock();
            if (!thirdBlock.isSolid() || thirdLoc.distanceSquared(playerLoc) < 4.0) {
                return checkBlock.getLocation();
            }
            return null;
        }
        return null;
    }

    private void handleCreeperError(Creeper creeper, Exception e, long now) {
        if (now - this.lastErrorLog > 30000L) {
            this.plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error processing creeper " + String.valueOf(creeper != null ? creeper.getUniqueId() : "null"), e);
            this.lastErrorLog = now;
        }
    }

    private void handleGlobalError(Exception e) {
        long now = System.currentTimeMillis();
        if (now - this.lastErrorLog > 30000L) {
            this.plugin.getLogger().log(Level.SEVERE, "[DEBUG] Unhandled error in CreeperAI task", e);
            this.lastErrorLog = now;
        }
    }
}

