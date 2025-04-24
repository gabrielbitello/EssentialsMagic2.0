package org.Bitello.essentialsMagic.core.database

import org.Bitello.essentialsMagic.EssentialsMagic
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class DataBaseManager(private val plugin: EssentialsMagic) {
    @Volatile
    private var connection: Connection? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var dbUrl: String
    private lateinit var username: String
    private lateinit var password: String

    fun initialize() {
        synchronized(this) {
            if (this::dbUrl.isInitialized) return // Já inicializado

            val config = plugin.configManager.getMysqlConfig()
            val host = config["host"] as String
            val port = config["port"] as Int
            val database = config["database"] as String
            username = config["username"] as String
            password = config["password"] as String
            dbUrl = "jdbc:mysql://$host:$port/$database?autoReconnect=true&useSSL=false"

            connect()
            startConnectionMonitor()
        }
    }

    private fun connect() {
        try {
            connection = DriverManager.getConnection(dbUrl, username, password)
            plugin.logger.info("Connected to MySQL database successfully.")
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to connect to MySQL database", e)
        }
    }

    fun getConnection(): Connection? {
        synchronized(this) {
            try {
                if (connection == null || connection!!.isClosed || !connection!!.isValid(2)) {
                    plugin.logger.warning("MySQL connection is invalid. Reconnecting...")
                    connect()
                }
            } catch (e: SQLException) {
                plugin.logger.log(Level.SEVERE, "Error while validating MySQL connection", e)
                connect()
            }
            return connection
        }
    }

    fun closeConnection() {
        synchronized(this) {
            try {
                connection?.close()
                plugin.logger.info("MySQL connection closed successfully.")
            } catch (e: SQLException) {
                plugin.logger.log(Level.SEVERE, "Error while closing MySQL connection", e)
            } finally {
                connection = null
            }
        }
    }

    private fun startConnectionMonitor() {
        scheduler.scheduleAtFixedRate({
            try {
                if (connection == null || connection!!.isClosed || !connection!!.isValid(2)) {
                    plugin.logger.warning("Connection check failed. Attempting reconnect...")
                    connect()
                }
            } catch (e: SQLException) {
                plugin.logger.log(Level.WARNING, "Error during connection monitor", e)
                connect()
            }
        }, 5, 5, TimeUnit.MINUTES)
    }

    fun shutdown() {
        scheduler.shutdownNow()
        closeConnection()
    }
}
