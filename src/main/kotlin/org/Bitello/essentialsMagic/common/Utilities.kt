package org.Bitello.essentialsMagic.common

fun String?.colorize(): String {
    return this?.replace("&", "§") ?: ""
}