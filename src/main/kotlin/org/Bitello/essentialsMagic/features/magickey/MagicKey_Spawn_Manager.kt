package org.Bitello.essentialsMagic.features.magickey

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.core.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.*

class MagicKey_Spawn_Manager(
    private val plugin: EssentialsMagic,
    private val configManager: ConfigManager,
) : Listener, CommandExecutor, TabCompleter {
    private val cooldowns: MutableMap<UUID, Long> = HashMap()

    fun initialize() {
        val message = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[EssentialsMagic] Spawn Manager has been enabled.").color(NamedTextColor.DARK_PURPLE))
        Bukkit.getConsoleSender().sendMessage(message)

        // Registra o listener de eventos
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Registra os comandos
        registerCommand("setspawn")
        registerCommand("spawn")
    }

    private fun registerCommand(name: String) {
        try {
            val commandMap = plugin.server.commandMap
            val command = object : Command(name) {
                override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
                    return this@MagicKey_Spawn_Manager.onCommand(sender, this, commandLabel, args)
                }

                override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
                    return this@MagicKey_Spawn_Manager.onTabComplete(sender, this, alias, args) ?: emptyList()
                }
            }

            command.description = "Comando de spawn do EssentialsMagic"
            command.usage = "/$name"

            commandMap.register(plugin.name.lowercase(), command)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao registrar o comando $name: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.")
            return true
        }

        if (label.equals("setspawn", ignoreCase = true)) {
            return handleSetSpawnCommand(sender)
        } else if (label.equals("spawn", ignoreCase = true)) {
            return handleSpawnCommand(sender)
        }

        return true
    }

    private fun handleSetSpawnCommand(player: Player): Boolean {
        val config = plugin.config
        if (!config.getBoolean("tp_commands.spawn", true)) {
            player.sendMessage("§cO comando de spawn está desativado.")
            return true
        }

        if (!plugin.luckPerms.hasPermission(player, "EssentialsMagic.Spawn.Set")) {
            player.sendMessage("§cVocê não tem permissão para definir o spawn.")
            return true
        }

        // Coletar o mundo e as coordenadas do jogador
        val world = player.world
        val location = player.location
        val x = Math.round(location.x).toDouble()
        val y = Math.round(location.y).toDouble()
        val z = Math.round(location.z).toDouble()
        val yaw = Math.round(location.yaw).toFloat()
        val pitch = Math.round(location.pitch).toFloat()

        // Salvar as informações no arquivo de configuração
        config.set("tp_commands.spawn_cords.world", world.name)
        config.set("tp_commands.spawn_cords.x", x)
        config.set("tp_commands.spawn_cords.y", y)
        config.set("tp_commands.spawn_cords.z", z)
        config.set("tp_commands.spawn_cords.yaw", yaw)
        config.set("tp_commands.spawn_cords.pitch", pitch)
        plugin.saveConfig()

        player.sendMessage("§aLocalização de spawn definida para: §f" +
                world.name + " " + x + " " + y + " " + z + " " + yaw + " " + pitch)
        return true
    }

    private fun handleSpawnCommand(player: Player): Boolean {
        val config = plugin.config
        if (!config.getBoolean("tp_commands.spawn", true)) {
            player.sendMessage("§cO comando de spawn está desativado.")
            return true
        }

        // Verificar cooldown e bypass
        if (!plugin.luckPerms.hasPermission(player, "EssentialsMagic.Spawn.byPass")) {
            val cooldownTime = config.getInt("tp_commands.spawn_cooldown", 5) * 1000L
            val lastUsed = cooldowns.getOrDefault(player.uniqueId, 0L)
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastUsed < cooldownTime) {
                val remainingTime = ((cooldownTime - (currentTime - lastUsed)) / 1000) + 1
                player.sendMessage("§cVocê deve esperar mais §f" + remainingTime + " §csegundos antes de usar este comando novamente.")
                return true
            }

            cooldowns[player.uniqueId] = currentTime
        }

        val worldName = config.getString("tp_commands.spawn_cords.world")
        if (worldName == null) {
            player.sendMessage("§cO spawn ainda não foi definido.")
            return true
        }

        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            player.sendMessage("§cO mundo do spawn não existe mais.")
            return true
        }

        val x = config.getDouble("tp_commands.spawn_cords.x")
        val y = config.getDouble("tp_commands.spawn_cords.y")
        val z = config.getDouble("tp_commands.spawn_cords.z")
        val yaw = config.getDouble("tp_commands.spawn_cords.yaw").toFloat()
        val pitch = config.getDouble("tp_commands.spawn_cords.pitch").toFloat()

        val spawnLocation = Location(world, x, y, z, yaw, pitch)
        player.teleport(spawnLocation)
        player.sendMessage("§aTeleportado para o spawn.")
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String>? {
        return emptyList()
    }

    companion object {
        fun isSpawnEnabled(plugin: EssentialsMagic): Boolean {
            return plugin.config.getBoolean("tp_commands.spawn", true)
        }
    }
}