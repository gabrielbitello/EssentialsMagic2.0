package org.Bitello.essentialsMagic.features.magicfire.gui

import org.Bitello.essentialsMagic.features.magicfire.Magicfire_DataBase_Manager
import org.Bitello.essentialsMagic.common.colorize

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.NexoItems

import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

import java.util.*

import kotlin.math.ceil
import kotlin.math.min

class MagicFire_TpMenu_Manage(private val plugin: JavaPlugin, private val mfMySQL: Magicfire_DataBase_Manager) : Listener {
    private val config = plugin.config
    private var fireLocation: Location? = null

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun openMenu(player: Player, fireLocation: Location?) {
        this.fireLocation = fireLocation
        val menuTitle: String = config.getString("magicfire.menu.title", "&cMenu").colorize() // Título do menu
        val menuSize = config.getInt("magicfire.menu.size", 54) // Tamanho do menu

        val menu = Bukkit.createInventory(null, menuSize, menuTitle)

        val buttons = config.getMapList("magicfire.menu.buttons")
        for (button in buttons) {
            // Processa cada botão e adiciona ao menu
            addButton(menu, button)
        }

        player.openInventory(menu)
    }

    private fun addButton(menu: Inventory, buttonConfig: Map<*, *>) {
        for (key in buttonConfig.keys) {
            val config = buttonConfig[key] as Map<*, *>?

            val materialName = config!!["material"] as String?
            val displayName = config["name"] as String?
            val loreList = config["lore"] as List<String>?
            val slot = config["slot"] as Int?
            val action = config["action"] as String?

            if (materialName == null || displayName == null || loreList == null || slot == null || action == null) {
                plugin.logger.severe("Invalid button configuration: $config")
                continue
            }

            var item = getItem(materialName)
            if (item == null) {
                item = ItemStack(Material.STONE) // Fallback para pedra
            }

            val meta = item.itemMeta
            if (meta != null) {
                meta.setDisplayName((displayName).colorize())
                val lore: MutableList<String> = ArrayList()
                for (loreLine in loreList) {
                    lore.add((loreLine).colorize()) // Aplica cor nas linhas de lore
                }
                meta.lore = lore

                // Armazena a ação no PersistentDataContainer
                meta.persistentDataContainer.set(NamespacedKey(plugin, "action"), PersistentDataType.STRING, action)
                item.setItemMeta(meta)
            }

            menu.setItem(slot, item)
        }
    }

    private fun getItem(materialName: String): ItemStack? {
        val nexoPlugin = Bukkit.getPluginManager().getPlugin("Nexo")
        var item: ItemStack? = null

        if (nexoPlugin != null && nexoPlugin.isEnabled) {
            // Se Nexo estiver ativo, tenta buscar o item pelo ID do Oraxen
            val nexoItem = NexoItems.itemFromId(materialName)
            if (nexoItem != null) {
                item = nexoItem.build()
            }
        }

        if (item == null) {
            // Tenta buscar o material padrão do Minecraft
            try {
                val material = Material.valueOf(materialName.uppercase(Locale.getDefault()))
                item = ItemStack(material)
            } catch (e: IllegalArgumentException) {
                // Se não encontrar o material, retorna nulo
                item = null
            }
        }

        return item
    }

    @EventHandler
    fun onMenuClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        // Verifica se o menu é o MagicFire
        val title = event.view.title
        if (title != config.getString("magicfire.menu.title").colorize()) return

        event.isCancelled = true // Previne pegar o item

        val currentItem = event.currentItem
        if (currentItem == null || !currentItem.hasItemMeta()) return

        val meta = currentItem.itemMeta
        val action = meta.persistentDataContainer.get(NamespacedKey(plugin, "action"), PersistentDataType.STRING)
        if (action == null || action.isEmpty()) return

        if (action.startsWith("/")) {
            // Trata como comando, executa o comando
            player.performCommand(action.substring(1))
        } else {
            // Trata como menu, abre o menu intermediário
            openIntermediateMenu(player)
        }
    }

    fun openIntermediateMenu(player: Player) {
        try {
            val inv = Bukkit.createInventory(null, 54, "Menu Intermediário")

            // Preencher todos os espaços vazios com vidro cinza claro
            val glassPane = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            val glassMeta = glassPane.itemMeta
            glassMeta.setDisplayName(" ")
            glassPane.setItemMeta(glassMeta)

            for (i in 0 until inv.size) {
                inv.setItem(i, glassPane)
            }

            val listButton = ItemStack(Material.BOOK)
            val listButtonMeta = listButton.itemMeta
            listButtonMeta.setDisplayName("§aLista Completa de Portais")
            listButton.setItemMeta(listButtonMeta)
            inv.setItem(31, listButton)

            val categories = categories
            val categoryIcons = arrayOf(
                Material.DIAMOND,
                Material.EMERALD,
                Material.GOLD_INGOT,
                Material.IRON_INGOT,
                Material.REDSTONE,
                Material.LAPIS_LAZULI,
                Material.QUARTZ,
                Material.COAL
            )

            val categorySlots = intArrayOf(21, 22, 23, 30, 32, 39, 40, 41)
            for (i in categories.indices) {
                val categoryItem = ItemStack(categoryIcons[i])
                val categoryMeta = categoryItem.itemMeta
                categoryMeta.setDisplayName("§b" + categories[i])
                categoryItem.setItemMeta(categoryMeta)
                inv.setItem(categorySlots[i], categoryItem)
            }

            val netherStar = ItemStack(Material.NETHER_STAR)
            val netherStarMeta = netherStar.itemMeta
            netherStarMeta.setDisplayName("§6Slot VIP")
            netherStar.setItemMeta(netherStarMeta)

            for (i in 1..4) {
                inv.setItem(i * 9 + 1, netherStar)
                inv.setItem(i * 9 + 7, netherStar)
            }

            player.openInventory(inv)
        } catch (e: Exception) {
            plugin.logger.severe("An error occurred while opening the intermediate menu: " + e.message)
            e.printStackTrace()
        }
    }

    fun openPortalMenu(player: Player, page: Int, category: String?) {
        var page = page
        try {
            val portals: List<Portal> = if (category == null || category.isEmpty()) {
                mfMySQL.portals
            } else {
                mfMySQL.getPortalsByCategory(category)
            }

            val totalPortals = portals.size
            val totalPages = ceil(totalPortals.toDouble() / 45).toInt()

            if (page > totalPages) {
                page = 1
            } else if (page < 1) {
                page = totalPages
            }

            val portalInv = Bukkit.createInventory(null, 54, "Portais Página $page")
            val glassPane = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            val glassMeta = glassPane.itemMeta
            glassMeta.setDisplayName(" ")
            glassPane.setItemMeta(glassMeta)

            for (i in 45 until portalInv.size) {
                portalInv.setItem(i, glassPane)
            }

            val nextPage = ItemStack(Material.ARROW)
            val nextPageMeta = nextPage.itemMeta
            nextPageMeta.setDisplayName("§aPróxima Página")
            nextPage.setItemMeta(nextPageMeta)
            portalInv.setItem(53, nextPage)

            val prevPage = ItemStack(Material.ARROW)
            val prevPageMeta = prevPage.itemMeta
            prevPageMeta.setDisplayName("§aPágina Anterior")
            prevPage.setItemMeta(prevPageMeta)
            portalInv.setItem(45, prevPage)

            // Adicionar botão para voltar ao menu intermediário
            val backButton = ItemStack(Material.BARRIER)
            val backButtonMeta = backButton.itemMeta
            backButtonMeta.setDisplayName("§cVoltar ao Menu Intermediário")
            backButton.setItemMeta(backButtonMeta)
            portalInv.setItem(49, backButton)

            val start = (page - 1) * 45
            val end = min((start + 45).toDouble(), totalPortals.toDouble()).toInt()

            val prefix = ChatColor.translateAlternateColorCodes(
                '&',
                plugin.config.getString("magicfire.prefix", "&c[Fire]&f")!!
            )

            var slotIndex = 0
            var portalFiltered = false

            for (i in start until end) {
                val portal = portals[i]
                try {
                    if (!portalFiltered && portal.world == fireLocation!!.world.name) {
                        val distance = fireLocation!!.distance(
                            Location(
                                Bukkit.getWorld(portal.world),
                                portal.x,
                                portal.y,
                                portal.z
                            )
                        )
                        if (distance <= 5) {
                            portalFiltered = true
                            continue
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("An error occurred while checking portal distance: " + e.message)
                    e.printStackTrace()
                }

                // Verificar se o ícone é um item do Nexo
                val portalItem = if (NexoItems.exists(portal.icon)) {
                    NexoItems.itemFromId(portal.icon)?.build()
                } else {
                    ItemStack(Material.matchMaterial(portal.icon)!!)
                }

                val portalMeta = portalItem?.itemMeta
                portalMeta?.setDisplayName("§a" + prefix + " " + portal.name)
                val lore: MutableList<String> = ArrayList()
                lore.add("§7" + portal.category)
                lore.add("§7" + portal.description)
                lore.add("§7Tipo: " + portal.type) // Adiciona o tipo do portal ao lore
                portalMeta?.lore = lore
                portalItem?.setItemMeta(portalMeta)
                portalInv.setItem(slotIndex, portalItem)
                slotIndex++
            }

            player.openInventory(portalInv)
        } catch (e: Exception) {
            plugin.logger.severe("An error occurred while opening the portal menu: " + e.message)
            e.printStackTrace()
        }
    }

    @EventHandler
    fun onIntermediateMenuClick(event: InventoryClickEvent) {
        try {
            if (event.view.title == "Menu Intermediário") {
                event.isCancelled = true
                val player = event.whoClicked as Player
                val clickedItem = event.currentItem
                if (clickedItem == null || clickedItem.type == Material.AIR) {
                    return
                }

                if (clickedItem.type == Material.BOOK) {
                    player.closeInventory()
                    openPortalMenu(player, 1, null)
                } else if (clickedItem.type == Material.NETHER_STAR) {
                    player.sendMessage("§6Slot VIP pode ser comprado para destacar seu portal!")
                } else {
                    val category = clickedItem.itemMeta.displayName.substring(2) // Remove §b
                    player.closeInventory()
                    openPortalMenu(player, 1, category)
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("An error occurred while handling intermediate menu click: " + e.message)
            e.printStackTrace()
        }
    }

    @EventHandler
    fun onPortalMenuClick(event: InventoryClickEvent) {
        try {
            if (event.view.title.startsWith("Portais")) {
                event.isCancelled = true
                val player = event.whoClicked as Player
                val clickedItem = event.currentItem
                if (clickedItem == null || clickedItem.type == Material.AIR) {
                    return
                }

                val titleParts = event.view.title.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val currentPage = titleParts[2].toInt()
                val category = if (titleParts.size > 3) titleParts[3] else null

                val portals: List<Portal> =
                    if (category == null) mfMySQL.portals else mfMySQL.getPortalsByCategory(category)
                val totalPages = ceil(portals.size.toDouble() / 45).toInt()

                if (clickedItem.type == Material.ARROW) {
                    val displayName = clickedItem.itemMeta.displayName
                    if (displayName == "§aPróxima Página") {
                        openPortalMenu(player, if (currentPage == totalPages) 1 else currentPage + 1, category)
                    } else if (displayName == "§aPágina Anterior") {
                        openPortalMenu(player, if (currentPage == 1) totalPages else currentPage - 1, category)
                    }
                } else if (clickedItem.type == Material.BARRIER) {
                    openIntermediateMenu(player)
                } else {
                    val prefix = ChatColor.translateAlternateColorCodes(
                        '&',
                        plugin.config.getString("magicfire.prefix", "&c[Fire]&f")!!
                    )
                    val displayName = ChatColor.translateAlternateColorCodes('&', clickedItem.itemMeta.displayName)
                    if (displayName.startsWith(prefix)) {
                        val portalName = displayName.substring(prefix.length + 1) // Remove prefix and space
                        val portal: Portal? = mfMySQL.getPortalByName(portalName)
                        if (portal != null) {
                            mfMySQL.incrementVisits(portal.name)
                            try {
                                val yawString = portal.yaw
                                plugin.logger.info("Yaw value from database: $yawString")
                                val yaw = yawString.toFloat()
                                val portalLocation = Location(
                                    Bukkit.getWorld(portal.world),
                                    portal.x,
                                    portal.y,
                                    portal.z, yaw, player.location.pitch
                                )
                                teleportPlayerToPortal(player, portalLocation, portal.type, portal.getbanned_players())
                            } catch (e: NumberFormatException) {
                                plugin.logger.severe("Invalid yaw value for portal: " + portal.yaw)
                            }
                            player.closeInventory()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("An error occurred while handling portal menu click: " + e.message)
            e.printStackTrace()
        }
    }

    fun teleportPlayerToPortal(player: Player, portalLocation: Location, portalType: String, bannedPlayers: String) {
        try {
            val animationEnabled = plugin.config.getBoolean("magicfire.animation", false)
            val portalIds = plugin.config.getStringList("magicfire.portal_ids")
            val portalAnimations: MutableMap<String, String> = HashMap()

            for (entry in plugin.config.getStringList("magicfire.portal_ids_animation")) {
                val parts = entry.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                Bukkit.getConsoleSender().sendMessage("Parts: " + Arrays.toString(parts))
                if (parts.size == 2) {
                    portalAnimations[parts[0]] = parts[1]
                }
            }

            // Verificar se o jogador está banido do portal
            val bannedPlayersList =
                Arrays.asList(*bannedPlayers.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            if (bannedPlayersList.contains(player.uniqueId.toString())) {
                player.sendMessage("§cVocê está banido deste portal.")
                return
            }

           if (animationEnabled) {
                val portalIdAnimation = portalAnimations[portalType]
                if (portalIdAnimation != null) {
                    NexoFurniture.remove(portalLocation, null)
                    NexoFurniture.place(portalIdAnimation, portalLocation, portalLocation.yaw, BlockFace.UP)

                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        NexoFurniture.remove(portalLocation, null)
                        NexoFurniture.place(portalType, portalLocation, portalLocation.yaw, BlockFace.UP)
                    }, 80L)
                }
            }

            val playerLocation = portalLocation.clone()
            playerLocation.pitch = player.location.pitch // Manter o pitch do jogador
            playerLocation.yaw = (portalLocation.yaw + 180) % 360 // Inverter o yaw
            player.teleport(playerLocation) // Teletransportar o jogador para a nova localização com yaw ajustado
        } catch (e: Exception) {
            plugin.logger.severe("An error occurred while teleporting the player to the portal: " + e.message)
            e.printStackTrace()
        }
    }

    class Portal(
        val name: String,
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        var visits: Int,
        val icon: String,
        val category: String,
        val description: String,
        val type: String,
        val yaw: String,
        private val banned_players: String
    ) {
        fun getbanned_players(): String {
            return banned_players
        }
    }

    companion object {
        val categories: List<String>
            get() = mutableListOf("Vila", "Loja", "Build", "Farmes", "PVP", "Clan", "Outros", "Museu")
    }
}