package com.krystalfox.aimobs; // Asegúrate que el paquete sea correcto

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.HashMap; // Usar import específico
import java.util.Map; // Usar import específico
import java.util.UUID; // Para el cooldown map

// Asumiendo que la clase principal se llama AdvancedMobAI
public class CreeperAIUpdater extends BukkitRunnable {

    private final AdvancedMobAI plugin;

    // --- Configuración Wall Breaching ---
    private final boolean wallBreachingEnabled;
    private final double maxCheckDistance; // Distancia max para buscar pared
    private final double proximityThresholdSquared; // Distancia^2 para estar 'pegado' a la pared

    // --- Configuración Proactive Targeting ---
    private final boolean proactiveTargetingEnabled;
    private final double proactiveTargetingRange;
    private final double proactiveTargetingRangeSq;

    // --- Configuración Pillar Explosion ---
    private final boolean pillarExplosionEnabled;
    private final double pillarMaxHorizDistSq;
    private final double pillarMinVertDist;
    private final double pillarMaxVertDist;

    // --- Cooldown (Compartido) ---
    private final Map<UUID, Long> creeperCooldown = new HashMap<>();
    private final long COOLDOWN_TIME_MS;

    public CreeperAIUpdater(AdvancedMobAI plugin) {
        this.plugin = plugin;
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig(); // Obtener config una vez

        // --- Cargar Config Wall Breaching ---
        this.wallBreachingEnabled = config.getBoolean("creeper.wall_breaching.enabled", true);
        this.maxCheckDistance = config.getDouble("creeper.wall_breaching.max_check_distance", 5.0);
        double proximityThreshold = config.getDouble("creeper.wall_breaching.proximity_threshold", 1.8);
        this.proximityThresholdSquared = proximityThreshold * proximityThreshold;

        // --- Cargar Config Proactive Targeting ---
        this.proactiveTargetingEnabled = config.getBoolean("creeper.proactive_targeting.enabled", true);
        this.proactiveTargetingRange = config.getDouble("creeper.proactive_targeting.range", 8.0);
        this.proactiveTargetingRangeSq = this.proactiveTargetingRange * this.proactiveTargetingRange;

        // --- Cargar Config Pillar Explosion ---
        this.pillarExplosionEnabled = config.getBoolean("creeper.pillar_explosion.enabled", true);
        double pillarMaxHorizDist = config.getDouble("creeper.pillar_explosion.max_horizontal_distance", 3.5);
        this.pillarMaxHorizDistSq = pillarMaxHorizDist * pillarMaxHorizDist;
        this.pillarMinVertDist = config.getDouble("creeper.pillar_explosion.min_vertical_distance", 2.0);
        this.pillarMaxVertDist = config.getDouble("creeper.pillar_explosion.max_vertical_distance", 5.0);

        // --- Cargar Cooldown (común) ---
        // Usa el valor de wall_breaching como cooldown general para acciones especiales
        this.COOLDOWN_TIME_MS = config.getLong("creeper.wall_breaching.cooldown_seconds", 5L) * 1000L;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (World world : plugin.getServer().getWorlds()) {
            // Considerar optimizar si hay muchísimos creepers, ej: solo chequear en chunks activos
            for (Creeper creeper : world.getEntitiesByClass(Creeper.class)) {

                // --- Lógica de Objetivo Proactivo ---
                if (proactiveTargetingEnabled && creeper.getTarget() == null) {
                    Player closestPlayer = null;
                    double minDistanceSqFound = proactiveTargetingRangeSq;
                    for (Player p : world.getPlayers()) { // Podría optimizarse buscando solo entidades cercanas
                        if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                        // Ignorar jugadores invisibles para el target proactivo? Podría ser una opción
                        // if (p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) continue;

                        double distSq = creeper.getLocation().distanceSquared(p.getLocation());
                        if (distSq <= minDistanceSqFound) {
                            // Chequeo básico de visión (opcional, pero evita target a través de montañas)
                            // if (creeper.hasLineOfSight(p)) { // <-- Descomentar si quieres este chequeo extra
                            minDistanceSqFound = distSq;
                            closestPlayer = p;
                            // }
                        }
                    }
                    if (closestPlayer != null) {
                        creeper.setTarget(closestPlayer);
                        // plugin.getLogger().info("Creeper " + creeper.getUniqueId() + " fijó objetivo proactivo en " + closestPlayer.getName());
                    }
                } // Fin Proactive Targeting

                // --- Comprobaciones Generales ---
                if (creeper.isIgnited()) { continue; } // Ya está encendido

                LivingEntity target = creeper.getTarget();
                if (target == null || !(target instanceof Player) || target.isDead()) { continue; } // Usar instanceof es más seguro
                Player player = (Player) target;
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) { continue; }

                // --- Cooldown General para Acciones Especiales ---
                UUID creeperId = creeper.getUniqueId();
                if (creeperCooldown.containsKey(creeperId) && (now - creeperCooldown.get(creeperId)) < COOLDOWN_TIME_MS) {
                    continue; // En cooldown
                }

                boolean specialActionTaken = false; // Flag para evitar múltiples acciones

                // --- Lógica de Explosión por Pilar ---
                if (pillarExplosionEnabled) {
                    Location pLoc = player.getLocation();
                    Location cLoc = creeper.getLocation();
                    double dx = pLoc.getX() - cLoc.getX();
                    double dy = pLoc.getY() - cLoc.getY();
                    double dz = pLoc.getZ() - cLoc.getZ();
                    double horizDistSq = dx*dx + dz*dz;

                    if (horizDistSq <= pillarMaxHorizDistSq && dy >= pillarMinVertDist && dy <= pillarMaxVertDist) {
                        // Comprobación simplificada de inalcanzable (jugador significativamente más alto)
                        if (dy > 1.6) {
                            plugin.getLogger().info("Creeper " + creeperId + " iniciando ignición por jugador en pilar!");
                            creeper.ignite();
                            creeperCooldown.put(creeperId, now);
                            specialActionTaken = true;
                        }
                        // Aquí iría el chequeo de pathfinding si prefieres más precisión (ver código anterior)
                    }
                } // Fin Pillar Explosion

                // --- Lógica de Romper Paredes ---
                // Solo si no explotó por pilar Y la función está habilitada
                if (!specialActionTaken && wallBreachingEnabled) {
                    Location pLoc = player.getLocation(); // Reusar si es posible
                    Location cLoc = creeper.getLocation();
                    double distanceToPlayerSquared = cLoc.distanceSquared(pLoc);

                    // Dentro de rango Y SIN línea de visión?
                    if (distanceToPlayerSquared <= maxCheckDistance * maxCheckDistance && !creeper.hasLineOfSight(player)) {
                        Location wallLocationDetected = checkWall(creeper, player);
                        if (wallLocationDetected != null) {
                            // ¿Está pegado a la pared detectada?
                            double distanceToWallSquared = cLoc.distanceSquared(wallLocationDetected.clone().add(0.5, 0.0, 0.5));
                            if (distanceToWallSquared <= proximityThresholdSquared) {
                                plugin.getLogger().info("Creeper " + creeperId + " iniciando ignición por pared!");
                                creeper.ignite();
                                creeperCooldown.put(creeperId, now);
                                // No necesitamos marcar specialActionTaken aquí si es la última posible
                            }
                        }
                    }
                } // Fin Wall Breaching

            } // Fin bucle creepers
        } // Fin bucle mundos

        // Limpiar cooldowns de entidades que ya no existen
        creeperCooldown.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null || plugin.getServer().getEntity(entry.getKey()).isDead());
    } // Fin run()


    /**
     * Comprueba si hay una pared delgada (1-2 bloques) y no resistente entre el creeper y el jugador.
     * Devuelve la ubicación base (Y entero) del PRIMER bloque de la pared si es adecuada, o null si no.
     */
    private Location checkWall(Creeper creeper, Player player) {
        Location creeperLoc = creeper.getEyeLocation(); // Punto de inicio: ojos del creeper
        Location playerLoc = player.getEyeLocation();   // Punto de destino: ojos del jugador
        Vector direction = playerLoc.toVector().subtract(creeperLoc.toVector()).normalize(); // Dirección normalizada

        // Iterar en pasos desde justo delante del creeper hasta la distancia máxima
        for (double d = 1.0; d <= maxCheckDistance; d += 0.5) { // Pasos de medio bloque
            Location checkLoc = creeperLoc.clone().add(direction.clone().multiply(d)); // Calcular punto de chequeo
            Block checkBlock = checkLoc.getBlock(); // Obtener el bloque en ese punto
            Material blockType = checkBlock.getType(); // Obtener el tipo de material

            // Ignorar aire y bloques no sólidos/no oclusivos en la trayectoria
            if (!blockType.isSolid() || !blockType.isOccluding()) {
                // Optimización: Si estamos muy cerca del jugador y encontramos aire, ya no hay pared relevante delante
                if (checkLoc.distanceSquared(playerLoc) < 1.5 * 1.5) {
                    // plugin.getLogger().finest("checkWall: Cerca del jugador, no hay pared."); // Debug fino
                    break; // Salir del bucle, no hay pared
                }
                continue; // Continuar al siguiente paso si encontramos aire/bloque no sólido lejos
            }

            // Encontramos un bloque sólido. ¿Es resistente?
            if (plugin.isCreeperResistantBlock(blockType)) {
                // plugin.getLogger().finest("checkWall: Pared resistente encontrada: " + blockType); // Debug fino
                return null; // Es resistente, no romper, detener búsqueda en esta dirección
            }

            // No es resistente. Comprobar grosor mirando 1 bloque más allá en la misma dirección.
            Location nextLoc = checkLoc.clone().add(direction.clone().multiply(1.0));
            Block nextBlock = nextLoc.getBlock();
            Material nextBlockType = nextBlock.getType();

            // Si el siguiente bloque NO es sólido (es aire, agua, etc.)...
            if (!nextBlockType.isSolid() || !nextBlockType.isOccluding()) {
                // ¡PARED DE 1 BLOQUE DE GROSOR ENCONTRADA!
                // plugin.getLogger().finest("checkWall: Pared de 1 bloque encontrada: " + blockType); // Debug fino
                return checkBlock.getLocation().getBlock().getLocation(); // Devolver ubicación (base Y entero) del bloque a romper
            } else {
                // El siguiente bloque también es sólido. ¿Es resistente?
                if (plugin.isCreeperResistantBlock(nextBlockType)) {
                    // plugin.getLogger().finest("checkWall: Segunda capa resistente: " + nextBlockType); // Debug fino
                    return null; // La segunda capa es resistente, no romper, detener búsqueda
                }

                // La segunda capa es sólida pero no resistente. Comprobar si hay espacio detrás (tercer bloque).
                Location thirdLoc = nextLoc.clone().add(direction.clone().multiply(1.0));
                Block thirdBlock = thirdLoc.getBlock();

                // Si el tercer bloque NO es sólido O estamos ya muy cerca del jugador...
                if (!thirdBlock.isSolid() || thirdLoc.distanceSquared(playerLoc) < 4.0) { // 4.0 es 2*2 (cerca del jugador)
                    // ¡PARED DE 2 BLOQUES DE GROSOR ENCONTRADA!
                    // plugin.getLogger().finest("checkWall: Pared de 2 bloques encontrada: " + blockType + ", " + nextBlockType); // Debug fino
                    return checkBlock.getLocation().getBlock().getLocation(); // Devolver ubicación del primer bloque
                } else {
                    // El tercer bloque también es sólido. Pared demasiado gruesa.
                    // plugin.getLogger().finest("checkWall: Pared demasiado gruesa (3+ bloques)"); // Debug fino
                    return null; // Demasiado gruesa, no romper, detener búsqueda
                }
            }
        } // Fin del bucle for

        // plugin.getLogger().finest("checkWall: No se encontró pared adecuada en el rango."); // Debug fino
        return null; // No se encontró pared adecuada en toda la distancia revisada
    }

}