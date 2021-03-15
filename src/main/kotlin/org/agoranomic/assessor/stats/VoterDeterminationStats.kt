package org.agoranomic.assessor.stats

import io.github.random_internet_cat.util.requireAllAreDistinct
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
import kotlinx.collections.immutable.toPersistentMap
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.ProposalNumber
import org.agoranomic.assessor.lib.resolve.AssessmentData
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.resolve
import org.agoranomic.assessor.lib.vote.MultiPersonPendingVoteMap
import org.agoranomic.assessor.lib.vote.ResolvedVote
import org.agoranomic.assessor.lib.vote.SinglePersonPendingVoteMap
import org.agoranomic.assessor.lib.vote.VoteKind

private data class VoterDeterminationCounts(
    val decisiveCount: Int,
    val indecisiveCount: Int,
)

fun SinglePersonPendingVoteMap.withChangedVote(
    proposal: ProposalNumber,
    newVote: VoteKind,
): SinglePersonPendingVoteMap {
    return SinglePersonPendingVoteMap(toMap().toPersistentMap().put(proposal, ResolvedVote(newVote)))
}

fun MultiPersonPendingVoteMap.withChangedVote(
    person: Person,
    proposal: ProposalNumber,
    newVote: VoteKind,
): MultiPersonPendingVoteMap {
    return MultiPersonPendingVoteMap(
        toMap().toPersistentMap().put(person, votesFor(person).withChangedVote(proposal, newVote)),
    )
}

private fun VoteKind.oppositeVote() = when (this) {
    VoteKind.FOR -> VoteKind.AGAINST
    VoteKind.AGAINST -> VoteKind.FOR
    else -> error("Unexpected vote kind")
}

private fun voterIsDeterminativeOn(voter: Person, decision: DecisionSpecification): Boolean {
    val resolution = resolve(decision.assessment).resolutionOf(decision.proposalNumber)
    val origVote = resolution.votes.voteFor(voter)

    if (resolution.result == ProposalResult.FAILED_QUORUM) return false
    if (origVote == VoteKind.PRESENT) return false

    val newVote = origVote.oppositeVote()

    val origResult = resolution.result

    val newResult = resolve(
        decision.assessment.copy(
            votes = decision.assessment.votes.withChangedVote(
                person = voter,
                proposal = decision.proposalNumber,
                newVote = newVote,
            ),
        )
    ).resolutionOf(decision.proposalNumber).result

    return newResult != origResult
}

private data class DecisionSpecification(val assessment: AssessmentData, val proposalNumber: ProposalNumber)

private data class VoterDeterminationData(
    val determinativeResolutions: ImmutableList<DecisionSpecification>,
    val nondeterminativeResolutions: ImmutableList<DecisionSpecification>,
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
private fun determinativeDecisionsByVoter(
    voters: List<Person>,
    assessments: List<AssessmentData>,
): Map<Person, VoterDeterminationData> {
    val resolutions = assessments.associateWith { resolve(it) }

    return voters.associateWith { voter ->
        val (determinative, nondeterminative) =
            resolutions
                .entries
                .flatMap { (assessment, resolution) ->
                    resolution
                        .proposalResolutions
                        .filter { it.votes.voters.contains(voter) }
                        .map { assessment to it.proposal.number }
                }
                .map { (assessment, proposalNumber) ->
                    DecisionSpecification(assessment, proposalNumber)
                }
                .partition {
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
    data: AssessmentsDerivedDataCache,
) = buildStatistics {
    val determinativeDataMap = determinativeDecisionsByVoter(voters, data.assessments).let { determinativeData ->
        voters.associateWith { determinativeData.getValue(it) }
    }

    // Don't want to deal with the case where a voter is determinative on the same proposal twice
    determinativeDataMap.values.forEach { it.determinativeResolutions.requireAllAreDistinct() }

    yield(
        Statistic.KeyValuePairs(
            name = "proposal_determinative_voter_count",
            data = determinativeDataMap
                .values
                .map { it.determinativeResolutions }
                .flatten()
                .valueCounts()
                .entries
                .sortedByDescending { it.value }
                .map { it.key.proposalNumber.toString() to it.value.toString() },
            keyName = "Proposal",
            valueName = "Determinative voter count",
        )
    )

    @Suppress("ControlFlowWithEmptyBody") // Needed to satisfy type inference
    yieldData(
        "voter_determinative_proposals",
        determinativeDataMap
            .mapValues { (_, determinationData) ->
                determinationData
                    .determinativeResolutions
                    .map { it.proposalNumber }
                    .sorted()
                    .joinToString(", ", prefix = "[", postfix = "]")
            }
            .also {},
    )

    @Suppress("ControlFlowWithEmptyBody") // Needed to satisfy type inference
    yieldData(
        "voter_determination_times",
        determinativeDataMap.mapValues { (_, v) -> v.counts.decisiveCount }.also {},
    )

    // Order is safe because determinativeDataMap preserves iteration order by contract
    yieldGraph(
        "voter_determination_counts",
        lets_plot(data = mapOf(
            "voter" to determinativeDataMap.flatMap { listOf(it.key.name, it.key.name) },
            "kind" to determinativeDataMap.flatMap { listOf("DETERMINATIVE", "NON-DETERMINATIVE") },
            "count" to determinativeDataMap.flatMap {
                listOf(
                    it.value.counts.decisiveCount,
                    it.value.counts.indecisiveCount
                )
            }
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
