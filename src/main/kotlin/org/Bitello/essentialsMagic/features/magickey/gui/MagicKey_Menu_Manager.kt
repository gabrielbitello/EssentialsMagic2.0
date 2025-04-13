package org.Bitello.essentialsMagic.features.magickey.gui

import org.Bitello.essentialsMagic.features.magickey.MagicKey_DataBase_Manager
import org.Bitello.essentialsMagic.common.colorize

import com.nexomc.nexo.api.NexoItems
import org.Bitello.essentialsMagic.EssentialsMagic

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType


import java.util.*

class MagicKey_Menu_Manager(private val plugin: EssentialsMagic) : Listener {
    private val database = MagicKey_DataBase_Manager(plugin)
    private val config = plugin.config
    private val cooldowns: MutableMap<UUID, Long> = HashMap()
    private val menuTitle: String

    init {
        this.menuTitle = (plugin.config.getString("magickey.menu_title", "Home Menu").colorize())
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun openHomeMenu(player: Player) {
        val gui = Bukkit.createInventory(null, config.getInt("magickey.menu.size", 45), menuTitle)

        // Central Icon
        val homeIcon: ItemStack
        val iconId = config.getString("magickey.menu.buttons.Home.material", "RED_BED")!!
            .uppercase(Locale.getDefault())

        homeIcon = if (NexoItems.exists(iconId)) {
            // Se o item existir no Nexo, obtenha-o do Nexo
            NexoItems.itemFromId(iconId)?.build() ?: ItemStack(Material.RED_BED)
        } else {
            // Caso contrário, tente usar como material do Minecraft
            try {
                createItemStack(
                    Material.valueOf(iconId),
                    (config.getString("magickey.menu.buttons.Home.name", "§aHome").colorize()),
                    colorizeList(config.getStringList("magickey.menu.buttons.Home.lore"))
                )
            } catch (e: IllegalArgumentException) {
                // Se não for um material válido, use material padrão
                plugin.logger.warning("Material inválido: $iconId, usando RED_BED como fallback")
                createItemStack(
                    Material.RED_BED,
                    (config.getString("magickey.menu.buttons.Home.name", "§aHome").colorize()),
                    colorizeList(config.getStringList("magickey.menu.buttons.Home.lore"))
                )
            }
        }

        // Adiciona metadado invisível
        val homeMeta = homeIcon.itemMeta
        if (homeMeta != null) {
            val keyNamespace = NamespacedKey(plugin, "MenuHomeTP")
            homeMeta.persistentDataContainer.set(keyNamespace, PersistentDataType.STRING, "HomeTP")
            homeIcon.setItemMeta(homeMeta)
        }

        gui.setItem(config.getInt("magickey.menu.buttons.Home.slot", 22), homeIcon) // Central slot

        // Load keys from database
        populateKeysInMenu(gui, player)

        // Add Nether Stars
        addNetherStars(gui, player)

        player.openInventory(gui)
    }

    private fun populateKeysInMenu(gui: Inventory, player: Player) {
        val keyData: String? = database.loadPortalKey(player.uniqueId)
        val occupiedSlots = BooleanArray(gui.size)

        if (keyData != null) {
            for (key in keyData.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val keyParts = key.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (keyParts.size == 6) {
                    val keyName = keyParts[0]
                    val creator = keyParts[1]
                    val location = keyParts[2]
                    val uses = keyParts[3].toInt()
                    val material = keyParts[4]
                    val slot = keyParts[5].toInt()

                    val keyItem = if (NexoItems.exists(material))
                        NexoItems.itemFromId(material)?.build() ?: ItemStack(Material.valueOf(material))
                    else
                        ItemStack(Material.valueOf(material))

                    setKeyItemMeta(keyItem, keyName, creator, location, uses)

                    // Adiciona metadado invisível
                    val meta = keyItem.itemMeta
                    if (meta != null) {
                        val keyNamespace = NamespacedKey(plugin, "MenuKey")
                        meta.persistentDataContainer.set(keyNamespace, PersistentDataType.STRING, "MenuKey")
                        keyItem.setItemMeta(meta)
                    }

                    gui.setItem(slot, keyItem)
                    occupiedSlots[slot] = true
                }
            }
        }
    }

    private fun addNetherStars(gui: Inventory, player: Player) {
        val netherStarIconId = config.getString("magickey.menu.buttons.keySlot.material", "NETHER_STAR")!!
            .uppercase(Locale.getDefault())

        val netherStar = if (NexoItems.exists(netherStarIconId)) {
            // Se o item existir no Nexo, obtenha-o do Nexo
            NexoItems.itemFromId(netherStarIconId)?.build() ?: ItemStack(Material.NETHER_STAR)
        } else {
            // Caso contrário, tente usar como material do Minecraft
            try {
                createItemStack(
                    Material.valueOf(netherStarIconId),
                    (config.getString("magickey.menu.buttons.keySlot.name", "§ePorta chave").colorize()),
                    colorizeList(config.getStringList("magickey.menu.buttons.keySlot.lore"))
                )
            } catch (e: IllegalArgumentException) {
                // Se não for um material válido, use material padrão
                plugin.logger.warning("Material inválido: $netherStarIconId, usando NETHER_STAR como fallback")
                createItemStack(
                    Material.NETHER_STAR,
                    (config.getString("magickey.menu.buttons.keySlot.name", "§ePorta chave").colorize()),
                    colorizeList(config.getStringList("magickey.menu.buttons.keySlot.lore"))
                )
            }
        }

        val slots: MutableList<Int> = ArrayList()
        val roles: MutableMap<String, List<Int>> = LinkedHashMap()

        // Adiciona os slots do cargo Default
        val defaultSlots = config.getIntegerList("magickey.menu.buttons.keySlot.roles.Default")
        roles["Default"] = defaultSlots

        // Adiciona os slots dos outros cargos dinamicamente
        for (role in config.getConfigurationSection("magickey.menu.buttons.keySlot.roles")!!.getKeys(false)) {
            if (role != "Default" && player.hasPermission("EssentialsMagic.MagicKey.home.$role")) {
                val roleSlots = config.getIntegerList("magickey.menu.buttons.keySlot.roles.$role")
                roles[role] = roleSlots
            }
        }

        // Adiciona os slots de acordo com a hierarquia
        for (roleSlots in roles.values) {
            slots.addAll(roleSlots)
        }

        for (slot in slots) {
            if (gui.getItem(slot) == null || gui.getItem(slot)!!.type == Material.AIR) {
                // Adiciona metadado invisível
                val meta = netherStar.itemMeta
                if (meta != null) {
                    val keyNamespace = NamespacedKey(plugin, "MenuKeySlot")
                    meta.persistentDataContainer.set(keyNamespace, PersistentDataType.STRING, "MenuKeySlot")
                    netherStar.setItemMeta(meta)
                }
                gui.setItem(slot, netherStar)
            }
        }
    }

    private fun colorizeList(list: List<String>): List<String> {
        val colorizedList: MutableList<String> = ArrayList()
        for (line in list) {
            colorizedList.add((line).colorize())
        }
        return colorizedList
    }

    private fun sanitizeKey(input: String): String {
        return input.lowercase(Locale.getDefault()).replace("[^a-z0-9/._-]".toRegex(), "_")
    }

    @EventHandler
    fun onMenuClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        // Verifica se o menu é o MagicFire
        val title = event.view.title
        if (title != (menuTitle).colorize()) {
            return
        }

        event.isCancelled = true
        if (event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP) {
            handleDropEvent(player, event.currentItem, event.slot)
        } else {
            handleMouseClick(player, event.currentItem, event.slot)
        }
    }

    private fun handleDropEvent(player: Player, droppedItem: ItemStack?, slot: Int) {
        if (droppedItem == null || droppedItem.type == Material.AIR) {
            return
        }

        val meta = droppedItem.itemMeta ?: return

        val keyNamespace = NamespacedKey(plugin, "MenuKey")
        if (meta.persistentDataContainer.has<String, String>(keyNamespace, PersistentDataType.STRING)) {
            val success: Boolean = database.deletePortalKey(player.uniqueId, slot)
            if (success) {
                // Tenta obter o item pelo ID do Nexo
                val nexoItemId: String? = NexoItems.idFromItem(droppedItem)
                val itemToGive = if (NexoItems.exists(nexoItemId)) {
                    NexoItems.itemFromId(nexoItemId)?.build() ?: droppedItem
                } else {
                    droppedItem
                }

                // Copia a lore e outras propriedades do item original
                val nexoMeta = itemToGive.itemMeta
                if (nexoMeta != null) {
                    nexoMeta.lore = meta.lore
                    nexoMeta.setDisplayName(meta.displayName)
                    nexoMeta.addItemFlags(*meta.itemFlags.toTypedArray<ItemFlag>())
                    if (meta.hasCustomModelData()) {
                        nexoMeta.setCustomModelData(meta.customModelData)
                    }
                    itemToGive.setItemMeta(nexoMeta)
                }

                player.inventory.addItem(itemToGive)
                player.sendMessage("§aChave de portal removida e adicionada ao seu inventário.")
                player.closeInventory() // Fecha o menu
            } else {
                plugin.logger.info("Failed to delete portal key from database.")
                player.sendMessage("§cErro ao remover a chave de portal do banco de dados.")
            }
        }
    }

    private fun handleMouseClick(player: Player, currentItem: ItemStack?, slot: Int) {
        if (currentItem == null || currentItem.type == Material.AIR) return

        val meta = currentItem.itemMeta ?: return

        // Lista de metadados para diferentes ações
        val keys = Arrays.asList(
            NamespacedKey(plugin, "MenuHomeTP"),
            NamespacedKey(plugin, "MenuKey"),
            NamespacedKey(plugin, "MenuKeySlot")
        )

        var action: String? = null
        for (key in keys) {
            if (meta.persistentDataContainer.has<String, String>(key, PersistentDataType.STRING)) {
                action = meta.persistentDataContainer.get(key, PersistentDataType.STRING)
                break
            }
        }

        if (action != null) {
            when (action) {
                "HomeTP" -> player.performCommand("home")
                "MenuKey" -> handlePortalKeyClick(player, currentItem, slot)
                "MenuKeySlot" -> handleNetherStarClick(player, slot)
                else -> player.sendMessage("§cAção desconhecida.")
            }
            player.closeInventory()
        }
    }

    //private int findKeySlotInDatabase(Player player, ItemStack key) {
    //    String keyData = database.loadPortalKey(player.getUniqueId());
    //    plugin.getLogger().info("Loaded key data from database: " + keyData);
    //    if (keyData != null) {
    //        for (String keyEntry : keyData.split("/")) {
    //            String[] keyParts = keyEntry.split(":");
    //           if (keyParts.length == 6) {
    //                String keyName = keyParts[0];
    //                String creator = keyParts[1];
    //                String location = keyParts[2];
    //                int uses = Integer.parseInt(keyParts[3]);
    //                String material = keyParts[4];
    //                int slot = Integer.parseInt(keyParts[5]);
    //                plugin.getLogger().info("Checking key entry: " + keyEntry);
    //                if (key.getItemMeta().getDisplayName().equals(keyName) &&
    //                        key.getType().toString().equals(material)) {
    //                    plugin.getLogger().info("Matching key found in database at slot: " + slot);
    //                    return slot;
    //                }
    //            }
    //        }
    //    }
    //    return -1;
    //}
    private fun handleNetherStarClick(player: Player, slot: Int) {
        val handItem = player.inventory.itemInMainHand
        if (handItem != null && handItem.type != Material.AIR) {
            savePortalKey(player, handItem, slot)
            handItem.amount = handItem.amount - 1 // Decrementa a quantidade do item em 1
            player.sendMessage("§aChave de portal salva com sucesso.")
        } else {
            player.sendMessage("§cNenhum item válido na mão para salvar como chave de portal.")
        }
    }

    private fun handlePortalKeyClick(player: Player, key: ItemStack, slot: Int) {
        if (canTeleport(player)) {
            val location = extractLocationFromLore(key.itemMeta.lore!!)
            if (!location.isEmpty()) {
                teleportPlayer(player, location)
                decreaseKeyUses(player, key, slot)
            } else {
                player.sendMessage("§cLocalização não encontrada na chave.")
            }
        }
    }

    private fun extractLocationFromLore(lore: List<String>): String {
        val keyLoreConfig = plugin.config.getStringList("magickey.key_lore")
        val locationPattern = keyLoreConfig.stream()
            .filter { line: String -> line.contains("{location}") }
            .findFirst()
            .orElse("&7Local: &c{location}")

        for (line in lore) {
            var line = line
            line = (line).colorize()
            val patternWithoutPlaceholder = locationPattern.replace("{location}", "").replace("&", "§")
            if (line.contains(patternWithoutPlaceholder)) {
                val location = line.replace(patternWithoutPlaceholder, "").trim { it <= ' ' }
                return location
            }
        }
        return ""
    }

    private fun extractCreatorFromLore(lore: List<String>): String {
        val keyLoreConfig = plugin.config.getStringList("magickey.key_lore")
        val creatorPattern = keyLoreConfig.stream()
            .filter { line: String -> line.contains("{player}") }
            .findFirst()
            .orElse("&7Chave criada por &c{player}")

        for (line in lore) {
            var line = line
            line = (line).colorize()
            val patternWithoutPlaceholder = creatorPattern.replace("{player}", "").replace("&", "§")
            if (line.contains(patternWithoutPlaceholder)) {
                val creator = line.replace(patternWithoutPlaceholder, "").trim { it <= ' ' }
                return creator
            }
        }
        return ""
    }

    private fun extractUsesFromLore(lore: List<String>): Int {
        val keyLoreConfig = plugin.config.getStringList("magickey.key_lore")
        val usesPattern = keyLoreConfig.stream()
            .filter { line: String -> line.contains("{uses}") }
            .findFirst()
            .orElse("Usos: {uses}")

        plugin.logger.info("Uses pattern: $usesPattern")

        for (line in lore) {
            var line = line
            line = (line).colorize()
            plugin.logger.info("Processing lore line: $line")
            val patternWithoutPlaceholder: String = (usesPattern.replace("{uses}", "").colorize())
            if (line.contains(patternWithoutPlaceholder)) {
                val usesStr = line.replace(patternWithoutPlaceholder, "").trim { it <= ' ' }
                plugin.logger.info("Extracted uses string: $usesStr")
                if (usesStr.equals("ilimitado", ignoreCase = true)) {
                    plugin.logger.info("Extracted uses: Ilimitado")
                    return -1 // Considera ilimitado se estiver escrito "ilimitado"
                }
                try {
                    val uses = usesStr.toInt()
                    plugin.logger.info("Extracted uses: $uses")
                    return uses
                } catch (e: NumberFormatException) {
                    plugin.logger.info("Failed to parse uses, defaulting to 1")
                    return 1 // Valor padrão se não for um número
                }
            }
        }
        plugin.logger.info("Uses not found in lore, defaulting to 1")
        return 1 // Valor padrão se não encontrar a linha de usos
    }

    private fun teleportPlayer(player: Player, location: String) {
        val parts = location.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size == 4) {
            try {
                val teleportLocation = Location(
                    Bukkit.getWorld(parts[0].trim { it <= ' ' }),
                    parts[1].trim { it <= ' ' }.toDouble(),
                    parts[2].trim { it <= ' ' }.toDouble(),
                    parts[3].trim { it <= ' ' }.toDouble()
                )
                player.teleport(teleportLocation)
                player.sendMessage("§aTeletransportado para: $location")
            } catch (e: NumberFormatException) {
                player.sendMessage("§cErro ao interpretar as coordenadas de teleporte.")
            }
        } else {
            player.sendMessage("§cFormato de localização inválido. Esperado: mundo,x,y,z")
        }
    }

    private fun decreaseKeyUses(player: Player, key: ItemStack, slot: Int) {
        val keyName = key.itemMeta.displayName
        val creator = extractCreatorFromLore(key.itemMeta.lore!!)
        val location = extractLocationFromLore(key.itemMeta.lore!!)
        var uses = extractUsesFromLore(key.itemMeta.lore!!)
        val material = key.type.toString()

        if (uses > 0) {
            uses--
        }

        if (uses == 0) {
            val success: Boolean = database.deletePortalKey(player.uniqueId, slot)
            if (success) {
                player.sendMessage("§cA chave de portal foi removida pois os usos chegaram a 0.")
            } else {
                player.sendMessage("§cErro ao remover a chave de portal do banco de dados.")
            }
        } else {
            val keyData = String.format("%s:%s:%s:%d:%s:%d", keyName, creator, location, uses, material, slot)
            plugin.logger.info("Dados da chave para atualização: $keyData")
            val success: Boolean = database.updatePortalKey(player.uniqueId, keyData, slot)
            if (success) {
                player.sendMessage("§aUsos restantes da chave: " + (if (uses == -1) "ilimitado" else uses))
            } else {
                player.sendMessage("§cErro ao atualizar os usos da chave no banco de dados.")
            }
        }
    }

    private fun canTeleport(player: Player): Boolean {
        val cooldown = plugin.config.getInt("magickey.key_cooldown", 5)

        if (player.hasPermission("EssentialsMagic.MagicKey.time.byPass")) {
            return true
        }

        val lastTeleport = cooldowns.getOrDefault(player.uniqueId, 0L)
        val currentTime = System.currentTimeMillis()

        if ((currentTime - lastTeleport) < cooldown * 1000) {
            player.sendMessage("§cVocê deve esperar " + (cooldown - (currentTime - lastTeleport) / 1000) + " segundos para teletransportar novamente.")
            return false
        }

        cooldowns[player.uniqueId] = currentTime
        return true
    }

    private fun savePortalKey(player: Player, key: ItemStack, slot: Int) {
        plugin.logger.info("Attempting to save portal key to database for player: " + player.name)

        val keyName = key.itemMeta.displayName
        val creator = extractCreatorFromLore(key.itemMeta.lore!!)
        val location = extractLocationFromLore(key.itemMeta.lore!!)
        val uses = extractUsesFromLore(key.itemMeta.lore!!)

        // Tenta obter o ID do Nexo primeiro
        val material = if (NexoItems.exists(key)) {
            NexoItems.idFromItem(key)
        } else {
            key.type.toString()
        }

        val keyData = String.format("%s:%s:%s:%d:%s:%d", keyName, creator, location, uses, material, slot)

        val success: Boolean = database.savePortalKey(player.uniqueId, keyData)
        if (success) {
            plugin.logger.info("Portal key saved to database successfully.")
        } else {
            plugin.logger.info("Failed to save portal key to database.")
        }
    }

    private fun setKeyItemMeta(keyItem: ItemStack, keyName: String, creator: String, location: String, uses: Int) {
        val keyMeta = keyItem.itemMeta
        if (keyMeta != null) {
            keyMeta.setDisplayName(keyName)
            val lore = plugin.config.getStringList("magickey.key_lore")
            lore.replaceAll { line: String -> formatLoreLine(line, creator, location, uses) }
            keyMeta.lore = lore
            keyItem.setItemMeta(keyMeta)
        }
    }

    private fun formatLoreLine(line: String, creator: String, location: String, uses: Int): String {
        return line.replace("{player}", creator)
            .replace("{location}", location)
            .replace("{uses}", if (uses == -1) "ilimitado" else uses.toString())
            .replace("&", "§")
    }

    private fun createItemStack(material: Material, displayName: String, lore: List<String>): ItemStack {
        val itemStack = ItemStack(material)
        val meta = itemStack.itemMeta
        if (meta != null) {
            meta.setDisplayName(displayName)
            meta.lore = lore
            itemStack.setItemMeta(meta)
        }
        return itemStack
    }
}