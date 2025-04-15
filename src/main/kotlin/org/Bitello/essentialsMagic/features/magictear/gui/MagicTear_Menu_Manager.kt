package org.Bitello.essentialsMagic.features.magictear.gui

import com.nexomc.nexo.api.NexoItems.idFromItem
import com.nexomc.nexo.api.NexoItems.itemFromId
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.features.magictear.MagicTearManager
import org.Bitello.essentialsMagic.features.magictear.MagicTearManager.ActiveCraft
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.Listener
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class MagicTear_Menu_Manager(
    private val plugin: EssentialsMagic,
    private val magicTearManager: MagicTearManager
) : Listener {
    private val configManager = plugin.configManager
    private val playerMenuLocations: MutableMap<UUID, Location> = ConcurrentHashMap()
    private val openMenus: MutableMap<UUID, Inventory> = ConcurrentHashMap()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        // Iniciar task para atualizar os menus abertos
        object : BukkitRunnable() {
            override fun run() {
                updateOpenMenus()
            }
        }.runTaskTimer(plugin, 10L, 10L) // Atualizar a cada 0.5 segundos
    }

    fun openMenu(player: Player, location: Location) {
        // Usar o título do menu configurado
        val menuTitle = configManager.getTearMenuTitle()
        val menuSize = configManager.getTearMenuSize()
        val inventory = Bukkit.createInventory(null, menuSize, menuTitle)

        // Verificar se há um craft ativo neste tear
        val activeCraft = magicTearManager.getActiveCraft(location)

        // Preencher o inventário com vidros decorativos
        fillInventory(inventory)

        if (activeCraft != null) {
            // Há um craft em andamento
            updateCraftingProgress(inventory, activeCraft)
        } else {
            // Não há craft em andamento, mostrar slots vazios
            setupEmptyCraftingSlots(inventory)
        }

        player.openInventory(inventory)
        playerMenuLocations[player.uniqueId] = location
        openMenus[player.uniqueId] = inventory
    }

    private fun fillInventory(inventory: Inventory) {
        val glass = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = glass.itemMeta
        meta.setDisplayName(" ")
        glass.setItemMeta(meta)

        for (i in 0..<inventory.size) {
            if (i != SLOT_ITEM_CENTRAL && i != SLOT_ITEM_SUPERIOR && i != SLOT_ITEM_INFERIOR && i != SLOT_RESULTADO && i != SLOT_CONFIRMAR) {
                inventory.setItem(i, glass)
            }
        }
    }

    private fun setupEmptyCraftingSlots(inventory: Inventory) {
        // Slot central (sempre necessário)
        val centralSlot = ItemStack(Material.RED_STAINED_GLASS_PANE)
        val centralMeta = centralSlot.itemMeta
        centralMeta.setDisplayName("§cItem Central §7(Obrigatório)")
        centralSlot.setItemMeta(centralMeta)
        inventory.setItem(SLOT_ITEM_CENTRAL, centralSlot)

        // Slot superior (opcional)
        val superiorSlot = ItemStack(Material.YELLOW_STAINED_GLASS_PANE)
        val superiorMeta = superiorSlot.itemMeta
        superiorMeta.setDisplayName("§eItem Superior §7(Opcional)")
        superiorSlot.setItemMeta(superiorMeta)
        inventory.setItem(SLOT_ITEM_SUPERIOR, superiorSlot)

        // Slot inferior (opcional)
        val inferiorSlot = ItemStack(Material.YELLOW_STAINED_GLASS_PANE)
        val inferiorMeta = inferiorSlot.itemMeta
        inferiorMeta.setDisplayName("§eItem Inferior §7(Opcional)")
        inferiorSlot.setItemMeta(inferiorMeta)
        inventory.setItem(SLOT_ITEM_INFERIOR, inferiorSlot)

        // Slot de resultado
        val resultadoSlot = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        val resultadoMeta = resultadoSlot.itemMeta
        resultadoMeta.setDisplayName("§bResultado")
        resultadoSlot.setItemMeta(resultadoMeta)
        inventory.setItem(SLOT_RESULTADO, resultadoSlot)

        // Botão de confirmar
        val confirmarSlot = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        val confirmarMeta = confirmarSlot.itemMeta
        confirmarMeta.setDisplayName("§aConfirmar Craft")
        confirmarSlot.setItemMeta(confirmarMeta)
        inventory.setItem(SLOT_CONFIRMAR, confirmarSlot)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = event.view.title

        if (title != plugin.configManager.getTearMenuTitle()) return

        // Permitir movimentação no inventário do jogador
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        event.isCancelled = true

        val slot = event.slot
        val currentItem = event.currentItem
        val clickedInventory = event.clickedInventory

        // Delegar a lógica para o método handleInventoryClick
        handleInventoryClick(player, slot, currentItem, clickedInventory!!)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = event.view.title

        if (title != plugin.configManager.getTearMenuTitle()) return

        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val title = event.view.title

        if (title != plugin.configManager.getTearMenuTitle()) return

        closeMenu(player)

        val inv = event.inventory
        val slots = listOf(SLOT_ITEM_CENTRAL, SLOT_ITEM_SUPERIOR, SLOT_ITEM_INFERIOR)

        for (slot in slots) {
            val item = inv.getItem(slot)
            if (item != null && !item.type.isAir &&
                item.type !in listOf(
                    Material.RED_STAINED_GLASS_PANE,
                    Material.YELLOW_STAINED_GLASS_PANE,
                    Material.BARRIER
                )
            ) {
                val leftover = player.inventory.addItem(item)

                for (leftItem in leftover.values) {
                    player.world.dropItemNaturally(player.location, leftItem)
                }
            }
        }
    }

    private fun updateCraftingProgress(inventory: Inventory, activeCraft: ActiveCraft) {
        // Bloquear os slots de ingredientes
        val blockedSlot = ItemStack(Material.BARRIER)
        val blockedMeta = blockedSlot.itemMeta
        blockedMeta.setDisplayName("§cCraft em andamento")
        blockedSlot.setItemMeta(blockedMeta)

        inventory.setItem(SLOT_ITEM_CENTRAL, blockedSlot)
        inventory.setItem(SLOT_ITEM_SUPERIOR, blockedSlot)
        inventory.setItem(SLOT_ITEM_INFERIOR, blockedSlot)

        // Atualizar o botão de confirmar
        val craftingSlot = ItemStack(Material.CLOCK)
        val craftingMeta = craftingSlot.itemMeta
        craftingMeta.setDisplayName("§eCraft em andamento")
        val lore: MutableList<String> = ArrayList()
        lore.add("§7Tempo restante: §f" + activeCraft.formattedTimeRemaining)
        lore.add("§7Progresso: §f" + Math.round(activeCraft.progress * 100) + "%")
        craftingMeta.lore = lore
        craftingSlot.setItemMeta(craftingMeta)
        inventory.setItem(SLOT_CONFIRMAR, craftingSlot)

        // Atualizar o slot de resultado
        val resultItem = itemFromId(activeCraft.resultItemId)!!.build()
        if (resultItem != null) {
            val resultMeta = resultItem.itemMeta
            val resultLore = if (resultMeta.hasLore()) resultMeta.lore else ArrayList()
            resultLore!!.add("")
            resultLore.add("§7Quantidade: §f" + activeCraft.quantity)

            if (activeCraft.isComplete) {
                resultLore.add("§aPronto para coletar!")
            } else {
                resultLore.add("§cTempo restante: §f" + activeCraft.formattedTimeRemaining)
                resultLore.add("§7Progresso: §f" + Math.round(activeCraft.progress * 100) + "%")
            }

            resultMeta.lore = resultLore
            resultItem.setItemMeta(resultMeta)
            resultItem.amount = min(activeCraft.quantity.toDouble(), resultItem.maxStackSize.toDouble()).toInt()
            inventory.setItem(SLOT_RESULTADO, resultItem)
        }
    }

    private fun updateOpenMenus() {
        val iterator: MutableIterator<Map.Entry<UUID, Inventory>> = openMenus.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val playerId = entry.key
            val inventory = entry.value
            val location = playerMenuLocations[playerId]

            if (location == null) {
                iterator.remove()
                continue
            }

            val player = Bukkit.getPlayer(playerId)
            if (player == null || !player.isOnline) {
                iterator.remove()
                playerMenuLocations.remove(playerId)
                continue
            }

            // Verificar se o inventário ainda está aberto
            if (player.openInventory.topInventory != inventory) {
                iterator.remove()
                playerMenuLocations.remove(playerId)
                continue
            }

            // Atualizar o inventário se houver um craft ativo
            val activeCraft = magicTearManager.getActiveCraft(location)
            if (activeCraft != null) {
                updateCraftingProgress(inventory, activeCraft)
            }
        }
    }

    fun handleInventoryClick(player: Player, slot: Int, clickedItem: ItemStack?, inventory: Inventory) {
        val tearLocation = playerMenuLocations[player.uniqueId] ?: return

        val activeCraft = magicTearManager.getActiveCraft(tearLocation)

        // Se houver um craft ativo
        if (activeCraft != null) {
            // Se clicou no slot de resultado e o craft estiver completo
            if (slot == SLOT_RESULTADO && activeCraft.isComplete) {
                // Dar o item ao jogador
                val resultItem = itemFromId(activeCraft.resultItemId)!!.build()
                if (resultItem != null) {
                    // Definir a quantidade após criar o item
                    resultItem.amount = activeCraft.quantity

                    val leftover = player.inventory.addItem(resultItem)

                    // Se não coube tudo no inventário, dropar o restante
                    if (!leftover.isEmpty()) {
                        for (item in leftover.values) {
                            player.world.dropItemNaturally(player.location, item)
                        }
                        player.sendMessage(
                            configManager.getTearMessage(
                                "inventory_full",
                                "§eAlguns itens não couberam no seu inventário e foram dropados."
                            )
                        )
                    }

                    magicTearManager.removeCraft(tearLocation)

                    // Reabrir o menu
                    openMenu(player, tearLocation)

                    // Enviar mensagem de sucesso
                    val itemName =
                        if (resultItem.itemMeta.hasDisplayName()) resultItem.itemMeta.displayName else activeCraft.resultItemId

                    val message = configManager.getTearMessage("craft_completed", "§aO craft de %item% foi concluído!")
                        .replace("%item%", itemName)
                        .replace("%quantity%", activeCraft.quantity.toString())

                    player.sendMessage(message)
                } else {
                    // Mensagem de erro
                    player.sendMessage(
                        configManager.getTearMessage(
                            "error_collecting",
                            "§cErro ao coletar o item do craft!"
                        )
                    )
                    magicTearManager.removeCraft(tearLocation)

                    // Reabrir o menu
                    openMenu(player, tearLocation)
                }
            }
            return
        }

        // Lógica para os slots de crafting
        if (slot in listOf(SLOT_ITEM_CENTRAL, SLOT_ITEM_SUPERIOR, SLOT_ITEM_INFERIOR)) {
            val cursorItem = player.itemOnCursor

            // Se o jogador está tentando colocar um item no slot
            if (cursorItem != null && !cursorItem.type.isAir) {
                // Remover o vidro decorativo
                if (clickedItem != null && clickedItem.type in listOf(
                        Material.RED_STAINED_GLASS_PANE,
                        Material.YELLOW_STAINED_GLASS_PANE
                    )
                ) {
                    inventory.setItem(slot, null)
                }
                // Permitir a colocação do item
                return
            }

            // Se o slot ficou vazio, recolocar o vidro decorativo
            if (clickedItem == null || clickedItem.type.isAir) {
                val glassPane = when (slot) {
                    SLOT_ITEM_CENTRAL -> ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
                        itemMeta = itemMeta?.apply { setDisplayName("§cItem Central §7(Obrigatório)") }
                    }
                    else -> ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
                        itemMeta = itemMeta?.apply { setDisplayName("§eItem §7(Opcional)") }
                    }
                }
                inventory.setItem(slot, glassPane)
            }
            return
        }

        // Se clicou no botão de confirmar
        if (slot == SLOT_CONFIRMAR) {
            // Verificar se há itens nos slots
            val centralItem = inventory.getItem(SLOT_ITEM_CENTRAL)
            val superiorItem = inventory.getItem(SLOT_ITEM_SUPERIOR)
            val inferiorItem = inventory.getItem(SLOT_ITEM_INFERIOR)

            if (centralItem == null || centralItem.type == Material.RED_STAINED_GLASS_PANE) {
                player.sendMessage(
                    configManager.getTearMessage(
                        "missing_central_item",
                        "§cVocê precisa colocar um item central!"
                    )
                )
                return
            }

            // Verificar se o craft é válido
            val craftId = checkValidCraft(centralItem, superiorItem, inferiorItem)
            if (craftId == null) {
                player.sendMessage(
                    configManager.getTearMessage(
                        "invalid_recipe",
                        "§cEsta combinação de itens não é válida!"
                    )
                )
                return
            }

            // Obter o item resultado
            val resultItemId = configManager.getCraftResult(craftId)
            if (resultItemId == null) {
                player.sendMessage(
                    configManager.getTearMessage(
                        "error_getting_result",
                        "§cErro ao obter o resultado do craft!"
                    )
                )
                return
            }

            // Calcular a quantidade máxima que pode ser craftada
            val maxQuantity = calculateMaxCraftQuantity(centralItem, superiorItem, inferiorItem, craftId)
            if (maxQuantity <= 0) {
                player.sendMessage(
                    configManager.getTearMessage(
                        "insufficient_materials",
                        "§cNão há materiais suficientes para este craft!"
                    )
                )
                return
            }

            // Consumir os itens
            consumeCraftItems(inventory, craftId, maxQuantity)

            // Iniciar o craft
            val success = magicTearManager.startCraft(tearLocation, craftId, resultItemId, maxQuantity)
            if (success) {
                // Obter o nome do item para a mensagem
                val resultPreview = itemFromId(resultItemId)!!.build()
                val itemName =
                    if (resultPreview != null && resultPreview.itemMeta.hasDisplayName()) resultPreview.itemMeta.displayName else resultItemId

                // Obter o tempo de craft
                val craftTime = configManager.getCraftTime(craftId) * maxQuantity
                val formattedTime = formatTime(craftTime)

                // Enviar mensagem personalizada
                val message = configManager.getTearMessage("craft_started", "§aCraft iniciado com sucesso!")
                    .replace("%item%", itemName)
                    .replace("%quantity%", maxQuantity.toString())
                    .replace("%time%", formattedTime)

                player.sendMessage(message)

                // Reabrir o menu
                openMenu(player, tearLocation)
            } else {
                player.sendMessage(configManager.getTearMessage("error_starting_craft", "§cErro ao iniciar o craft!"))
            }
        }
    }

    private fun checkValidCraft(centralItem: ItemStack, superiorItem: ItemStack?, inferiorItem: ItemStack?): String? {
        // Obter os IDs dos itens
        val centralId = idFromItem(centralItem)
        val superiorId =
            if (superiorItem != null && superiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) idFromItem(superiorItem) else null
        val inferiorId =
            if (inferiorItem != null && inferiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) idFromItem(inferiorItem) else null

        // Verificar se a combinação existe nos crafts
        return configManager.findCraftId(centralId, superiorId, inferiorId)
    }

    private fun calculateMaxCraftQuantity(
        centralItem: ItemStack,
        superiorItem: ItemStack?,
        inferiorItem: ItemStack?,
        craftId: String
    ): Int {
        val materials: Map<String, Int> = configManager.getCraftMaterials(craftId)

        var maxQuantity = Int.MAX_VALUE

        // Verificar o item central
        val centralId = idFromItem(centralItem)
        if (centralId != null && materials.containsKey(centralId)) {
            val requiredAmount = materials[centralId]!!
            maxQuantity = min(maxQuantity.toDouble(), (centralItem.amount / requiredAmount).toDouble()).toInt()
        }

        // Verificar o item superior
        if (superiorItem != null && superiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) {
            val superiorId = idFromItem(superiorItem)
            if (superiorId != null && materials.containsKey(superiorId)) {
                val requiredAmount = materials[superiorId]!!
                maxQuantity = min(maxQuantity.toDouble(), (superiorItem.amount / requiredAmount).toDouble()).toInt()
            }
        }

        // Verificar o item inferior
        if (inferiorItem != null && inferiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) {
            val inferiorId = idFromItem(inferiorItem)
            if (inferiorId != null && materials.containsKey(inferiorId)) {
                val requiredAmount = materials[inferiorId]!!
                maxQuantity = min(maxQuantity.toDouble(), (inferiorItem.amount / requiredAmount).toDouble()).toInt()
            }
        }

        return maxQuantity
    }

    private fun consumeCraftItems(inventory: Inventory, craftId: String, quantity: Int) {
        val materials: Map<String, Int> = configManager.getCraftMaterials(craftId)

        // Consumir o item central
        val centralItem = inventory.getItem(SLOT_ITEM_CENTRAL)
        if (centralItem != null) {
            val centralId = idFromItem(centralItem)
            if (centralId != null && materials.containsKey(centralId)) {
                val requiredAmount = materials[centralId]!! * quantity
                if (centralItem.amount > requiredAmount) {
                    centralItem.amount = centralItem.amount - requiredAmount
                } else {
                    inventory.setItem(SLOT_ITEM_CENTRAL, null)
                }
            }
        }

        // Consumir o item superior
        val superiorItem = inventory.getItem(SLOT_ITEM_SUPERIOR)
        if (superiorItem != null && superiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) {
            val superiorId = idFromItem(superiorItem)
            if (materials.containsKey(superiorId)) {
                val requiredAmount = materials[superiorId]!! * quantity
                if (superiorItem.amount > requiredAmount) {
                    superiorItem.amount = superiorItem.amount - requiredAmount
                } else {
                    inventory.setItem(SLOT_ITEM_SUPERIOR, null)
                }
            }
        }

        // Consumir o item inferior
        val inferiorItem = inventory.getItem(SLOT_ITEM_INFERIOR)
        if (inferiorItem != null && inferiorItem.type != Material.YELLOW_STAINED_GLASS_PANE) {
            val inferiorId = idFromItem(inferiorItem)
            if (materials.containsKey(inferiorId)) {
                val requiredAmount = materials[inferiorId]!! * quantity
                if (inferiorItem.amount > requiredAmount) {
                    inferiorItem.amount = inferiorItem.amount - requiredAmount
                } else {
                    inventory.setItem(SLOT_ITEM_INFERIOR, null)
                }
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        return if (minutes > 0) {
            minutes.toString() + " min" + (if (remainingSeconds > 0) " $remainingSeconds seg" else "")
        } else {
            "$seconds seg"
        }
    }

    fun closeMenu(player: Player) {
        playerMenuLocations.remove(player.uniqueId)
        openMenus.remove(player.uniqueId)
    }

    companion object {
        // Posições dos slots no inventário
        private const val SLOT_ITEM_CENTRAL = 13
        private const val SLOT_ITEM_SUPERIOR = 4
        private const val SLOT_ITEM_INFERIOR = 22
        private const val SLOT_RESULTADO = 16
        private const val SLOT_CONFIRMAR = 25
    }
}