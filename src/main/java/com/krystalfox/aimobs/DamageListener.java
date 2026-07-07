package com.krystalfox.aimobs;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageListener
implements Listener {
    private final AdvancedMobAI plugin;

    public DamageListener(AdvancedMobAI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        if (!(event.getDamager() instanceof Creeper)) {
            return;
        }
        Creeper attacker = (Creeper)event.getDamager();
        if (attacker.isPowered()) {
            return;
        }
        if (this.plugin.isCreeperExplosionFriendlyFireEnabled()) {
            return;
        }
        Entity victim = event.getEntity();
        EntityType victimType = victim.getType();
        boolean shouldProtect = false;
        if (victimType == EntityType.CREEPER || victimType == EntityType.ZOMBIE || victimType == EntityType.SKELETON || victimType == EntityType.SPIDER || victimType == EntityType.HUSK || victimType == EntityType.STRAY || victimType == EntityType.CAVE_SPIDER || victimType == EntityType.ZOMBIE_VILLAGER) {
            shouldProtect = true;
        }
        if (shouldProtect) {
            if (victim.getUniqueId().equals(attacker.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
        }
    }
}

