package org.vicson.abilitypvp

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.HandlerList
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class AbilityManager(private val plugin: JavaPlugin) : Listener {
    private val abilityKey = NamespacedKey(plugin, "ability")
    private var abilitiesConfig: YamlConfiguration = loadAbilitiesConfig()
    private var abilityMenuTitle: String = abilitiesConfig.getString("ui.title") ?: "Choose Ability"
    private var abilities: Map<String, Ability> = buildAbilities(abilitiesConfig)
    private var abilityListeners: List<Listener> = collectAbilityListeners(abilities)

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        registerAbilityListeners()
    }

    fun handleAbilityCommand(sender: CommandSender): Boolean {
        val player = sender as? Player ?: return true
        player.openInventory(createAbilityMenu())
        return true
    }

    fun handleAbilityCancelCommand(sender: CommandSender): Boolean {
        val player = sender as? Player ?: return true
        clearAbility(player)
        return true
    }

    fun handleAbilityReloadCommand(sender: CommandSender): Boolean {
        reloadAbilities()
        sender.sendMessage("Ability config reloaded.")
        return true
    }

    @EventHandler
    fun onFallDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val player = event.entity as? Player ?: return
        val ability = getPlayerAbility(player) ?: return
        if (!ability.shouldCancelFallDamage(player)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onExplosionDamage(event: EntityDamageEvent) {
        val cause = event.cause
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION &&
            cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        ) {
            return
        }
        val player = event.entity as? Player ?: return
        val ability = getPlayerAbility(player) ?: return
        if (!ability.shouldCancelExplosionDamage(player)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val ability = getPlayerAbility(player) ?: return
        ability.onDamage(player, event.entity, event)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != abilityMenuTitle) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clicked = event.currentItem ?: return
        if (clicked.type == Material.BARRIER) {
            clearAbility(player)
            player.closeInventory()
            return
        }
        val ability = abilities.values.firstOrNull { it.isMenuItem(clicked) } ?: return

        val data = player.persistentDataContainer
        if (data.has(abilityKey, PersistentDataType.STRING)) {
            player.sendMessage("Ability already selected.")
            player.closeInventory()
            return
        }

        data.set(abilityKey, PersistentDataType.STRING, ability.id)
        ability.onSelect(player)
        player.closeInventory()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val ability = getPlayerAbility(player) ?: return
        ability.onPlayerJoin(player)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val player = event.player

        if (item.type == Material.CLOCK) {
            player.openInventory(createAbilityMenu())
            return
        }

        val ability = getPlayerAbility(player) ?: return
        if (ability.onInteract(player, action, item)) {
            event.isCancelled = true
        }
    }

    private fun createAbilityMenu(): Inventory {
        val inventory = Bukkit.createInventory(null, 9, abilityMenuTitle)
        for (ability in abilities.values) {
            inventory.setItem(ability.menuSlot, ability.createMenuItem())
        }
        inventory.setItem(8, createCancelItem())
        return inventory
    }

    private fun getPlayerAbility(player: Player): Ability? {
        val id = player.persistentDataContainer.get(abilityKey, PersistentDataType.STRING) ?: return null
        return abilities[id]
    }

    private fun clearAbility(player: Player) {
        val ability = getPlayerAbility(player)
        player.persistentDataContainer.remove(abilityKey)
        ability?.onAbilityCleared(player)
        player.sendMessage("Ability cleared.")
    }

    private fun createCancelItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.RED}Ability Cancel")
        meta.lore = listOf(
            "${ChatColor.GRAY}Click to clear your current ability.",
            "${ChatColor.DARK_GRAY}This will remove ability items."
        )
        item.itemMeta = meta
        return item
    }

    private fun loadAbilitiesConfig(): YamlConfiguration {
        val file = plugin.dataFolder.resolve("abilities.yml")
        if (!file.exists()) {
            plugin.dataFolder.mkdirs()
            plugin.saveResource("abilities.yml", false)
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun reloadAbilities() {
        unregisterAbilityListeners()
        abilitiesConfig = loadAbilitiesConfig()
        abilityMenuTitle = abilitiesConfig.getString("ui.title") ?: "Choose Ability"
        abilities = buildAbilities(abilitiesConfig)
        abilityListeners = collectAbilityListeners(abilities)
        registerAbilityListeners()

        for (player in plugin.server.onlinePlayers) {
            val ability = getPlayerAbility(player) ?: continue
            ability.onPlayerJoin(player)
            ability.refreshItems(player)
        }
    }

    private fun buildAbilities(config: YamlConfiguration): Map<String, Ability> {
        return mapOf(
            "dash" to DashAbility(plugin, config),
            "bumb" to BumbAbility(plugin, config),
            "sonic_boom" to SonicBoomAbility(plugin, config),
            "trident" to TridentAbility(plugin, config),
            "assassin" to AssassinAbility(plugin, config),
            "plus_mace" to PlusMaceAbility(plugin, config),
            "shield_dash" to ShieldDashAbility(plugin, config)
        )
    }

    private fun collectAbilityListeners(abilities: Map<String, Ability>): List<Listener> {
        return abilities.values.filterIsInstance<Listener>()
    }

    private fun registerAbilityListeners() {
        for (listener in abilityListeners) {
            plugin.server.pluginManager.registerEvents(listener, plugin)
        }
    }

    private fun unregisterAbilityListeners() {
        for (listener in abilityListeners) {
            HandlerList.unregisterAll(listener)
        }
    }
}
