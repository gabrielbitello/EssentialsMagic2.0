package org.Bitello.essentialsMagic.common.craft

import com.nexomc.nexo.api.NexoItems.idFromItem
import com.nexomc.nexo.api.NexoItems.itemFromId
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.common.craft.CraftManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

abstract class CraftGuiManager(
    protected val plugin: EssentialsMagic,
    protected val craftingManager: CraftManager,
    private val systemName: String
) : Listener {
    protected val configManager = plugin.configManager
    protected val playerMenuLocations: MutableMap<UUID, Location> = ConcurrentHashMap()
    protected val openMenus: MutableMap<UUID, Inventory> = ConcurrentHashMap()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        // Iniciar task para atualizar os menus abertos
        object : BukkitRunnable() {
            override fun run() {
                updateOpenMenus()
            }
        }.runTaskTimer(plugin, 10L, 10L) // Atualizar a cada 0.5 segundos
    }

    abstract fun openMenu(player: Player, location: Location)

    protected abstract fun updateOpenMenus()

    protected abstract fun createInventory(title: String): Inventory

    protected abstract fun updateProgressBar(inventory: Inventory, craft: CraftManager.ActiveCraft?, slotStart: Int, slotEnd: Int)

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        // Permitir movimentação no inventário do jogador
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        // Verificar se este inventário é um dos nossos menus
        if (player.uniqueId in openMenus.keys && openMenus[player.uniqueId] == inventory) {
            event.isCancelled = true // Cancelar o evento por padrão

            // Processar o clique de acordo com as regras específicas
            handleInventoryClick(player, event)
        }
    }

    protected abstract fun handleInventoryClick(player: Player, event: InventoryClickEvent)

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        // Cancelar arrastar itens no nosso inventário
        if (player.uniqueId in openMenus.keys && openMenus[player.uniqueId] == inventory) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory

        // Verificar se este inventário é um dos nossos menus
        if (player.uniqueId in openMenus.keys && openMenus[player.uniqueId] == inventory) {
            handleInventoryClose(player, inventory, event)

            // Remover o jogador da lista de menus abertos
            closeMenu(player)
        }
    }

    protected abstract fun handleInventoryClose(player: Player, inventory: Inventory, event: InventoryCloseEvent)

    protected fun checkValidCraft(items: List<ItemStack?>): String? {
        // Template para validação de crafts - deve ser implementado por subclasses
        return null
    }

    protected fun calculateMaxCraftQuantity(items: List<ItemStack?>, craftId: String): Int {
        val materials: Map<String, Int> = getMaterialsForCraft(craftId)

        var maxQuantity = Int.MAX_VALUE

        for ((materialId, requiredAmount) in materials) {
            var availableAmount = 0

            // Verificar cada item para ver se ele fornece o material necessário
            for (item in items.filterNotNull()) {
                if (item.type !in listOf(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE)) {
                    val itemId = idFromItem(item) ?: item.type.name
                    if (itemId == materialId) {
                        availableAmount += item.amount
                    }
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

    protected abstract fun consumeCraftItems(inventory: Inventory, craftId: String, quantity: Int, location: Location)

    protected fun formatTime(seconds: Int): String {
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

    // Métodos abstratos específicos que devem ser implementados por subclasses
    protected abstract fun getMaterialsForCraft(craftId: String): Map<String, Int>
}