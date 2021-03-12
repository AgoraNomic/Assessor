package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Figure
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.agoranomic.assessor.lib.Person
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

sealed class Statistic {
    data class KeyValuePairs(
        val data: ImmutableList<Pair<String, String>>,
        val name: String,
        val keyName: String,
        val valueName: String,
    ) : Statistic() {
        constructor(
            data: List<Pair<String, String>>,
            name: String,
            keyName: String,
            valueName: String,
        ) : this(
            data = data.toImmutableList(),
            name = name,
            keyName = keyName,
            valueName = valueName,
        )

        constructor(
            data: Map<String, String>,
            name: String,
            keyName: String,
            valueName: String,
        ) : this(
            data = data.map { it.toPair() },
            name = name,
            keyName = keyName,
            valueName = valueName,
        )
    }

    data class Graph(
        val name: String,
        val plot: Figure,
    ) : Statistic()
}

interface StatisticsBuilderScope {
    fun yield(statistic: Statistic)
}

fun StatisticsBuilderScope.yieldGraph(name: String, plot: Figure) = yield(Statistic.Graph(name, plot))

@JvmName("yieldDataString")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Iterable<Pair<Person, String>>) =
    yield(
        Statistic.KeyValuePairs(
            statistic.map { it.first.name to it.second },
            name = name,
            keyName = "PERSON",
            valueName = name
        )
    )

private fun <K, V> Map<K, V>.pairsSequence() = asSequence().map { it.toPair() }

@JvmName("yieldDataString")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Map<Person, String>) =
    yieldData(name, statistic.pairsSequence().asIterable())

@JvmName("yieldDataBigDecimal")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Iterable<Pair<Person, BigDecimal>>) =
    yieldData(name, statistic.asSequence().map { it.first to it.second.toString() }.asIterable())

@JvmName("yieldDataBigDecimal")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Map<Person, BigDecimal>) =
    yieldData(name, statistic.pairsSequence().asIterable())

private inline fun <T> StatisticsBuilderScope.doYieldDataNumeric(
    name: String,
    statistic: Map<Person, T>,
    crossinline mapToBigDecimal: (T) -> BigDecimal,
) {
    yieldData(name, statistic.asSequence().map { it.key to mapToBigDecimal(it.value) }.asIterable())
}

@JvmName("yieldDataBigInteger")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Map<Person, BigInteger>) =
    doYieldDataNumeric(name, statistic) { it.toBigDecimal() }

@JvmName("yieldDataInt")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Map<Person, Int>) =
    doYieldDataNumeric(name, statistic) { it.toBigDecimal() }

@JvmName("yieldDataDouble")
fun StatisticsBuilderScope.yieldData(name: String, statistic: Map<Person, Double>, scale: Int = 2) =
    doYieldDataNumeric(name, statistic) { it.toBigDecimal().setScale(scale, RoundingMode.HALF_UP) }

fun buildStatistics(block: StatisticsBuilderScope.() -> Unit): List<Statistic> {
    val out = mutableListOf<Statistic>()

    block(object : StatisticsBuilderScope {
        override fun yield(statistic: Statistic) {
            out += statistic
        }
    })

    return out
}
