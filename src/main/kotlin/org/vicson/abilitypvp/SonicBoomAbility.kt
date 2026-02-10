package org.vicson.abilitypvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

class SonicBoomAbility(plugin: JavaPlugin, config: YamlConfiguration) : Ability {
    override val id: String = "sonic_boom"
    override val menuSlot: Int = config.getInt("sonicBoom.menuSlot", 2)

    private val itemKey = NamespacedKey(plugin, "sonic_boom_item")
    private val baseHealthKey = NamespacedKey(plugin, "sonic_boom_base_health")
    private val cooldownMs = config.getLong("sonicBoom.cooldownSeconds", 5L) * 1000L
    private val range = config.getDouble("sonicBoom.range", 20.0)
    private val damage = config.getDouble("sonicBoom.damage", 5.0)
    private val knockback = config.getDouble("sonicBoom.knockback", 1.0)
    private val showPotionParticles = config.getBoolean("sonicBoom.showPotionParticles", false)
    private val showPotionIcon = config.getBoolean("sonicBoom.showPotionIcon", false)
    private val bonusHealth = config.getDouble("sonicBoom.bonusHealth", 40.0)
    private val cooldowns = mutableMapOf<UUID, Long>()

    override fun createMenuItem(): ItemStack = createSonicBoomItem()

    override fun isMenuItem(item: ItemStack): Boolean = isSonicBoomItem(item)

    override fun onSelect(player: Player) {
        player.sendMessage("Sonic Boom ability acquired.")
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
        if (!isSonicBoomItem(item)) return false
        if (!canUse(player)) return false

        val target = findTarget(player)
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()

        player.world.spawnParticle(Particle.SONIC_BOOM, eye.add(direction.multiply(0.5)), 1, 0.0, 0.0, 0.0, 0.0)
        player.world.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f)

        if (target is LivingEntity) {
            target.damage(damage, player)
            val push = target.location.toVector().subtract(player.location.toVector()).normalize()
            target.velocity = push.multiply(knockback)
        }
        return true
    }

    override fun onAbilityCleared(player: Player) {
        removeItems(player)
        cooldowns.remove(player.uniqueId)
        clearStrength(player)
        clearBonusHealth(player)
    }

    override fun refreshItems(player: Player) {
        replaceItems(player)
    }

    override fun shouldCancelFallDamage(player: Player): Boolean = false

    override fun shouldCancelExplosionDamage(player: Player): Boolean = false

    private fun createSonicBoomItem(): ItemStack {
        val item = ItemStack(Material.ECHO_SHARD)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.DARK_AQUA}Sonic Boom")
        meta.lore = listOf(
            "${ChatColor.GRAY}Right-click to fire a sonic boom.",
            "${ChatColor.DARK_GRAY}Damage: ${damage}",
            "${ChatColor.DARK_GRAY}Cooldown: ${cooldownMs / 1000}s",
            "${ChatColor.DARK_GRAY}Bonus Health: ${bonusHealth}"
        )
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isSonicBoomItem(item: ItemStack): Boolean {
        if (item.type != Material.ECHO_SHARD) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    private fun giveItem(player: Player) {
        val leftover = player.inventory.addItem(createSonicBoomItem())
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
            if (isSonicBoomItem(item)) {
                inventory.setItem(i, null)
            }
        }
    }

    private fun replaceItems(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (isSonicBoomItem(item)) {
                inventory.setItem(i, createSonicBoomItem())
            }
        }
    }

    private fun canUse(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = cooldowns[player.uniqueId] ?: 0L
        val remaining = cooldownMs - (now - last)
        if (remaining > 0) {
            val seconds = (remaining / 1000).coerceAtLeast(1)
            player.sendActionBar(Component.text("Sonic Boom cooldown: ${seconds}s", NamedTextColor.RED))
            return false
        }
        cooldowns[player.uniqueId] = now
        return true
    }

    private fun applyStrength(player: Player) {
        val effect = PotionEffect(
            PotionEffectType.STRENGTH,
            Int.MAX_VALUE,
            1,
            true,
            showPotionParticles,
            showPotionIcon
        )
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

    private fun findTarget(player: Player): Entity? {
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val result = player.world.rayTraceEntities(eye, direction, range) { entity ->
            entity != player && entity is LivingEntity
        }
        return result?.hitEntity
    }
}
