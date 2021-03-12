package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Figure
import jetbrains.letsPlot.export.ggsave
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val FILE_CHARSET = Charsets.UTF_8

private fun formatTable(
    statistic: List<Pair<String, String>>,
    keyName: String,
    valueName: String,
): String {
    val personNames = statistic.map { it.first }
    val personValues = statistic.map { it.second }

    val maxPersonLength = (personNames + keyName).maxOf { it.length }
    val maxValueLength = (personValues + valueName).maxOf { it.length }

    return buildString {
        appendLine("| " + keyName.padStart(maxPersonLength) + " | " + valueName.padStart(maxValueLength) + " |")
        appendLine("| :" + "-".repeat(maxPersonLength - 1) + " | " + "-".repeat(maxValueLength - 1) + ": |")

        for ((personName, personValue) in personNames zip personValues) {
            appendLine(
                "| " + personName.padStart(maxPersonLength) + " | " + personValue.padStart(maxValueLength) + " |",
            )
        }
    }
}

@JvmName("writeStatisticBigDecimal")
fun saveKeyValuePairs(
    name: String,
    statistic: List<Pair<String, String>>,
    keyName: String,
    valueName: String,
) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.joinToString("\n") { "${it.first}: ${it.second}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )

    Files.writeString(
        Path.of("tables").also { Files.createDirectories(it) }.resolve("$name.txt"),
        formatTable(statistic = statistic, keyName = keyName, valueName = valueName),
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

fun saveGraph(name: String, plot: Figure) {
    ggsave(
        plot = plot,
        filename = "$name.svg",
        path = "graphs",
    )
}
