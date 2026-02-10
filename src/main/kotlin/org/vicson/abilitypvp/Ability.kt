package org.vicson.abilitypvp

import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Entity

interface Ability {
    val id: String
    val menuSlot: Int

    fun createMenuItem(): ItemStack
    fun isMenuItem(item: ItemStack): Boolean
    fun onSelect(player: Player)
    fun onPlayerJoin(player: Player)
    fun onInteract(player: Player, action: Action, item: ItemStack): Boolean
    fun onAbilityCleared(player: Player)
    fun refreshItems(player: Player)
    fun onDamage(attacker: Player, victim: Entity, event: EntityDamageByEntityEvent) {}
    fun shouldCancelFallDamage(player: Player): Boolean
    fun shouldCancelExplosionDamage(player: Player): Boolean
}
