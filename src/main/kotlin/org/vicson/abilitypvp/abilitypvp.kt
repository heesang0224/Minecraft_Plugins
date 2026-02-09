package org.vicson.abilitypvp

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class abilitypvp : JavaPlugin(), Listener {
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
        getCommand("abilityreload")?.setExecutor { sender, _, _, _ -> abilityManager.handleAbilityReloadCommand(sender) }
    }



}
