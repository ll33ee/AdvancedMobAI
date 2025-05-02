package com.krystalfox.aimobs; // Asegúrate que el paquete sea correcto

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AdvancedMobAI extends JavaPlugin {

    private static AdvancedMobAI instance;

    // --- Tareas y Handles ---
    private CreeperAIUpdater creeperAIUpdaterTask;
    private BukkitTask creeperTaskHandle;
    private EndermanAIUpdater endermanAIUpdaterTask; // Para Enderman
    private BukkitTask endermanTaskHandle;      // Para Enderman

    // --- Listas de Configuración Cacheadas ---
    private Set<Material> creeperResistantMaterials = new HashSet<>();
    private Set<Material> endermanResistantMaterials = new HashSet<>(); // Lista separada para Enderman
    // La lista 'pickupable' del Enderman la maneja su propia tarea al iniciar

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("------------------------------------------");
        getLogger().info("   AdvancedMobAI v" + getDescription().getVersion() + " Habilitado");
        getLogger().info("------------------------------------------");

        saveDefaultConfig(); // Guarda config.yml si no existe
        loadConfigValues();  // Carga toda la configuración inicial

        // --- Registrar Listeners ---
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getLogger().info("Listener de daño registrado.");

        // --- Iniciar Tareas (si están habilitadas) ---
        startCreeperTask(); // Intenta iniciar la tarea de Creeper
        startEndermanTask(); // Intenta iniciar la tarea de Enderman

        getLogger().info("AdvancedMobAI cargado completamente.");
    }

    @Override
    public void onDisable() {
        getLogger().info("------------------------------------------");
        getLogger().info("   AdvancedMobAI Deshabilitado");
        getLogger().info("------------------------------------------");
        // Detener tareas activas
        if (creeperTaskHandle != null && !creeperTaskHandle.isCancelled()) {
            creeperTaskHandle.cancel();
        }
        if (endermanTaskHandle != null && !endermanTaskHandle.isCancelled()) {
            endermanTaskHandle.cancel();
        }
        getLogger().info("Tareas detenidas.");
    }

    // --- Gestión de Configuración ---
    public void loadConfigValues() {
        reloadConfig(); // Asegura leer la última versión del archivo config.yml
        getLogger().info("Recargando configuración de AdvancedMobAI...");

        // --- Cargar Config Creeper ---
        getLogger().info("Cargando configuración de Creeper...");
        creeperResistantMaterials.clear();
        List<String> creeperResistNames = getConfig().getStringList("creeper.wall_breaching.resistant_blocks");
        if (creeperResistNames != null) {
            for (String name : creeperResistNames) {
                try {
                    creeperResistantMaterials.add(Material.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("[Config Creeper] Material resistente inválido: " + name);
                }
            }
        }
        addAlwaysResistant(creeperResistantMaterials); // Añadir Bedrock, etc.
        getLogger().info("[Config Creeper] Bloques resistentes cargados: " + creeperResistantMaterials.size());
        logEnabledStatus("Creeper Wall Breaching", getConfig().getBoolean("creeper.wall_breaching.enabled", false));
        logEnabledStatus("Creeper Proactive Targeting", getConfig().getBoolean("creeper.proactive_targeting.enabled", false));
        logEnabledStatus("Creeper Pillar Explosion", getConfig().getBoolean("creeper.pillar_explosion.enabled", false));


        // --- Cargar Config Enderman ---
        getLogger().info("Cargando configuración de Enderman...");
        endermanResistantMaterials.clear();
        List<String> endermanResistNames = getConfig().getStringList("enderman.dismantler_ai.resistant_blocks");
        if (endermanResistNames != null) {
            for (String name : endermanResistNames) {
                try {
                    endermanResistantMaterials.add(Material.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("[Config Enderman] Material resistente inválido: " + name);
                }
            }
        }
        addAlwaysResistant(endermanResistantMaterials); // Añadir Bedrock, etc.
        getLogger().info("[Config Enderman] Bloques resistentes cargados: " + endermanResistantMaterials.size());
        // La lista pickupable la carga la tarea del Enderman
        logEnabledStatus("Enderman Dismantler AI", getConfig().getBoolean("enderman.dismantler_ai.enabled", false));


        // Cargar otras configs aquí en el futuro...

        getLogger().info("Configuración recargada completamente.");
    }

    // Helper para loguear estado de activación
    private void logEnabledStatus(String featureName, boolean enabled) {
        getLogger().info(featureName + ": " + (enabled ? ChatColor.GREEN + "Activado" : ChatColor.RED + "Desactivado"));
    }


    // Helper para añadir bloques siempre resistentes
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
        // Añadir otros si es necesario
    }

    // --- Gestión de Tareas ---
    public void startCreeperTask() {
        if (creeperTaskHandle != null && !creeperTaskHandle.isCancelled()) {
            creeperTaskHandle.cancel();
        }
        // Iniciar solo si alguna de sus funciones está habilitada
        boolean wbEnabled = getConfig().getBoolean("creeper.wall_breaching.enabled", false);
        boolean ptEnabled = getConfig().getBoolean("creeper.proactive_targeting.enabled", false);
        boolean peEnabled = getConfig().getBoolean("creeper.pillar_explosion.enabled", false);

        if (wbEnabled || ptEnabled || peEnabled) {
            // Usa el intervalo de wall_breaching como general, o define uno propio si prefieres
            long interval = getConfig().getLong("creeper.wall_breaching.check_interval_ticks", 20L);
            if (interval <= 0) interval = 20L;

            try {
                creeperAIUpdaterTask = new CreeperAIUpdater(this); // Pasa la instancia del plugin
                creeperTaskHandle = creeperAIUpdaterTask.runTaskTimer(this, 0L, interval);
                getLogger().info("Tarea de IA de Creepers (re)iniciada (Intervalo: " + interval + " ticks).");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error al iniciar la tarea de IA de Creepers:", e);
            }
        } else {
            getLogger().info("Tarea de IA de Creepers no iniciada (deshabilitada en config).");
        }
    }

    public void startEndermanTask() { // Nueva función
        if (endermanTaskHandle != null && !endermanTaskHandle.isCancelled()) {
            endermanTaskHandle.cancel();
        }
        // Iniciar solo si está habilitado
        if (getConfig().getBoolean("enderman.dismantler_ai.enabled", true)) { // Habilitado por defecto si no existe
            long interval = getConfig().getLong("enderman.dismantler_ai.check_interval_ticks", 30L);
            if (interval <= 0) interval = 30L;

            try {
                endermanAIUpdaterTask = new EndermanAIUpdater(this); // Pasa la instancia del plugin
                endermanTaskHandle = endermanAIUpdaterTask.runTaskTimer(this, 0L, interval);
                getLogger().info("Tarea de IA de Enderman (Dismantler) (re)iniciada (Intervalo: " + interval + " ticks).");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error al iniciar la tarea de IA de Enderman:", e);
            }
        } else {
            getLogger().info("Tarea de IA de Enderman (Dismantler) no iniciada (deshabilitada en config).");
        }
    }

    // --- Acceso Singleton ---
    public static AdvancedMobAI getInstance() {
        return instance;
    }

    // --- Verificación de Bloques ---

    /**
     * Verifica si un material es resistente a la rotura por explosión de Creeper.
     */
    public boolean isCreeperResistantBlock(Material material) {
        // Usa la lista específica del Creeper (cargada en loadConfigValues)
        return creeperResistantMaterials.contains(material);
    }

    /**
     * Verifica si un material es resistente a ser recogido por un Enderman.
     */
    public boolean isEndermanResistantBlock(Material material) {
        // Usa la lista específica del Enderman (cargada en loadConfigValues)
        return endermanResistantMaterials.contains(material);
    }

    // --- Manejador de Comandos ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("am") || label.equalsIgnoreCase("advancedmobai")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                // Permiso
                if (!sender.hasPermission("advancedmobai.reload")) {
                    sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
                    return true;
                }
                // Recarga y reinicio de tareas
                try {
                    loadConfigValues();
                    startCreeperTask();
                    startEndermanTask();
                    sender.sendMessage(ChatColor.GREEN + "¡Configuración de AdvancedMobAI recargada correctamente!");
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error al recargar la configuración. Revisa la consola.");
                    getLogger().log(Level.SEVERE, "Error durante la recarga por comando:", e);
                }
                return true;
            } else {
                // Uso incorrecto
                sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " reload");
                return true;
            }
        }
        // No es nuestro comando
        return false;
    }
}