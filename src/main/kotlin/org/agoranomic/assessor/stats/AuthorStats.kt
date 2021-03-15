// Have to use also {} calls to satisfy compiler about type inference.
@file:Suppress("ControlFlowWithEmptyBody")

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
import java.math.BigInteger

fun buildAuthorStats(
    authors: List<Person>,
    data: AssessmentsDerivedDataCache,
) = buildStatistics {
    val writtenCountsByAuthor = data.allProposalsData.countsByAuthor
    val adoptedProposalsByAuthor = data.adoptedProposalsData.groupedByAuthor
    val adoptedCountsByAuthor = data.adoptedProposalsData.countsByAuthor

    yieldData(
        "author_adopted",
        authors
            .associateWith {
                adoptedCountsByAuthor.getOrDefault(it, 0)
            }
            .also {}
    )

    yieldData(
        "author_adopted_rate",
        authors
            .associateWith {
                (adoptedCountsByAuthor.getOrDefault(it, 0)).toDouble() /
                        (writtenCountsByAuthor.getValue(it)).toDouble()
            }
            .also {},
    )

    val adoptedWordsByAuthor = authors.associateWith { author ->
        adoptedProposalsByAuthor[author]?.sumOf { it.textWords().toBigInteger() } ?: BigInteger.ZERO
    }

    yieldData(
        "author_adopted_words",
        adoptedWordsByAuthor,
    )

    yieldGraph(
        "author_written",
        lets_plot(data = mapOf(
            "author" to authors.flatMap { listOf(it.name, it.name) },
            "count" to authors.flatMap {
                val adoptedCount = adoptedCountsByAuthor.getOrDefault(it, 0)
                val totalCount = writtenCountsByAuthor.getValue(it)
                listOf(adoptedCount, totalCount - adoptedCount)
            },
            "kind" to authors.flatMap { listOf("ADOPTED", "NON-ADOPTED") }
        )) +
                geom_bar(stat = Stat.identity, sampling = sampling_none) {
                    x = "author"
                    y = "count"
                    fill = "kind"
                } +
                ggtitle("Adopted proposals by author") +
                scale_x_discrete(name = "Author", limits = authors.map { it.name }) +
                scale_y_continuous(name = "Proposals") +
                ggsize(authors.size * 60 + 60, 1000),
    )

    data class AdoptedWordsEntry(val authorName: String, val totalWords: Int)

    val adoptedWordsEntries = adoptedWordsByAuthor.map {
        AdoptedWordsEntry(authorName = it.key.name, totalWords = it.value.intValueExact())
    }

    yieldGraph(
        "author_adopted_words",
        lets_plot(data = mapOf(
            "author" to adoptedWordsEntries.map { it.authorName },
            "count" to adoptedWordsEntries.map { it.totalWords },
        )) +
                geom_bar(stat = Stat.identity, sampling = sampling_none) {
                    x = "author"
                    y = "count"
                } +
                ggtitle("Adopted proposal words by author") +
                scale_x_discrete(name = "Author", limits = authors.map { it.name }) +
                scale_y_continuous(name = "Adopted proposal words") +
                ggsize(authors.size * 60 + 60, 1000),
    )
}

fun buildCoauthorsStats(
    coauthors: List<Person>,
    data: AssessmentsDerivedDataCache,
) = buildStatistics {
    val writtenCountsByCoauthor = data.allProposalsData.countsByCoauthor
    val adoptedCountsByCoauthor = data.adoptedProposalsData.countsByCoauthor

    yieldData(
        "coauthor_adopted",
        coauthors
            .associateWith {
                adoptedCountsByCoauthor.getOrDefault(it, 0)
            }
            .also {},
    )

    yieldData(
        "coauthor_adopted_rate",
        coauthors
            .associateWith {
                (adoptedCountsByCoauthor.getOrDefault(it, 0)).toDouble() /
                        (writtenCountsByCoauthor.getValue(it)).toDouble()
            }
            .also {},
    )
}
