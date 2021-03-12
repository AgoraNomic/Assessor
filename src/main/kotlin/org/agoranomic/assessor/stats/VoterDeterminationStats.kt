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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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

@OptIn(ExperimentalStdlibApi::class)
private fun voterIsDeterminativeOn(voter: Person, resolution: ResolutionData): Boolean {
    if (resolution.result == ProposalResult.FAILED_QUORUM) return false
    if (resolution.votes.voteFor(voter) == VoteKind.PRESENT) return false

    val origResult = resolution.result

    val newResult = resolve(
        proposal = resolution.proposal,
        quorum = resolution.quorum,
        votingStrengthMap = resolution.votingStrengths,
        votes = SimplifiedSingleProposalVoteMap(
            resolution.votes.toMap().toMutableMap().apply {
                put(
                    voter,
                    ResolvingVoteResolvedVote(
                        stepDescriptions = emptyList(),
                        resolution = when (resolution.votes.voteFor(voter)) {
                            VoteKind.FOR -> VoteKind.AGAINST
                            VoteKind.AGAINST -> VoteKind.FOR
                            else -> error("Unexpected vote kind")
                        },
                    )
                )
            }
        )
    ).result

    return newResult != origResult
}

private data class VoterDeterminationData(
    val determinativeResolutions: ImmutableList<ResolutionData>,
    val nondeterminativeResolutions: ImmutableList<ResolutionData>,
) {
    val counts by lazy {
        VoterDeterminationCounts(
            decisiveCount = determinativeResolutions.count(),
            indecisiveCount = nondeterminativeResolutions.count(),
        )
    }
}

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
): Map<Person, VoterDeterminationData> {
    return voters.associateWith { voter ->
        val resolutions = proposalResolutionsByVoter.getValue(voter)

        val (determinative, nondeterminative) = resolutions.partition {
            voterIsDeterminativeOn(voter, it)
        }

        VoterDeterminationData(
            determinativeResolutions = determinative.toImmutableList(),
            nondeterminativeResolutions = nondeterminative.toImmutableList(),
        )
    }
}

fun buildVoterDeterminationStats(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
) = buildStatistics {
    val decisiveDecisionsByVoter = voters.associateWith { voter ->
        proposalResolutionsByVoter
            .getValue(voter)
            .filter { voterIsDeterminativeOn(voter, it) }
            .map { it.proposal.number }.sorted()
    }

    yield(
        Statistic.KeyValuePairs(
            name = "proposal_determinative_voter_count",
            data = decisiveDecisionsByVoter
                .values
                .flatten()
                .valueCounts()
                .entries
                .sortedByDescending { it.value }
                .map { it.key.toString() to it.value.toString() },
            keyName = "Proposal",
            valueName = "Determinative voter count",
        )
    )

    val countsMap = countVoterDecisiveTimes(voters, proposalResolutionsByVoter)

    run {
        // Must use variable because overload resolution fails if we don't
        val orderedCountsMap = voters.associateWith { countsMap.getValue(it).counts.decisiveCount }

        yieldData(
            "voter_determination_times",
            orderedCountsMap,
        )
    }

    // In order to ensure a hostile Map implementation doesn't screw with iteration order
    val countEntries = countsMap.entries.toList()

    yieldGraph(
        "voter_determination_counts",
        lets_plot(data = mapOf(
            "voter" to countEntries.flatMap { listOf(it.key.name, it.key.name) },
            "kind" to countEntries.flatMap { listOf("DETERMINATIVE", "NON-DETERMINATIVE") },
            "count" to countEntries.flatMap { listOf(it.value.counts.decisiveCount, it.value.counts.indecisiveCount) }
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
