package com.pvpbot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ThrownSplashPotion;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BotManager {
    private static final List<Bot> BOTS = new ArrayList<>();

    private static final class Bot {
        final Zombie mob;
        final BotMode mode;
        final int level;
        int cooldown = 20;
        int pots = 16, gapples = 6, totems = 3;
        boolean leaping = false;

        Bot(Zombie mob, BotMode mode, int level) {
            this.mob = mob;
            this.mode = mode;
            this.level = level;
        }
    }

    // ---------- spawning ----------

    public static void spawn(ServerPlayer p, BotMode mode, int level) {
        ServerLevel lvl = (ServerLevel) p.level();
        Zombie z = new Zombie(lvl);
        Vec3 look = p.getLookAngle();
        Vec3 pos = p.position().add(look.x * 6, 0, look.z * 6);
        z.setPos(pos.x, pos.y, pos.z);

        z.setPersistenceRequired();
        z.setCanPickUpLoot(false);
        z.setCustomName(Component.literal("Bot [" + mode + " L" + level + "]"));
        z.setCustomNameVisible(true);

        set(z, Attributes.MAX_HEALTH, 20 + level * 2);
        set(z, Attributes.MOVEMENT_SPEED, Math.min(0.5, 0.20 + 0.03 * level));
        set(z, Attributes.ATTACK_DAMAGE, 3 + level * 0.7);
        set(z, Attributes.FOLLOW_RANGE, 48);
        set(z, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
        z.setHealth(z.getMaxHealth());

        equip(z, mode);
        for (EquipmentSlot s : EquipmentSlot.values()) z.setDropChance(s, 0f);

        lvl.addFreshEntity(z);
        BOTS.add(new Bot(z, mode, level));
    }

    private static void set(Zombie z, Holder<Attribute> attr, double v) {
        AttributeInstance a = z.getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }

    // ---------- kits ----------

    private static void equip(Zombie z, BotMode m) {
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        switch (m) {
            case SWORD, POT -> z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            case MACE -> z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
            case CART -> z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            case SMP -> {
                z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }
    }

    // ---------- behaviour ----------

    public static int clear() {
        int n = BOTS.size();
        for (Bot b : BOTS) b.mob.discard();
        BOTS.clear();
        return n;
    }

    public static void tick(MinecraftServer server) {
        BOTS.removeIf(b -> !b.mob.isAlive());
        for (Bot b : BOTS) think(b);
    }

    private static void think(Bot b) {
        Zombie mob = b.mob;
        ServerLevel lvl = (ServerLevel) mob.level();
        LivingEntity t = lvl.getNearestPlayer(mob, 48);
        if (t == null) return;

        mob.setTarget(t);
        if (b.cooldown > 0) b.cooldown--;
        double d = mob.distanceTo(t);
        int cd = Math.max(8, 70 - b.level * 6); // higher level = faster reactions

        switch (b.mode) {
            case SWORD -> {
                if (d < 4 && mob.onGround() && b.cooldown == 0) { // sprint-jump crits
                    mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.42, 0));
                    b.cooldown = cd;
                }
            }
            case SMP -> {
                if (d < 4 && mob.onGround() && b.cooldown == 0) {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.42, 0));
                    b.cooldown = cd;
                }
                if (mob.getHealth() < 8 && b.gapples > 0 && mob.tickCount % 40 == 0) {
                    mob.heal(6); b.gapples--;
                    fx(lvl, mob, ParticleTypes.HAPPY_VILLAGER);
                }
                if (mob.getHealth() < 4 && b.totems > 0) { // totem pop
                    mob.setHealth(Math.min(mob.getMaxHealth(), 10)); b.totems--;
                    fx(lvl, mob, ParticleTypes.TOTEM_OF_UNDYING);
                }
            }
            case POT -> {
                if (mob.getHealth() < mob.getMaxHealth() * 0.5f && b.pots > 0 && mob.tickCount % 20 == 0) {
                    mob.heal(8); b.pots--; // instant health pot
                    fx(lvl, mob, ParticleTypes.HEART);
                } else if (d > 3 && d < 14 && b.cooldown == 0 && b.pots > 0) {
                    throwHarm(lvl, mob, t, d);
                    b.pots--; b.cooldown = cd * 2;
                }
                if (d < 4 && mob.onGround() && mob.tickCount % 25 == 0) {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.42, 0));
                }
            }
            case MACE -> {
                if (!b.leaping && mob.onGround() && d > 3 && d < 10 && b.cooldown == 0) {
                    Vec3 dir = t.position().subtract(mob.position()).normalize();
                    mob.setDeltaMovement(dir.x * 0.6, 1.0, dir.z * 0.6);
                    mob.hurtMarked = true;
                    mob.fallDistance = 0;
                    b.leaping = true;
                    b.cooldown = cd * 2;
                } else if (b.leaping) {
                    if (mob.getDeltaMovement().y < 0 && d < 2.8) { // smash
                        float dmg = (float) (6 + 2.5 * Math.max(0, mob.fallDistance));
                        t.hurtServer(lvl, lvl.damageSources().mobAttack(mob), dmg);
                        mob.fallDistance = 0;
                        b.leaping = false;
                    } else if (mob.onGround()) {
                        b.leaping = false;
                    }
                }
            }
            case CART -> {
                if (d < 10 && b.cooldown == 0) {
                    MinecartTNT cart = new MinecartTNT(EntityType.TNT_MINECART, lvl);
                    cart.setPos(t.getX(), t.getY(), t.getZ());
                    cart.primeFuse();
                    lvl.addFreshEntity(cart);
                    b.cooldown = cd * 3;
                }
            }
        }
    }

    private static void throwHarm(ServerLevel lvl, Zombie mob, LivingEntity t, double d) {
        ItemStack s = new ItemStack(Items.SPLASH_POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));
        ThrownSplashPotion pot = new ThrownSplashPotion(lvl, mob, s);
        Vec3 dir = t.position().add(0, 0.5, 0).subtract(mob.getEyePosition());
        pot.shoot(dir.x, dir.y + d * 0.2, dir.z, 0.8f, 1.0f);
        lvl.addFreshEntity(pot);
    }

    private static void fx(ServerLevel lvl, Zombie mob, net.minecraft.core.particles.ParticleOptions p) {
        lvl.sendParticles(p, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
    }
}
