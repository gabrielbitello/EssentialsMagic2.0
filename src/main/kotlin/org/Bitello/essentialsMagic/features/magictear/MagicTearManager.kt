package org.Bitello.essentialsMagic.features.magictear

import org.Bitello.essentialsMagic.EssentialsMagic
import org.Bitello.essentialsMagic.common.craft.CraftManager
import org.Bitello.essentialsMagic.features.magictear.gui.MagicTear_Menu_Manager
import org.bukkit.Location
import org.bukkit.entity.Player

class MagicTearManager(plugin: EssentialsMagic) : CraftManager(
    plugin,
    "Magic Tear",
    "active_tear_crafts.yml"
) {
    private val tearCraftMenu: MagicTear_Menu_Manager

    init {
        this.tearCraftMenu = MagicTear_Menu_Manager(plugin, this)
    }

    override fun isSystemEnabled(): Boolean {
        return configManager.isTearEnabled()
    }

    override fun getCraftingStationId(): String {
        return configManager.getTearId()!!
    }

    override fun getSystemMessage(key: String, default: String): String {
        return configManager.getTearMessage(key, default)
    }

    override fun getCraftMaterials(craftId: String): Map<String, Int> {
        return configManager.getCraftMaterials(craftId)
    }

    override fun getCraftTime(craftId: String): Int {
        return configManager.getCraftTime(craftId)
    }

    override fun craftExists(craftId: String): Boolean {
        return configManager.craftExists(craftId)
    }

    override fun openCraftingMenu(player: Player, location: Location) {
        tearCraftMenu.openMenu(player, location)
    }
}