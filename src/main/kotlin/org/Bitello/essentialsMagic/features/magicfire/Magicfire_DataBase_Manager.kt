package org.Bitello.essentialsMagic.features.magicfire

import org.Bitello.essentialsMagic.core.config.ConfigManager
import org.Bitello.essentialsMagic.core.database.DataBaseManager
import org.Bitello.essentialsMagic.features.magicfire.gui.MagicFire_TpMenu_Manage as tp_menu
import net.luckperms.api.LuckPermsProvider
import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.SQLException
import java.util.*

class Magicfire_DataBase_Manager(private val plugin: EssentialsMagic) {
    private val connection: Connection? = DataBaseManager(plugin).getConnection()
    private val configManager: ConfigManager = plugin.configManager

    init {
        checkAndCreateTable()
    }

    private fun checkAndCreateTable() {
        if (!configManager.isMagicFireEnabled()) return

        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS EM_MagicFire (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36),
                name VARCHAR(255),
                description TEXT,
                category VARCHAR(100),
                icon VARCHAR(100),
                world VARCHAR(100),
                x DOUBLE,
                y DOUBLE,
                z DOUBLE,
                status INT,
                banned_players TEXT,
                visits INT,
                yaw FLOAT,
                portal_type VARCHAR(100)
            );
        """.trimIndent()

        try {
            connection?.prepareStatement(createTableSQL)?.use { stmt ->
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Não foi criar a tabela: ${e.message}")
        }
    }

    val portals: List<tp_menu.Portal>
        get() {
            val portals = mutableListOf<tp_menu.Portal>()
            val query = "SELECT * FROM EM_MagicFire"

            try {
                connection?.createStatement()?.use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        while (resultSet.next()) {
                            val portal = tp_menu.Portal(
                                resultSet.getString("name"),
                                resultSet.getString("world"),
                                resultSet.getDouble("x"),
                                resultSet.getDouble("y"),
                                resultSet.getDouble("z"),
                                resultSet.getInt("visits"),
                                resultSet.getString("icon"),
                                resultSet.getString("category"),
                                resultSet.getString("description"),
                                resultSet.getString("portal_type"),
                                resultSet.getString("yaw"),
                                resultSet.getString("banned_players")
                            )
                            portals.add(portal)
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }

            return portals
        }

    fun getPortalByName(name: String?): tp_menu.Portal? {
        val query = "SELECT * FROM EM_MagicFire WHERE name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return tp_menu.Portal(
                            resultSet.getString("name"),
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getInt("visits"),
                            resultSet.getString("icon"),
                            resultSet.getString("category"),
                            resultSet.getString("description"),
                            resultSet.getString("portal_type"),
                            resultSet.getString("yaw"),
                            resultSet.getString("banned_players")
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return null
    }

    fun incrementVisits(portalName: String?) {
        val query = "UPDATE EM_MagicFire SET visits = visits + 1 WHERE name = ?"
        try {
            connection?.prepareStatement(query)?.use { stmt ->
                stmt.setString(1, portalName)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun isPortalNearby(location: Location, radius: Int): Boolean {
        val query = """
            SELECT COUNT(*) AS count FROM EM_MagicFire WHERE world = ? AND 
            SQRT(POW(x - ?, 2) + POW(y - ?, 2) + POW(z - ?, 2)) <= ?
        """.trimIndent()
        try {
            connection?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, location.world.name)
                pstmt.setDouble(2, location.x)
                pstmt.setDouble(3, location.y)
                pstmt.setDouble(4, location.z)
                pstmt.setInt(5, radius)
                pstmt.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("count") > 0
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("Could not execute query: ${e.message}")
        }
        return false
    }

    fun isPortalNameExists(name: String?): Boolean {
        val query = "SELECT COUNT(*) AS count FROM EM_MagicFire WHERE name = ?"
        try {
            connection?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, name)
                pstmt.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("count") > 0
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("Could not execute query: ${e.message}")
        }
        return false
    }

    fun verifyAndInsertPortal(
        player: Player,
        playerUUID: String?,
        portalName: String?,
        playerName: String?,
        description: String?,
        category: String?,
        icon: String?,
        world: String?,
        x: Double,
        y: Double,
        z: Double,
        status: Int,
        bannedPlayers: String?,
        visits: Int,
        portalType: String?,
        yaw: Float
    ): Boolean {
        val maxPortals = getMaxPortals(player)
        val query = "SELECT COUNT(*) AS count FROM EM_MagicFire WHERE player_uuid = ?"
        try {
            connection?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, playerUUID)
                pstmt.executeQuery().use { resultSet ->
                    if (resultSet.next() && resultSet.getInt("count") < maxPortals) {
                        insertData(
                            playerUUID,
                            portalName,
                            description,
                            category,
                            icon,
                            world,
                            x,
                            y,
                            z,
                            status,
                            bannedPlayers,
                            visits,
                            portalType,
                            yaw
                        )
                        return true
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Não foi possível executar a consulta: ${e.message}")
        }
        return false
    }

    private fun insertData(
        playerUUID: String?,
        portalName: String?,
        description: String?,
        category: String?,
        icon: String?,
        world: String?,
        x: Double,
        y: Double,
        z: Double,
        status: Int,
        bannedPlayers: String?,
        visits: Int,
        portalType: String?,
        yaw: Float
    ) {
        val insertSQL = """
            INSERT INTO EM_MagicFire (player_uuid, name, description, category, icon, world, x, y, z, status, banned_players, visits, portal_type, yaw) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            connection?.prepareStatement(insertSQL)?.use { pstmt ->
                pstmt.setString(1, playerUUID)
                pstmt.setString(2, portalName)
                pstmt.setString(3, description)
                pstmt.setString(4, category)
                pstmt.setString(5, icon)
                pstmt.setString(6, world)
                pstmt.setDouble(7, x)
                pstmt.setDouble(8, y)
                pstmt.setDouble(9, z)
                pstmt.setInt(10, status)
                pstmt.setString(11, bannedPlayers)
                pstmt.setInt(12, visits)
                pstmt.setString(13, portalType)
                pstmt.setFloat(14, yaw)
                pstmt.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Não foi inserir a data: ${e.message}")
        }
    }

    fun deleteNearbyPortal(location: Location, radius: Int) {
        val query = """
            DELETE FROM EM_MagicFire WHERE world = ? AND 
            SQRT(POW(x - ?, 2) + POW(y - ?, 2) + POW(z - ?, 2)) <= ? LIMIT 1
        """.trimIndent()
        try {
            connection?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, location.world.name)
                pstmt.setDouble(2, location.x)
                pstmt.setDouble(3, location.y)
                pstmt.setDouble(4, location.z)
                pstmt.setInt(5, radius)
                pstmt.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Não foi possível executar a consulta: ${e.message}")
        }
    }

    private fun getMaxPortals(player: Player): Int {
        if (configManager.useLuckPerms()) {
            val luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms")
            if (luckPermsPlugin != null && luckPermsPlugin.isEnabled) {
                val luckPerms = LuckPermsProvider.get()
                val user = luckPerms.userManager.getUser(player.uniqueId)
                if (user != null) {
                    for (role in configManager.getMagicFireRoles().keys) {
                        if (player.hasPermission("EssentialsMagic.MagicFire.$role")) {
                            return configManager.getMagicFireRoles()[role] ?: configManager.getMagicFireDefault()
                        }
                    }
                }
            }
        }
        return configManager.getMagicFireDefault()
    }

    fun getPortalsByCategory(category: String?): List<tp_menu.Portal> {
        val portals = mutableListOf<tp_menu.Portal>()
        val query = "SELECT * FROM EM_MagicFire WHERE category = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, category)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val portal = tp_menu.Portal(
                            resultSet.getString("name"),
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getInt("visits"),
                            resultSet.getString("icon"),
                            resultSet.getString("category"),
                            resultSet.getString("description"),
                            resultSet.getString("portal_type"),
                            resultSet.getString("yaw"),
                            resultSet.getString("banned_players")
                        )
                        portals.add(portal)
                    }
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return portals
    }

    fun isPortalOwner(playerUUID: UUID, portalName: String?): PortalInfo? {
        val query = "SELECT yaw, world, x, y, z, banned_players FROM EM_MagicFire WHERE player_uuid = ? AND name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, portalName)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return PortalInfo(
                            resultSet.getFloat("yaw"),
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getString("banned_players")
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao verificar propriedades do protal: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    fun updatePortalType(playerUUID: UUID, newPortalType: String?) {
        val query = "UPDATE EM_MagicFire SET portal_type = ? WHERE player_uuid = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, newPortalType)
                statement.setString(2, playerUUID.toString())
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao atualziar o tipo do portal: ${e.message}")
            e.printStackTrace()
        }
    }

    class PortalInfo(
        val yaw: Float,
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val bannedPlayers: String
    ) {
        val location: Location
            get() = Location(Bukkit.getWorld(world), x, y, z)
    }

    fun updatePortalLocation(playerUUID: UUID, portalName: String?, newLocation: Location, newYaw: Float) {
        val query = "UPDATE EM_MagicFire SET world = ?, x = ?, y = ?, z = ?, yaw = ? WHERE player_uuid = ? AND name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, newLocation.world.name)
                statement.setDouble(2, newLocation.x)
                statement.setDouble(3, newLocation.y)
                statement.setDouble(4, newLocation.z)
                statement.setFloat(5, newYaw)
                statement.setString(6, playerUUID.toString())
                statement.setString(7, portalName)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao atualizar a localização do protal: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateBannedPlayers(portalName: String?, bannedPlayers: String?) {
        val query = "UPDATE EM_MagicFire SET banned_players = ? WHERE name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, bannedPlayers)
                statement.setString(2, portalName)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao atulizar a lsita de jogadores banidos: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updatePortalIcon(playerUUID: UUID, portalName: String?, iconType: String?) {
        val query = "UPDATE EM_MagicFire SET icon = ? WHERE player_uuid = ? AND name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, iconType)
                statement.setString(2, playerUUID.toString())
                statement.setString(3, portalName)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao atualizar o icone do protal: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updatePortalCategory(playerUUID: UUID, portalName: String?, category: String?) {
        val query = "UPDATE EM_MagicFire SET category = ? WHERE player_uuid = ? AND name = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, category)
                statement.setString(2, playerUUID.toString())
                statement.setString(3, portalName)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getConsoleSender().sendMessage("Erro ao atualziar a categorai do protal: ${e.message}")
            e.printStackTrace()
        }
    }
}