package org.Bitello.essentialsMagic.features.magicfire

import org.Bitello.essentialsMagic.features.magicfire.gui.MagicFire_TpMenu_Manage as tp_menu
import org.Bitello.essentialsMagic.features.magicfire.Magicfire_DataBase_Manager.PortalInfo
import org.Bitello.essentialsMagic.core.config.ConfigManager
import org.Bitello.essentialsMagic.EssentialsMagic


import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.NexoItems
import org.Bitello.essentialsMagic.core.apis.WorldGuardManager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.CommandExecutor

import java.util.*

class MagicFire_Commands_Manager(
    private val plugin: EssentialsMagic,
    private val configManager: ConfigManager,
    private val mfMySQL: Magicfire_DataBase_Manager
) : CommandExecutor, TabCompleter {

    init {
        registerCommand("magicfire")
    }

    private fun registerCommand(name: String) {
        try {
            val commandMap = plugin.server.commandMap
            val command = object : Command(name) {
                override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
                    return this@MagicFire_Commands_Manager.onCommand(sender, this, commandLabel, args)
                }

                override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
                    return this@MagicFire_Commands_Manager.onTabComplete(sender, this, alias, args) ?: emptyList()
                }
            }

            command.description = "Comando principal do MagicFire"
            command.usage = "/$name [subcomando]"
            command.aliases = listOf("mf")

            commandMap.register(plugin.name.lowercase(), command)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao registrar o comando $name: ${e.message}")
            e.printStackTrace()
        }
    }



    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Este comando só pode ser utilizado por jogadores.")
            return true
        }

        val player: Player = sender
        if (args.isEmpty()) {
            player.sendMessage("Uso: /$label help")
            return true
        }

        when (args[0].lowercase()) {
            "help" -> player.sendMessage("Comando principal do MagicFire:\n" +
                                        "Uso: /magicfire [subcomando]\n" +
                                        "\n" +
                                        "Subcomandos disponíveis:\n" +
                                        "\n" +
                                        "1. **/magicfire help**\n" +
                                        "   - Exibe esta mensagem de ajuda.\n" +
                                        "\n" +
                                        "2. **/magicfire change [opção]**\n" +
                                        "   - Permite alterar uma propriedade ou configuração relacionada ao MagicFire.\n" +
                                        "\n" +
                                        "3. **/magicfire move [localização]**\n" +
                                        "   - Move um ponto ou estrutura mágica para outra localização.\n" +
                                        "\n" +
                                        "4. **/magicfire ban [jogador]**\n" +
                                        "   - Bane um jogador de utilizar portais ou estruturas do MagicFire.\n" +
                                        "\n" +
                                        "5. **/magicfire unban [jogador]**\n" +
                                        "   - Revoga o banimento de um jogador relacionado ao MagicFire.\n" +
                                        "\n" +
                                        "6. **/magicfire icon [ícone]**\n" +
                                        "   - Modifica ou define um ícone visual representando um elemento do MagicFire.\n" +
                                        "\n" +
                                        "7. **/magicfire category [categoria]**\n" +
                                        "   - Define ou organiza elementos do MagicFire em uma categoria específica.\n" +
                                        "\n" +
                                        "---")
            "change" -> handleChangeCommand(player, args)
            "mover" -> handleMoveCommand(player, args)
            "ban" -> handleBanCommand(player, args)
            "unban" -> handleUnbanCommand(player, args)
            "icon" -> handleIconCommand(player, args)
            "categoria" -> handleCategoryCommand(player, args)
            else -> player.sendMessage("Comando desconhecido: ${args[0]}")
        }

        return true
    }


private fun handleChangeCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender
        if (args.size < 2) {
            player.sendMessage("§cUso: /mf change <nome_do_portal>")
            return
        }

        val portalName = args[1]
        val itemInHand = player.inventory.itemInMainHand

        if (itemInHand.type.isAir) {
            player.sendMessage("§cVocê precisa segurar uma mobília de portal válida.")
            return
        }

        val itemId = NexoItems.idFromItem(itemInHand)
        val portalIds = configManager.getConfig().getStringList("magicfire.portal_ids")

        if (!portalIds.contains(itemId)) {
            player.sendMessage("§cVocê precisa segurar uma mobília de portal válida.")
            return
        }

        val portalType = itemId

        /// Verificar se o jogador é o dono do portal e obter yaw e localização
        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        // Recuperar o yaw e a localização do portal
        val yaw: Float = portalInfo.yaw
        val portalLocation = Location(
            Bukkit.getWorld(portalInfo.world),
            portalInfo.x,
            portalInfo.y,
            portalInfo.z
        )

        // Coletar o ID da mobília antiga
        val oldFurnitureId: String
        try {
            oldFurnitureId = NexoFurniture.furnitureMechanic(portalLocation.block)?.itemID ?: run {
                player.sendMessage("§cMobília não encontrada.")
                return
            }
            NexoFurniture.remove(portalLocation, null)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao remover a mobília do portal antigo: " + e.message)
            e.printStackTrace()
            player.sendMessage("§cErro ao remover a mobília do portal antigo.")
            return
        }

        // Colocar a nova mobília no portal
        try {
            NexoFurniture.place(portalType, portalLocation, yaw, BlockFace.UP)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao colocar a mobília do portal na nova localização: " + e.message)
            e.printStackTrace()
            player.sendMessage("§cErro ao colocar a mobília do portal na nova localização.")
            return
        }

        // Remover uma unidade do item da mão do jogador
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }

        // Dar a mobília antiga ao jogador
        val oldFurnitureItem: ItemStack? = NexoItems.itemFromId(oldFurnitureId)?.build()
        if (oldFurnitureItem != null) {
            player.inventory.addItem(oldFurnitureItem)
        } else {
            player.sendMessage("§cErro ao recuperar a mobília antiga.")
        }

        // Atualizar o tipo do portal no banco de dados
        mfMySQL.updatePortalType(player.uniqueId, portalType)

        player.sendMessage("§aTipo de portal atualizado com sucesso.")
    }

    private fun handleMoveCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender

        if (args.size < 2) {
            player.sendMessage("§cUso: /mf mover <nome_do_portal>")
            return
        }

        val portalName = args[1]

        // Verificar se o jogador é o dono do portal
        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        // Verificar se há um portal em um raio de 10 blocos
        val playerLocation = player.location

        if (!WorldGuardManager.isRegionFlagAllowed(playerLocation, WorldGuardManager.MAGIC_FIRE_FLAG)) {
            player.sendMessage("§cVocê não tem permissão para colocar um portal aqui.")
            return
        }

        for (x in -10..10) {
            for (y in -10..10) {
                for (z in -10..10) {
                    val checkLocation = playerLocation.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    val inf = NexoFurniture.isFurniture(checkLocation)
                    if (inf) {
                        player.sendMessage("§cHá um portal muito próximo. Mova-se para um local mais distante.")
                        return
                    }
                }
            }
        }

        // Remover o portal da localização antiga
        val oldLocation = Location(
            Bukkit.getWorld(portalInfo.world),
            portalInfo.x,
            portalInfo.y,
            portalInfo.z
        )
        val oldFurnitureId: String
        try {
            oldFurnitureId = NexoFurniture.furnitureMechanic(oldLocation.block)?.itemID ?: run {
                player.sendMessage("§cMobília não encontrada.")
                return
            }
            NexoFurniture.remove(oldLocation, null)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao remover a mobília do portal antigo: " + e.message)
            e.printStackTrace()
            player.sendMessage("§cErro ao remover a mobília do portal antigo.")
            return
        }

        // Mover o portal para a nova localização
        val newYaw = (player.location.yaw + 180) % 360
        val newLocation = player.location
        try {
            NexoFurniture.place(oldFurnitureId, newLocation, newYaw, BlockFace.UP)
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao colocar a mobília do portal na nova localização: " + e.message)
            e.printStackTrace()
            player.sendMessage("§cErro ao colocar a mobília do portal na nova localização.")
            return
        }

        // Atualizar o banco de dados com as novas coordenadas e yaw
        mfMySQL.updatePortalLocation(player.uniqueId, portalName, newLocation, newYaw)

        player.sendMessage("§aPortal movido com sucesso.")
    }

    private fun handleBanCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender
        if (args.size < 3) {
            player.sendMessage("§cUso: /mf ban <nome_do_portal> <jogador>")
            return
        }

        val portalName = args[1]
        val targetPlayerName = args[2]
        val targetPlayer = Bukkit.getPlayer(targetPlayerName)

        if (targetPlayer == null) {
            player.sendMessage("§cJogador não encontrado.")
            return
        }

        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        val bannedPlayers: MutableList<String> = ArrayList(portalInfo.bannedPlayers.split(","))
        if (bannedPlayers.contains(targetPlayer.uniqueId.toString())) {
            player.sendMessage("§cEste jogador já está banido deste portal.")
            return
        }

        bannedPlayers.add(targetPlayer.uniqueId.toString())
        mfMySQL.updateBannedPlayers(portalName, bannedPlayers.joinToString(","))
        player.sendMessage("§aJogador banido com sucesso.")
    }

    private fun handleUnbanCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender
        if (args.size < 3) {
            player.sendMessage("§cUso: /mf unban <nome_do_portal> <jogador>")
            return
        }

        val portalName = args[1]
        val targetPlayerName = args[2]
        val targetPlayer = Bukkit.getPlayer(targetPlayerName)

        if (targetPlayer == null) {
            player.sendMessage("§cJogador não encontrado.")
            return
        }

        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        val bannedPlayers: MutableList<String> = ArrayList(portalInfo.bannedPlayers.split(","))
        if (!bannedPlayers.contains(targetPlayer.uniqueId.toString())) {
            player.sendMessage("§cEste jogador não está banido deste portal.")
            return
        }

        bannedPlayers.remove(targetPlayer.uniqueId.toString())
        mfMySQL.updateBannedPlayers(portalName, bannedPlayers.joinToString(","))
        player.sendMessage("§aJogador desbanido com sucesso.")
    }

    private fun handleIconCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender
        if (args.size < 2) {
            player.sendMessage("§cUso: /mf icon <nome_do_portal>")
            return
        }

        val portalName = args[1]
        val itemInHand = player.inventory.itemInMainHand

        if (itemInHand.type.isAir) {
            player.sendMessage("§cVocê precisa segurar um item válido.")
            return
        }

        var iconType: String? = NexoItems.idFromItem(itemInHand)
        if (iconType == null) {
            // Se não for um item do Oraxen, use o tipo do item Vanilla
            iconType = itemInHand.type.name
        }

        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        mfMySQL.updatePortalIcon(player.uniqueId, portalName, iconType)
        player.sendMessage("§aÍcone do portal atualizado com sucesso.")
    }

    private fun handleCategoryCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§cApenas jogadores podem executar este comando.")
            return
        }

        val player = sender
        if (args.size < 3) {
            player.sendMessage("§cUso: /mf categoria <nome_do_portal> <categoria>")
            return
        }

        val portalName = args[1]
        val category = args[2]
        val validCategories: List<String> = tp_menu.categories

        if (!validCategories.contains(category)) {
            player.sendMessage(
                "§cCategoria inválida. As categorias válidas são: " + validCategories.joinToString(", ")
            )
            return
        }

        val portalInfo: PortalInfo? = mfMySQL.isPortalOwner(player.uniqueId, portalName)
        if (portalInfo == null) {
            player.sendMessage("§cVocê não é o dono deste portal.")
            return
        }

        mfMySQL.updatePortalCategory(player.uniqueId, portalName, category)
        player.sendMessage("§aCategoria do portal atualizada com sucesso.")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String>? {
        if (args.size == 1) {
            return mutableListOf("change", "mover", "ban", "unban", "icon", "categoria")
        } else if (args.size == 2 && (args[0].equals("change", ignoreCase = true) || args[0].equals(
                "mover",
                ignoreCase = true
            ) || args[0].equals("icon", ignoreCase = true) || args[0].equals("categoria", ignoreCase = true))
        ) {
            // Retornar os nomes dos portais em vez dos IDs
            return configManager.getConfig().getStringList("magicfire.portal_names")
        } else if (args.size == 3 && args[0].equals("categoria", ignoreCase = true)) {
            // Retornar as categorias disponíveis
            return tp_menu.categories
        } else if (args.size == 3 && (args[0].equals("ban", ignoreCase = true) || args[0].equals(
                "unban",
                ignoreCase = true
            ))
        ) {
            // Retornar os nomes dos jogadores online
            val playerNames: MutableList<String> = ArrayList()
            for (player in Bukkit.getOnlinePlayers()) {
                playerNames.add(player.name)
            }
            return playerNames
        }
        return ArrayList()
    }
}