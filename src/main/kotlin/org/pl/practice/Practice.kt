package org.pl.practice

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class Practice : JavaPlugin(), Listener {
    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("Enabling plugin")

        getCommand("good")?.setExecutor(GoodCommand())

    }

}






