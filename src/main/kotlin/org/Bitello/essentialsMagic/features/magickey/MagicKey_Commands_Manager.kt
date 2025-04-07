package org.Bitello.essentialsMagic.features.magickey

import org.Bitello.essentialsMagic.features.magickey.gui.MagicKey_Menu_Manager
import org.Bitello.essentialsMagic.EssentialsMagic

import org.Bitello.essentialsMagic.core.apis.WorldGuardManager
import org.Bitello.essentialsMagic.core.config.ConfigManager

import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

import java.util.*

class MagicKey_Commands_Manager(
    private val plugin: EssentialsMagic,
    private val configManager: ConfigManager,
    private val mkMySQL: MagicKey_DataBase_Manager
) : CommandExecutor, TabCompleter {
    private val homeCooldowns: MutableMap<UUID, Long> = HashMap()
    private val homeMenu = MagicKey_Menu_Manager(plugin) // Adiciona a instância de home_menu

    init {
        registerCommand("home")
    }

    private fun registerCommand(name: String) {
        try {
            val commandMap = plugin.server.commandMap
            val command = object : Command(name) {
                override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
                    return this@MagicKey_Commands_Manager.onCommand(sender, this, commandLabel, args)
                }

                override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
                    return this@MagicKey_Commands_Manager.onTabComplete(sender, this, alias, args) ?: emptyList()
                }
            }

            command.description = "Comando principal do MagicKey"
            command.usage = "/$name [subcomando]"
            command.aliases = listOf("mf")

            commandMap.register(plugin.name.lowercase(), command)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao registrar o comando $name: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.")
            return true
        }

        val player = sender
        val playerId = player.uniqueId

        if (!plugin.getConfig().getBoolean("magickey.home", true)) {
            player.sendMessage("§cO comando de home está desativado.")
            return true
        }

        if (args.size == 0) {
            // Teleportar para a home
            teleportToHome(player, playerId)
        } else if (args.size == 1 && args[0].equals("set", ignoreCase = true)) {
            // Definir a home
            setHome(player, playerId)
        } else if (args.size == 1 && args[0].equals("menu", ignoreCase = true)) {
            // Abrir o menu
            homeMenu.openHomeMenu(player)
        } else {
            player.sendMessage("§cUso incorreto do comando. Use /home para teleportar, /home set para definir a home ou /home menu para abrir o menu.")
        }

        return true
    }

    private fun teleportToHome(player: Player, playerId: UUID) {
        if (!EssentialsMagic.instance.luckPerms.hasPermission(player, "EssentialsMagic.MagicKey.home")) {
            player.sendMessage("§cVocê não tem permissão para usar este comando.")
            return
        }

        val cooldown: Long = plugin.getConfig().getInt("magickey.home_cooldown", 5) * 1000L
        val lastUsed = homeCooldowns.getOrDefault(playerId, 0L)
        val timeSinceLastUse = System.currentTimeMillis() - lastUsed

        if (timeSinceLastUse < cooldown && !player.hasPermission("EssentialsMagic.MagicKey.home.byPass")) {
            player.sendMessage("§cVocê deve esperar antes de usar este comando novamente.")
            return
        }

        if (!canTeleportHome(player)) {
            player.sendMessage("§cVocê não pode teleportar para este mundo.")
            return
        }

        val homeLocation: Location? = mkMySQL.getHome(playerId)
        if (homeLocation != null) {
            val worldName = homeLocation.world.name
            val teleportBlacklist: List<String> = plugin.getConfig().getStringList("magickey.world_teleport_blacklist")
            if (teleportBlacklist.contains(worldName) && !player.hasPermission("EssentialsMagic.MagicKey.teleport.byPass")) {
                player.sendMessage("§cVocê não pode teleportar para este mundo.")
                return
            }

            player.teleport(homeLocation)
            player.sendMessage("§aVocê foi teleportado para sua home.")
            homeCooldowns[playerId] = System.currentTimeMillis()
        } else {
            player.sendMessage("§cVocê ainda não definiu uma home.")
        }
    }

    private fun setHome(player: Player, playerId: UUID) {
        if (!EssentialsMagic.instance.luckPerms.hasPermission(player, "EssentialsMagic.MagicKey.home")) {
            player.sendMessage("§cVocê não tem permissão para usar este comando.")
            return
        }

        if (!canCreateHome(player)) {
            player.sendMessage("§cVocê não pode definir uma home neste mundo.")
            return
        }

        val location = player.location
        val world = location.world.name
        val createBlacklist: List<String> = plugin.getConfig().getStringList("magickey.world_create_blacklist")
        if (createBlacklist.contains(world) && !EssentialsMagic.instance.luckPerms.hasPermission(player,"EssentialsMagic.MagicKey.Create.byPass")) {
            player.sendMessage("§cVocê não pode definir uma home neste mundo.")
            return
        }

        val x = Math.round(location.x).toDouble()
        val y = Math.round(location.y).toDouble()
        val z = Math.round(location.z).toDouble()
        val yaw = Math.round(location.yaw).toFloat()

        mkMySQL.setHome(playerId, world, x, y, z, yaw)
        player.sendMessage("§aSua home foi definida com sucesso.")
    }

    private fun canCreateHome(player: Player): Boolean {
        return WorldGuardManager.isRegionFlagAllowed(player.location, WorldGuardManager.HOME_CREATE_FLAG)
    }

    private fun canTeleportHome(player: Player): Boolean {
        return WorldGuardManager.isRegionFlagAllowed(player.location, WorldGuardManager.HOME_TELEPORT_FLAG)
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String>? {
        if (args.size == 1) {
            return mutableListOf("set", "menu")
        }
        return ArrayList()
    }
}