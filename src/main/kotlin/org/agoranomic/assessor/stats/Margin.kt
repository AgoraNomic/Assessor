package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_continuous
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData

private data class MarginStats(
    val average: Double,
)

private fun calculateMarginStats(data: List<ResolutionData>): MarginStats {
    return MarginStats(
        average = data
            .map {
                it.aiStrengths.strengthFor.raw.toBigDecimal() -
                        it.aiStrengths.strengthAgainst.raw.toBigDecimal() * it.proposal.ai.raw
            }
            .map { it.toDouble() }
            .onEach { check(it.isFinite()) }
            .average()
    )
}

private fun Map<Person, List<ResolutionData>>.calculateValueMarginStats(): Map<Person, Double> {
    return this
        .filterValues { it.isNotEmpty() }
        .mapValues { (_, v) ->
            calculateMarginStats(v).average.also { check(it.isFinite()) }
        }
}

fun writeMarginStats(authors: List<Person>, resolutions: List<ResolutionData>) {
    val resolutionsByAuthor = resolutions.groupBy { it.proposal.author }

    val averageAllMarginByAuthor =
        resolutionsByAuthor
            .calculateValueMarginStats()

    val averageAdoptedMarginByAuthor =
        resolutionsByAuthor
            .mapValues { (_, v) -> v.filter { it.result == ProposalResult.ADOPTED } }
            .calculateValueMarginStats()

    val averageRejectedMarginByAuthor =
        resolutionsByAuthor
            .mapValues { (_, v) -> v.filter { it.result == ProposalResult.REJECTED } }
            .calculateValueMarginStats()

    writeSingleMarginStat(
        authors = authors,
        averageAdoptedMarginByAuthor = averageAllMarginByAuthor,
        name = "author_avg_all_strength_margin",
        graphTitle = "Average voting strength margin by author",
    )

    writeSingleMarginStat(
        authors = authors,
        averageAdoptedMarginByAuthor = averageAdoptedMarginByAuthor,
        name = "author_avg_adopted_strength_margin",
        graphTitle = "Average ADOPTED voting strength margin by author",
    )

    writeSingleMarginStat(
        authors = authors,
        averageAdoptedMarginByAuthor = averageRejectedMarginByAuthor,
        name = "author_avg_rejected_strength_margin",
        graphTitle = "Average REJECTED voting strength margin by author",
    )
}

private fun writeSingleMarginStat(
    authors: List<Person>,
    averageAdoptedMarginByAuthor: Map<Person, Double>,
    name: String,
    graphTitle: String,
) {
    val graphedAdoptedMargins =
        authors
            .filter { it in averageAdoptedMarginByAuthor.keys }
            .associateWith { averageAdoptedMarginByAuthor.getValue(it) }

    writeAverageMarginGraph(
        graphedAdoptedMargins,
        name,
        graphTitle,
    )

    writeStatistic(
        name,
        averageAdoptedMarginByAuthor,
    )
}

private fun writeAverageMarginGraph(margins: Map<Person, Double>, name: String, title: String) {
    writeGraph(
        name,
        lets_plot(mapOf(
            "author" to margins.map { it.key.name },
            "margin" to margins.map { it.value },
        )) +
                geom_bar(stat = Stat.identity, sampling = sampling_none) {
                    x = "author"
                    y = "margin"
                } +
                ggsize(margins.keys.size * 60 + 60, 1000) +
                scale_x_discrete(name = "Author") +
                scale_y_continuous(name = "Average margin") +
                ggtitle(title)
    )
}
