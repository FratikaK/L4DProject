package com.github.kamunyan.leftcrafterdead.listener

import com.github.kamunyan.leftcrafterdead.LeftCrafterDead
import com.github.kamunyan.leftcrafterdead.MatchManager
import com.github.kamunyan.leftcrafterdead.enemy.specials.Smoker
import com.github.kamunyan.leftcrafterdead.skill.SpecialSkillType
import com.github.kamunyan.leftcrafterdead.subgadget.TripMine
import com.github.kamunyan.leftcrafterdead.task.BuffRunnable
import com.github.kamunyan.leftcrafterdead.util.Buff
import com.github.kamunyan.leftcrafterdead.util.MetadataUtil
import com.github.kamunyan.leftcrafterdead.weapons.GunCategory
import com.github.kamunyan.leftcrafterdead.weapons.WeaponUtil
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent
import com.shampaggon.crackshot.events.WeaponExplodeEvent
import net.kyori.adventure.text.Component
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import xyz.xenondevs.particle.ParticleBuilder
import xyz.xenondevs.particle.ParticleEffect
import kotlin.random.Random
import kotlin.random.nextInt

class DamageControlListener : Listener {
    private val plugin = LeftCrafterDead.instance
    private val manager = MatchManager

    @EventHandler
    fun onCrackShotExplosion(e: WeaponExplodeEvent) {
        val location = e.location.clone()
        val lcdPlayer = manager.getLCDPlayer(e.player)

        if (!WeaponUtil.isLCDGrenade(lcdPlayer, e.weaponTitle)) return

        var radius = plugin.crackShot.handle.getInt("${e.weaponTitle}.Explosions.Explosion_Radius")
        if (e.weaponTitle == "TRIP MINE") {
            radius = ((radius * lcdPlayer.statusData.tripMineRangeMultiplier).toInt())
        }
        val weaponDamage = plugin.crackShot.handle.getInt("${e.weaponTitle}.Shooting.Projectile_Damage")
        val distanceDecay = weaponDamage / radius
        val entities = location.getNearbyLivingEntities(radius.toDouble())

        //グレネードエフェクト
        lcdPlayer.grenade.explosionEffects(location)

        //ダメージを与える処理
        entities.forEach { livingEntity ->

            lcdPlayer.grenade.specialEffects(e.player, livingEntity)

            if (manager.enemyHashMap.containsKey(livingEntity.uniqueId)) {
                val enemy = manager.enemyHashMap[livingEntity.uniqueId]!!
                if (livingEntity is HumanEntity || livingEntity.isDead) {
                    return@forEach
                }
                val distance = (livingEntity.location.distance(location)).toInt()
                if (lcdPlayer.statusData.specialSkillTypes.contains(SpecialSkillType.FIRE_TRAP)
                    && e.weaponTitle == "TRIP MINE"
                ) {
                    livingEntity.fireTicks = 200
                }
                var lastDamage = weaponDamage - (distance * distanceDecay) - enemy.explosionResistance
                lastDamage *= lcdPlayer.statusData.explosionDamageMultiplier
                if (lastDamage <= 0) {
                    lastDamage = 0.0
                }
                livingEntity.damage(lastDamage, e.player)
            }
        }
    }

    @EventHandler
    fun onWeaponDamage(e: WeaponDamageEntityEvent) {
        if (e.victim.type == EntityType.PLAYER) {
            e.isCancelled = true
            return
        }
        val lcdPlayer = manager.getLCDPlayer(e.player)
        if (WeaponUtil.isLCDGrenade(lcdPlayer, e.weaponTitle)) {
            e.damage = 0.0
            return
        }
        val lcdWeapon = WeaponUtil.getLCDWeapon(lcdPlayer, e.weaponTitle) ?: return
        if (e.victim.type == EntityType.SNOWMAN || e.victim.type == EntityType.VILLAGER) {
            e.isCancelled = true
            return
        }
        if (!manager.enemyHashMap.containsKey(e.victim.uniqueId)) {
            return
        }
        val enemy = manager.enemyHashMap[e.victim.uniqueId]!!
        val data = lcdPlayer.statusData

        if (e.damager.hasMetadata(MetadataUtil.SENTRY_GUN_BALL)) {
            e.damage *= data.sentryGunPowerMultiplier
            return
        }

        e.damage = e.damage * data.weaponDamageMultiplier
        val category = WeaponUtil.getGunCategory(e.weaponTitle)
        if (category == GunCategory.SHOTGUN) {
            e.damage *= data.shotgunDamageMultiplier
            if (data.specialSkillTypes.contains(SpecialSkillType.DIE_HARD)){
                if (Random.nextInt(100) <= 10){
                    e.player.absorptionAmount++
                }
            }
        }
        if (category == GunCategory.SIDE_ARM) {
            e.damage *= data.secondaryDamageMultiplier
        }
        if (data.specialSkillTypes.contains(SpecialSkillType.UNDERDOG)) {
            val entityList = e.player.location.getNearbyLivingEntities(4.0)
            if (entityList.size >= 4) {
                e.damage *= 1.1
                if (category == GunCategory.SHOTGUN && data.specialSkillTypes.contains(SpecialSkillType.CLOSE_BY)) {
                    e.damage *= 1.2
                }
            }
        }
        if (data.specialSkillTypes.contains(SpecialSkillType.BERSERKER) && category == GunCategory.SIDE_ARM) {
            val health = data.healthScaleAmount
            if (health <= 10) {
                var increase = (10 - health).toInt()
                if (increase > 8) {
                    increase = 8
                }
                val addDamage = 1.1 + (increase / 10)
                e.damage *= addDamage
            }
        }

        if (lcdPlayer.buff.contains(Buff.COMBAT_DOCTOR)){
            e.damage *= 1.2
        }

        //ヘッドショット
        if (e.isHeadshot || (!e.isHeadshot && data.specialSkillTypes.contains(SpecialSkillType.BODY_EXPERTISE))) {
            e.damage *= 1.5
        }

        //クリティカル
        var addCritical = 0
        if (data.specialSkillTypes.contains(SpecialSkillType.LOW_BLOW) && category == GunCategory.SUB_MACHINE_GUN) {
            addCritical += 30
        }
        if (lcdPlayer.buff.contains(Buff.UNSEEN_STRIKE)){
            addCritical += 30
        }
        println("最終的なクリティカル率 ${data.criticalMultiplier + addCritical}")
        if (data.criticalMultiplier + addCritical != 0) {
            if (data.criticalMultiplier + addCritical >= Random.nextInt(100)) {
                e.damage *= data.criticalDamageMultiplier
                if (data.specialSkillTypes.contains(SpecialSkillType.SILENT_KILLER)) {
                    e.damage *= data.suppressorCriticalDamageMultiplier
                }
                ParticleBuilder(ParticleEffect.CRIT_MAGIC, e.victim.location)
                    .setOffset(1.0f, 2.0f, 1.0f)
                    .setAmount(100)
                    .display()
            }
        }

        if (!e.isHeadshot) {
            if (e.damage <= enemy.nonHeadShotDamageResistance) {
                e.damage = 1.0
            }
        }
        if (e.isHeadshot && data.specialSkillTypes.contains(SpecialSkillType.BULLSEYE)) {
            if (Math.random() <= 0.1) {
                e.player.absorptionAmount += 2
            }
        }
        e.damage = e.damage - enemy.nonHeadShotDamageResistance
        e.damage *= 1.0 + (lcdWeapon.weaponLevel * 0.2)
        if (category == GunCategory.SNIPER) {
            if (data.specialSkillTypes.contains(SpecialSkillType.GRAZE)) {
                val entities = e.victim.location.getNearbyLivingEntities(5.0)
                entities.remove(e.victim)
                var count = 0
                for (entity in entities) {
                    if (entity.type == EntityType.PLAYER) continue
                    if (count >= 3) break
                    entity.damage(e.damage, e.player)
                    entity.noDamageTicks = 0
                    count++
                }
            }
        }
        if (e.damage < 1.0) {
            e.damage = 1.0
        }
        println("ダメージ量 ${e.damage}")
    }

    @EventHandler
    fun onDeath(e: EntityDeathEvent) {
        if (e.entity.hasMetadata(MetadataUtil.SUPPLY_CART)) {
            e.isCancelled = true
            return
        }
        val uuid = e.entity.uniqueId
        if (manager.enemyHashMap.containsKey(uuid)) {
            val enemy = manager.enemyHashMap[uuid]!!
            enemy.enemyDeathEffects(e.entity)
            if (e.entity.killer is HumanEntity) {
                val player = e.entity.killer as Player
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                val lcdPlayer = manager.getLCDPlayer(player)
                lcdPlayer.campaignData.kill += 1
                lcdPlayer.campaignData.money += (enemy.money * lcdPlayer.statusData.addMoneyMultiplier).toInt()
                if (lcdPlayer.statusData.specialSkillTypes.contains(SpecialSkillType.FULLY_LOADED)) {
                    if (Math.random() <= 0.05) {
                        lcdPlayer.perk.getGrenade().sendGrenade(lcdPlayer.player, 1)
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 24f)
                        player.sendMessage(Component.text("${ChatColor.AQUA}Fully Loadedの効果によりグレネードを回収しました！"))
                    }
                }
                manager.enemyHashMap.remove(uuid)
            }
        }
    }

    @EventHandler
    fun onEntityDamage(e: EntityDamageEvent) {
        if (e.entity.type == EntityType.SNOWMAN) {
            e.isCancelled = true
            return
        }
        if (e.entity.hasMetadata(MetadataUtil.SUPPLY_CART)) {
            e.isCancelled = true
            return
        }
//        if (e.entity.type == EntityType.PLAYER) {
//            val player = e.entity as Player
//            val lcdPlayer = manager.getLCDPlayer(player)
//            val data = lcdPlayer.statusData
//            var addDodge = 0
//            e.damage *= data.damageResistMultiplier
//            var addResist = 1.0
//            if (data.specialSkillTypes.contains(SpecialSkillType.COMMITMENT_TO_SURVIVAL)) {
//                if (player.healthScale <= 10) {
//                    addResist -= 0.15
//                }
//            }
//            if (data.specialSkillTypes.contains(SpecialSkillType.UP_YOU_GO)) {
//                if (data.healthScaleAmount <= 7.0 && Random.nextInt(100) <= 20) {
//                    player.absorptionAmount = data.armorLimit
//                    println("UP YOU GO発動")
//                }
//            }
//            if (data.specialSkillTypes.contains(SpecialSkillType.SWAN_SONG)) {
//                if (player.healthScale <= 4) {
//                    addResist -= 0.2
//                }
//            }
//            e.damage *= addResist
//            if (data.specialSkillTypes.contains(SpecialSkillType.SNEAKY_BASTARD) && data.armorLimit <= 20) {
//                addDodge += (20 - data.armorLimit.toInt()) * 2
//                if (addDodge > 20) {
//                    addDodge = 20
//                }
//            }
//            if (data.dodgeMultiplier + addDodge >= Random.nextInt(100)) {
//                e.isCancelled = true
//                (e.entity as Player).playSound(e.entity.location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f)
//                println("回避しました")
//                println("addDodge $addDodge")
//                println("回避率 ${data.dodgeMultiplier + addDodge}")
//            }
//            if (lcdPlayer.buff.contains(Buff.COMBAT_MEDIC)){
//                e.damage *= 0.7
//            }
//
//            if (e.damage < 1.0){
//                e.damage = 1.0
//            }
//            if (data.specialSkillTypes.contains(SpecialSkillType.UNSEEN_STRIKE)){
//                if (!lcdPlayer.buff.contains(Buff.UNSEEN_STRIKE)){
//                    BuffRunnable(lcdPlayer,10,Buff.UNSEEN_STRIKE).addBuff()
//                }
//            }
//        }
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageByEntityEvent) {
        var player = e.entity
        if (player.type != EntityType.PLAYER) {
            return
        }
        if (!manager.enemyHashMap.containsKey(e.damager.uniqueId)) {
            return
        }
        val enemy = manager.enemyHashMap[e.damager.uniqueId]!!
        enemy.attackSpecialEffects(e.damager as LivingEntity, player as LivingEntity)
        e.damage = enemy.getPower()
        if (e.entity.type == EntityType.PLAYER) {
            player = e.entity as Player
            val lcdPlayer = manager.getLCDPlayer(player)
            val data = lcdPlayer.statusData
            var addDodge = 0
            e.damage *= data.damageResistMultiplier
            var addResist = 1.0
            if (data.specialSkillTypes.contains(SpecialSkillType.COMMITMENT_TO_SURVIVAL)) {
                if (player.healthScale <= 10) {
                    addResist -= 0.15
                }
            }
            if (data.specialSkillTypes.contains(SpecialSkillType.UP_YOU_GO)) {
                if (data.healthScaleAmount <= 7.0 && Random.nextInt(100) <= 20) {
                    player.absorptionAmount = data.armorLimit
                    println("UP YOU GO発動")
                }
            }
            if (data.specialSkillTypes.contains(SpecialSkillType.SWAN_SONG)) {
                if (player.healthScale <= 4) {
                    addResist -= 0.2
                }
            }
            e.damage *= addResist
            if (data.dodgeMultiplier + addDodge >= Random.nextInt(100)) {
                e.isCancelled = true
                (e.entity as Player).playSound(e.entity.location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f)
                println("回避しました")
                println("addDodge $addDodge")
                println("回避率 ${data.dodgeMultiplier + addDodge}")
            }
            if (lcdPlayer.buff.contains(Buff.COMBAT_MEDIC)){
                e.damage *= 0.7
            }

            if (e.damage < 1.0){
                e.damage = 1.0
            }
            if (data.specialSkillTypes.contains(SpecialSkillType.UNSEEN_STRIKE)){
                if (!lcdPlayer.buff.contains(Buff.UNSEEN_STRIKE)){
                    BuffRunnable(lcdPlayer,10,Buff.UNSEEN_STRIKE).addBuff()
                }
            }
        }
        println("PlayerDamage ${e.damage}")
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPvP(e: EntityDamageByEntityEvent) {
        if (e.entity.type == e.damager.type) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onArrowHit(e: ProjectileHitEvent) {
        // MasterSmoker
        if (e.entity.hasMetadata(MetadataUtil.EXPLODE_ARROW)) {
            e.entity.world.playSound(e.entity.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.2f)
            ParticleBuilder(ParticleEffect.EXPLOSION_HUGE)
                .setLocation(e.entity.location)
                .display()
            e.entity.location.clone().getNearbyPlayers(5.0).forEach {
                val lcdPlayer = manager.getLCDPlayer(it)
                if (lcdPlayer.isSurvivor) {
                    it.damage(4.0)
                }
            }
        }
        if (e.hitEntity != null && e.entity.hasMetadata(MetadataUtil.ENEMY_ARROW)) {
            if (e.hitEntity!!.type == EntityType.PLAYER && e.hitEntity!! is LivingEntity) {
                val player = e.hitEntity as LivingEntity
                player.damage(Smoker.getPower(),e.entity)
            }
        }
    }

    @EventHandler
    fun onVehicleDamage(e: VehicleDamageEvent) {
        if (e.vehicle.hasMetadata(MetadataUtil.SUPPLY_CART)) {
            e.isCancelled = true
            return
        }
    }
}