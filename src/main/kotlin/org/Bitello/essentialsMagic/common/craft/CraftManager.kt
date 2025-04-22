package org.Bitello.essentialsMagic.common.craft

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

abstract class CraftManager(
    protected val plugin: EssentialsMagic,
    private val systemName: String,
    private val configFilename: String,
    private val ymlname: String
) : Listener {

    protected val configManager = plugin.configManager
    protected val craftManager = plugin.craftManager.CraftFileManager(ymlname)
    protected val activeCrafts: MutableMap<Location, ActiveCraft> = ConcurrentHashMap()
    protected val craftsFile: File
    protected var craftsConfig: YamlConfiguration? = null

    init {
        // Criar a pasta crafts_data dentro do diretório do plugin
        val craftsDataFolder = File(plugin.dataFolder, "crafts_data")
        if (!craftsDataFolder.exists()) {
            craftsDataFolder.mkdirs()
        }

        // Definir o arquivo de crafts dentro da pasta crafts_data
        this.craftsFile = File(craftsDataFolder, configFilename)
        loadCraftsConfig()

        // Registrar o listener
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun initialize() {
        Bukkit.getConsoleSender().sendMessage("[EssentialsMagic] $systemName Crafting System has been enabled.")

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
                plugin.logger.severe("Não foi possível criar o arquivo $configFilename: " + e.message)
            }
        }
        craftsConfig = YamlConfiguration.loadConfiguration(craftsFile)
    }

    protected fun saveCraftsConfig() {
        try {
            craftsConfig?.save(craftsFile)
        } catch (e: IOException) {
            plugin.logger.severe("Não foi possível salvar o arquivo $configFilename: " + e.message)
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
                val normalizedLocation = normalizeLocation(loc)
                val path = "crafts.$index"
                config["$path.world"] = loc.world.name
                config["$path.x"] = normalizedLocation.x
                config["$path.y"] = normalizedLocation.y
                config["$path.z"] = normalizedLocation.z
                config["$path.craftId"] = craft.craftId
                config["$path.resultItem"] = craft.resultItemId
                config["$path.quantity"] = craft.quantity
                config["$path.remainingTime"] = craft.remainingTime
                config["$path.totalTime"] = craft.totalTime
                config["$path.fuelId"] = craft.fuelId
                config["$path.fuelTime"] = craft.fuelTime

                // Salvar os itens de craft
                for (i in 0 until craft.craftItems.size) {
                    config["$path.craftItems.$i.id"] = craft.craftItems[i]
                }

                // Salvar dados adicionais específicos da implementação
                saveExtraCraftData(config, path, craft)

                index++
            }
        }

        saveCraftsConfig()
    }

    // Método para sobrescrever e salvar dados extras específicos do sistema
    protected open fun saveExtraCraftData(config: YamlConfiguration, path: String, craft: ActiveCraft) {
        // Implementação padrão vazia
    }

    protected fun loadActiveCrafts() {
        val craftsSection = craftsConfig?.getConfigurationSection("crafts") ?: return

        for (key in craftsSection.getKeys(false)) {
            val craftSection = craftsSection.getConfigurationSection(key) ?: continue
            val craftConfig = YamlConfiguration()
            craftConfig.set("crafts", craftSection)

            val worldName = craftSection.getString("world") ?: continue
            val x = craftSection.getDouble("x")
            val y = craftSection.getDouble("y")
            val z = craftSection.getDouble("z")

            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                plugin.logger.warning("Mundo '$worldName' não encontrado ao carregar o craft com a chave '$key'.")
                continue
            }

            val location = normalizeLocation(Location(world, x, y, z))
            val craftId = craftSection.getString("craftId")
            val resultItemId = craftSection.getString("resultItem")
            val quantity = craftSection.getInt("quantity", -1)
            val remainingTime = craftSection.getInt("remainingTime", -1)
            val totalTime = craftSection.getInt("totalTime", -1)
            val fuelId = craftSection.getString("fuelId", "") ?: ""
            val fuelTime = craftSection.getInt("fuelTime", 0)

            if (craftId == null || resultItemId == null || quantity <= 0 || remainingTime < 0 || totalTime <= 0) {
                plugin.logger.warning("Dados inválidos para o craft com a chave '$key'. Ignorando...")
                continue
            }

            // Carregar os itens de craft
            val craftItems = mutableListOf<String>()
            val craftItemsSection = craftSection.getConfigurationSection("craftItems")
            if (craftItemsSection != null) {
                for (itemKey in craftItemsSection.getKeys(false)) {
                    val itemId = craftItemsSection.getString("$itemKey.id")
                    if (itemId != null) {
                        craftItems.add(itemId)
                    }
                }
            }

            // Criar o craft ativo
            val craft = createActiveCraft(craftId, resultItemId, quantity, remainingTime, totalTime, fuelId, fuelTime)
            craft.craftItems.addAll(craftItems)

            // Carregar dados extras específicos da implementação
            loadExtraCraftData(craftConfig, craft)

            // Adicionar ao mapa de crafts ativos
            activeCrafts[location] = craft
        }
    }

    // Método para sobrescrever e carregar dados extras específicos do sistema
    protected open fun loadExtraCraftData(config: YamlConfiguration, craft: ActiveCraft) {
        // Implementação padrão vazia
    }

    protected fun checkActiveCrafts() {
        val iterator = activeCrafts.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val craft = entry.value

            craft.decrementTime(1)

            if (craft.isComplete) {
                // O craft foi concluído
                craft.isCompleted = true
                // Permitir processamento específico
                onCraftComplete(entry.key, craft)
            }
        }
    }

    // Método para sobrescrever e adicionar comportamentos quando um craft é concluído
    protected open fun onCraftComplete(location: Location, craft: ActiveCraft) {
        // Implementação padrão vazia
    }

    @EventHandler
    fun onFurnitureBreak(event: NexoFurnitureBreakEvent) {
        if (!isSystemEnabled()) return

        val itemId = event.mechanic.itemID
        val craftingStationIds = getCraftingStationId(listOf(itemId)) // Passa uma lista com o itemId

        if (craftingStationIds.contains(itemId)) {
            val location = normalizeLocation(event.baseEntity.location)

            // Verificar se há um craft ativo nesta estação
            val craft = activeCrafts[location]
            if (craft != null) {
                // Dropar os itens do craft
                dropCraftItems(location, craft)
                activeCrafts.remove(location)

                // Enviar mensagem para o jogador
                val message = getSystemMessage("station_broken", "§cA estação foi quebrada e os materiais do craft foram dropados.")
                event.player.sendMessage(message)
            }
        }
    }

    protected fun dropCraftItems(location: Location, craft: ActiveCraft) {
        // Obter os materiais do craft a partir do ID do craft
        val materials = craftManager.getCraftMaterials(craft.craftId)

        // Dropar os materiais
        for ((itemId, value) in materials) {
            val totalAmount = value * craft.quantity

            // Tentar obter o item do NexoItems
            val item = NexoItems.itemFromId(itemId)?.build()?.apply {
                amount = totalAmount
            } ?: run {
                // Caso não seja um item do NexoItems, criar um ItemStack com o Material correspondente
                val material = Material.matchMaterial(itemId)
                if (material != null) {
                    ItemStack(material, totalAmount)
                } else {
                    null // Ignorar itens inválidos
                }
            }

            // Dropar o item, se válido
            if (item != null) {
                val maxStackSize = item.maxStackSize
                var remainingAmount = totalAmount

                // Dropar em pilhas menores, se necessário
                while (remainingAmount > 0) {
                    val dropAmount = minOf(remainingAmount, maxStackSize)
                    val dropItem = item.clone().apply { amount = dropAmount }
                    location.world.dropItemNaturally(location, dropItem)
                    remainingAmount -= dropAmount
                }
            }
        }

        // Dropar o combustível também, se existir
        if (craft.fuelId.isNotEmpty()) {
            val fuelItem = NexoItems.itemFromId(craft.fuelId)?.build() ?: run {
                val material = Material.matchMaterial(craft.fuelId)
                if (material != null) {
                    ItemStack(material, 1)
                } else {
                    null
                }
            }

            if (fuelItem != null) {
                location.world.dropItemNaturally(location, fuelItem)
            }
        }
    }

    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        if (!isSystemEnabled()) return

        val itemId = event.mechanic.itemID
        val craftingStationIds = getCraftingStationId(listOf(itemId)) // Passa uma lista com o itemId

        if (craftingStationIds.contains(itemId)) {
            val player = event.player
            val location = normalizeLocation(event.baseEntity.location)

            // Abrir o menu da estação de craft
            openCraftingMenu(player, location)
        }
    }

    // Método atualizado para incluir combustível e tempo de combustível
    fun startCraft(location: Location, craftId: String?, resultItemId: String?, quantity: Int, fuelId: String = "", fuelTime: Int = 0): Boolean {
        // Verificar se o craft existe
        if (craftId == null || !craftExists(craftId)) {
            return false
        }

        // Obter o tempo necessário para o craft
        val craftTime = if (fuelTime > 0) fuelTime else getCraftTime(craftId)
        if (craftTime <= 0) {
            return false
        }

        // Criar o craft ativo com os novos parâmetros
        val craft = createActiveCraft(craftId, resultItemId ?: "", quantity, craftTime * quantity, craftTime * quantity, fuelId, fuelTime)
        activeCrafts[normalizeLocation(location)] = craft

        return true
    }

    // Método para adicionar itens ao craft
    fun addCraftItem(location: Location, itemId: String): Boolean {
        val craft = getActiveCraft(location) ?: return false

        // Verificar se já atingiu o limite de 9 itens
        if (craft.craftItems.size >= 9) {
            return false
        }

        craft.craftItems.add(itemId)
        return true
    }

    fun getActiveCraft(location: Location): ActiveCraft? {
        val normalizedLocation = normalizeLocation(location)
        return activeCrafts[normalizedLocation]
    }

    fun isCraftComplete(location: Location): Boolean {
        val craft = activeCrafts[location]
        return craft != null && craft.isComplete
    }

    fun removeCraft(location: Location) {
        activeCrafts.remove(normalizeLocation(location))
    }

    protected fun normalizeLocation(location: Location): Location {
        return Location(
            location.world,
            location.blockX.toDouble(),
            location.blockY.toDouble(),
            location.blockZ.toDouble(),
            location.yaw,
            location.pitch
        )
    }

    // Métodos abstratos que devem ser implementados por classes específicas
    protected abstract fun isSystemEnabled(): Boolean
    protected abstract fun getCraftingStationId(ids: List<String>): List<String>
    protected abstract fun getSystemMessage(key: String, default: String): String
    protected abstract fun getCraftMaterials(craftId: String): Map<String, Int>
    protected abstract fun getCraftTime(craftId: String): Int
    protected abstract fun craftExists(craftId: String): Boolean
    protected abstract fun openCraftingMenu(player: org.bukkit.entity.Player, location: Location)

    // Factory method atualizado para criar instâncias de ActiveCraft com os novos parâmetros
    protected open fun createActiveCraft(
        craftId: String,
        resultItemId: String,
        quantity: Int,
        remainingTime: Int,
        totalTime: Int,
        fuelId: String = "",
        fuelTime: Int = 0
    ): ActiveCraft {
        return ActiveCraft(craftId, resultItemId, quantity, remainingTime, totalTime, fuelId, fuelTime)
    }

    // Método para ser chamado quando o servidor desligar
    fun onDisable() {
        saveActiveCrafts()
        plugin.logger.info("[$systemName] Salvando crafts ativos...")
    }

    // Classe ActiveCraft atualizada com novos campos
    open class ActiveCraft(
        val craftId: String,
        val resultItemId: String,
        val quantity: Int,
        var remainingTime: Int,
        val totalTime: Int,
        val fuelId: String = "",
        val fuelTime: Int = 0
    ) {
        var isCompleted: Boolean = false
        val craftItems: MutableList<String> = mutableListOf()

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
}