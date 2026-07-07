package com.krystalfox.aimobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Witch;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WitchAIUpdater
extends BukkitRunnable {
    private final AdvancedMobAI plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Random random = new Random();

    public WitchAIUpdater(AdvancedMobAI plugin) {
        this.plugin = plugin;
    }

    public void run() {
        for (World world : this.plugin.getServer().getWorlds()) {
            for (Witch witch : world.getEntitiesByClass(Witch.class)) {
                LivingEntity ally;
                long cooldownMillis;
                long lastAction;
                if (!witch.isValid() || witch.isDead()) continue;
                UUID witchId = witch.getUniqueId();
                long now = System.currentTimeMillis();
                if (this.cooldowns.containsKey(witchId) && now - (lastAction = this.cooldowns.get(witchId).longValue()) < (cooldownMillis = (long)this.plugin.getWitchCooldownSeconds() * 1000L)) continue;
                boolean playerInRange = false;
                boolean playerTooClose = false;
                for (Player player : world.getPlayers()) {
                    double distance = witch.getLocation().distance(player.getLocation());
                    if (distance <= this.plugin.getWitchMaxPlayerDistance()) {
                        playerInRange = true;
                    }
                    if (!(distance <= this.plugin.getWitchMinPlayerDistance())) continue;
                    playerTooClose = true;
                    break;
                }
                if (!playerInRange || playerTooClose || (ally = this.findAllyToBuff(witch)) == null) continue;
                this.throwBeneficialPotion(witch, ally);
                this.cooldowns.put(witchId, now);
            }
        }
    }

    private LivingEntity findAllyToBuff(Witch witch) {
        List<Entity> nearbyEntities = witch.getNearbyEntities(this.plugin.getWitchAllySearchRadius(), this.plugin.getWitchAllySearchRadius(), this.plugin.getWitchAllySearchRadius());
        List<String> targetMobs = this.plugin.getWitchTargetMobs();
        ArrayList<LivingEntity> validAllies = new ArrayList<LivingEntity>();
        for (Entity entity : nearbyEntities) {
            LivingEntity livingEntity;
            String entityType;
            if (!(entity instanceof LivingEntity) || entity instanceof Player || !targetMobs.contains(entityType = entity.getType().name()) || (livingEntity = (LivingEntity)entity) instanceof Witch) continue;
            validAllies.add(livingEntity);
        }
        if (validAllies.isEmpty()) {
            return null;
        }
        return (LivingEntity)validAllies.get(this.random.nextInt(validAllies.size()));
    }

    private void throwBeneficialPotion(Witch witch, LivingEntity target) {
        Map<String, Object> effectConfig = this.selectRandomEffect();
        if (effectConfig == null) {
            return;
        }
        String effectType = (String)effectConfig.get("type");
        int amplifier = (Integer)effectConfig.get("amplifier");
        int duration = (Integer)effectConfig.get("duration_seconds") * 20;
        PotionEffectType type = PotionEffectType.getByName((String)effectType);
        if (type == null) {
            return;
        }
        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta)potionItem.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, duration, amplifier), true);
        meta.setColor(type.getColor());
        potionItem.setItemMeta((ItemMeta)meta);
        ThrownPotion potion = (ThrownPotion)witch.launchProjectile(ThrownPotion.class);
        potion.setItem(potionItem);
        Location witchLoc = witch.getEyeLocation();
        Location targetLoc = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
        double distance = witchLoc.distance(targetLoc);
        Vector direction = targetLoc.toVector().subtract(witchLoc.toVector());
        direction.normalize();
        double baseSpeed = 0.75;
        double speedMultiplier = Math.min(2.0, Math.max(0.5, distance / 8.0));
        double finalSpeed = baseSpeed * speedMultiplier;
        double arcHeight = Math.min(0.4, distance * 0.05);
        direction.multiply(finalSpeed);
        direction.setY(direction.getY() + arcHeight);
        potion.setVelocity(direction);
    }

    private Map<String, Object> selectRandomEffect() {
        List<Map<?, ?>> effects = this.plugin.getWitchEffectsConfig();
        if (effects == null || effects.isEmpty()) {
            return null;
        }
        double totalChance = 0.0;
        for (Map<?, ?> effect : effects) {
            Object chanceObj = effect.get("chance");
            if (!(chanceObj instanceof Number)) continue;
            totalChance += ((Number)chanceObj).doubleValue();
        }
        if (totalChance <= 0.0) {
            return null;
        }
        double randomValue = this.random.nextDouble() * totalChance;
        double cumulative = 0.0;
        for (Map<?, ?> effect : effects) {
            Object chanceObj = effect.get("chance");
            if (!(chanceObj instanceof Number) || !(randomValue <= (cumulative += ((Number)chanceObj).doubleValue()))) continue;
            HashMap<String, Object> result = new HashMap<String, Object>();
            result.put("type", effect.get("type"));
            result.put("amplifier", effect.get("amplifier"));
            result.put("duration_seconds", effect.get("duration_seconds"));
            return result;
        }
        return null;
    }
}

