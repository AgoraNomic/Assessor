// Have to use also {} calls to satisfy compiler about type inference.
@file:Suppress("ControlFlowWithEmptyBody")

package org.agoranomic.assessor.stats

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

    writeStatistic(
        "author_adopted_words",
        authors
            .associateWith { author ->
                adoptedProposalsByAuthor[author]?.sumOf { it.textWords().toBigInteger() } ?: BigInteger.ZERO
            }
            .also {},
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
