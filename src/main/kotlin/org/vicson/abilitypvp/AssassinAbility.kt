package org.vicson.abilitypvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*

class AssassinAbility(private val plugin: JavaPlugin, config: YamlConfiguration) : Ability {
    override val id: String = "assassin"
    override val menuSlot: Int = config.getInt("assassin.menuSlot", 3)

    private val itemKey = NamespacedKey(plugin, "assassin_item")
    private val baseHealthKey = NamespacedKey(plugin, "assassin_base_health")
    private val dashCooldownMs = config.getLong("assassin.dashCooldownSeconds", 2L) * 1000L
    private val stealthCooldownMs = config.getLong("assassin.stealthCooldownSeconds", 5L) * 1000L
    private val stealthDurationTicks = config.getLong("assassin.stealthDurationSeconds", 3L) * 20L
    private val dashSpeed = config.getDouble("assassin.dashSpeed", 1.8)
    private val dashYBoost = config.getDouble("assassin.dashYBoost", 0.2)
    private val damageMultiplier = config.getDouble("assassin.damageMultiplier", 1.5)
    private val backstabDamage = config.getDouble("assassin.backstabDamage", 20.0)
    private val bonusHealth = config.getDouble("assassin.bonusHealth", 10.0)
    private val dashCooldowns = mutableMapOf<UUID, Long>()
    private val stealthCooldowns = mutableMapOf<UUID, Long>()

    override fun createMenuItem(): ItemStack = createAssassinItem()

    override fun isMenuItem(item: ItemStack): Boolean = isAssassinItem(item)

    override fun onSelect(player: Player) {
        player.sendMessage("Assassin ability acquired.")
        applyStrength(player)
        applyBonusHealth(player)
        giveItem(player)
    }

    override fun onPlayerJoin(player: Player) {
        applyStrength(player)
        applyBonusHealth(player)
    }

    override fun onInteract(player: Player, action: Action, item: ItemStack): Boolean {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false
        if (!isAssassinItem(item)) return false

        return if (player.isSneaking) {
            if (!canUseStealth(player)) return false
            applyStealth(player)
            true
        } else {
            if (!canUseDash(player)) return false
            dash(player)
            true
        }
    }

    override fun onDamage(attacker: Player, victim: Entity, event: EntityDamageByEntityEvent) {
        if (attacker.inventory.itemInMainHand.type != Material.IRON_SWORD) return
        if (!isAssassinItem(attacker.inventory.itemInMainHand)) return
        if (victim !is LivingEntity) return

        val direction = victim.location.direction.normalize()
        val toAttacker = attacker.location.toVector().subtract(victim.location.toVector()).normalize()
        val dot = direction.dot(toAttacker)
        if (dot < -0.5) {
            event.damage = backstabDamage
        } else {
            event.damage = event.damage * damageMultiplier
        }
    }

    override fun onAbilityCleared(player: Player) {
        removeItems(player)
        dashCooldowns.remove(player.uniqueId)
        stealthCooldowns.remove(player.uniqueId)
        clearStrength(player)
        clearBonusHealth(player)
    }

    override fun refreshItems(player: Player) {
        replaceItems(player)
    }

    override fun shouldCancelFallDamage(player: Player): Boolean = true

    override fun shouldCancelExplosionDamage(player: Player): Boolean = false

    private fun createAssassinItem(): ItemStack {
        val item = ItemStack(Material.IRON_SWORD)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.DARK_RED}Assassin")
        meta.lore = listOf(
            "${ChatColor.GRAY}Right-click to dash.",
            "${ChatColor.GRAY}Sneak + right-click to stealth.",
            "${ChatColor.DARK_GRAY}Cooldown: ${dashCooldownMs / 1000}s",
            "${ChatColor.DARK_GRAY}Stealth Cooldown: ${stealthCooldownMs / 1000}s",
            "${ChatColor.DARK_GRAY}Bonus HP: +${bonusHealth}"
        )
        meta.addEnchant(Enchantment.SHARPNESS, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isAssassinItem(item: ItemStack): Boolean {
        if (item.type != Material.IRON_SWORD) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    private fun giveItem(player: Player) {
        val leftover = player.inventory.addItem(createAssassinItem())
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
            if (isAssassinItem(item)) {
                inventory.setItem(i, null)
            }
        }
    }

    private fun replaceItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isAssassinItem(item)) {
                inventory.setItem(i, createAssassinItem())
            }
        }
    }

    private fun canUseDash(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = dashCooldowns[player.uniqueId] ?: 0L
        val remaining = dashCooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(Component.text("Dash cooldown: ${seconds}s", NamedTextColor.RED))
            return false
        }
        dashCooldowns[player.uniqueId] = now
        return true
    }

    private fun canUseStealth(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = stealthCooldowns[player.uniqueId] ?: 0L
        val remaining = stealthCooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(Component.text("Stealth cooldown: ${seconds}s", NamedTextColor.RED))
            return false
        }
        stealthCooldowns[player.uniqueId] = now
        return true
    }

    private fun dash(player: Player) {
        val direction = player.location.direction.normalize()
        val dash = Vector(direction.x, dashYBoost, direction.z).multiply(dashSpeed)
        player.velocity = dash
    }

    private fun applyStealth(player: Player) {
        val effect = PotionEffect(PotionEffectType.INVISIBILITY, stealthDurationTicks.toInt(), 0, true, false, false)
        player.addPotionEffect(effect)
    }

    private fun applyStrength(player: Player) {
        val effect = PotionEffect(PotionEffectType.STRENGTH, Int.MAX_VALUE, 0, true, false, false)
        player.addPotionEffect(effect)
    }

    private fun clearStrength(player: Player) {
        player.removePotionEffect(PotionEffectType.STRENGTH)
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
