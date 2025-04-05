package org.Bitello.essentialsMagic.features.magicfire

import com.sk89q.worldguard.LocalPlayer
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.ApplicableRegionSet

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User

import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.features.magicfire.gui.MagicFire_TpMenu_Manage as tp_menu
import org.Bitello.essentialsMagic.core.apis.WorldGuardManager as wgm
//import org.Bitello.essentialsMagic.features.magicfire.MagicFire_Commands_Manager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

import java.util.*

class MagicFireManager(private val plugin: EssentialsMagic) : Listener {
    private val mfMySQL: Magicfire_DataBase_Manager = Magicfire_DataBase_Manager(plugin)
    private val tpMenu: tp_menu = tp_menu(plugin, mfMySQL)
    val pendingPortals: MutableMap<Player, Location> = HashMap()
    private val awaitingPortalName: MutableMap<UUID, Boolean> = HashMap()
    private val playerLocations: MutableMap<UUID, Location> = HashMap()

    fun initialize() {
        val message = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[EssentialsMagic] MagicFire has been enabled.").color(NamedTextColor.DARK_PURPLE))
        Bukkit.getConsoleSender().sendMessage(message)

        // Registrar o handler
        Bukkit.getPluginManager().registerEvents(this, plugin)

        //MagicFire_Commands_Manager(plugin, plugin.configManager, mfMySQL)
    }

    @EventHandler
    fun onFurniturePlace(event: NexoFurniturePlaceEvent) {
        Bukkit.getConsoleSender().sendMessage("[EssentialsMagic] Furniture placed: ${event.mechanic.itemID}")
        if (!plugin.configManager.isMagicFireEnabled()) return

        val portalIds = plugin.configManager.getMagicFirePortalIds()

        val itemId: String = event.mechanic.itemID
        if (portalIds.contains(itemId)) {
            event.isCancelled = true // Cancelar a colocação padrão
            val player: Player = event.player
            val location: Location = event.block.location
            val localPlayer: LocalPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
            val set: ApplicableRegionSet = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer.createQuery().getApplicableRegions(localPlayer.location)

            if (!wgm.isRegionFlagAllowed(location, wgm.MAGIC_FIRE_FLAG)) {
                player.sendMessage("§cVocê não tem permissão para colocar um portal aqui.")
                return
            }

            if (mfMySQL.isPortalNearby(location, 2)) {
                player.sendMessage("§cNão é possível criar um portal tão próximo de outro!")
                return
            }

            pendingPortals[player] = location
            awaitingPortalName[player.uniqueId] = true
            playerLocations[player.uniqueId] = player.location
            player.sendMessage("§aPor favor, digite o nome do portal no chat. Você tem 30 segundos para responder.")
            PortalNameTimeoutTask(this, player).runTaskLater(plugin, 600L)

            // Recolocar a mobília com o yaw correto
            placeFurniture(itemId, location, player)

            // Remover uma unidade do item da mão do jogador
            updatePlayerInventory(player)
        }
    }

    private fun hasPermission(player: Player): Boolean {
        val luckPerms = LuckPermsProvider.get()
        val user: User? = luckPerms.userManager.getUser(player.uniqueId)
        return user?.cachedData?.permissionData?.checkPermission("EssentialsMagic.MagicFire.ByPass")?.asBoolean() ?: player.isOp
    }

    private fun placeFurniture(itemId: String, location: Location, player: Player) {
        val yaw = player.location.yaw
        Bukkit.getScheduler().runTask(plugin, Runnable {
            NexoFurniture.place(itemId, location, yaw, BlockFace.UP)
        })
    }

    private fun updatePlayerInventory(player: Player) {
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (!isMagicFireEnabled(plugin)) return

        val player = event.player
        if (awaitingPortalName.getOrDefault(player.uniqueId, false)) {
            event.isCancelled = true
            val portalName = event.message.trim()
            val location = pendingPortals[player]

            awaitingPortalName.remove(player.uniqueId)
            pendingPortals.remove(player)
            playerLocations.remove(player.uniqueId)

            if (mfMySQL.verifyAndInsertPortal(
                    player,
                    player.uniqueId.toString(),
                    portalName,
                    player.name,
                    "Um portal mágico criado por " + player.name,
                    "Outros",
                    "stone",
                    location!!.world.name,
                    location.x,
                    location.y,
                    location.z,
                    1,
                    "",
                    0,
                    "default",
                    player.location.yaw
                )
            ) {
                player.sendMessage("§aPortal criado com sucesso!")
            } else {
                player.sendMessage("§cVocê já possui o número máximo de portais.")
            }
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!isMagicFireEnabled(plugin)) return

        val player = event.player
        if (awaitingPortalName.getOrDefault(player.uniqueId, false)) {
            val initialLocation = playerLocations[player.uniqueId]
            if (initialLocation != null && initialLocation != player.location) {
                awaitingPortalName.remove(player.uniqueId)
                playerLocations.remove(player.uniqueId)
                pendingPortals.remove(player)
                player.sendMessage("§cVocê se moveu. A criação do portal foi cancelada.")
            }
        }
    }

    @EventHandler
    fun onFurnitureBreak(event: NexoFurnitureBreakEvent) {
        if (!isMagicFireEnabled(plugin)) return

        val portalIds = plugin.configManager.getMagicFirePortalIds()
        if (portalIds.contains(event.mechanic.itemID)) {
            val location: Location = event.baseEntity.location
            val player: Player = event.player

            if (!mfMySQL.isPortalNearby(location, 2)) {
                return
            }

            mfMySQL.deleteNearbyPortal(location, 2)
            event.player.sendMessage("§cPortal removido da rede!")
        }
    }

    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        if (!isMagicFireEnabled(plugin)) return

        val player: Player = event.player
        val mechanicItemId: String = event.mechanic.itemID
        val portalIds = plugin.configManager.getMagicFirePortalIds()

        if (portalIds.contains(mechanicItemId)) {
            val itemInHand = player.inventory.itemInMainHand
            val portalKeyId = plugin.configManager.getMagicFirePortalKeyId()

            if (portalKeyId == NexoItems.idFromItem(itemInHand)) {
                player.sendMessage("§aVocê interagiu com o portal com a chave correta.")
                val fireLocation: Location = event.baseEntity.location
                tpMenu.openMenu(player, fireLocation)
            } else {
                player.sendMessage("§cVocê precisa da chave correta para interagir com este portal.")
            }
        }
    }

    class PortalNameTimeoutTask(private val magicFire: MagicFireManager, private val player: Player) : BukkitRunnable() {
        override fun run() {
            if (magicFire.awaitingPortalName.getOrDefault(player.uniqueId, false)) {
                magicFire.awaitingPortalName.remove(player.uniqueId)
                magicFire.playerLocations.remove(player.uniqueId)
                magicFire.pendingPortals.remove(player)
                player.sendMessage("§cTempo esgotado para nomear o portal.")
            }
        }
    }

    companion object {
        fun isMagicFireEnabled(plugin: JavaPlugin): Boolean {
            return plugin.config.getBoolean("magicfire.status", true)
        }
    }
}