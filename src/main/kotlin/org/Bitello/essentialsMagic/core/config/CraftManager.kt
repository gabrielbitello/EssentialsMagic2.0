package org.Bitello.essentialsMagic.core.config

import org.Bitello.essentialsMagic.EssentialsMagic
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class CraftManager(private val plugin: EssentialsMagic) {

    private lateinit var craftsConfig: YamlConfiguration

    fun loadCraftConfigs() {
        val craftsFolder = File(plugin.dataFolder, "crafts")
        if (!craftsFolder.exists()) {
            craftsFolder.mkdirs()
        }

        // Verificar e criar arquivos padrão, se necessário
        val defaultFiles = listOf("tear.yml", "prism.yml")
        for (fileName in defaultFiles) {
            val file = File(craftsFolder, fileName)
            if (!file.exists()) {
                plugin.saveResource("crafts/$fileName", false)
            }
        }

        val craftFiles = craftsFolder.listFiles { file -> file.extension == "yml" } ?: return
        craftsConfig = YamlConfiguration()

        for (file in craftFiles) {
            val config = YamlConfiguration.loadConfiguration(file)
            for (key in config.getConfigurationSection("crafts")?.getKeys(false) ?: emptySet()) {
                craftsConfig.set("crafts.$key", config.getConfigurationSection("crafts.$key"))
            }
        }
    }

    inner class CraftFileManager(fileName: String) {
        private val file: File = File(plugin.dataFolder, "crafts/$fileName.yml")
        private val config: YamlConfiguration = YamlConfiguration.loadConfiguration(file)

        fun craftExists(craftId: String): Boolean {
            return config.contains("crafts.$craftId")
        }

        fun getCraftTime(craftId: String): Int {
            return config.getInt("crafts.$craftId.time", 60)
        }

       fun getCraftMaterials(craftId: String): Map<String, Int> {
           val result = mutableMapOf<String, Int>()

           // Verificar se a configuração foi carregada
           if (!::craftsConfig.isInitialized) {
               plugin.logger.warning("Erro: craftsConfig não foi inicializado.")
               return emptyMap()
           }

           plugin.logger.info("Carregando materiais para o craft ID: $craftId")

           // Obter as seções de materiais e quantidades
           val materialsSection = craftsConfig.getConfigurationSection("crafts.$craftId.materials")
           val quantitiesSection = craftsConfig.getConfigurationSection("crafts.$craftId.quantities")

           if (materialsSection == null) {
               plugin.logger.warning("Seção 'materials' não encontrada para o craft ID: $craftId")
               return emptyMap()
           }

           if (quantitiesSection == null) {
               plugin.logger.warning("Seção 'quantities' não encontrada para o craft ID: $craftId")
               return emptyMap()
           }

           // Processar materiais (item1 a item9)
           for (i in 1..9) {
               val key = "item$i"
               val item = materialsSection.getString(key)
               if (item != null) {
                   val quantity = quantitiesSection.getInt(key, 1)
                   result[item] = quantity
                   plugin.logger.info("Material encontrado: $item, Quantidade: $quantity")
               }
           }

           // Garantir que pelo menos 1 material obrigatório esteja presente
           if (result.isEmpty()) {
               plugin.logger.warning("Erro: Nenhum material obrigatório encontrado para o craft ID: $craftId")
               return emptyMap()
           }

           return result
       }

        fun findCraftId(vararg itemIds: String?): String? {
            if (itemIds.isEmpty() || itemIds[0] == null) {
                plugin.logger.warning("Erro: Nenhum item obrigatório fornecido para findCraftId.")
                return null
            }

            plugin.logger.info("Procurando craft para os itens: ${itemIds.joinToString()}")

            val craftsSection = config.getConfigurationSection("crafts") ?: return null

            for (craftId in craftsSection.getKeys(false)) {
                val materialsSection = config.getConfigurationSection("crafts.$craftId.materials") ?: continue

                // Obter todos os materiais necessários para o craft na ordem
                val requiredItems = (1..9).map { materialsSection.getString("item$it") }

                plugin.logger.info("Verificando craft ID: $craftId, Materiais necessários: $requiredItems, Itens fornecidos: ${itemIds.toList()}")

                // Verificar se os itens fornecidos correspondem aos itens necessários
                val matches = itemIds.withIndex().all { (index, provided) ->
                    provided == null || provided == requiredItems.getOrNull(index)
                }

                if (matches) {
                    plugin.logger.info("Craft encontrado: $craftId")
                    return craftId
                }
            }

            plugin.logger.warning("Nenhum craft encontrado para os itens fornecidos.")
            return null
        }

       fun getCraftResult(craftId: String): String? {
           val result = config.getString("crafts.$craftId.result")
           return result
       }

    }
}