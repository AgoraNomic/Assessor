package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.scale_fill_discrete
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_continuous
import kotlinx.collections.immutable.toPersistentMap
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.resolve.resolve
import org.agoranomic.assessor.lib.vote.ResolvingVoteResolvedVote
import org.agoranomic.assessor.lib.vote.SimplifiedSingleProposalVoteMap
import org.agoranomic.assessor.lib.vote.VoteKind

private data class VoterDeterminationCounts(
    val decisiveCount: Int,
    val indecisiveCount: Int,
)

/**
 * Returns a map from the persons in [voters] to decisiveness counts.
 *
 * A voter is _decisive_ on a resolution if:
 * * The resolution is not resolved FAILED QUORUM.
 * * E voted FOR or AGAINST on that resolution.
 * * Flipping eir vote from FOR to AGAINST, or vice-versa, would change the outcome of the resolution.
 *
 * A voter is otherwise _indecisive_ on that resolution.
 */
private fun countVoterDecisiveTimes(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
): Map<Person, VoterDeterminationCounts> {
    return voters.associateWith { voter ->
        val resolutions = proposalResolutionsByVoter.getValue(voter)

        val decisiveCount =
            resolutions
                .filter { it.result != ProposalResult.FAILED_QUORUM }
                .filter { it.votes.voteFor(voter) != VoteKind.PRESENT }
                .count {
                    val origResult = it.result

                    val newResult = resolve(
                        proposal = it.proposal,
                        quorum = it.quorum,
                        votingStrengthMap = it.votingStrengths,
                        votes = SimplifiedSingleProposalVoteMap(
                            it.votes
                                .toMap()
                                .toPersistentMap()
                                .put(
                                    voter,
                                    ResolvingVoteResolvedVote(
                                        stepDescriptions = emptyList(),
                                        resolution = when (it.votes.voteFor(voter)) {
                                            VoteKind.FOR -> VoteKind.AGAINST
                                            VoteKind.AGAINST -> VoteKind.FOR
                                            else -> error("Unexpected vote kind")
                                        },
                                    )
                                ),
                        )
                    ).result

                    newResult != origResult
                }

        VoterDeterminationCounts(
            decisiveCount = decisiveCount,
            indecisiveCount = resolutions.count() - decisiveCount,
        )
    }
}

fun writeVoterDeterminationStats(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
) {
    val countsMap = countVoterDecisiveTimes(voters, proposalResolutionsByVoter)

    run {
        // Must use variable because overload resolution fails if we don't
        val orderedCountsMap = voters.associateWith { countsMap.getValue(it).decisiveCount }

        writeStatistic(
            "voter_determination_times",
            orderedCountsMap,
        )
    }

    // In order to ensure a hostile Map implementation doesn't screw with iteration order
    val countEntries = countsMap.entries.toList()

    writeGraph(
        "voter_determination_counts",
        lets_plot(data = mapOf(
            "voter" to countEntries.flatMap { listOf(it.key.name, it.key.name) },
            "kind" to countEntries.flatMap { listOf("DETERMINATIVE", "NON-DETERMINATIVE") },
            "count" to countEntries.flatMap { listOf(it.value.decisiveCount, it.value.indecisiveCount) }
        )) +
                geom_bar(stat = Stat.identity, sampling = sampling_none) {
                    x = "voter"
                    y = "count"
                    fill = "kind"
                } +
                ggsize(voters.size * 60 + 60, 1000) +
                ggtitle("Determinative votes by voter") +
                scale_x_discrete(name = "Voter", limits = voters.map { it.name }) +
                scale_y_continuous(name = "Count") +
                scale_fill_discrete(name = "Kind")
    )
}
