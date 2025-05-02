package com.krystalfox.aimobs; // Usa tu paquete

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType; // Importar EntityType
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public class DamageListener implements Listener {

    private final AdvancedMobAI plugin;

    public DamageListener(AdvancedMobAI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        // --- Comprobación 1: ¿El daño fue por explosión de un Creeper? ---
        if (event.getCause() != DamageCause.ENTITY_EXPLOSION) {
            return; // No fue una explosión de entidad
        }
        if (!(event.getDamager() instanceof Creeper)) {
            return; // El causante no fue un Creeper
        }

        // Sabemos que el causante es un Creeper
        Creeper attacker = (Creeper) event.getDamager();

        // --- Comprobación 2: ¿El Creeper que explotó NO estaba cargado? ---
        if (attacker.isPowered()) {
            // Si ESTABA cargado, no hacemos nada. Permitimos el daño para que
            // funcione la mecánica de cabezas y el comportamiento normal de los cargados.
            return;
        }

        // --- Si llegamos aquí, sabemos que un Creeper NORMAL explotó ---

        // --- Comprobación 3: ¿La víctima es uno de los mobs a proteger? ---
        Entity victim = event.getEntity();
        EntityType victimType = victim.getType(); // Obtenemos el tipo de la entidad dañada

        boolean shouldProtect = false;
        if (victimType == EntityType.CREEPER ||
                victimType == EntityType.ZOMBIE ||
                victimType == EntityType.SKELETON ||
                victimType == EntityType.SPIDER ||
                victimType == EntityType.HUSK ||        // Variante de Zombie
                victimType == EntityType.STRAY ||        // Variante de Esqueleto
                victimType == EntityType.CAVE_SPIDER ||  // Variante de Araña
                victimType == EntityType.ZOMBIE_VILLAGER // Otra variante de Zombie
        )
        {
            shouldProtect = true;
        }

        if (shouldProtect) {
            // Opcional: Evitar cancelar el daño si es el mismo creeper (raro, pero por si acaso)
            if (victim.getUniqueId().equals(attacker.getUniqueId())) {
                return;
            }

            // ¡Sí! Cancelar el daño de la explosión del Creeper NORMAL a este mob.
            event.setCancelled(true);
            // plugin.getLogger().info("Daño de Creeper NORMAL a " + victimType.name() + " cancelado."); // Debug
        }
    }
}