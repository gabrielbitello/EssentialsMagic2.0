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
                "world VARCHAR(255) NULL," +
                "x DOUBLE NULL," +
                "y DOUBLE NULL," +
                "z DOUBLE NULL," +
                "yaw VARCHAR(255)NULL," +
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
        try {
            // Primeiro verifica se o jogador já tem um registro
            val checkQuery = "SELECT 1 FROM EM_MagicKey WHERE player_id = ?"
            var exists = false
            connection?.prepareStatement(checkQuery)?.use { checkStatement ->
                checkStatement.setString(1, playerId.toString())
                checkStatement.executeQuery().use { resultSet ->
                    exists = resultSet.next()
                }
            }

            // Se o registro existir, atualiza. Caso contrário, insere
            val query = if (exists) {
                "UPDATE EM_MagicKey SET world = ?, x = ?, y = ?, z = ?, yaw = ? WHERE player_id = ?"
            } else {
                "INSERT INTO EM_MagicKey (world, x, y, z, yaw, player_id) VALUES (?, ?, ?, ?, ?, ?)"
            }

            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, world)
                statement.setDouble(2, x)
                statement.setDouble(3, y)
                statement.setDouble(4, z)
                statement.setFloat(5, yaw)
                statement.setString(6, playerId.toString())
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

        val query = if (existingData == null) {
            "INSERT INTO EM_MagicKey (player_id, `MK-a`) VALUES (?, ?)"
        } else {
            "UPDATE EM_MagicKey SET `MK-a` = ? WHERE player_id = ?"
        }

        try {
            connection?.prepareStatement(query)?.use { statement ->
                if (existingData == null) {
                    statement.setString(1, playerUUID.toString())
                    statement.setString(2, newData)
                } else {
                    statement.setString(1, newData)
                    statement.setString(2, playerUUID.toString())
                }
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