package org.Bitello.essentialsMagic.features.magictear

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.features.magictear.gui.MagicTear_Menu_Manager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class MagicTearManager(private val plugin: EssentialsMagic) : Listener {
    private val configManager = plugin.configManager
    private val tearCraftMenu: MagicTear_Menu_Manager
    private val activeCrafts: MutableMap<Location, ActiveCraft> = ConcurrentHashMap()
    private val craftsFile: File
    private var craftsConfig: YamlConfiguration? = null

    init {
        this.tearCraftMenu = MagicTear_Menu_Manager(plugin, this)
        this.craftsFile = File(plugin.dataFolder, "active_crafts.yml")
        loadCraftsConfig()

        // Registrar o listener
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun initialize() {
        val message: Component = Component.text("[EssentialsMagic] Tear Crafting System has been enabled.")
            .color(NamedTextColor.DARK_PURPLE)
        Bukkit.getConsoleSender().sendMessage(
            LegacyComponentSerializer.legacySection().serialize(message)
        )

        // Carregar crafts em andamento
        loadActiveCrafts()

        // Iniciar task para verificar crafts
        object : BukkitRunnable() {
            override fun run() {
                checkActiveCrafts()
            }
        }.runTaskTimer(plugin, 20L, 20L) // Verificar a cada segundo
    }

    private fun loadCraftsConfig() {
        if (!craftsFile.exists()) {
            try {
                craftsFile.createNewFile()
            } catch (e: IOException) {
                plugin.logger.severe("Não foi possível criar o arquivo active_crafts.yml: " + e.message)
            }
        }
        craftsConfig = YamlConfiguration.loadConfiguration(craftsFile)
    }

    private fun saveCraftsConfig() {
        try {
            craftsConfig?.save(craftsFile)
        } catch (e: IOException) {
            plugin.logger.severe("Não foi possível salvar o arquivo active_crafts.yml: " + e.message)
        }
    }

    fun saveActiveCrafts() {
        // Limpar configuração atual
        craftsConfig?.let { config ->
            for (key in config.getKeys(false)) {
                config[key] = null
            }

            // Salvar crafts ativos
            var index = 0
            for ((loc, craft) in activeCrafts) {
                val path = "crafts.$index"
                config["$path.world"] = loc.world.name
                config["$path.x"] = loc.x
                config["$path.y"] = loc.y
                config["$path.z"] = loc.z
                config["$path.craftId"] = craft.craftId
                config["$path.resultItem"] = craft.resultItemId
                config["$path.quantity"] = craft.quantity
                config["$path.remainingTime"] = craft.remainingTime
                config["$path.totalTime"] = craft.totalTime

                index++
            }
        }

        saveCraftsConfig()
    }

    private fun loadActiveCrafts() {
        val craftsSection = craftsConfig?.getConfigurationSection("crafts") ?: return

        for (key in craftsSection.getKeys(false)) {
            val craftSection = craftsSection.getConfigurationSection(key) ?: continue

            val worldName = craftSection.getString("world") ?: continue
            val x = craftSection.getDouble("x")
            val y = craftSection.getDouble("y")
            val z = craftSection.getDouble("z")

            val world = Bukkit.getWorld(worldName) ?: continue

            val location = Location(world, x, y, z)
            val craftId = craftSection.getString("craftId") ?: continue
            val resultItemId = craftSection.getString("resultItem") ?: continue
            val quantity = craftSection.getInt("quantity")
            val remainingTime = craftSection.getInt("remainingTime")
            val totalTime = craftSection.getInt("totalTime")

            val craft = ActiveCraft(craftId, resultItemId, quantity, remainingTime, totalTime)
            activeCrafts[location] = craft
        }
    }

    private fun checkActiveCrafts() {
        val iterator = activeCrafts.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val craft = entry.value

            craft.decrementTime(1)

            if (craft.isComplete) {
                // O craft foi concluído
                craft.isCompleted = true
            }
        }
    }

    @EventHandler
    fun onFurniturePlace(event: NexoFurniturePlaceEvent) {
        if (!isTearEnabled) return

        val itemId = event.mechanic.itemID
        if (configManager.getTearId() == itemId) {
            Bukkit.getConsoleSender().sendMessage("[EssentialsMagic] Tear colocado: $itemId")
        }
    }

    @EventHandler
    fun onFurnitureBreak(event: NexoFurnitureBreakEvent) {
        if (!isTearEnabled) return

        val itemId = event.mechanic.itemID
        if (configManager.getTearId() == itemId) {
            val location = event.baseEntity.location

            // Verificar se há um craft ativo neste tear
            val craft = activeCrafts[location]
            if (craft != null) {
                // Dropar os itens do craft
                dropCraftItems(location, craft)
                activeCrafts.remove(location)

                // Enviar mensagem para o jogador
                val message = configManager.getTearMessage("tear_broken", "§cO tear foi quebrado e os materiais do craft foram dropados.")
                event.player.sendMessage(message)
            }
        }
    }

    private fun dropCraftItems(location: Location, craft: ActiveCraft) {
        // Obter os materiais do craft a partir do ID do craft
        val materials = configManager.getCraftMaterials(craft.craftId)

        // Dropar os materiais
        for ((itemId, value) in materials) {
            val amount = value * craft.quantity

            val item = NexoItems.itemFromId(itemId)?.build()
            if (item != null) {
                // Definir a quantidade após criar o item
                item.amount = amount
                location.world.dropItemNaturally(location, item)
            }
        }
    }
    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        if (!isTearEnabled) return

        val itemId = event.mechanic.itemID
        if (configManager.getTearId() == itemId) {
            val player = event.player
            val location = event.baseEntity.location

            // Abrir o menu do tear
            tearCraftMenu.openMenu(player, location)
        }
    }

    fun startCraft(location: Location, craftId: String?, resultItemId: String?, quantity: Int): Boolean {
        // Verificar se o craft existe
        if (craftId == null || !configManager.craftExists(craftId)) {
            return false
        }

        // Obter o tempo necessário para o craft
        val craftTime = configManager.getCraftTime(craftId)
        if (craftTime <= 0) {
            return false
        }

        // Criar o craft ativo
        val craft = ActiveCraft(craftId, resultItemId ?: "", quantity, craftTime * quantity, craftTime * quantity)
        activeCrafts[location] = craft

        return true
    }

    fun getActiveCraft(location: Location): ActiveCraft? {
        return activeCrafts[location]
    }

    fun isCraftComplete(location: Location): Boolean {
        val craft = activeCrafts[location]
        return craft != null && craft.isComplete
    }

    fun removeCraft(location: Location) {
        activeCrafts.remove(location)
    }

    private val isTearEnabled: Boolean
        get() = configManager.isTearEnabled()

    inner class ActiveCraft(
        val craftId: String,
        val resultItemId: String,
        val quantity: Int,
        var remainingTime: Int,
        val totalTime: Int
    ) {
        var isCompleted: Boolean = false

        val isComplete: Boolean
            get() = remainingTime <= 0

        fun decrementTime(seconds: Int) {
            remainingTime = max(0, remainingTime - seconds)
        }

        val progress: Double
            get() = 1.0 - (remainingTime.toDouble() / totalTime)

        val formattedTimeRemaining: String
            get() {
                val minutes = remainingTime / 60
                val seconds = remainingTime % 60
                return String.format("%02d:%02d", minutes, seconds)
            }
    }

    // Método para ser chamado quando o servidor desligar
    fun onDisable() {
        saveActiveCrafts()
        plugin.logger.info("[MagicTear] Salvando crafts ativos...")
    }
}