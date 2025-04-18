package org.Bitello.essentialsMagic.core.config

import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: EssentialsMagic) {

    private lateinit var config: FileConfiguration

    fun loadConfigs() {
        plugin.saveDefaultConfig()
        config = plugin.config

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

    fun getTearIdAnimation(): String? = config.getString("tear.id_animation")

    fun getTearMenuTitle(): String = config.getString("tear.menu.title", "§8Tear de Crafting") ?: "§8Tear de Crafting"

    // Prisma
    fun isPrismaEnabled(): Boolean = config.getBoolean("prisma.enabled", true)

    fun getPrismaId(): String? = config.getString("prisma.id")

    fun getPrismaIdAnimation(): String? = config.getString("prisma.id_animation")

   fun getPrismaFuel(): List<Pair<String, Int>> {
       val fuelSection = config.getConfigurationSection("prisma.fuel")
       if (fuelSection == null) {
           plugin.logger.warning("A seção 'prisma.fuel' não foi encontrada no arquivo de configuração.")
           return emptyList()
       }

       val fuels = fuelSection.getKeys(false).map { key ->
           val value = fuelSection.getInt(key)
           plugin.logger.info("Carregando combustível: $key com valor: $value")
           key to value
       }

       if (fuels.isEmpty()) {
           plugin.logger.warning("Nenhum combustível foi encontrado na seção 'prisma.fuel'.")
       }

       return fuels
   }


    fun getMensagem(key: String, default: String): String {
        return config.getString("messages.$key", default) ?: default
    }
}