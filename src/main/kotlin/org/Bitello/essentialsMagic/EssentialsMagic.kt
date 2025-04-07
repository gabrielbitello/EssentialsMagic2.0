package org.Bitello.essentialsMagic


import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

import org.Bitello.essentialsMagic.core.apis.WorldGuardManager
import org.Bitello.essentialsMagic.core.apis.LuckPermsManager
import org.Bitello.essentialsMagic.core.config.ConfigManager
import org.Bitello.essentialsMagic.core.database.DataBaseManager

import org.Bitello.essentialsMagic.features.magicfire.MagicFireManager
import org.Bitello.essentialsMagic.features.magickey.MagicKeyManager

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin


class EssentialsMagic : JavaPlugin() {

    companion object {
        lateinit var instance: EssentialsMagic
            private set
    }


    lateinit var configManager: ConfigManager
        private set
    lateinit var databaseManager: DataBaseManager
        private set

    lateinit var worldGuard: WorldGuardManager
        private set
    lateinit var luckPerms: LuckPermsManager



    lateinit var magicfireManager: MagicFireManager
        private set
    lateinit var magickeyManager: MagicKeyManager
        private set

    override fun onLoad() {

        // ConfigManager initialization
        configManager = ConfigManager(this)
        configManager.loadConfigs()

        // WorldGuardManager initialization
        try {
            WorldGuardManager(this).initialize()
        } catch (e: Exception) {
            logger.severe("Erro ao inicializar WorldGuardManager: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onEnable() {
        instance = this

        // DataBase initialization
        databaseManager = DataBaseManager(this)
        databaseManager.initialize()

        // MagicFire initialization
        magicfireManager = MagicFireManager(this)
        magicfireManager.initialize()

        // MagicKey initialization
        magickeyManager = MagicKeyManager(this)
        magickeyManager.initialize()

        // LuckPerms initialization
        try {
            luckPerms = LuckPermsManager(this)
            luckPerms.initialize()
        } catch (e: Exception) {
            logger.severe("Erro ao inicializar LuckPermsManager: ${e.message}")
            e.printStackTrace()
        }

        val message = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[EssentialsMagic] plugin has been enabled.").color(NamedTextColor.LIGHT_PURPLE))
        Bukkit.getConsoleSender().sendMessage(message)
    }

    override fun onDisable() {
        instance = this
        DataBaseManager(this).closeConnection()


        val message = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[EssentialsMagic] plugin has been disabled.").color(NamedTextColor.RED))
        Bukkit.getConsoleSender().sendMessage(message)
    }
}


