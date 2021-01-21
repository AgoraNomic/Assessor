package org.agoranomic.assessor.cli

import org.agoranomic.assessor.decisions.findAssessments
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toProposalSet
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.resolve
import org.agoranomic.assessor.lib.vote.VoteKind
import org.agoranomic.assessor.stats.*

private val WHITESPACE_REGEX = Regex("\\s")

private fun Proposal.textWords(): Int {
    return text.split(WHITESPACE_REGEX).count { it.isNotBlank() }
}

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

fun main() {
    val assessmentResolutions = findAssessments().map { resolve(it) }

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

    val writtenCountsByAuthor =
        proposalsByAuthor
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic(
                    "author_written",
                    stat.entries.sortedByDescending { it.value }.mapToPairs(),
                )
            }

    val sortedAuthors = allAuthors.sortedByDescending { writtenCountsByAuthor.getValue(it) }

    val writtenCountsByCoauthor =
        proposalsByCoauthor
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic(
                    "coauthor_written",
                    stat.entries.sortedByDescending { it.value }.mapToPairs(),
                )
            }

    fun <V> Iterable<Map.Entry<Person, V>>.sortedByAuthoredCount(): List<Map.Entry<Person, V>> {
        return sortedByDescending { writtenCountsByAuthor.getValue(it.key) }
    }

    fun <V> Iterable<Map.Entry<Person, V>>.sortedByCoauthoredCount(): List<Map.Entry<Person, V>> {
        return sortedByDescending { writtenCountsByCoauthor.getValue(it.key) }
    }

    val adoptedCountsByAuthor =
        adoptedProposalsByAuthor
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic(
                    "author_adopted",
                    stat.entries.sortedByAuthoredCount().mapToPairs(),
                )
            }

    val adoptedCountsByCoauthor =
        adoptedProposalsByCoauthor
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic(
                    "coauthor_adopted",
                    stat.entries.sortedByCoauthoredCount().mapToPairs(),
                )
            }

    val adoptedRateByAuthor =
        allAuthors
            .associateWith {
                (adoptedCountsByAuthor[it] ?: 0).toDouble() / (writtenCountsByAuthor.getValue(it)).toDouble()
            }
            .also { stat ->
                writeStatistic("author_adopted_rate", stat.entries.sortedByAuthoredCount().mapToPairs())
            }

    val adoptedRateByCoauthor =
        allCoauthors
            .associateWith {
                (adoptedCountsByCoauthor[it] ?: 0).toDouble() / (writtenCountsByCoauthor.getValue(it)).toDouble()
            }
            .also { stat ->
                writeStatistic("coauthor_adopted_rate", stat.entries.sortedByCoauthoredCount().mapToPairs())
            }

    val adoptedWordsByAuthor =
        adoptedProposalsByAuthor
            .mapValues { (_, v) ->
                v.sumOf { it.textWords().toBigInteger() }
            }
            .also { stat ->
                writeStatistic("author_adopted_words", stat.entries.sortedByAuthoredCount().mapToPairs())
            }

    val proposalResolutionsByVoter =
        allVoters
            .associateWith { voter ->
                proposalResolutions.filter { resolution -> resolution.votes.voters.contains(voter) }
            }

    val voteCountsByVoter = proposalResolutionsByVoter.mapValuesToCounts()
    val sortedVoters = allVoters.sortedByDescending { voteCountsByVoter.getValue(it) }

    writeStatistic("voter_votes", voteCountsByVoter.entries.sortedByDescending { it.value }.mapToPairs())

    writeEndorsementsData(
        voters = sortedVoters,
        proposalResolutions = proposalResolutions,
    )

    writeVotingStrengthData(
        voters = sortedVoters,
        proposalResolutionsByVoter = proposalResolutionsByVoter,
    )

    writeVoterResultData(
        voters = sortedVoters,
        proposalResolutionsByVoter = proposalResolutionsByVoter,
    )

    writeVoterMutualAgreementGraph(
        voters = sortedVoters,
        proposalResolutions = proposalResolutions,
    )

    writeVoteKindData(
        voters = sortedVoters,
        voteKindsForCountsAndRates = VoteKind.values().toSet(),
        voteCountsByVoter = voteCountsByVoter,
        proposalResolutions = proposalResolutions
    )

    writeVoterAuthorAgreementGraph(
        voters = sortedVoters,
        authors = sortedAuthors,
        resolutionsByProposal = resolutionsByProposal,
        votesByVoter = voteCountsByVoter,
    )
}
