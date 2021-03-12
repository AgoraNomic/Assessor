package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData
import java.math.BigInteger

private data class VoterStrengthStats(
    val average: Double,
    val min: Int,
    val max: Int,
    val total: BigInteger,
)

fun buildVotingStrengthStats(
    voters: List<Person>,
    proposalResolutionsByVoter: Map<Person, List<ResolutionData>>,
) = buildStatistics {
    val data = voters.associateWith { voter ->
        val resolutions = proposalResolutionsByVoter.getValue(voter)

        val strengths = resolutions.map { it.votingStrengths.trailForPerson(voter).final.raw.intValueExact() }

        VoterStrengthStats(
            average = strengths.average(),
            min = strengths.minOrNull() ?: error(""),
            max = strengths.maxOrNull() ?: error(""),
            total = strengths.sumOf { it.toBigInteger() },
        )
    }


    // The variables are needed, otherwise overload resolution fails (probably due to type inference).

    run {
        val averages = data.mapValues { (_, v) -> v.average }

        yieldData(
            "voter_strength_avg",
            averages,
        )
    }

    run {
        val mins = data.mapValues { (_, v) -> v.min }

        yieldData(
            "voter_strength_min",
            mins,
        )
    }

    run {
        val maxs = data.mapValues { (_, v) -> v.max }

        yieldData(
            "voter_strength_max",
            maxs,
        )
    }

    run {
        val totals = data.mapValues { (_, v) -> v.total }

        yieldData(
            "voter_strength_cumulative",
            totals,
        )
    }
}
