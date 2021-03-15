package org.agoranomic.assessor.cli

import org.agoranomic.assessor.decisions.findAssessments
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toProposalSet
import org.agoranomic.assessor.lib.resolve.ProposalResolutionMap
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.resolve
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

private fun buildAllStats(assessmentResolutions: List<ProposalResolutionMap>): List<Statistic> {
    val proposals = assessmentResolutions.flatMap { it.proposals }.toProposalSet()

    val resolutionsByProposal = proposals.associateWith { proposal ->
        assessmentResolutions.filter { it.proposals.contains(proposal.number) }.map { it.resolutionOf(proposal.number) }
    }

    val proposalResolutions = resolutionsByProposal.values.flatten()

    val proposalsByAuthor = proposals.groupByAuthor()
    val proposalsByCoauthor = proposals.groupByCoauthor()

    val allAuthors = proposalsByAuthor.keys
    val allCoauthors = proposalsByCoauthor.keys
    val allVoters = proposalResolutions.asSequence().flatMap { it.votes.voters }.toSet()

    val adoptedProposals =
        resolutionsByProposal
            .entries
            .filter { (_, resolutions) -> resolutions.any { it.result == ProposalResult.ADOPTED } }
            .map { it.key }
            .toProposalSet()

    val adoptedProposalsByAuthor = adoptedProposals.groupByAuthor()
    val adoptedProposalsByCoauthor = adoptedProposals.groupByCoauthor()

    val writtenCountsByAuthor = proposalsByAuthor.mapValuesToCounts()

    val writtenCountsByCoauthor = proposalsByCoauthor.mapValuesToCounts()

    val sortedAuthors = allAuthors.sortedByDescending { writtenCountsByAuthor.getValue(it) }
    val sortedCoauthors = allCoauthors.sortedByDescending { writtenCountsByCoauthor.getValue(it) }

    val proposalResolutionsByVoter =
        allVoters
            .associateWith { voter ->
                proposalResolutions.filter { resolution -> resolution.votes.voters.contains(voter) }
            }

    val voteCountsByVoter = proposalResolutionsByVoter.mapValuesToCounts()
    val sortedVoters = allVoters.sortedByDescending { voteCountsByVoter.getValue(it) }

    val allStatistics = mutableListOf<Statistic>()

    fun addStatistics(statistics: List<Statistic>) {
        allStatistics += statistics
    }

    addStatistics(buildStatistics {
        yieldData(
            "author_written",
            writtenCountsByAuthor.entries.sortedByDescending { it.value }.toMap(),
        )

        yieldData(
            "coauthor_written",
            writtenCountsByCoauthor.entries.sortedByDescending { it.value }.toMap(),
        )

        yieldData("voter_votes", voteCountsByVoter.entries.sortedByDescending { it.value }.toMap())
    })

    addStatistics(
        buildAuthorStats(
            authors = sortedAuthors,
            adoptedProposalsByAuthor = adoptedProposalsByAuthor,
            writtenCountsByAuthor = writtenCountsByAuthor,
        )
    )

    addStatistics(
        buildCoauthorsStats(
            coauthors = sortedCoauthors,
            adoptedProposalsByCoauthor = adoptedProposalsByCoauthor,
            writtenCountsByCoauthor = writtenCountsByCoauthor,
        )
    )

    addStatistics(
        buildEndorsementStats(
            voters = sortedVoters,
            proposalResolutions = proposalResolutions,
        )
    )

    addStatistics(
        buildVotingStrengthStats(
            voters = sortedVoters,
            proposalResolutionsByVoter = proposalResolutionsByVoter,
        )
    )

    addStatistics(
        buildVoterResultStats(
            voters = sortedVoters,
            proposalResolutionsByVoter = proposalResolutionsByVoter,
        )
    )

    addStatistics(
        buildVoterMutualAgreementStats(
            voters = sortedVoters,
            proposalResolutions = proposalResolutions,
        )
    )

    addStatistics(
        buildVoteKindStats(
            voters = sortedVoters,
            voteKindsForCountsAndRates = VoteKind.values().toSet(),
            voteCountsByVoter = voteCountsByVoter,
            proposalResolutions = proposalResolutions
        )
    )

    addStatistics(
        buildVoterAuthorAgreementStats(
            voters = sortedVoters,
            authors = sortedAuthors,
            resolutionsByProposal = resolutionsByProposal,
            votesByVoter = voteCountsByVoter,
        )
    )

    addStatistics(
        buildVoterDeterminationStats(
            voters = sortedVoters,
            proposalResolutionsByVoter = proposalResolutionsByVoter,
        )
    )

    addStatistics(buildMarginStats(sortedAuthors, proposalResolutions))

    addStatistics(buildLengthStats(resolutionsByProposal = resolutionsByProposal))
    return allStatistics
}

fun main() {
    val assessmentResolutions = findAssessments().map { resolve(it) }

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
