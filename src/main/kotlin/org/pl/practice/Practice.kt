package org.pl.practice

import org.bukkit.entity.Creeper
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ExplosionPrimeEvent
import org.bukkit.plugin.java.JavaPlugin

class Practice : JavaPlugin(), Listener {
    private lateinit var abilityManager: AbilityManager

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("Plugin enabled")

        abilityManager = AbilityManager(this)
        abilityManager.register()

        getCommand("good")?.setExecutor(GoodCommand())
        getCommand("help")?.setExecutor(GoodCommand())
        getCommand("ability")?.setExecutor { sender, _, _, _ -> abilityManager.handleAbilityCommand(sender) }
        getCommand("abilitycancel")?.setExecutor { sender, _, _, _ -> abilityManager.handleAbilityCancelCommand(sender) }
    }

    @EventHandler
    fun onExplosionPrime(event: ExplosionPrimeEvent) {
        val creeper = event.entity as? Creeper ?: return

        val base = event.radius
        val power = base * 20.0f

        event.radius = power.coerceAtMost(12.0f)
    }
}
