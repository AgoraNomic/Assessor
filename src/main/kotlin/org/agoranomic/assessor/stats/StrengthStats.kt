package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData

fun writeVotingStrengthData(voters: List<Person>, proposalResolutionsByVoter: Map<Person, List<ResolutionData>>) {
    writeStatistic(
        "voter_average_strength",
        voters.map { voter ->
            val resolutions = proposalResolutionsByVoter.getValue(voter)

            voter to
                    resolutions
                        .sumOf {
                            it.votingStrengths.trailForPerson(voter).final.raw
                        }
                        .intValueExact()
                        .toDouble() /
                    resolutions
                        .count()
                        .toDouble()
        }
    )
}
