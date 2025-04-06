package org.Bitello.essentialsMagic.features.magickey

import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node

import org.Bitello.essentialsMagic.core.apis.WorldGuardManager
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.features.magicfire.MagicFire_Commands_Manager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.command.Command
import org.bukkit.command.CommandSender



class MagicKeyManager(private val plugin: EssentialsMagic) : Listener {
    private val config = plugin.config
    private val teleportingPlayers: MutableMap<Player, Location> = HashMap()


    fun initialize() {
        val message = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[EssentialsMagic] MagicKey has been enabled.").color(NamedTextColor.DARK_PURPLE))
        Bukkit.getConsoleSender().sendMessage(message)

        // Registra o listener de eventos
        Bukkit.getPluginManager().registerEvents(this, plugin)

        registerCommand("home")
    }

    private fun registerCommand(name: String) {
        try {
            val commandMap = plugin.server.commandMap
            val command = object : Command(name) {
                override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
                    return MagicKey_Commands_Manager(plugin).onCommand(sender, this, commandLabel, args)
                }

                override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
                    return MagicKey_Commands_Manager(plugin).onTabComplete(sender, this, alias, args) ?: emptyList()
                }
            }

            command.description = "Comando principal do MagicKey"
            command.usage = "/$name [subcomando]"
            command.aliases = listOf("mk")

            commandMap.register(plugin.name.lowercase(), command)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao registrar o comando $name: ${e.message}")
            e.printStackTrace()
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!MagicKeyManager.Companion.isMagicKeyEnabled(plugin)) return

        val player = event.player
        val item = player.inventory.itemInMainHand

        // Checar se o item está vazio
        if (item == null || item.type == Material.AIR) {
            return  // Não há item na mão
        }

        // Verificar se o item é uma chave válida via Nexo
        if (player.isSneaking && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            if (isValidKey(item)) {
                val meta = item.itemMeta
                if (meta != null && meta.hasLore()) {
                    val lore = meta.lore
                    var hasCoordinates = false

                    // Verificar se a lore contém coordenadas
                    for (line in lore!!) {
                        val cleanedLine = line.replace("§.".toRegex(), "") // Remover códigos de cor
                        if (cleanedLine.matches(".*\\w+, -?\\d+, -?\\d+, -?\\d+.*".toRegex())) {
                            hasCoordinates = true
                            break
                        }
                    }

                    if (hasCoordinates) {
                        // Lore configurada para um portal
                        handleTeleport(player, item)
                    } else {
                        handleKeyCreation(player, item)
                    }
                } else {
                    player.sendMessage("§cErro: O item não possui lore.")
                }
            }
        }
    }

    private fun isValidKey(item: ItemStack): Boolean {
        // Verificar se o item é uma chave válida usando Nexo
        val itemId: String? = NexoItems.idFromItem(item)
        if (itemId != null) {
            val keyConfigs = plugin.config.getStringList("magickey.key_id")
            for (keyConfig in keyConfigs) {
                val parts = keyConfig.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size > 0 && parts[0] == itemId) {
                    return true
                }
            }
        }
        return false
    }


    private fun handleTeleport(player: Player, key: ItemStack) {
        val luckPerms = LuckPermsProvider.get()
        val user = luckPerms.userManager.getUser(player.uniqueId)

        val hasCooldownBypass =
            user != null && user.nodes.contains(Node.builder("EssentialsMagic.MagicKey.time.byPass").build())
        val hasBlacklistBypass =
            user != null && user.nodes.contains(Node.builder("EssentialsMagic.MagicKey.teleport.byPass").build())

        val targetLocation = getTargetLocationFromKey(key)
        if (targetLocation == null) {
            player.sendMessage("§cLocalização da chave inválida.")
            return
        }

        // Verificar se a região permite o uso de chaves mágicas
        if (!canUseMagicKey(player)) {
            player.sendMessage("§cVocê não tem permissão para usar uma chave mágica nesta região.")
            return
        }

        // Obter o ID da chave
        val keyId: String? = NexoItems.idFromItem(key)
        var maxDistance = -1.0
        var isInterdimensional = false
        var isRestricted = false

        // Verificar a configuração específica da chave
        val keyConfigs = config.getStringList("magickey.key_id")
        for (keyConfig in keyConfigs) {
            val parts = keyConfig.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size > 0 && parts[0] == keyId) {
                if (parts.size > 1) {
                    maxDistance = parts[1].trim { it <= ' ' }.toDouble()
                }
                if (parts.size > 2) {
                    isInterdimensional = parts[2].trim { it <= ' ' }.toBoolean()
                }
                if (parts.size > 4) {
                    isRestricted = parts[4].trim { it <= ' ' }.toBoolean()
                }
                break
            }
        }

        // Verificar se o mundo atual está na blacklist de teleporte
        val teleportBlacklist = config.getStringList("magickey.world_teleport_blacklist")
        if (teleportBlacklist.contains(player.world.name) && !hasBlacklistBypass) {
            player.sendMessage("§cVocê não pode usar a chave para teletransportar deste mundo.")
            return
        }

        // Verificar se a chave pode teletransportar entre dimensões
        if (!isInterdimensional && player.world != targetLocation.world) {
            player.sendMessage("§cEsta chave não pode teletransportar entre dimensões.")
            return
        }

        // Verificar a distância máxima permitida
        if (maxDistance != -1.0 && player.world == targetLocation.world && player.location.distance(targetLocation) > maxDistance) {
            player.sendMessage("§cA localização alvo está muito longe.")
            return
        }

        val uses = getUsesFromKey(key)
        if (uses == 0) {
            player.sendMessage("§cEsta chave não tem mais usos.")
            if (uses != -1) { // Verificar se a chave não é ilimitada
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            }
            return
        }

        player.sendMessage("§aIniciando o processo de teleporte...")
        teleportingPlayers[player] = player.location

        if (hasCooldownBypass) {
            player.teleport(targetLocation)
            player.sendMessage("§aTeleporte concluído com sucesso.")
            teleportingPlayers.remove(player)
            if (uses > 0) {
                updateUsesInKey(key, uses - 1)
                player.sendMessage("§aUsos restantes da chave: " + (uses - 1))
            }
        } else {
            val cooldown = config.getInt("magickey.key_cooldown", 8) // Usar a chave correta
            player.sendMessage("§aCooldown de teleporte configurado: $cooldown segundos.")

            object : BukkitRunnable() {
                override fun run() {
                    if (teleportingPlayers.containsKey(player)) {
                        player.teleport(targetLocation)
                        player.sendMessage("§aTeleporte concluído com sucesso.")
                        teleportingPlayers.remove(player)
                        if (uses > 0) {
                            updateUsesInKey(key, uses - 1)
                            player.sendMessage("§aUsos restantes da chave: " + (uses - 1))
                        }
                    }
                }
            }.runTaskLater(plugin, cooldown * 20L)
        }
    }

    private fun canUseMagicKey(player: Player): Boolean {
        return WorldGuardManager.isRegionFlagAllowed(player.location, WorldGuardManager.MAGIC_KEY_USE_FLAG)
    }

    private fun handleKeyCreation(player: Player, key: ItemStack) {
        // Verificar se o item é uma chave válida via Oraxen
        if (!isValidKey(key)) {
            player.sendMessage("§cErro: O item não é uma chave válida.")
            return
        }

        // Verificar se a região permite a criação de chaves mágicas
        if (!canCreateMagicKey(player)) {
            player.sendMessage("§cVocê não tem permissão para criar uma chave mágica nesta região.")
            return
        }

        // Obter o ID da chave
        val keyId: String? = NexoItems.idFromItem(key)
        var isRestricted = false

        // Verificar a configuração específica da chave
        val keyConfigs = config.getStringList("magickey.key_id")
        for (keyConfig in keyConfigs) {
            val parts = keyConfig.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size > 0 && parts[0] == keyId) {
                if (parts.size > 4) { // Verificar se há pelo menos 5 elementos
                    isRestricted =
                        parts[4].trim { it <= ' ' }.toBoolean() // Supondo que a restrição de mundo está na posição 4
                }
                break
            }
        }

        // Verificar se o mundo está na blacklist de criação de chaves
        val createBlacklist = config.getStringList("magickey.world_create_blacklist")
        if (createBlacklist.contains(player.world.name)) {
            player.sendMessage("§cVocê não pode criar uma chave neste mundo.")
            return
        }

        // Deletar a lore antiga da chave
        val meta = key.itemMeta
        if (meta != null) {
            meta.lore = ArrayList()
            key.setItemMeta(meta)
        }

        // Adicionar a nova lore usando a formatação do config.yml
        saveLocationInKeyLore(key, player.location, player)
        addEnchantmentEffect(key)
        player.sendMessage("§aChave criada com sucesso.")
    }

    private fun canCreateMagicKey(player: Player): Boolean {
        return WorldGuardManager.isRegionFlagAllowed(player.location, WorldGuardManager.MAGIC_KEY_CREATE_FLAG)
    }

    private fun getTargetLocationFromKey(key: ItemStack): Location? {
        if (key.hasItemMeta()) {
            val meta = key.itemMeta
            if (meta != null && meta.hasLore()) {
                val lore = meta.lore
                val configLore = config.getStringList("magickey.key_lore")

                for (i in configLore.indices) {
                    if (configLore[i].contains("{location}")) {
                        if (i < lore!!.size) {
                            val cleanedLine = lore[i].replace("§.".toRegex(), "") // Remover códigos de cor
                            val parts =
                                cleanedLine.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].split(
                                    ",".toRegex()
                                ).dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (parts.size == 4) {
                                try {
                                    val worldName = parts[0].trim { it <= ' ' }
                                    val x = parts[1].trim { it <= ' ' }.toDouble()
                                    val y = parts[2].trim { it <= ' ' }.toDouble()
                                    val z = parts[3].trim { it <= ' ' }.toDouble()
                                    return Location(Bukkit.getWorld(worldName), x, y, z)
                                } catch (e: NumberFormatException) {
                                    plugin.logger.severe("Erro ao converter coordenadas: " + parts.contentToString())
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun getUsesFromKey(key: ItemStack): Int {
        if (key.hasItemMeta()) {
            val meta = key.itemMeta
            if (meta != null && meta.hasLore()) {
                val lore = meta.lore
                val configLore = config.getStringList("magickey.key_lore")

                for (i in configLore.indices) {
                    if (configLore[i].contains("{uses}")) {
                        if (i < lore!!.size) {
                            val usesString = lore[i].replace("§.".toRegex(), "") // Remove os códigos de cor

                            val parts = usesString.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (parts.size > 1) {
                                val usesValue = parts[1].trim { it <= ' ' }

                                // Tentar converter para número, se falhar considerar como "Ilimitado"
                                try {
                                    val uses = usesValue.toInt()
                                    return uses
                                } catch (e: NumberFormatException) {
                                    // Se houver texto, considerar como ilimitado
                                    return -1
                                }
                            }
                        }
                    }
                }
            }
        }
        return 0 // Retorna 0 se a chave não tiver a lore esperada
    }

    private fun updateUsesInKey(key: ItemStack, uses: Int) {
        if (key.hasItemMeta()) {
            val meta = key.itemMeta
            if (meta != null && meta.hasLore()) {
                val lore = meta.lore
                val configLore = config.getStringList("magickey.key_lore")

                for (i in configLore.indices) {
                    if (configLore[i].contains("{uses}")) {
                        if (i < lore!!.size) {
                            val formattedUses =
                                configLore[i].replace("{uses}", if (uses > 0) uses.toString() else "§cIlimitado")
                            lore[i] = formattedUses.replace("&", "§") // Aplicar cores
                            meta.lore = lore
                            key.setItemMeta(meta)
                            break
                        }
                    }
                }

                // Remover o item se os usos chegarem a 0
                if (uses == 0) {
                    // Obter o jogador que está segurando a chave
                    var player: Player? = null
                    for (p in Bukkit.getOnlinePlayers()) {
                        if (p.inventory.itemInMainHand == key) {
                            player = p
                            break
                        }
                    }
                    player?.inventory?.setItemInMainHand(ItemStack(Material.AIR))
                }
            }
        }
    }

    private fun saveLocationInKeyLore(key: ItemStack, location: Location, player: Player) {
        val meta = key.itemMeta
        val lore: MutableList<String> = ArrayList()
        val configLore = config.getStringList("magickey.key_lore")

        // Obter o ID da chave
        val keyId: String? = NexoItems.idFromItem(key)
        var uses = 0

        // Verificar a configuração para definir os usos
        val keyConfigs = config.getStringList("magickey.key_id")
        for (keyConfig in keyConfigs) {
            val parts = keyConfig.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size > 0 && parts[0] == keyId) {
                uses = parts[3].trim { it <= ' ' }.toInt()
                break
            }
        }

        for (line in configLore) {
            var line = line
            line = line.replace("{player}", player.name)
                .replace(
                    "{location}",
                    String.format(
                        "%s, %d, %d, %d",
                        location.world.name,
                        location.blockX,
                        location.blockY,
                        location.blockZ
                    )
                )
                .replace("{uses}", if (uses > 0) uses.toString() else "Ilimitado")
                .replace("&", "§") // Aplicar cores
            lore.add(line)
        }

        meta.lore = lore
        key.setItemMeta(meta)
    }

    private fun addEnchantmentEffect(key: ItemStack?) {
        if (key == null || key.type == Material.AIR) {
            return  // No item to enchant
        }

        val meta = key.itemMeta
        if (meta != null) {
            // Add the Luck enchantment
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)

            // Hide the enchantment from the item tooltip
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)

            // Set the updated meta back to the item
            key.setItemMeta(meta)
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (teleportingPlayers.containsKey(player)) {
            val initialLocation = teleportingPlayers[player]
            if (initialLocation!!.distance(event.to) > 0.1) {
                teleportingPlayers.remove(player)
                player.sendMessage("§cTeleporte cancelado porque você se moveu.")
            }
        }
    }

    companion object {
        fun isMagicKeyEnabled(plugin: JavaPlugin): Boolean {
            return plugin.config.getBoolean("magickey.status", true)
        }
    }
}