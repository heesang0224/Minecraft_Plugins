package org.vicson.abilitypvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID

class DashAbility(plugin: JavaPlugin, config: YamlConfiguration) : Ability {
    override val id: String = "dash"
    override val menuSlot: Int = config.getInt("dash.menuSlot", 4)

    private val dashItemKey = NamespacedKey(plugin, "dash_item")
    private val baseHealthKey = NamespacedKey(plugin, "dash_base_health")
    private val dashCooldowns = mutableMapOf<UUID, Long>()
    private val dashConfig = DashConfig(
        cooldownMs = config.getLong("dash.cooldownSeconds", 3L) * 1000L,
        speedMultiplier = config.getDouble("dash.speedMultiplier", 1.6),
        yBoost = config.getDouble("dash.yBoost", 0.5),
        speedAmplifier = config.getInt("dash.speedAmplifier", 1),
        showPotionParticles = config.getBoolean("dash.showPotionParticles", false),
        showPotionIcon = config.getBoolean("dash.showPotionIcon", false),
        bonusHealth = config.getDouble("dash.bonusHealth", 20.0)
    )

    override fun createMenuItem(): ItemStack = createDashItem()

    override fun isMenuItem(item: ItemStack): Boolean = isDashItem(item)

    override fun onSelect(player: Player) {
        player.sendMessage("Dash ability acquired.")
        applyDashEffects(player)
        applyBonusHealth(player)
        giveDashItem(player)
    }

    override fun onPlayerJoin(player: Player) {
        applyDashEffects(player)
        applyBonusHealth(player)
    }

    override fun onInteract(player: Player, action: Action, item: ItemStack): Boolean {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false
        if (!isDashItem(item)) return false
        if (!canDash(player)) return false

        val direction = player.location.direction.normalize()
        val dash = Vector(direction.x, dashConfig.yBoost, direction.z).multiply(dashConfig.speedMultiplier)
        player.velocity = dash
        return true
    }

    override fun onAbilityCleared(player: Player) {
        removeDashItems(player)
        dashCooldowns.remove(player.uniqueId)
        clearDashEffects(player)
        clearBonusHealth(player)
    }

    override fun refreshItems(player: Player) {
        replaceDashItems(player)
    }

    override fun shouldCancelFallDamage(player: Player): Boolean = true

    override fun shouldCancelExplosionDamage(player: Player): Boolean = false

    private fun createDashItem(): ItemStack {
        val item = ItemStack(Material.FEATHER)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.AQUA}DASH")
        meta.lore = listOf(
            "${ChatColor.GRAY}Right-click to dash forward.",
            "${ChatColor.DARK_GRAY}Cooldown: ${dashConfig.cooldownMs / 1000}s",
            "${ChatColor.DARK_GRAY}Bonus Health: ${dashConfig.bonusHealth}"
        )
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(dashItemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isDashItem(item: ItemStack): Boolean {
        if (item.type != Material.FEATHER) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(dashItemKey, PersistentDataType.BYTE)
    }

    private fun giveDashItem(player: Player) {
        val leftover = player.inventory.addItem(createDashItem())
        if (leftover.isNotEmpty()) {
            for (stack in leftover.values) {
                player.world.dropItemNaturally(player.location, stack)
            }
        }
    }

    private fun canDash(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = dashCooldowns[player.uniqueId] ?: 0L
        val remaining = dashConfig.cooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(Component.text("Dash cooldown: ${seconds}s", NamedTextColor.RED))
            return false
        }
        dashCooldowns[player.uniqueId] = now
        return true
    }

    private fun removeDashItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isDashItem(item)) {
                inventory.setItem(i, null)
            }
        }
    }

    private fun replaceDashItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isDashItem(item)) {
                inventory.setItem(i, createDashItem())
            }
        }
    }

    private fun applyDashEffects(player: Player) {
        val effect = PotionEffect(
            PotionEffectType.SPEED,
            Int.MAX_VALUE,
            dashConfig.speedAmplifier,
            true,
            dashConfig.showPotionParticles,
            dashConfig.showPotionIcon
        )
        player.addPotionEffect(effect)
    }

    private fun clearDashEffects(player: Player) {
        player.removePotionEffect(PotionEffectType.SPEED)
    }

    private fun applyBonusHealth(player: Player) {
        val container = player.persistentDataContainer
        val attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
        if (!container.has(baseHealthKey, PersistentDataType.DOUBLE)) {
            container.set(baseHealthKey, PersistentDataType.DOUBLE, attribute.baseValue)
        }
        val base = container.get(baseHealthKey, PersistentDataType.DOUBLE) ?: attribute.baseValue
        attribute.baseValue = base + dashConfig.bonusHealth
        if (player.health < attribute.baseValue) {
            player.health = attribute.baseValue
        }
    }

    private fun clearBonusHealth(player: Player) {
        val container = player.persistentDataContainer
        val attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
        val base = container.get(baseHealthKey, PersistentDataType.DOUBLE)
        if (base != null) {
            attribute.baseValue = base
            container.remove(baseHealthKey)
            if (player.health > attribute.baseValue) {
                player.health = attribute.baseValue
            }
        }
    }

    private data class DashConfig(
        val cooldownMs: Long,
        val speedMultiplier: Double,
        val yBoost: Double,
        val speedAmplifier: Int,
        val showPotionParticles: Boolean,
        val showPotionIcon: Boolean,
        val bonusHealth: Double
    )
}
