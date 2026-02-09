package org.pl.practice

import org.bukkit.Bukkit
import org.bukkit.entity.Animals
import org.bukkit.entity.Creeper
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ExplosionPrimeEvent
import org.bukkit.plugin.java.JavaPlugin

class Practice : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("Hostile world")
        startHostilePassiveMobsTask()
    }

    @EventHandler
    fun onExplosionPrime(event: ExplosionPrimeEvent) {
        when (event.entity) {
            is Creeper, is TNTPrimed  -> event.radius = event.radius * 10.0f
        }
    }

    private fun startHostilePassiveMobsTask() {
        val targetTypes = setOf(
            EntityType.VILLAGER,
            EntityType.COW,
            EntityType.PIG,
            EntityType.SHEEP,
            EntityType.CHICKEN,
            EntityType.IRON_GOLEM
        )
        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            for (player in server.onlinePlayers) {
                val nearby = player.getNearbyEntities(16.0, 8.0, 16.0)
                for (entity in nearby) {
                    if (entity.type !in targetTypes) continue
                    if (entity is Mob) {
                        entity.target = player
                    }
                    if (entity is Animals) {
                        chaseAndMeleeAnimal(entity, player)
                        continue
                    }
                    if (entity is Villager) {
                        chaseAndMeleeVillager(entity, player)
                    }
                }
            }
        }, 20L, 10L)
    }

    private fun chaseAndMeleeVillager(villager: Villager, player: Player) {
        val distance = chaseEntity(villager, player, 0.5)
        // Simple melee hit since villagers have no native attack AI.
        meleeIfClose(villager, player, 3.0, distance)
    }

    private fun chaseAndMeleeAnimal(animal: Animals, player: Player) {
        val distance = chaseEntity(animal, player, 1.0)
        meleeIfClose(animal, player, 5.0, distance)
    }

    private fun chaseEntity(attacker: LivingEntity, player: Player, speed: Double): Double {
        val direction = player.location.toVector().subtract(attacker.location.toVector())
        val distance = direction.length()
        if (distance > 0.01) {
            attacker.velocity = direction.normalize().multiply(speed)
        }
        return distance
    }

    private fun meleeIfClose(attacker: LivingEntity, player: Player, damage: Double) {
        val distance = attacker.location.distance(player.location)
        meleeIfClose(attacker, player, damage, distance)
    }

    private fun meleeIfClose(attacker: LivingEntity, player: Player, damage: Double, distance: Double) {
        if (distance <= 2.0) {
            player.damage(damage, attacker)
        }
    }
}
