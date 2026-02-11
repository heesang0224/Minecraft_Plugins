package org.vicson.abilitypvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class TridentAbility(private val plugin: JavaPlugin, config: YamlConfiguration) : Ability {
    override val id: String = "trident"
    override val menuSlot: Int = config.getInt("trident.menuSlot", 0)

    private val itemKey = NamespacedKey(plugin, "trident_item")
    private val baseHealthKey = NamespacedKey(plugin, "trident_base_health")
    private val cooldownMs = config.getLong("trident.cooldownSeconds", 1L) * 1000L
    private val count = config.getInt("trident.count", 15)
    private val speed = config.getDouble("trident.speed", 2.5)
    private val spread = config.getDouble("trident.spread", 0.12)
    private val damageMultiplier = config.getDouble("trident.damageMultiplier", 1.5)
    private val bonusHealth = config.getDouble("trident.bonusHealth", 30.0)
    private val cooldowns = mutableMapOf<UUID, Long>()

    override fun createMenuItem(): ItemStack = createTridentItem()

    override fun isMenuItem(item: ItemStack): Boolean = isTridentItem(item)

    override fun onSelect(player: Player) {
        player.sendMessage("Trident ability acquired.")
        applyBonusHealth(player)
        giveItem(player)
    }

    override fun onPlayerJoin(player: Player) {
        applyBonusHealth(player)
    }

    override fun onInteract(player: Player, action: Action, item: ItemStack): Boolean {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false
        if (!isTridentItem(item)) return false
        if (!canUse(player)) return false

        val baseDirection = player.location.direction.normalize()
        val random = ThreadLocalRandom.current()
        repeat(count) {
            val trident = player.launchProjectile(Trident::class.java)
            val offset = Vector(
                random.nextDouble(-spread, spread),
                random.nextDouble(-spread, spread),
                random.nextDouble(-spread, spread)
            )
            val direction = baseDirection.clone().add(offset).normalize()
            trident.velocity = direction.multiply(speed)
            trident.damage = trident.damage * damageMultiplier
            trident.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!trident.isDead) {
                    trident.remove()
                }
            }, 60L)
        }
        return true
    }

    override fun onAbilityCleared(player: Player) {
        removeItems(player)
        cooldowns.remove(player.uniqueId)
        clearBonusHealth(player)
    }

    override fun refreshItems(player: Player) {
        replaceItems(player)
    }

    override fun shouldCancelFallDamage(player: Player): Boolean = false

    override fun shouldCancelExplosionDamage(player: Player): Boolean = false

    private fun createTridentItem(): ItemStack {
        val item = ItemStack(Material.TRIDENT)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.AQUA}Trident")
        meta.lore = listOf(
            "${ChatColor.GRAY}Right-click to throw ${count} tridents.",
            "${ChatColor.DARK_GRAY}Damage: x${damageMultiplier}",
            "${ChatColor.BLUE}Cooldown: ${cooldownMs / 1000}s",
            "${ChatColor.DARK_RED}Bonus HP♥♥♥: +${bonusHealth}"
        )
        meta.addEnchant(Enchantment.RIPTIDE, 3, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isTridentItem(item: ItemStack): Boolean {
        if (item.type != Material.TRIDENT) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    private fun giveItem(player: Player) {
        val leftover = player.inventory.addItem(createTridentItem())
        if (leftover.isNotEmpty()) {
            for (stack in leftover.values) {
                player.world.dropItemNaturally(player.location, stack)
            }
        }
    }

    private fun removeItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isTridentItem(item)) {
                inventory.setItem(i, null)
            }
        }
    }

    private fun replaceItems(player: Player) {
        val inventory = player.inventory
        var found = false
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isTridentItem(item)) {
                inventory.setItem(i, createTridentItem())
                found = true
            }
        }
        if (!found) {
            giveItem(player)
        }
    }

    private fun canUse(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = cooldowns[player.uniqueId] ?: 0L
        val remaining = cooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(Component.text("Trident cooldown: ${seconds}s", NamedTextColor.RED))
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
