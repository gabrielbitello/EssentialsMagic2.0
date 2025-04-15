package org.Bitello.essentialsMagic.core.config

import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: EssentialsMagic) {

    private lateinit var config: FileConfiguration
    private lateinit var craftsConfig: YamlConfiguration

    fun loadConfigs() {
        plugin.saveDefaultConfig()
        config = plugin.config

        // Carregar configuração de crafts
        val craftsFile = File(plugin.dataFolder, "crafts.yml")
        if (!craftsFile.exists()) {
            plugin.saveResource("crafts.yml", false)
        }
        craftsConfig = YamlConfiguration.loadConfiguration(craftsFile)
    }

    fun getConfig(): FileConfiguration = config

    // Integrações externas
    fun useLuckPerms(): Boolean = config.getBoolean("use_luckperms")
    fun useWorldGuard(): Boolean = config.getBoolean("use_worldguard")

    // MySQL
    fun getMysqlConfig(): Map<String, Any?> = mapOf(
        "host" to config.getString("mysql.host"),
        "port" to config.getInt("mysql.port"),
        "database" to config.getString("mysql.database"),
        "username" to config.getString("mysql.username"),
        "password" to config.getString("mysql.password")
    )

    // TP Commands
    fun isTpCommandsEnabled(): Boolean = config.getBoolean("tp_commands.status")
    fun isSpawnEnabled(): Boolean = config.getBoolean("tp_commands.spawn")
    fun getSpawnCooldown(): Int = config.getInt("tp_commands.spawn_cooldown")
    fun getSpawnCoords(): Map<String, Any?> = mapOf(
        "world" to config.getString("tp_commands.spawn_cords.world"),
        "x" to config.getDouble("tp_commands.spawn_cords.x"),
        "y" to config.getDouble("tp_commands.spawn_cords.y"),
        "z" to config.getDouble("tp_commands.spawn_cords.z"),
        "yaw" to config.getDouble("tp_commands.spawn_cords.yaw"),
        "pitch" to config.getDouble("tp_commands.spawn_cords.pitch")
    )

    // MagicFire
    fun isMagicFireEnabled(): Boolean = config.getBoolean("magicfire.status")
    fun getMagicFirePrefix(): String? = config.getString("magicfire.prefix")
    fun getMagicFireMenuTitle(): String? = config.getString("magicfire.menu.title")
    fun getMagicFireMenuSize(): Int = config.getInt("magicfire.menu.size")
    fun getMagicFirePortalIds(): List<String> = config.getStringList("magicfire.portal_ids")
    fun getMagicFireAnimation(): Boolean = config.getBoolean("magicfire.animation")
    fun getMagicFireDefault(): Int = config.getInt("magicfire.default")
    fun getMagicFirePortalKeyId(): String? = config.getString("magicfire.portal_key_id")
    fun getMagicFireRoles(): Map<String, Int> {
        val rolesSection = config.getConfigurationSection("magicfire.roles") ?: return emptyMap()
        return rolesSection.getKeys(false).associateWith { rolesSection.getInt(it) }
    }

    // MagicKey
    fun isMagicKeyEnabled(): Boolean = config.getBoolean("magickey.status")
    fun getKeyCooldown(): Int = config.getInt("magickey.key_cooldown")
    fun getKeyIds(): List<String> = config.getStringList("magickey.key_id")
    fun getKeyLore(): List<String> = config.getStringList("magickey.key_lore")
    fun isHomeEnabled(): Boolean = config.getBoolean("magickey.home")
    fun getMenuTitle(): String? = config.getString("magickey.menu_title")
    fun getHomeCooldown(): Int = config.getInt("magickey.home_cooldown")
    fun isHomeGuiEnabled(): Boolean = config.getBoolean("magickey.home_gui")
    fun getWorldTeleportBlacklist(): List<String> = config.getStringList("magickey.world_teleport_blacklist")
    fun getWorldCreateBlacklist(): List<String> = config.getStringList("magickey.world_create_blacklist")

    // Psgod
    fun isPsgodEnabled(): Boolean = config.getBoolean("psgod.status")
    fun getPsgodPrefix(): String? = config.getString("psgod.prefix")
    fun getPsgodAliases(): List<String> = config.getStringList("psgod.alias")
    fun usePsgodRegion(): Boolean = config.getBoolean("psgod.use_region")
    fun getPsgodMaxPerPlayer(): Int = config.getInt("psgod.max_per_player")
    fun getPsgodRestartDays(): Int = config.getInt("psgod.restart_days")
    fun getPsgodStartDate(): String? = config.getString("psgod.start_date")
    fun getPsgodMessages(): Map<String, String> {
        val messagesSection = config.getConfigurationSection("psgod.mensages") ?: return emptyMap()
        return messagesSection.getKeys(false).associateWith { messagesSection.getString(it).orEmpty() }
    }

    // Tear - Novos métodos
    fun isTearEnabled(): Boolean = config.getBoolean("tear.enabled", true)

    fun getTearId(): String? = config.getString("tear.id")

    fun getTearMenuTitle(): String = config.getString("tear.menu.title", "§8Tear de Crafting") ?: "§8Tear de Crafting"

    fun getTearMenuSize(): Int = config.getInt("tear.menu.size", 36)

    // Métodos para gerenciar crafts
    fun getAllCraftIds(): List<String> {
        val craftsSection = craftsConfig.getConfigurationSection("crafts") ?: return emptyList()
        return craftsSection.getKeys(false).toList()
    }

    fun craftExists(craftId: String): Boolean {
        return craftsConfig.contains("crafts.$craftId")
    }

    fun getCraftTime(craftId: String): Int {
        return craftsConfig.getInt("crafts.$craftId.time", 60)
    }

    fun getCraftResult(craftId: String): String? {
        return craftsConfig.getString("crafts.$craftId.result")
    }

    fun getCraftMaterials(craftId: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()

        // Obter os materiais
        val materialsSection = craftsConfig.getConfigurationSection("crafts.$craftId.materials") ?: return emptyMap()
        val quantitiesSection = craftsConfig.getConfigurationSection("crafts.$craftId.quantities") ?: return emptyMap()

        // Processar item central (obrigatório)
        val centralItem = materialsSection.getString("central")
        if (centralItem != null) {
            val quantity = quantitiesSection.getInt(centralItem, 1)
            result[centralItem] = quantity
        }

        // Processar item superior (opcional)
        val superiorItem = materialsSection.getString("superior")
        if (superiorItem != null) {
            val quantity = quantitiesSection.getInt(superiorItem, 1)
            result[superiorItem] = quantity
        }

        // Processar item inferior (opcional)
        val inferiorItem = materialsSection.getString("inferior")
        if (inferiorItem != null) {
            val quantity = quantitiesSection.getInt(inferiorItem, 1)
            result[inferiorItem] = quantity
        }

        return result
    }

    fun findCraftId(centralItemId: String?, superiorItemId: String?, inferiorItemId: String?): String? {
        if (centralItemId == null) return null

        val craftsSection = craftsConfig.getConfigurationSection("crafts") ?: return null

        for (craftId in craftsSection.getKeys(false)) {
            val materialsSection = craftsConfig.getConfigurationSection("crafts.$craftId.materials") ?: continue

            val centralMatch = materialsSection.getString("central") == centralItemId

            // Verificar item superior
            val superiorMatch = if (superiorItemId == null) {
                materialsSection.getString("superior") == null
            } else {
                materialsSection.getString("superior") == superiorItemId
            }

            // Verificar item inferior
            val inferiorMatch = if (inferiorItemId == null) {
                materialsSection.getString("inferior") == null
            } else {
                materialsSection.getString("inferior") == inferiorItemId
            }

            if (centralMatch && superiorMatch && inferiorMatch) {
                return craftId
            }
        }

        return null
    }

    fun getTearMessages(): Map<String, String> {
        val messagesSection = config.getConfigurationSection("tear.messages") ?: return emptyMap()
        return messagesSection.getKeys(false).associateWith { messagesSection.getString(it).orEmpty() }
    }

    fun getTearMessage(key: String, default: String): String {
        return config.getString("tear.messages.$key", default) ?: default
    }
}