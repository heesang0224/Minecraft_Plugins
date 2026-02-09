package org.pl.practice

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GoodCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        when (command.name.lowercase()) {
            "good" -> {
                if (sender !is Player) return true
                sender.inventory.addItem(ItemStack(Material.GOLD_INGOT))
            }








            "help" -> sender.sendMessage("Help command")
        }
        return true
    }
}
