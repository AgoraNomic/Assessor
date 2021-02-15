package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.proposal.Proposal

fun <K, VE> Map<K, Iterable<VE>>.mapValuesToCounts(): Map<K, Int> {
    return mapValues { (_, v) -> v.count() }
}

fun <K, V> Iterable<Map.Entry<K, V>>.mapToPairs(): List<Pair<K, V>> {
    return map { it.toPair() }
}

private val WHITESPACE_REGEX = Regex("\\s")

fun Proposal.textWords(): Int {
    return text.split(WHITESPACE_REGEX).count { it.isNotBlank() }
}

