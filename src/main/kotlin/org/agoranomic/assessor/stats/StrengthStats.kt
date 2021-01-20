package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData

private data class AverageMinMax(
    val average: Double,
    val min: Int,
    val max: Int,
)

fun writeVotingStrengthData(voters: List<Person>, proposalResolutionsByVoter: Map<Person, List<ResolutionData>>) {
    val data = voters.associateWith { voter ->
        val resolutions = proposalResolutionsByVoter.getValue(voter)

        val strengths = resolutions.map { it.votingStrengths.trailForPerson(voter).final.raw.intValueExact() }

        AverageMinMax(
            average = strengths.average(),
            min = strengths.minOrNull() ?: error(""),
            max = strengths.maxOrNull() ?: error(""),
        )
    }


    // The variables are needed, otherwise overload resolution fails (probably due to type inference).

    run {
        val averages = data.mapValues { (_, v) -> v.average }

        writeStatistic(
            "voter_strength_avg",
            averages,
        )
    }

    run {
        val mins = data.mapValues { (_, v) -> v.min }

        writeStatistic(
            "voter_strength_min",
            mins,
        )
    }

    run {
        val maxs = data.mapValues { (_, v) -> v.max }

        writeStatistic(
            "voter_strength_max",
            maxs,
        )
    }
}
