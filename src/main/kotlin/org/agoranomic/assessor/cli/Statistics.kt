package org.agoranomic.assessor.cli

import org.agoranomic.assessor.decisions.findAssessments
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toProposalSet
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.resolve.resolve
import org.agoranomic.assessor.lib.vote.VoteKind
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val FILE_CHARSET = Charsets.UTF_8

@JvmName("writeStatisticBigDecimal")
private fun writeStatistic(name: String, statistic: Map<String, BigDecimal>) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

@JvmName("writeStatisticBigInteger")
private fun writeStatistic(name: String, statistic: Map<String, BigInteger>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.mapValues { (_, v) -> v.toBigDecimal() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticDouble")
private fun writeStatistic(name: String, statistic: Map<String, Double>, scale: Int = 2) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.mapValues { (_, v) -> v.toBigDecimal().setScale(scale, RoundingMode.HALF_UP) }
    writeStatistic(name, transformed)
}


@JvmName("writeStatisticInt")
private fun writeStatistic(name: String, statistic: Map<String, Int>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.mapValues { (_, v) -> v.toBigInteger() }
    writeStatistic(name, transformed)
}

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

private fun <K, VE> Map<K, Iterable<VE>>.mapValuesToCounts(): Map<K, Int> {
    return mapValues { (_, v) -> v.count() }
}

private fun <V> Map<Person, V>.mapKeysToNames(): Map<String, V> {
    return mapKeys { (k, _) -> k.name }
}

private fun Map<String, List<ResolutionData>>.mapToResolutionVoteToResultRates(
    targetResultFor: ProposalResult,
    targetResultAgainst: ProposalResult,
): Map<String, Double> {
    return mapValues { (name, resolutions) ->
        resolutions.count { resolution ->
            val vote = resolution.votes.voteFor(Person(name = name))
            (vote == VoteKind.FOR && resolution.result == targetResultFor) ||
                    (vote == VoteKind.AGAINST && resolution.result == targetResultAgainst)
        }.toDouble() / resolutions.count().toDouble()
    }
}

fun main() {
    val resolutionsList = findAssessments().map { resolve(it) }

    val proposals = resolutionsList.flatMap { it.proposals }.toProposalSet()

    val proposalResolutionsByNumber = proposals.associateWith { proposal ->
        resolutionsList.filter { it.proposals.contains(proposal.number) }.map { it.resolutionOf(proposal.number) }
    }

    val proposalResolutions = proposalResolutionsByNumber.values.flatten()

    val proposalsByAuthor = proposals.groupByAuthor().mapKeysToNames()
    val proposalsByCoauthor = proposals.groupByCoauthor().mapKeysToNames()

    val allAuthors = proposalsByAuthor.keys
    val allCoauthors = proposalsByCoauthor.keys
    val allVoters = proposalResolutions.asSequence().flatMap { it.votes.voters }.toSet().map { it.name }

    val adoptedProposals =
        proposalResolutionsByNumber
            .entries
            .filter { (_, resolutions) -> resolutions.any { it.result == ProposalResult.ADOPTED } }
            .map { it.key }
            .toProposalSet()

    val adoptedProposalsByAuthor = adoptedProposals.groupByAuthor().mapKeysToNames()
    val adoptedProposalsByCoauthor = adoptedProposals.groupByCoauthor().mapKeysToNames()

    val writtenCountsByAuthor =
        proposalsByAuthor
            .mapValuesToCounts()
            .also { writeStatistic("author_written", it) }

    val writtenCountsByCoauthor =
        proposalsByCoauthor
            .mapValuesToCounts()
            .also { writeStatistic("coauthor_written", it) }

    val adoptedCountsByAuthor =
        adoptedProposalsByAuthor
            .mapValuesToCounts()
            .also { writeStatistic("author_adopted", it) }

    val adoptedCountsByCoauthor =
        adoptedProposalsByCoauthor
            .mapValuesToCounts()
            .also { writeStatistic("coauthor_adopted", it) }

    val adoptedRateByAuthor =
        allAuthors
            .associateWith {
                (adoptedCountsByAuthor[it] ?: 0).toDouble() / (writtenCountsByAuthor.getValue(it)).toDouble()
            }
            .also { writeStatistic("author_adopted_rate", it) }

    val adoptedRateByCoauthor =
        allCoauthors
            .associateWith {
                (adoptedCountsByCoauthor[it] ?: 0).toDouble() / (writtenCountsByCoauthor.getValue(it)).toDouble()
            }
            .also { writeStatistic("coauthor_adopted_rate", it) }

    val adoptedWordsByAuthor =
        adoptedProposalsByAuthor
            .mapValues { (_, v) ->
                v.sumOf { it.textWords().toBigInteger() }
            }
            .also { writeStatistic("author_adopted_words", it) }

    val endorsements =
        proposalResolutions
            .asSequence()
            .map { it.votes }
            .flatMap { votes -> votes.voters.flatMap { voter -> votes.voteDescriptionsFor(voter) } }
            .filterNotNull()
            .filter { it.kind == "endorsement" }
            .groupBy { it.parameters.getValue("endorsee") }
            .mapValuesToCounts()
            .also { writeStatistic("endorsement_counts", it) }

    val resolutionsByVoter =
        allVoters
            .associateWith { voter ->
                proposalResolutions.filter { resolution -> resolution.votes.voters.any { it.name == voter } }
            }

    val votesByVoter =
        resolutionsByVoter
            .mapValuesToCounts()
            .also { writeStatistic("voter_votes", it) }

    val votesPresentByVoter =
        resolutionsByVoter
            .mapValues { (name, resolutions) ->
                resolutions.count { resolution ->
                    resolution.votes.voteFor(Person(name = name)) == VoteKind.PRESENT
                }
            }
            .also { writeStatistic("voter_votes_present", it) }

    val votesPresentRateByVoter =
        votesPresentByVoter
            .mapValues { (name, presentVotes) ->
                presentVotes.toDouble() / votesByVoter.getValue(name).toDouble()
            }
            .also { writeStatistic("voter_votes_present_rate", it) }

    val voterAgreementRate =
        resolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.ADOPTED,
                targetResultAgainst = ProposalResult.REJECTED,
            )
            .also { writeStatistic("voter_agreement_rate", it) }

    val voterDisagreementRate =
        resolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.REJECTED,
                targetResultAgainst = ProposalResult.ADOPTED,
            )
            .also { writeStatistic("voter_disagreement_rate", it) }
}
