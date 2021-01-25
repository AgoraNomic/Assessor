// Have to use also {} calls to satisfy compiler about type inference.
@file:Suppress("ControlFlowWithEmptyBody")

package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.scale.scale_x_discrete
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import java.math.BigInteger

private val WHITESPACE_REGEX = Regex("\\s")

private fun Proposal.textWords(): Int {
    return text.split(WHITESPACE_REGEX).count { it.isNotBlank() }
}

fun writeAuthorData(
    authors: List<Person>,
    adoptedProposalsByAuthor: Map<Person, ProposalSet>,
    writtenCountsByAuthor: Map<Person, Int>,
) {
    val adoptedCountsByAuthor = adoptedProposalsByAuthor.mapValuesToCounts()

    writeStatistic(
        "author_adopted",
        authors
            .associateWith {
                adoptedCountsByAuthor.getOrDefault(it, 0)
            }
            .also {}
    )

    writeStatistic(
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

    writeStatistic(
        "author_adopted_words",
        adoptedWordsByAuthor,
    )

    writeGraph(
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
                geom_bar(stat = Stat.identity) {
                    x = "author"
                    y = "count"
                    fill = "kind"
                } +
                scale_x_discrete(limits = authors.map { it.name }) +
                ggsize(authors.size * 60 + 60, 1000),
    )

    writeGraph(
        "author_adopted_words",
        lets_plot(data = mapOf(
            "author" to adoptedWordsByAuthor.map { it.key.name },
            "count" to adoptedWordsByAuthor.map { it.value.intValueExact() },
        )) +
                geom_bar(stat = Stat.identity) {
                    x = "author"
                    y = "count"
                } +
                scale_x_discrete(limits = authors.map { it.name }) +
                ggsize(authors.size * 60 + 60, 1000),
    )
}

fun writeCoauthorsData(
    coauthors: List<Person>,
    adoptedProposalsByCoauthor: Map<Person, ImmutableProposalSet>,
    writtenCountsByCoauthor: Map<Person, Int>,
) {
    val adoptedCountsByCoauthor = adoptedProposalsByCoauthor.mapValuesToCounts()

    writeStatistic(
        "coauthor_adopted",
        coauthors
            .associateWith {
                adoptedCountsByCoauthor.getOrDefault(it, 0)
            }
            .also {},
    )

    writeStatistic(
        "coauthor_adopted_rate",
        coauthors
            .associateWith {
                (adoptedCountsByCoauthor.getOrDefault(it, 0)).toDouble() /
                        (writtenCountsByCoauthor.getValue(it)).toDouble()
            }
            .also {},
    )
}
