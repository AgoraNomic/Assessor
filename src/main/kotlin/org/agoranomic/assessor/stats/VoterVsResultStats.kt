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
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.vote.VoteKind

private fun calculateVoteToResultCounts(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
    targetResultFor: ProposalResult,
    targetResultAgainst: ProposalResult,
): Map<Person, Int> {
    return voters.associateWith { voter ->
        proposalResolutionsByVoter.getValue(voter).count { resolution ->
            val vote = resolution.votes.voteFor(voter)
            val result = resolution.result

            (vote == VoteKind.FOR && result == targetResultFor) ||
                    (vote == VoteKind.AGAINST && result == targetResultAgainst)
        }
    }
}


fun writeVoterResultData(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
) {
    val agreementCountsByVoter = calculateVoteToResultCounts(
        voters = voters,
        proposalResolutionsByVoter = proposalResolutionsByVoter,
        targetResultFor = ProposalResult.ADOPTED,
        targetResultAgainst = ProposalResult.REJECTED,
    )

    writeStatistic(
        "voter_result_agreement_rate",
        agreementCountsByVoter.mapValues { (voter, agreementCount) ->
            agreementCount.toDouble() / proposalResolutionsByVoter.getValue(voter).count().toDouble()
        }.also {}
    )

    val disagreementCountsByVoter = calculateVoteToResultCounts(
        voters = voters,
        proposalResolutionsByVoter = proposalResolutionsByVoter,
        targetResultFor = ProposalResult.REJECTED,
        targetResultAgainst = ProposalResult.ADOPTED,
    )

    writeStatistic(
        "voter_result_disagreement_rate",
        disagreementCountsByVoter.mapValues { (voter, disagreementCount) ->
            disagreementCount.toDouble() / proposalResolutionsByVoter.getValue(voter).count().toDouble()
        }.also {}
    )

    writeGraph(
        "voter_result_agreement",
        lets_plot(data = mapOf(
            "voter" to voters.flatMap {
                listOf(it.name, it.name)
            },
            "count" to voters.flatMap {
                listOf(agreementCountsByVoter.getValue(it), disagreementCountsByVoter.getValue(it))
            },
            "kind" to voters.flatMap {
                listOf("AGREEMENT", "DISAGREEMENT")
            },
        )) +
                geom_bar(stat = Stat.identity, sampling = sampling_none) {
                    x = "voter"
                    y = "count"
                    fill = "kind"
                } +
                ggtitle("Voter vs. result") +
                scale_x_discrete(name = "Voter") +
                scale_y_continuous(name = "Count") +
                scale_fill_discrete(name = "Kind") +
                ggsize(
                    voters.size * 30 + 10,
                    1000,
                ),
    )
}
