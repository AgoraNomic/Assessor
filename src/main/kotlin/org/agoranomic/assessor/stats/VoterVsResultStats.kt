package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.vote.VoteKind

private fun calculateVoteToResultRates(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
    targetResultFor: ProposalResult,
    targetResultAgainst: ProposalResult,
): Map<Person, Double> {
    return voters.associateWith { voter ->
        val resolutions = proposalResolutionsByVoter.getValue(voter)

        val agreementCount = resolutions.count { resolution ->
            val vote = resolution.votes.voteFor(voter)
            val result = resolution.result

            (vote == VoteKind.FOR && result == targetResultFor) ||
                    (vote == VoteKind.AGAINST && result == targetResultAgainst)
        }

        val totalCount = resolutions.count()

        agreementCount.toDouble() / totalCount.toDouble()
    }
}


fun writeVoterResultData(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
) {
    writeStatistic(
        "voter_result_agreement_rate",
        calculateVoteToResultRates(
            voters = voters,
            proposalResolutionsByVoter = proposalResolutionsByVoter,
            targetResultFor = ProposalResult.ADOPTED,
            targetResultAgainst = ProposalResult.REJECTED,
        )
    )

    writeStatistic(
        "voter_result_disagreement_rate",
        calculateVoteToResultRates(
            voters = voters,
            proposalResolutionsByVoter = proposalResolutionsByVoter,
            targetResultFor = ProposalResult.REJECTED,
            targetResultAgainst = ProposalResult.ADOPTED,
        )
    )
}
