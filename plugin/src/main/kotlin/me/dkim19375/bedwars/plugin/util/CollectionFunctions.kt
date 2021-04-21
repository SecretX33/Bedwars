package me.dkim19375.bedwars.plugin.util

import me.dkim19375.bedwars.plugin.data.SerializablePair


fun <V> Map<String, V>.getKeyFromStr(key: String): String? {
    for (str in keys) {
        if (key.equals(str, ignoreCase = true)) {
            return str
        }
    }
    return null
}

fun <T> List<T>.getSafe(index: Int): T? {
    if (index >= size) {
        return null
    }
    if (index < 0) {
        return null
    }
    return get(index)
}

fun <K, V> serializedPairMapOf(vararg pairs: SerializablePair<K, V>): Map<K, V> {
    val newPairs = mutableListOf<Pair<K, V>>()
    for (pair in pairs) {
        newPairs.add(pair.toPair())
    }
    return mapOf(*newPairs.toTypedArray())
}

fun <V> Map<String, V>.getIgnoreCase(other: String): V? {
    for (entry in entries) {
        if (entry.key.equals(other, ignoreCase = true)) {
            return entry.value
        }
    }
    return null
}

fun Collection<String>.containsIgnoreCase(value: String): Boolean {
    return any { item -> item.equals(value, ignoreCase = true) }
}

fun <T> MutableCollection<MutableSet<T>>.getCombinedValues(): List<T> {
    val newList = mutableListOf<T>()
    forEach { c ->
        c.forEach(newList::add)
    }
    return newList
}

fun <K, V> Map<K, V>.getKeyByValue(value: V): K? {
    for (entry in this.entries) {
        val k = entry.key ?: continue
        val v = entry.value ?: continue
        if (v == value) {
            return k
        }
    }
    return null
}

fun <T> List<T>.convertToString(): List<String> {
    return map { item -> item.toString() }
}

fun <T> T.containsAny(vararg element: List<T>): Boolean {
    this?: return false
    element.forEach { tList ->
        for (t in tList) {
            t?: continue
            if (equals(t)) {
                return true
            }
        }
    }
    return false
}

fun <T> List<T>.containsAny(vararg element: T): Boolean {
    forEach { t ->
        if (element.contains(t)) {
            return true
        }
    }
    return false
}

fun <T> List<T>.combine(other: List<T>): List<T> {
    return plus(other).toList()
}

fun <T> Array<T>.combine(other: Array<T>): Array<T> {
    return plus(other).clone()
}