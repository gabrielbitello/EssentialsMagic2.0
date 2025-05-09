package org.Bitello.essentialsMagic.features.magictear

import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.common.craft.CraftManager
import org.Bitello.essentialsMagic.features.magictear.gui.MagicTear_Menu_Manager
import org.bukkit.Location
import org.bukkit.entity.Player

class MagicTearManager(plugin: EssentialsMagic) : CraftManager(
    plugin,
    "Magic Tear",
    "active_tear_crafts.yml",
    "tear"
) {
    private val tearCraftMenu: MagicTear_Menu_Manager

    init {
        this.tearCraftMenu = MagicTear_Menu_Manager(plugin, this)
    }

    override fun isSystemEnabled(): Boolean {
        return configManager.isTearEnabled()
    }

    override fun getCraftingStationId(ids: List<String>): List<String> {
        return ids.flatMap { id ->
            listOfNotNull(
                configManager.getTearId(),
                configManager.getTearIdAnimation()
            )
        }
    }

    override fun getSystemMessage(key: String, default: String): String {
        return configManager.getMensagem(key, default)
    }

    override fun getCraftMaterials(craftId: String): Map<String, Int> {
        return craftManager.getCraftMaterials(craftId)
    }

    override fun getCraftTime(craftId: String): Int {
        return craftManager.getCraftTime(craftId)
    }

    override fun craftExists(craftId: String): Boolean {
        return craftManager.craftExists(craftId)
    }

    override fun openCraftingMenu(player: Player, location: Location) {
        tearCraftMenu.openMenu(player, location)
    }
}