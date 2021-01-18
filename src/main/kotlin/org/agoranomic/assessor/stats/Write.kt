package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val FILE_CHARSET = Charsets.UTF_8

@JvmName("writeStatisticBigDecimal")
fun writeStatistic(name: String, statistic: List<Pair<Person, BigDecimal>>) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.joinToString("\n") { "${it.first.name}: ${it.second}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

@JvmName("writeStatisticBigDecimal")
fun writeStatistic(name: String, statistic: Map<Person, BigDecimal>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticBigInteger")
fun writeStatistic(name: String, statistic: List<Pair<Person, BigInteger>>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigDecimal() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticBigInteger")
fun writeStatistic(name: String, statistic: Map<Person, BigInteger>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticDouble")
fun writeStatistic(name: String, statistic: List<Pair<Person, Double>>, scale: Int = 2) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigDecimal().setScale(scale, RoundingMode.HALF_UP) }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticDouble")
fun writeStatistic(name: String, statistic: Map<Person, Double>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticInt")
fun writeStatistic(name: String, statistic: List<Pair<Person, Int>>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigInteger() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticInt")
fun writeStatistic(name: String, statistic: Map<Person, Int>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })
