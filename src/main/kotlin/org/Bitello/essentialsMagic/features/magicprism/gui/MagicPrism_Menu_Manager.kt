package org.Bitello.essentialsMagic.features.magicprism.gui

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.NexoItems.idFromItem
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.common.craft.CraftManager
import org.Bitello.essentialsMagic.common.craft.CraftGuiManager
import org.Bitello.essentialsMagic.features.magicprism.MagicPrismManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import kotlin.math.min

class MagicPrism_Menu_Manager(
    plugin: EssentialsMagic,
    private val prismaManager: MagicPrismManager
) : CraftGuiManager(plugin, prismaManager, "Prisma", "prism") {

    override fun openMenu(player: Player, location: Location) {
        // Implementação específica para o Prisma
        val title = "§b§lPrisma Mágico"
        val inventory = createInventory(title)

        // Configuração inicial do inventário
        setupInitialInventory(inventory)

        // Verificar se há um craft em andamento
        val craft = prismaManager.getActiveCraft(location)
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
            inventory.setItem(i, ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
                itemMeta = itemMeta?.apply { setDisplayName(" ") }
            })
        }

        // Configurar slots de itens (formato de fornalha)
        // Primeiro item (esquerda)
        inventory.setItem(SLOT_ITEM_PRIMEIRO, ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§c1º Item §7(Obrigatório)") }
        })

        // Combustível (centro)
        inventory.setItem(SLOT_COMBUSTIVEL, ItemStack(Material.ORANGE_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§6Combustível §7(Obrigatório)") }
        })

        // Segundo item (abaixo do primeiro)
        inventory.setItem(SLOT_ITEM_SEGUNDO, ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§c2º Item §7(Obrigatório)") }
        })

        // Resultado (direita)
        inventory.setItem(SLOT_RESULTADO, ItemStack(Material.LIME_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§aCombine itens no Prisma para criar novos itens!") }
        })

        // Decoração de fogo (abaixo do combustível)
        inventory.setItem(SLOT_COMBUSTIVEL + 9, ItemStack(Material.FIRE_CHARGE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§6Energia Mágica") }
        })

        // Configurar botão de confirmar
        inventory.setItem(SLOT_CONFIRMAR, ItemStack(Material.EMERALD).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§a§lIniciar Craft") }
        })

        // Adicionar decoração de setas indicando o fluxo
        inventory.setItem(SLOT_ITEM_PRIMEIRO + 1, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§7→") }
        })

        inventory.setItem(SLOT_COMBUSTIVEL + 1, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§7→") }
        })
    }

    private fun setupProgressInventory(inventory: Inventory, craft: CraftManager.ActiveCraft) {
        // Limpar slots de itens
        inventory.setItem(SLOT_ITEM_PRIMEIRO, null)
        inventory.setItem(SLOT_ITEM_SEGUNDO, null)
        inventory.setItem(SLOT_COMBUSTIVEL, null)

        // Informações do craft em andamento
        val resultItem = NexoItems.itemFromId(craft.resultItemId)?.build()
        if (resultItem != null) {
            resultItem.amount = craft.quantity
            inventory.setItem(SLOT_RESULTADO, resultItem)
        }

        // Decoração de fogo animado durante o craft
        inventory.setItem(SLOT_COMBUSTIVEL + 9, ItemStack(Material.BLAZE_POWDER).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§6Energia Mágica Ativa") }
        })

        // Criar barra de progresso
        updateProgressBar(inventory, craft, 19, 25)

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
            val craft = prismaManager.getActiveCraft(location)

            if (craft != null) {
                // Atualizar a barra de progresso
                updateProgressBar(inv, craft, 19, 25)

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
        return Bukkit.createInventory(null, 36, title)  // 4 linhas (36 slots)
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

        val prismaLocation = playerMenuLocations[player.uniqueId] ?: return

        // Cancelar cliques em slots que não são interativos
        if (slot < 36 && slot !in listOf(SLOT_ITEM_PRIMEIRO, SLOT_ITEM_SEGUNDO, SLOT_COMBUSTIVEL, SLOT_CONFIRMAR)) {
            event.isCancelled = true
            return
        }

        // Ignorar interações no SLOT_RESULTADO
        if (slot == SLOT_RESULTADO) {
            event.isCancelled = true
            return
        }

        // Verificar se há um craft em andamento
        val activeCraft = prismaManager.getActiveCraft(prismaLocation)

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
                prismaManager.removeCraft(prismaLocation)
                inventory.setItem(SLOT_ITEM_PRIMEIRO, null)
                inventory.setItem(SLOT_ITEM_SEGUNDO, null)
                inventory.setItem(SLOT_COMBUSTIVEL, null)

                // Reabrir o menu
                openMenu(player, prismaLocation)

                // remove a mobilia do nexo e coloca outra no lugar
                NexoFurniture.remove(prismaLocation, null)
                NexoFurniture.place(configManager.getPrismaId(), prismaLocation, prismaLocation.yaw, BlockFace.UP)

                // Enviar mensagem de sucesso
                player.sendMessage(configManager.getMensagem("craft_collected", "§aVocê coletou os itens do craft!"))
            }
            return
        }

        // Se há um craft em andamento e não está completo, não permitir interação
        if (activeCraft != null && !activeCraft.isComplete) {
            event.isCancelled = true
            return
        }

        // Permitir colocar/retirar itens dos slots específicos
        if (slot in listOf(SLOT_ITEM_PRIMEIRO, SLOT_ITEM_SEGUNDO, SLOT_COMBUSTIVEL)) {
            handleItemSlotInteraction(player, inventory, slot, clickedItem, cursor)
        }
        // Processamento do botão de confirmar
        else if (slot == SLOT_CONFIRMAR) {
            event.isCancelled = true
            handleConfirmButton(player, inventory, prismaLocation)
        }
    }

    private fun handleItemSlotInteraction(player: Player, inventory: Inventory, slot: Int, clickedItem: ItemStack?, cursor: ItemStack) {
        // Se o jogador está tentando colocar um item no slot (tem item no cursor)
        if (!cursor.type.isAir) {
            // Se o slot está vazio ou tem vidro decorativo
            if (clickedItem == null || clickedItem.type.isAir ||
                clickedItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE)) {

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
            else if (clickedItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE)) {
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
            clickedItem.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE)) {

            // Pegar o item do slot
            player.setItemOnCursor(clickedItem.clone())

            // Limpar o slot
            inventory.setItem(slot, null)

            // Colocar o vidro decorativo de volta
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Se o slot ainda estiver vazio (não foi modificado por outro evento)
                if (inventory.getItem(slot) == null || inventory.getItem(slot)?.type?.isAir == true) {
                    val glassPane = when (slot) {
                        SLOT_ITEM_PRIMEIRO, SLOT_ITEM_SEGUNDO -> ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
                            itemMeta = itemMeta?.apply {
                                setDisplayName(
                                    if (slot == SLOT_ITEM_PRIMEIRO) "§c1º Item §7(Obrigatório)"
                                    else "§c2º Item §7(Obrigatório)"
                                )
                            }
                        }
                        SLOT_COMBUSTIVEL -> ItemStack(Material.ORANGE_STAINED_GLASS_PANE).apply {
                            itemMeta = itemMeta?.apply { setDisplayName("§6Combustível §7(Obrigatório)") }
                        }
                        else -> ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
                            itemMeta = itemMeta?.apply { setDisplayName(" ") }
                        }
                    }
                    inventory.setItem(slot, glassPane)
                }
                player.updateInventory()
            })
        }
    }

    private fun handleConfirmButton(player: Player, inventory: Inventory, prismaLocation: Location) {
        // Verificar se há itens nos slots
        val primeiroItem = inventory.getItem(SLOT_ITEM_PRIMEIRO)
        val segundoItem = inventory.getItem(SLOT_ITEM_SEGUNDO)
        val combustivelItem = inventory.getItem(SLOT_COMBUSTIVEL)

        // Verificar se todos os itens obrigatórios estão presentes
        if (primeiroItem == null || primeiroItem.type == Material.RED_STAINED_GLASS_PANE) {
            player.sendMessage(
                configManager.getMensagem(
                    "missing_first_item",
                    "§cVocê precisa colocar o primeiro item!"
                )
            )
            return
        }

        if (segundoItem == null || segundoItem.type == Material.RED_STAINED_GLASS_PANE) {
            player.sendMessage(
                configManager.getMensagem(
                    "missing_second_item",
                    "§cVocê precisa colocar o segundo item!"
                )
            )
            return
        }

        if (combustivelItem == null || combustivelItem.type == Material.ORANGE_STAINED_GLASS_PANE) {
            player.sendMessage(
                configManager.getMensagem(
                    "missing_fuel",
                    "§cVocê precisa colocar um combustível!"
                )
            )
            return
        }

        // Verificar se o craft é válido
        val craftId = checkValidCraft(primeiroItem, segundoItem, combustivelItem)
        if (craftId == null) {
            player.sendMessage(
                configManager.getMensagem(
                    "invalid_recipe",
                    "§cEsta combinação de itens não é válida!"
                )
            )
            return
        }

        // Obter o item resultado
        val resultItemId = craftManager.getCraftResult(craftId)
        if (resultItemId == null) {
            player.sendMessage(
                configManager.getMensagem(
                    "error_getting_result",
                    "§cErro ao obter o resultado do craft!"
                )
            )
            return
        }

        // Calcular a quantidade máxima que pode ser craftada
        val maxQuantity = calculateMaxCraftQuantity(primeiroItem, segundoItem, combustivelItem, craftId)
        if (maxQuantity <= 0) {
            player.sendMessage(
                configManager.getMensagem(
                    "insufficient_materials",
                    "§cNão há materiais suficientes para este craft!"
                )
            )
            return
        }

        // Obter o tempo de craft baseado no combustível
        val combustivelId = idFromItem(combustivelItem) ?: combustivelItem.type.name
        val fuelTime = getFuelTime(combustivelId)
        if (fuelTime <= 0) {
            player.sendMessage(
                configManager.getMensagem(
                    "invalid_fuel",
                    "§cEste item não é um combustível válido!"
                )
            )
            return
        }

        // Consumir os itens
        consumeCraftItems(inventory, craftId, maxQuantity, prismaLocation)

        // Iniciar o craft com o tempo baseado no combustível
        val success = prismaManager.startCraft(prismaLocation, craftId, resultItemId, maxQuantity, combustivelId, fuelTime)
        if (success) {
            // Obter o nome do item para a mensagem
            val resultPreview = NexoItems.itemFromId(resultItemId)!!.build()
            val itemName = if (resultPreview.itemMeta.hasDisplayName()) resultPreview.itemMeta.displayName else resultItemId

            // Obter o tempo de craft
            val craftTime = fuelTime * maxQuantity
            val formattedTime = formatTime(craftTime)

            // Enviar mensagem personalizada
            val message = configManager.getMensagem("craft_started", "§aCraft iniciado com sucesso!")
                .replace("%item%", itemName)
                .replace("%quantity%", maxQuantity.toString())
                .replace("%time%", formattedTime)

            player.sendMessage(message)

            // Trocar a mobília para a versão animada
            NexoFurniture.remove(prismaLocation, null)
            NexoFurniture.place(configManager.getPrismaIdAnimation(), prismaLocation, prismaLocation.yaw, BlockFace.UP)

            // Reabrir o menu para atualizar o estado do craft
            openMenu(player, prismaLocation)
        } else {
            player.sendMessage(configManager.getMensagem("error_starting_craft", "§cErro ao iniciar o craft!"))
        }
    }

    private fun getFuelTime(fuelId: String): Int {
        val fuelList = configManager.getPrismaFuel()
        for ((id, time) in fuelList) {
            if (id == fuelId) {
                return time
            }
        }
        return -1
    }

   private fun checkValidCraft(primeiroItem: ItemStack, segundoItem: ItemStack, combustivelItem: ItemStack): String? {
        plugin.logger.info("Iniciando validação do craft com os itens: Primeiro=${primeiroItem.type}, Segundo=${segundoItem.type}, Combustível=${combustivelItem.type}")

        // Verificar se os itens são painéis de vidro decorativos
        if (primeiroItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE) ||
            segundoItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE) ||
            combustivelItem.type in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE)) {
            plugin.logger.warning("Itens inválidos: um ou mais itens são painéis de vidro decorativos.")
            return null
        }

        // Obter os IDs dos itens ou usar o tipo como fallback
        val primeiroId = idFromItem(primeiroItem) ?: primeiroItem.type.name
        val segundoId = idFromItem(segundoItem) ?: segundoItem.type.name
        val combustivelId = idFromItem(combustivelItem) ?: combustivelItem.type.name

        plugin.logger.info("IDs dos itens obtidos: Primeiro=$primeiroId, Segundo=$segundoId, Combustível=$combustivelId")

        // Verificar se o combustível é válido
        val fuelList = configManager.getPrismaFuel()
        var validFuel = false
        for ((id, _) in fuelList) {
            if (id == combustivelId) {
                validFuel = true
                break
            }
        }

        if (!validFuel) {
            plugin.logger.warning("Combustível inválido: $combustivelId não está na lista de combustíveis permitidos. combustiveis permitidos: $fuelList")
            return null
        }

        plugin.logger.info("Combustível válido: $combustivelId")

        // Verificar se a combinação existe nos crafts
        val craftId = craftManager.findCraftId(primeiroId, segundoId)
        if (craftId == null) {
            plugin.logger.warning("Nenhum craft encontrado para os itens: Primeiro=$primeiroId, Segundo=$segundoId")
        } else {
            plugin.logger.info("Craft válido encontrado: $craftId")
        }

        return craftId
    }

    private fun calculateMaxCraftQuantity(
        primeiroItem: ItemStack,
        segundoItem: ItemStack,
        combustivelItem: ItemStack,
        craftId: String
    ): Int {
        val materials: Map<String, Int> = getMaterialsForCraft(craftId)

        var maxQuantity = Int.MAX_VALUE

        for ((materialId, requiredAmount) in materials) {
            var availableAmount = 0

            // Verificar o primeiro item
            val primeiroId = idFromItem(primeiroItem) ?: primeiroItem.type.name
            if (primeiroId == materialId) {
                availableAmount += primeiroItem.amount
            }

            // Verificar o segundo item
            val segundoId = idFromItem(segundoItem) ?: segundoItem.type.name
            if (segundoId == materialId) {
                availableAmount += segundoItem.amount
            }

            // Verificar o combustível
            val combustivelId = idFromItem(combustivelItem) ?: combustivelItem.type.name
            if (combustivelId == materialId) {
                availableAmount += combustivelItem.amount
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
            player.sendMessage("§cInventário cheio! Dropando item nas coordenadas do prisma.")
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
                if (it.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE)) {
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
                        }
                    } ?: plugin.logger.warning("Material não encontrado nos requisitos do craft para o $slotName.")
                }
            }
        }

        // Processar os itens nos slots
        processItem(SLOT_ITEM_PRIMEIRO, "Primeiro item")
        processItem(SLOT_ITEM_SEGUNDO, "Segundo item")
        processItem(SLOT_COMBUSTIVEL, "Combustível")
    }

    override fun getMaterialsForCraft(craftId: String): Map<String, Int> {
        return craftManager.getCraftMaterials(craftId)
    }

    override fun handleInventoryClose(player: Player, inventory: Inventory, event: InventoryCloseEvent) {
        val inv = event.inventory
        val location = playerMenuLocations[player.uniqueId] ?: return

        // Verificar se há um craft em andamento
        val activeCraft = prismaManager.getActiveCraft(location)
        if (activeCraft != null) {
            return // Não fazer nada se houver um craft em andamento
        }

        val slots = listOf(SLOT_ITEM_PRIMEIRO, SLOT_ITEM_SEGUNDO, SLOT_COMBUSTIVEL)

        for (slot in slots) {
            val item = inv.getItem(slot)
            if (item != null && !item.type.isAir &&
                item.type !in listOf(
                    Material.RED_STAINED_GLASS_PANE,
                    Material.ORANGE_STAINED_GLASS_PANE,
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
        // Posições dos slots no inventário (layout tipo fornalha)
        private const val SLOT_ITEM_PRIMEIRO = 10  // Primeiro item (esquerda)
        private const val SLOT_COMBUSTIVEL = 13    // Combustível (centro)
        private const val SLOT_ITEM_SEGUNDO = 19   // Segundo item (abaixo do primeiro)
        private const val SLOT_RESULTADO = 16      // Resultado (direita)
        private const val SLOT_CONFIRMAR = 34      // Botão de confirmar (canto inferior direito)
    }
}