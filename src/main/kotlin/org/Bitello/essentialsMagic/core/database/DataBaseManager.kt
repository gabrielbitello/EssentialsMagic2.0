package org.Bitello.essentialsMagic.core.database

import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.core.config.ConfigManager
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Level

class DataBaseManager(private val plugin: EssentialsMagic)  {
    private var connection: Connection? = null

    fun initialize() {
        val sqldata = plugin.configManager.getMysqlConfig()
        val host = sqldata["host"] as String
        val port = sqldata["port"] as Int
        val database = sqldata["database"] as String
        val username = sqldata["username"] as String
        val password = sqldata["password"] as String
        val url = "jdbc:mysql://$host:$port/$database"

        try {
            connection = DriverManager.getConnection(url, username, password)
            EssentialsMagic.instance.logger.info("Connected to MySQL database successfully.")
        } catch (e: SQLException) {
            EssentialsMagic.instance.logger.log(Level.SEVERE, "Could not connect to MySQL database", e)
        }
    }

    fun getConnection(): Connection? {
        if (connection == null || connection!!.isClosed) {
            initialize()
        }
        return connection
    }

    fun closeConnection() {
        if (connection != null) {
            try {
                connection!!.close()
                EssentialsMagic.instance.logger.info("MySQL connection closed successfully.")
            } catch (e: SQLException) {
                EssentialsMagic.instance.logger.log(Level.SEVERE, "Could not close MySQL connection", e)
            }
        }
    }
}