package org.agoranomic.assessor.stats

import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.geom.geom_hline
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.scale_fill_discrete
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_continuous
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.vote.VoteKind

private fun countVotesOfKindByVoter(
    voteKind: VoteKind,
    voters: Set<Person>,
    resolutions: List<ResolutionData>,
): Map<Person, Int> {
    return voters.associateWith { voter ->
        resolutions.count { resolution ->
            resolution.votes.voters.contains(voter) && (resolution.votes.voteFor(voter) == voteKind)
        }
    }
}

private fun writeVoteKindCountsStat(
    voters: List<Person>,
    voteKinds: Set<VoteKind>,
    voteCountsByVoterByVoteKind: Map<VoteKind, Map<Person, Int>>,
) {
    for (voteKind in voteKinds) {
        writeStatistic(
            "voter_votes_${voteKind.name.toLowerCase()}",
            voters.map { it to voteCountsByVoterByVoteKind.getValue(voteKind).getValue(it) }
        )
    }
}

private fun writeVoteKindRatesStat(
    voters: List<Person>,
    voteKinds: Set<VoteKind>,
    voteCountRatesByVoterByVoteKind: Map<VoteKind, Map<Person, Double>>,
) {
    for (voteKind in voteKinds) {
        writeStatistic(
            "voter_votes_${voteKind.name.toLowerCase()}_rate",
            voters.map { it to voteCountRatesByVoterByVoteKind.getValue(voteKind).getValue(it) },
        )
    }
}

private fun writeVoteKindsByVoterGraph(
    voters: List<Person>,
    voteCountsByVoterByVoteKind: Map<VoteKind, Map<Person, Int>>,
    proposalResolutionsCount: Int,
) {
    data class VoterVoteKindCountSpecification(
        val voter: Person,
        val voteCount: Int,
        val kind: VoteKind,
    )

    val voteKindOrder = listOf(VoteKind.FOR, VoteKind.AGAINST, VoteKind.PRESENT)

    val voterKindSpecificationList =
        voters
            .flatMap { voter ->
                VoteKind.values().map { voteKind ->
                    VoterVoteKindCountSpecification(
                        voter = voter,
                        kind = voteKind,
                        voteCount = voteCountsByVoterByVoteKind.getValue(voteKind).getValue(voter),
                    )
                }
            }
            .sortedBy { voteKindOrder.indexOf(it.kind) }

    val voterKindData = mapOf(
        "voter" to voterKindSpecificationList.map { it.voter.name },
        "count" to voterKindSpecificationList.map { it.voteCount },
        "kind" to voterKindSpecificationList.map { it.kind.name },
    )

    writeGraph(
        "vote_kinds",
        lets_plot(voterKindData) +
                geom_bar(
                    stat = Stat.identity,
                    sampling = sampling_none,
                    position = Pos.stack,
                ) {
                    x = "voter"
                    y = "count"
                    fill = "kind"
                } +
                ggsize(width = voters.size * 45 + 10, height = 500) +
                scale_fill_discrete(
                    name = "Vote Kind",
                    limits = listOf("FOR", "AGAINST", "PRESENT")
                ) +
                ggtitle("Votes by voter") +
                scale_x_discrete(
                    name = "Voter",
                    limits = voters.map { it.name },
                ) +
                scale_y_continuous(
                    name = "Votes",
                    limits = 0 to proposalResolutionsCount,
                ) +
                theme().legendPosition_top() +
                geom_hline(yintercept = proposalResolutionsCount, linetype = "dashed", color = "red"),
    )
}

fun writeVoteKindData(
    voters: List<Person>,
    voteKindsForCountsAndRates: Set<VoteKind>,
    voteCountsByVoter: Map<Person, Int>,
    proposalResolutions: List<ResolutionData>,
) {
    val voteCountsByVoterByVoteKind = VoteKind.values().associateWith { kind ->
        countVotesOfKindByVoter(kind, voters.toSet(), proposalResolutions)
    }

    val voteCountRatesByVoterByVoteKind =
        voteCountsByVoterByVoteKind
            .mapValues { (voteKind, countsMap) ->
                countsMap.mapValues { (voter, count) ->
                    count.toDouble() / voteCountsByVoter.getValue(voter).toDouble()
                }
            }

    writeVoteKindCountsStat(
        voters = voters,
        voteKinds = voteKindsForCountsAndRates,
        voteCountsByVoterByVoteKind = voteCountsByVoterByVoteKind,
    )

    writeVoteKindRatesStat(
        voters = voters,
        voteKinds = voteKindsForCountsAndRates,
        voteCountRatesByVoterByVoteKind = voteCountRatesByVoterByVoteKind,
    )

    writeVoteKindsByVoterGraph(
        voters = voters,
        voteCountsByVoterByVoteKind = voteCountsByVoterByVoteKind,
        proposalResolutionsCount = proposalResolutions.size,
    )
}
