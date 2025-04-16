package org.Bitello.essentialsMagic.features.magictear.gui

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.NexoItems.idFromItem
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.common.craft.CraftManager
import org.Bitello.essentialsMagic.common.craft.CraftGuiManager
import org.Bitello.essentialsMagic.features.magictear.MagicTearManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import kotlin.math.min

class MagicTear_Menu_Manager(
    plugin: EssentialsMagic,
    private val magicTearManager: MagicTearManager
) : CraftGuiManager(plugin, magicTearManager, "Magic Tear") {

    override fun openMenu(player: Player, location: Location) {
        // Implementação específica para o Magic Tear
        val title = "§5§lMagic Tear Crafting"
        val inventory = createInventory(title)

        // Configuração inicial do inventário
        setupInitialInventory(inventory)

        // Verificar se há um craft em andamento
        val craft = magicTearManager.getActiveCraft(location)
        if (craft != null) {
            // Se existe um craft em andamento, mostrar a interface de progresso
            setupProgressInventory(inventory, craft)
        }

        // Armazenar a referência para o inventário e a localização
        player.openInventory(inventory)
        playerMenuLocations[player.uniqueId] = location
        openMenus[player.uniqueId] = inventory
    }

    private fun setupInitialInventory(inventory: Inventory) {
        // Configurar slots com vidros decorativos
        for (i in 0 until inventory.size) {
            if (i !in listOf(SLOT_ITEM_CENTRAL, SLOT_ITEM_SUPERIOR, SLOT_ITEM_INFERIOR, SLOT_RESULTADO, SLOT_CONFIRMAR)) {
                inventory.setItem(i, ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
                    itemMeta = itemMeta?.apply { setDisplayName(" ") }
                })
            }
        }

        // Configurar slots de itens
        inventory.setItem(SLOT_ITEM_CENTRAL, ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§cItem Central §7(Obrigatório)") }
        })

        inventory.setItem(SLOT_ITEM_SUPERIOR, ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§eItem Superior §7(Opcional)") }
        })

        inventory.setItem(SLOT_ITEM_INFERIOR, ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§eItem Inferior §7(Opcional)") }
        })
        inventory.setItem(SLOT_RESULTADO, ItemStack(Material.LIME_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§aCombine itens no Tear magico para criar novos itens!") }
        })

        // Configurar botão de confirmar
        inventory.setItem(SLOT_CONFIRMAR, ItemStack(Material.EMERALD).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§a§lConfirmar Craft") }
        })
    }

    private fun setupProgressInventory(inventory: Inventory, craft: CraftManager.ActiveCraft) {
        // Limpar slots de itens
        inventory.setItem(SLOT_ITEM_CENTRAL, null)
        inventory.setItem(SLOT_ITEM_SUPERIOR, null)
        inventory.setItem(SLOT_ITEM_INFERIOR, null)

        // Informações do craft em andamento
        val resultItem = NexoItems.itemFromId(craft.resultItemId)?.build()
        if (resultItem != null) {
            resultItem.amount = craft.quantity
            inventory.setItem(SLOT_ITEM_SUPERIOR, resultItem)
        }

        // Criar barra de progresso
        updateProgressBar(inventory, craft, 10, 16)

        inventory.setItem(SLOT_ITEM_INFERIOR, ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§eItem Inferior §7(Opcional)") }
        })

        // Atualizar botão de confirmar para mostrar tempo restante, item e quantidade
        inventory.setItem(SLOT_CONFIRMAR, ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§e§lTempo Restante: §f" + craft.formattedTimeRemaining)
                lore = listOf(
                    "§7Progresso: §a${(craft.progress * 100).toInt()}%",
                    "§7Quantidade: §f${craft.quantity}"
                )
            }
        })
    }

    override fun updateOpenMenus() {
        for ((playerId, inv) in openMenus) {
            val player = Bukkit.getPlayer(playerId) ?: continue
            val location = playerMenuLocations[playerId] ?: continue

            // Obter o craft ativo na localização
            val craft = magicTearManager.getActiveCraft(location)

            if (craft != null) {
                // Atualizar a barra de progresso
                updateProgressBar(inv, craft, 10, 16)

                // Atualizar o botão de tempo restante
                inv.setItem(SLOT_CONFIRMAR, ItemStack(Material.CLOCK).apply {
                    itemMeta = itemMeta?.apply {
                        setDisplayName("§e§lTempo Restante: §f" + craft.formattedTimeRemaining)
                        lore = listOf("§7Progresso: §a${(craft.progress * 100).toInt()}%")
                    }
                })

                // Se o craft está completo, mostrar botão para coletar
                if (craft.isComplete) {
                    inv.setItem(SLOT_CONFIRMAR, ItemStack(Material.CHEST).apply {
                        itemMeta = itemMeta?.apply {
                            setDisplayName("§a§lColetar Itens")
                            lore = listOf("§7Clique para coletar os itens craftados!")
                        }
                    })
                }
            }
        }
    }

    override fun createInventory(title: String): Inventory {
        return Bukkit.createInventory(null, 27, title)
    }

    override fun updateProgressBar(inventory: Inventory, craft: CraftManager.ActiveCraft?, slotStart: Int, slotEnd: Int) {
        if (craft == null) return

        val totalSlots = slotEnd - slotStart + 1
        val filledSlots = (craft.progress * totalSlots).toInt()

        for (i in 0 until totalSlots) {
            val slot = slotStart + i
            val material = if (i < filledSlots) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE

            inventory.setItem(slot, ItemStack(material).apply {
                itemMeta = itemMeta?.apply { setDisplayName("§7Progresso: §a${(craft.progress * 100).toInt()}%") }
            })
        }
    }

    override fun handleInventoryClick(player: Player, event: InventoryClickEvent) {
        val slot = event.rawSlot
        val inventory = event.inventory
        val cursor = event.cursor ?: ItemStack(Material.AIR)
        val clickedItem = event.currentItem

        val tearLocation = playerMenuLocations[player.uniqueId] ?: return

        // Ignorar interações no SLOT_RESULTADO
        if (slot == SLOT_RESULTADO) {
            event.isCancelled = true
            return
        }

        // Verificar se há um craft em andamento
        val activeCraft = magicTearManager.getActiveCraft(tearLocation)

        if (activeCraft != null && activeCraft.isComplete && slot == SLOT_CONFIRMAR) {
            // Coletar os itens do craft
            val resultItem = NexoItems.itemFromId(activeCraft.resultItemId)?.build()
            if (resultItem != null) {
                resultItem.amount = activeCraft.quantity

                // Tentar dar o item para o jogador
                val leftover = player.inventory.addItem(resultItem)

                // Se não conseguir adicionar tudo, dropar o resto
                if (leftover.isNotEmpty()) {
                    for (item in leftover.values) {
                        player.world.dropItemNaturally(player.location, item)
                    }
                }

                // Remover o craft
                magicTearManager.removeCraft(tearLocation)
                inventory.setItem(SLOT_ITEM_SUPERIOR, null)
                inventory.setItem(SLOT_ITEM_CENTRAL, null)
                inventory.setItem(SLOT_ITEM_INFERIOR, null)

                // Reabrir o menu
                openMenu(player, tearLocation)

                // Enviar mensagem de sucesso
                player.sendMessage(configManager.getTearMessage("craft_collected", "§aVocê coletou os itens do craft!"))
            }
            return
        }

        // Se há um craft em andamento e não está completo, não permitir interação
        if (activeCraft != null && !activeCraft.isComplete) {
            return
        }

        // Permitir colocar/retirar itens dos slots específicos
        if (slot in listOf(SLOT_ITEM_CENTRAL, SLOT_ITEM_SUPERIOR, SLOT_ITEM_INFERIOR)) {
            handleItemSlotInteraction(player, inventory, slot, clickedItem, cursor)
        }
        // Processamento do botão de confirmar
        else if (slot == SLOT_CONFIRMAR) {
            handleConfirmButton(player, inventory, tearLocation)
        }
    }

    private fun handleItemSlotInteraction(player: Player, inventory: Inventory, slot: Int, clickedItem: ItemStack?, cursor: ItemStack) {
        // Se o jogador está tentando colocar um item no slot (tem item no cursor)
        if (!cursor.type.isAir) {
            // Se o slot está vazio ou tem vidro decorativo
            if (clickedItem == null || clickedItem.type.isAir ||
                clickedItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {

                // Colocar o item do cursor no slot
                val itemToPlace = cursor.clone()
                inventory.setItem(slot, itemToPlace)

                // Limpar o cursor do jogador
                player.setItemOnCursor(null)

                // Atualizar o inventário do jogador
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.updateInventory()
                })
            }
            // Se o slot já tem um item (que não é vidro decorativo), trocar os itens
            else if (clickedItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
                val tempItem = clickedItem.clone()
                inventory.setItem(slot, cursor.clone())
                player.setItemOnCursor(tempItem)

                // Atualizar o inventário do jogador
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.updateInventory()
                })
            }
        }
        // Se o jogador está tentando pegar um item do slot (cursor vazio)
        else if (cursor.type.isAir && clickedItem != null &&
            clickedItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {

            // Pegar o item do slot
            player.setItemOnCursor(clickedItem.clone())

            // Limpar o slot
            inventory.setItem(slot, null)

            // Colocar o vidro decorativo de volta
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Se o slot ainda estiver vazio (não foi modificado por outro evento)
                if (inventory.getItem(slot) == null || inventory.getItem(slot)?.type?.isAir == true) {
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
                player.updateInventory()
            })
        }
    }

    private fun handleConfirmButton(player: Player, inventory: Inventory, tearLocation: Location) {
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
        consumeCraftItems(inventory, craftId, maxQuantity, tearLocation)

        // Iniciar o craft
        val success = magicTearManager.startCraft(tearLocation, craftId, resultItemId, maxQuantity)
        if (success) {
            // Obter o nome do item para a mensagem
            val resultPreview = NexoItems.itemFromId(resultItemId)!!.build()
            val itemName = if (resultPreview.itemMeta.hasDisplayName()) resultPreview.itemMeta.displayName else resultItemId

            // Obter o tempo de craft
            val craftTime = configManager.getCraftTime(craftId) * maxQuantity
            val formattedTime = formatTime(craftTime)

            // Enviar mensagem personalizada
            val message = configManager.getTearMessage("craft_started", "§aCraft iniciado com sucesso!")
                .replace("%item%", itemName)
                .replace("%quantity%", maxQuantity.toString())
                .replace("%time%", formattedTime)

            player.sendMessage(message)

            // Reabrir o menu para atualizar o estado do craft
            openMenu(player, tearLocation)
        } else {
            player.sendMessage(configManager.getTearMessage("error_starting_craft", "§cErro ao iniciar o craft!"))
        }
    }

    private fun checkValidCraft(centralItem: ItemStack, superiorItem: ItemStack?, inferiorItem: ItemStack?): String? {
        // Verificar se o item central é um painel de vidro decorativo
        if (centralItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
            return null
        }

        // Verificar se o item superior é um painel de vidro decorativo
        val validSuperiorItem = if (superiorItem != null &&
            superiorItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
            null
        } else {
            superiorItem
        }

        // Tratar o item inferior como null se for um painel de vidro decorativo
        val validInferiorItem = if (inferiorItem != null &&
            inferiorItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
            null
        } else {
            inferiorItem
        }

        // Obter os IDs dos itens ou usar o tipo como fallback
        val centralId = idFromItem(centralItem) ?: centralItem.type.name
        val superiorId = validSuperiorItem?.let { idFromItem(it) ?: it.type.name }
        val inferiorId = validInferiorItem?.let { idFromItem(it) ?: it.type.name }

        // Verificar se a combinação existe nos crafts
        return configManager.findCraftId(centralId, superiorId, inferiorId)
    }

    private fun calculateMaxCraftQuantity(
        centralItem: ItemStack,
        superiorItem: ItemStack?,
        inferiorItem: ItemStack?,
        craftId: String
    ): Int {
        val materials: Map<String, Int> = getMaterialsForCraft(craftId)

        var maxQuantity = Int.MAX_VALUE

        for ((materialId, requiredAmount) in materials) {
            var availableAmount = 0

            // Verificar o item central
            val centralId = idFromItem(centralItem) ?: centralItem.type.name
            if (centralId == materialId) {
                availableAmount += centralItem.amount
            }

            // Verificar o item superior
            if (superiorItem != null && superiorItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
                val superiorId = idFromItem(superiorItem) ?: superiorItem.type.name
                if (superiorId == materialId) {
                    availableAmount += superiorItem.amount
                }
            }

            // Verificar o item inferior
            if (inferiorItem != null && inferiorItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
                val inferiorId = idFromItem(inferiorItem) ?: inferiorItem.type.name
                if (inferiorId == materialId) {
                    availableAmount += inferiorItem.amount
                }
            }

            // Calcular a quantidade máxima para este material
            if (requiredAmount > 0) {
                val maxForThisMaterial = availableAmount / requiredAmount
                maxQuantity = min(maxQuantity, maxForThisMaterial)
            }
        }

        return if (maxQuantity == Int.MAX_VALUE) 0 else maxQuantity
    }

   private fun giveOrDropLeftover(amount: Int, item: ItemStack, location: Location?) {
        plugin.logger.info("Chamando giveOrDropLeftover para o item: ${item.type} com quantidade: $amount")

        if (amount <= 0) {
            plugin.logger.info("Nenhum item restante para devolver ou dropar.")
            return
        }

        if (location == null) {
            plugin.logger.warning("Localização nula fornecida para giveOrDropLeftover.")
            return
        }

        val playerId = playerMenuLocations.entries.firstOrNull { it.value == location }?.key
        if (playerId == null) {
            return
        }

        val player = Bukkit.getPlayer(playerId)
        if (player == null) {
            plugin.logger.warning("Jogador não encontrado para o UUID: $playerId")
            return
        }

        // Criar o item restante
        val leftoverItem = item.clone().apply { this.amount = amount }

        // Tentar adicionar ao inventário do jogador
        val leftover = player.inventory.addItem(leftoverItem)

       if (leftover.isNotEmpty()) {
           player.sendMessage("§cInventário cheio! Dropando item nas coordenadas do tear.")
           val dropLocation = location.clone().add(0.0, 1.0, 0.0)
           for (leftItem in leftover.values) {
               dropLocation.world.dropItemNaturally(dropLocation, leftItem)
           }
       }
    }

    override fun consumeCraftItems(inventory: Inventory, craftId: String, quantity: Int, location: Location) {
        val materials: Map<String, Int> = getMaterialsForCraft(craftId)

        // Função auxiliar para processar os itens
        fun processItem(slot: Int, slotName: String) {
            val item = inventory.getItem(slot)
            item?.let {
                if (it.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
                    val itemId = idFromItem(it) ?: it.type.name
                    materials[itemId]?.let { requiredAmount ->
                        val totalRequired = requiredAmount * quantity
                        if (totalRequired <= it.amount) {
                            val leftover = it.amount - totalRequired
                            it.amount -= totalRequired
                            if (leftover > 0) {
                                giveOrDropLeftover(leftover, it, location)
                            }
                            if (it.amount <= 0) {
                                inventory.setItem(slot, null)
                            }
                        } else {
                        }
                    } ?: plugin.logger.warning("Material não encontrado nos requisitos do craft para o $slotName.")
                }
            }
        }

        // Processar os itens nos slots
        processItem(SLOT_ITEM_CENTRAL, "Item central")
        processItem(SLOT_ITEM_SUPERIOR, "Item superior")
        processItem(SLOT_ITEM_INFERIOR, "Item inferior")
    }
    override fun getMaterialsForCraft(craftId: String): Map<String, Int> {
        return configManager.getCraftMaterials(craftId)
    }

    override fun handleInventoryClose(player: Player, inventory: Inventory, event: InventoryCloseEvent) {
        val inv = event.inventory
        val location = playerMenuLocations[player.uniqueId] ?: return

        // Verificar se há um craft em andamento
        val activeCraft = magicTearManager.getActiveCraft(location)
        if (activeCraft != null) {
            return // Não fazer nada se houver um craft em andamento
        }

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
                val itemClone = item.clone() // Clonar o ItemStack para evitar alterações no original
                val leftover = player.inventory.addItem(itemClone)

                for (leftItem in leftover.values) {
                    player.world.dropItemNaturally(player.location, leftItem)
                }
            }
        }
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