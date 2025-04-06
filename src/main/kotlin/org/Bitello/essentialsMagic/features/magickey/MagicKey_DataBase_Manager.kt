package org.Bitello.essentialsMagic.features.magickey

import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.core.database.DataBaseManager

import org.bukkit.Location

import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.logging.Level


class MagicKey_DataBase_Manager(private val plugin: EssentialsMagic) {
    private val connection: Connection? = DataBaseManager(plugin).getConnection()

    init {
        this.checkAndCreateTable()
    }

    private fun checkAndCreateTable() {
        if (!MagicKeyManager.isMagicKeyEnabled(plugin)) return

        val createTableSQL = "CREATE TABLE IF NOT EXISTS EM_MagicKey (" +
                "player_id VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(255) NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "yaw VARCHAR(255) NOT NULL," +
                "`MK-a` VARCHAR(800) NULL," +
                "`MK-b` VARCHAR(2000) NULL" +
                ");"

        try {
            connection?.prepareStatement(createTableSQL)?.use { statement ->
                statement.execute()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Erro ao criar a tabela homes.", e)
        }
    }

    fun setHome(playerId: UUID, world: String?, x: Double, y: Double, z: Double, yaw: Float) {
        val query = "REPLACE INTO EM_MagicKey (player_id, world, x, y, z, yaw) VALUES (?, ?, ?, ?, ?, ?)"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, playerId.toString())
                statement.setString(2, world)
                statement.setDouble(3, x)
                statement.setDouble(4, y)
                statement.setDouble(5, z)
                statement.setFloat(6, yaw)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Erro ao definir a home do jogador.", e)
        }
    }

    fun getHome(playerId: UUID): Location? {
        val query = "SELECT world, x, y, z, yaw FROM EM_MagicKey WHERE player_id = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val world = resultSet.getString("world")
                        val x = resultSet.getDouble("x")
                        val y = resultSet.getDouble("y")
                        val z = resultSet.getDouble("z")
                        val yaw = resultSet.getFloat("yaw")

                        return Location(plugin.server.getWorld(world), x, y, z, yaw, 0f)
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Erro ao obter a home do jogador.", e)
        }
        return null
    }

    fun savePortalKey(playerUUID: UUID, keyData: String): Boolean {
        val existingData = loadPortalKey(playerUUID)
        val newData = (existingData ?: "") + keyData + "/"

        if (newData.length > 800) {
            plugin.logger.severe("Erro: Dados da chave são muito longos para salvar no banco de dados.")
            return false
        }

        val query = "UPDATE EM_MagicKey SET `MK-a` = ? WHERE player_id = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, newData)
                statement.setString(2, playerUUID.toString())
                statement.executeUpdate()
            }
            return true
        } catch (e: SQLException) {
            plugin.logger.severe("Erro ao salvar a chave de portal: " + e.message)
            return false
        }
    }

    fun loadPortalKey(playerId: UUID): String? {
        val query = "SELECT `MK-a` FROM EM_MagicKey WHERE player_id = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getString("MK-a")
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Erro ao carregar a chave de portal: " + e.message)
        }
        return null
    }

    fun updatePortalKey(playerUUID: UUID, keyData: String?, slot: Int): Boolean {
        val existingData = loadPortalKey(playerUUID)
        val keys = existingData!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newData = StringBuilder()

        var keyUpdated = false
        for (key in keys) {
            if (!key.isEmpty() && !key.endsWith(":$slot")) {
                newData.append(key).append("/")
            } else if (key.endsWith(":$slot")) {
                newData.append(keyData).append("/")
                keyUpdated = true
            }
        }

        val query = "UPDATE EM_MagicKey SET `MK-a` = ? WHERE player_id = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, newData.toString())
                statement.setString(2, playerUUID.toString())
                statement.executeUpdate()
            }
            return true
        } catch (e: SQLException) {
            plugin.logger.severe("Erro ao atualizar a chave de portal: " + e.message)
            return false
        }
    }

    fun deletePortalKey(playerUUID: UUID, slot: Int): Boolean {
        val existingData = loadPortalKey(playerUUID) ?: return false

        val keys = existingData.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newData = StringBuilder()

        for (key in keys) {
            if (!key.isEmpty() && !key.endsWith(":$slot")) {
                newData.append(key).append("/")
            }
        }

        val query = "UPDATE EM_MagicKey SET `MK-a` = ? WHERE player_id = ?"
        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, newData.toString())
                statement.setString(2, playerUUID.toString())
                statement.executeUpdate()
            }
            return true
        } catch (e: SQLException) {
            plugin.logger.severe("Erro ao deletar a chave de portal: " + e.message)
            return false
        }
    }
}