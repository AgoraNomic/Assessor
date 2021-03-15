package org.agoranomic.assessor.cli

import org.agoranomic.assessor.decisions.findAssessments
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.resolve.AssessmentData
import org.agoranomic.assessor.lib.vote.VoteKind
import org.agoranomic.assessor.stats.*

private fun ProposalSet.groupByAuthor(): Map<Person, ImmutableProposalSet> {
    return groupBy { it.author }.mapValues { (_, v) -> v.toImmutableProposalSet() }
}

private fun ProposalSet.groupByCoauthor(): Map<Person, ImmutableProposalSet> {
    return this
        .flatMap { it.coauthors }
        .toSet()
        .associateWith { person ->
            this.filter { it.coauthors.contains(person) }.toImmutableProposalSet()
        }
}

private fun <K, V> Iterable<Map.Entry<K, V>>.toMap() = associate { it.toPair() }

private fun buildAllStats(assessments: List<AssessmentData>): List<Statistic> {
    val dataWithCache = AssessmentsDerivedDataCache(assessments)

    val sortedAuthors =
        dataWithCache.allAuthors.sortedByDescending { dataWithCache.allProposalsData.countsByAuthor.getValue(it) }

    val sortedCoauthors =
        dataWithCache.allCoauthors.sortedByDescending { dataWithCache.allProposalsData.countsByCoauthor.getValue(it) }

    val sortedVoters = dataWithCache.allVoters.sortedByDescending { dataWithCache.voteCountsByVoter.getValue(it) }

    val allStatistics = mutableListOf<Statistic>()

    fun addStatistics(statistics: List<Statistic>) {
        allStatistics += statistics
    }

    addStatistics(buildStatistics {
        yieldData(
            "author_written",
            dataWithCache.allProposalsData.countsByAuthor.entries.sortedByDescending { it.value }.toMap(),
        )

        yieldData(
            "coauthor_written",
            dataWithCache.allProposalsData.countsByCoauthor.entries.sortedByDescending { it.value }.toMap(),
        )

        yieldData("voter_votes", dataWithCache.voteCountsByVoter.entries.sortedByDescending { it.value }.toMap())
    })

    addStatistics(
        buildAuthorStats(
            authors = sortedAuthors,
            data = dataWithCache,
        )
    )

    addStatistics(
        buildCoauthorsStats(
            coauthors = sortedCoauthors,
            data = dataWithCache,
        )
    )

    addStatistics(
        buildEndorsementStats(
            voters = sortedVoters,
            proposalResolutions = dataWithCache.proposalResolutions,
        )
    )

    addStatistics(
        buildVotingStrengthStats(
            voters = sortedVoters,
            proposalResolutionsByVoter = dataWithCache.proposalResolutionsByVoter,
        )
    )

    addStatistics(
        buildVoterResultStats(
            voters = sortedVoters,
            proposalResolutionsByVoter = dataWithCache.proposalResolutionsByVoter,
        )
    )

    addStatistics(
        buildVoterMutualAgreementStats(
            voters = sortedVoters,
            proposalResolutions = dataWithCache.proposalResolutions,
        )
    )

    addStatistics(
        buildVoteKindStats(
            voters = sortedVoters,
            voteKindsForCountsAndRates = VoteKind.values().toSet(),
            data = dataWithCache,
        )
    )

    addStatistics(
        buildVoterAuthorAgreementStats(
            voters = sortedVoters,
            authors = sortedAuthors,
            resolutionsByProposal = dataWithCache.resolutionsByProposal,
            votesByVoter = dataWithCache.voteCountsByVoter,
        )
    )

    addStatistics(
        buildVoterDeterminationStats(
            voters = sortedVoters,
            data = dataWithCache,
        )
    )

    addStatistics(buildMarginStats(sortedAuthors, dataWithCache.proposalResolutions))

    addStatistics(buildLengthStats(resolutionsByProposal = dataWithCache.resolutionsByProposal))

    return allStatistics
}

fun main() {
    val assessmentResolutions = findAssessments()

    val allStatistics = buildAllStats(assessmentResolutions)

    for (statistic in allStatistics) {
        val ensureExhaustive = when (statistic) {
            is Statistic.KeyValuePairs -> {
                saveKeyValuePairs(
                    name = statistic.name,
                    statistic = statistic.data,
                    keyName = statistic.keyName,
                    valueName = statistic.valueName,
                )
            }

            is Statistic.Graph -> {
                saveGraph(statistic.name, statistic.plot)
            }
        }
    }
}
