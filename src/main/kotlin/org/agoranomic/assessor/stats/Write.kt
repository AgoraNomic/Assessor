package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Figure
import jetbrains.letsPlot.export.ggsave
import org.agoranomic.assessor.lib.Person
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val FILE_CHARSET = Charsets.UTF_8

private fun formatTable(name: String, statistic: List<Pair<Person, BigDecimal>>): String {
    val personNames = statistic.map { it.first.name }
    val personValues = statistic.map { it.second.toString() }

    val maxPersonLength = (personNames + "PERSON").maxOf { it.length }
    val maxValueLength = (personValues + name).maxOf { it.length }

    return buildString {
        appendLine("| " + "PERSON".padStart(maxPersonLength) + " | " + name.padStart(maxValueLength) + " |")
        appendLine("| :" + "-".repeat(maxPersonLength - 1) + " | " + "-".repeat(maxValueLength - 1) + ": |")

        for ((personName, personValue) in personNames zip personValues) {
            appendLine(
                "| " + personName.padStart(maxPersonLength) + " | " + personValue.padStart(maxValueLength) + " |",
            )
        }
    }
}

@JvmName("writeStatisticBigDecimal")
fun writeStatistic(name: String, statistic: List<Pair<Person, BigDecimal>>) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.joinToString("\n") { "${it.first.name}: ${it.second}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )

    Files.writeString(
        Path.of("tables").also { Files.createDirectories(it) }.resolve("$name.txt"),
        formatTable(name, statistic),
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

fun writeGraph(name: String, plot: Figure) {
    ggsave(
        plot = plot,
        filename = "$name.svg",
        path = "graphs",
    )
}
