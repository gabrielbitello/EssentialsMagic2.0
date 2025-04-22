package org.Bitello.essentialsMagic.core.database

        import org.Bitello.essentialsMagic.EssentialsMagic
        import java.sql.Connection
        import java.sql.DriverManager
        import java.sql.SQLException
        import java.util.concurrent.Executors
        import java.util.concurrent.TimeUnit
        import java.util.logging.Level

        class DataBaseManager(private val plugin: EssentialsMagic) {
            private var connection: Connection? = null
            private val scheduler = Executors.newScheduledThreadPool(1)

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

                // Agendar reinicialização da conexão a cada 5 minutos
                scheduler.scheduleAtFixedRate({
                    try {
                        closeConnection()
                        connection = DriverManager.getConnection(url, username, password)
                        EssentialsMagic.instance.logger.info("MySQL connection restarted successfully.")
                    } catch (e: SQLException) {
                        EssentialsMagic.instance.logger.log(Level.SEVERE, "Could not restart MySQL connection", e)
                    }
                }, 5, 5, TimeUnit.MINUTES)
            }

            fun getConnection(): Connection? {
                synchronized(this) {
                    try {
                        if (connection == null || connection!!.isClosed) {
                            EssentialsMagic.instance.logger.warning("MySQL connection was closed. Reinitializing...")
                            initialize()
                        }
                    } catch (e: SQLException) {
                        EssentialsMagic.instance.logger.log(Level.SEVERE, "Error while checking MySQL connection state", e)
                        initialize()
                    }
                    return connection
                }
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

            fun shutdownScheduler() {
                scheduler.shutdown()
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    scheduler.shutdownNow()
                }
            }
        }