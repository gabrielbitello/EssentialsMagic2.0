package org.Bitello.essentialsMagic.core.apis

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StateFlag

import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

class WorldGuardManager(private val plugin: JavaPlugin) {

    fun initialize() {

        try {
            MAGIC_FIRE_FLAG = registerFlag("magicfire", true)
            MAGIC_KEY_CREATE_FLAG = registerFlag("magickey-create", true)
            MAGIC_KEY_USE_FLAG = registerFlag("magickey-use", true)
            HOME_CREATE_FLAG = registerFlag("home-create", true)
            HOME_TELEPORT_FLAG = registerFlag("home-teleport", true)
            PSGOD_FLAG = registerFlag("PsGod", false)

            plugin.logger.info("Flags personalizadas do WorldGuard registradas com sucesso.")
        } catch (e: Exception) {
            plugin.logger.severe("Erro ao registrar flags do WorldGuard: ${e.message}")
        }
    }

    private fun registerFlag(name: String, defaultValue: Boolean): StateFlag? {
        val registry = WorldGuard.getInstance().flagRegistry
        return try {
            val flag = StateFlag(name, defaultValue)
            registry.register(flag)
            plugin.logger.info("Flag '$name' registered successfully.")
            flag
        } catch (e: Exception) { // <--- Aqui trocamos FlagConflictException por Exception genérica
            val existing = registry.get(name)
            if (existing is StateFlag) {
                plugin.logger.info("Flag '$name' already exists, using the existing flag.")
                existing
            } else {
                plugin.logger.severe("Flag conflict detected for '$name' and could not resolve it!")
                plugin.server.pluginManager.disablePlugin(plugin)
                null
            }
        }
    }

    companion object {
        var MAGIC_FIRE_FLAG: StateFlag? = null
        var MAGIC_KEY_CREATE_FLAG: StateFlag? = null
        var MAGIC_KEY_USE_FLAG: StateFlag? = null
        var HOME_CREATE_FLAG: StateFlag? = null
        var HOME_TELEPORT_FLAG: StateFlag? = null
        var PSGOD_FLAG: StateFlag? = null

        fun isRegionFlagAllowed(location: Location, flag: StateFlag?): Boolean {
            val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
            val set = query.getApplicableRegions(BukkitAdapter.adapt(location))
            return set.testState(null, flag)
        }
    }
}
