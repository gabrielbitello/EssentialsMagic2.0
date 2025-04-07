package org.Bitello.essentialsMagic.core.apis

import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.platform.PlayerAdapter
import org.bukkit.entity.Player
import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.Bukkit


class LuckPermsManager(private val plugin: EssentialsMagic) {
    private var luckPerms: LuckPerms? = null

    fun initialize() {
        luckPerms = plugin.server.servicesManager.getRegistration(LuckPerms::class.java)?.provider
            ?: throw IllegalStateException("LuckPerms API não encontrada")
        plugin.logger.info("LuckPerms API inicializada com sucesso.")
    }

    fun hasPermission(player: Player, permission: String): Boolean {
        val adapter: PlayerAdapter<Player> = luckPerms?.getPlayerAdapter(Player::class.java)
            ?: throw IllegalStateException("PlayerAdapter não inicializado")
        val user: User = adapter.getUser(player)
        return user.cachedData.permissionData.checkPermission(permission).asBoolean()
    }
}