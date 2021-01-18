package org.agoranomic.assessor.stats

fun <K, VE> Map<K, Iterable<VE>>.mapValuesToCounts(): Map<K, Int> {
    return mapValues { (_, v) -> v.count() }
}

fun <K, V> Iterable<Map.Entry<K, V>>.mapToPairs(): List<Pair<K, V>> {
    return map { it.toPair() }
}
