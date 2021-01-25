package org.agoranomic.assessor.stats

import jetbrains.letsPlot.bistro.corr.CorrPlot
import jetbrains.letsPlot.geom.geom_tile
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.scale.scale_fill_gradient2
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_discrete
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.vote.VoteKind

fun writeVoterAuthorAgreementGraph(
    voters: List<Person>,
    authors: List<Person>,
    resolutionsByProposal: Map<Proposal, List<ResolutionData>>,
    votesByVoter: Map<Person, Int>,
) {
    data class VoterAuthorSpecification(
        val voter: Person,
        val author: Person,
    )

    val voterAuthorAgreementRates =
        voters
            .flatMap { voter ->
                authors.map { author ->
                    VoterAuthorSpecification(voter = voter, author = author)
                }
            }
            .map { specification ->
                specification to
                        resolutionsByProposal
                            .asIterable()
                            .filter { it.key.author == specification.author }
                            .flatMap { it.value }
                            .mapNotNull {
                                if (it.votes.voters.contains(specification.voter))
                                    when (it.votes.voteFor(specification.voter)) {
                                        VoteKind.FOR -> +1
                                        VoteKind.AGAINST -> -1
                                        VoteKind.PRESENT -> 0
                                    }
                                else
                                    null
                            }
                            .average()
            }

    val authorDataList = voterAuthorAgreementRates.map { it.first.author }
    val voterDataList = voterAuthorAgreementRates.map { it.first.voter }
    val rateDataList = voterAuthorAgreementRates.map { it.second }

    writeGraph(
        "voter_author_agreement_rates.svg",
        lets_plot() +
                geom_tile(
                    data = mapOf(
                        "voter" to voterDataList.map { it.name },
                        "author" to authorDataList.map { it.name },
                        "rate" to rateDataList,
                    ),
                    showLegend = true,
                ) {
                    x = "author"
                    y = "voter"
                    fill = "rate"
                } +
                scale_fill_gradient2(
                    name = "Agreement",
                    low = "#B3412C",
                    mid = "#EDEDED",
                    high = "#326C81",
                    limits = -1.0 to +1.0,
                ) +
                scale_x_discrete(
                    name = "Author",
                    limits = authors.map { it.name },
                ) +
                scale_y_discrete(
                    name = "Voter",
                    limits = voterDataList.distinct().sortedBy { votesByVoter.getValue(it) }.map { it.name },
                ) +
                ggsize(authors.size * 35 + 60, voters.size * 30 + 10),
    )
}

fun writeVoterMutualAgreementGraph(
    voters: List<Person>,
    proposalResolutions: List<ResolutionData>,
) {
    writeGraph(
        "voter_agreement.svg",
        CorrPlot(
            data = voters
                .associateWith { voter ->
                    proposalResolutions.map { resolution ->
                        if (resolution.votes.voters.contains(voter))
                            when (resolution.votes.voteFor(voter)) {
                                VoteKind.FOR -> 1
                                VoteKind.PRESENT -> 0
                                VoteKind.AGAINST -> -1
                            }
                        else
                            null
                    }
                }
                .mapKeys { (voter, _) -> voter.name },
            title = "Voter agreement",
        ).tiles().build(),
    )
}
