package com.krystalfox.aimobs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class EndermanAIUpdater extends BukkitRunnable {

    private final AdvancedMobAI plugin;
    private final double maxCheckDistanceSq;
    private final long cooldownTimeMs;
    private final Set<Material> pickupableMaterials;
    private final Map<UUID, Long> endermanCooldown = new HashMap<>();

    public EndermanAIUpdater(AdvancedMobAI plugin) {
        this.plugin = plugin;
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        double maxDist = config.getDouble("enderman.dismantler_ai.max_check_distance", 6.0);
        this.maxCheckDistanceSq = maxDist * maxDist;
        this.cooldownTimeMs = config.getLong("enderman.dismantler_ai.cooldown_seconds", 8L) * 1000L;
        this.pickupableMaterials = new HashSet<>();
        List<String> pickupableNames = config.getStringList("enderman.dismantler_ai.pickupable_blocks");
        if (pickupableNames != null && !pickupableNames.isEmpty()) {
            plugin.getLogger().info("[Enderman AI] Modo Pickupable Restringido Activado.");
            for (String name : pickupableNames) {
                try { pickupableMaterials.add(Material.valueOf(name.toUpperCase())); }
                catch (IllegalArgumentException e) { plugin.getLogger().warning("[Enderman AI] Material 'pickupable' inválido: " + name); }
            }
        } else {
            plugin.getLogger().info("[Enderman AI] Modo Pickupable Amplio Activado.");
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (World world : plugin.getServer().getWorlds()) {
            for (Enderman enderman : world.getEntitiesByClass(Enderman.class)) {

                LivingEntity target = enderman.getTarget();
                if (target == null || !(target instanceof Player) || target.isDead()) continue;
                Player player = (Player) target;
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (plugin.isIgnoreInvulnerablePlayers() && player.isInvulnerable()) continue;

                Location eLoc = enderman.getLocation();
                Location pLoc = player.getLocation();

                if (eLoc.distanceSquared(pLoc) > maxCheckDistanceSq) continue;

                UUID endermanId = enderman.getUniqueId();
                if (endermanCooldown.containsKey(endermanId) && (now - endermanCooldown.get(endermanId)) < cooldownTimeMs) continue;

                if (!enderman.hasLineOfSight(player)) {

                    Block obstructingBlock = findObstructingBlock(enderman, player);

                    if (obstructingBlock != null) {
                        Material blockMat = obstructingBlock.getType();

                        boolean isPickupable = pickupableMaterials.isEmpty() || pickupableMaterials.contains(blockMat);
                        boolean isResistant = plugin.isEndermanResistantBlock(blockMat);

                        if (isPickupable && !isResistant) {
                            plugin.getLogger().info("Enderman " + endermanId + " desmantelando y soltando bloque: " + blockMat + " en " + obstructingBlock.getLocation().toVector());
                            Material materialToDrop = obstructingBlock.getType();
                            obstructingBlock.setType(Material.AIR);
                            world.dropItemNaturally(obstructingBlock.getLocation().add(0.5, 0.2, 0.5), new ItemStack(materialToDrop, 1));
                            world.playSound(enderman.getEyeLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.7f);
                            endermanCooldown.put(endermanId, now);
                        }
                    }
                }

            }
        }

        endermanCooldown.entrySet().removeIf(entry -> {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            return entity == null || entity.isDead();
        });
    }

    /**
     * Encuentra el primer bloque sólido, no resistente y potencialmente recogible
     * en la línea directa desde los ojos del Enderman hacia los ojos del jugador.
     * @return El bloque a quitar, o null si no se encuentra uno adecuado.
     */
    private Block findObstructingBlock(Enderman enderman, Player player) {
        Location startLoc = enderman.getEyeLocation();
        Location endLoc = player.getEyeLocation();
        Vector direction = endLoc.toVector().subtract(startLoc.toVector()).normalize();
        World world = enderman.getWorld();
        double effectiveMaxDist = Math.min(maxCheckDistanceSq > 0 ? Math.sqrt(maxCheckDistanceSq) : 6.0, 5.0);

        for (double d = 0.8; d < effectiveMaxDist; d += 0.4) {
            Location checkLoc = startLoc.clone().add(direction.clone().multiply(d));
            Block checkBlock = checkLoc.getBlock();
            Material blockMat = checkBlock.getType();

            if (blockMat.isSolid()) {
                boolean isPickupable = pickupableMaterials.isEmpty() || pickupableMaterials.contains(blockMat);
                boolean isResistant = plugin.isEndermanResistantBlock(blockMat);

                if (isPickupable && !isResistant) {
                    return checkBlock;
                } else {
                    return null; // Obstáculo inválido/resistente
                }
            }
        }
        return null; // No se encontró obstáculo sólido
    }

}