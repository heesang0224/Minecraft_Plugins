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
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class BumbAbility(private val plugin: JavaPlugin, config: YamlConfiguration) : Ability, Listener {
    override val id: String = "bumb"
    override val menuSlot: Int = config.getInt("bumb.menuSlot", 6)

    private val bumbItemKey = NamespacedKey(plugin, "bumb_item")
    private val baseHealthKey = NamespacedKey(plugin, "bumb_base_health")
    private val projectileKey = NamespacedKey(plugin, "bumb_projectile")
    private val throwSpeed = config.getDouble("bumb.throwSpeed", 1.6)
    private val basePower = config.getDouble("bumb.basePower", 4.0)
    private val powerMultiplier = config.getDouble("bumb.powerMultiplier", 1.5)
    private val breakBlocks = config.getBoolean("bumb.breakBlocks", true)
    private val setFire = config.getBoolean("bumb.setFire", false)
    private val cooldownMs = config.getLong("bumb.cooldownSeconds", 5L) * 1000L
    private val bonusHealth = config.getDouble("bumb.bonusHealth", 25.0)
    private val cooldowns = mutableMapOf<UUID, Long>()

    init {
        // Registration handled by AbilityManager.
    }

    override fun createMenuItem(): ItemStack = createBumbItem()

    override fun isMenuItem(item: ItemStack): Boolean = isBumbItem(item)

    override fun onSelect(player: Player) {
        player.sendMessage("BUMB ability acquired.")
        applyBonusHealth(player)
        giveBumbItem(player)
    }

    override fun onPlayerJoin(player: Player) {
        applyBonusHealth(player)
    }

    override fun onInteract(player: Player, action: Action, item: ItemStack): Boolean {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false
        if (!isBumbItem(item)) return false
        if (!canUse(player)) return false

        val projectile = player.launchProjectile(Snowball::class.java)
        projectile.item = ItemStack(Material.TNT)
        projectile.velocity = player.location.direction.normalize().multiply(throwSpeed)
        projectile.persistentDataContainer.set(projectileKey, PersistentDataType.BYTE, 1.toByte())
        return true
    }

    override fun onAbilityCleared(player: Player) {
        removeBumbItems(player)
        cooldowns.remove(player.uniqueId)
        clearBonusHealth(player)
    }

    override fun refreshItems(player: Player) {
        replaceBumbItems(player)
    }

    override fun shouldCancelFallDamage(player: Player): Boolean = false

    override fun shouldCancelExplosionDamage(player: Player): Boolean = true

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile !is Snowball) return
        if (!projectile.persistentDataContainer.has(projectileKey, PersistentDataType.BYTE)) return

        val location = projectile.location
        val power = (basePower * powerMultiplier).toFloat()
        location.world?.createExplosion(location, power, setFire, breakBlocks, projectile.shooter as? Player)
        projectile.remove()
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (!isBumbItem(item)) return
        event.isCancelled = true
    }

    private fun createBumbItem(): ItemStack {
        val item = ItemStack(Material.TNT)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.RED}BUMB")
        meta.lore = listOf(
            "${ChatColor.GRAY}Right-click to throw explosive TNT.",
            "${ChatColor.DARK_GRAY}Impact explosion: ${powerMultiplier}x",
            "${ChatColor.DARK_GRAY}Cooldown: ${cooldownMs / 1000}s",
            "${ChatColor.DARK_GRAY}Bonus Health: ${bonusHealth}"
        )
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(bumbItemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isBumbItem(item: ItemStack): Boolean {
        if (item.type != Material.TNT) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(bumbItemKey, PersistentDataType.BYTE)
    }

    private fun giveBumbItem(player: Player) {
        val leftover = player.inventory.addItem(createBumbItem())
        if (leftover.isNotEmpty()) {
            for (stack in leftover.values) {
                player.world.dropItemNaturally(player.location, stack)
            }
        }
    }

    private fun removeBumbItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isBumbItem(item)) {
                inventory.setItem(i, null)
            }
        }
    }

    private fun replaceBumbItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isBumbItem(item)) {
                inventory.setItem(i, createBumbItem())
            }
        }
    }

    private fun canUse(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = cooldowns[player.uniqueId] ?: 0L
        val remaining = cooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(
                Component.text(
                    "BUMB cooldown: ${seconds}s",
                    NamedTextColor.RED
                )
            )
            return false
        }
        cooldowns[player.uniqueId] = now
        return true
    }

    private fun applyBonusHealth(player: Player) {
        val container = player.persistentDataContainer
        val attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
        if (!container.has(baseHealthKey, PersistentDataType.DOUBLE)) {
            container.set(baseHealthKey, PersistentDataType.DOUBLE, attribute.baseValue)
        }
        val base = container.get(baseHealthKey, PersistentDataType.DOUBLE) ?: attribute.baseValue
        attribute.baseValue = base + bonusHealth
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
}
