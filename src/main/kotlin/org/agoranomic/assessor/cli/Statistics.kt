package org.agoranomic.assessor.cli

import jetbrains.letsPlot.bistro.corr.CorrPlot
import jetbrains.letsPlot.export.ggsave
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
private fun writeStatistic(name: String, statistic: List<Pair<Person, BigDecimal>>) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.joinToString("\n") { "${it.first.name}: ${it.second}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

@JvmName("writeStatisticBigDecimal")
private fun writeStatistic(name: String, statistic: Map<Person, BigDecimal>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticBigInteger")
private fun writeStatistic(name: String, statistic: List<Pair<Person, BigInteger>>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigDecimal() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticBigInteger")
private fun writeStatistic(name: String, statistic: Map<Person, BigInteger>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticDouble")
private fun writeStatistic(name: String, statistic: List<Pair<Person, Double>>, scale: Int = 2) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigDecimal().setScale(scale, RoundingMode.HALF_UP) }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticDouble")
private fun writeStatistic(name: String, statistic: Map<Person, Double>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

@JvmName("writeStatisticInt")
private fun writeStatistic(name: String, statistic: List<Pair<Person, Int>>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.map { it.first to it.second.toBigInteger() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticInt")
private fun writeStatistic(name: String, statistic: Map<Person, Int>) =
    writeStatistic(name, statistic.entries.map { it.toPair() })

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

private fun Map<Person, List<ResolutionData>>.mapToResolutionVoteToResultRates(
    targetResultFor: ProposalResult,
    targetResultAgainst: ProposalResult,
): Map<Person, Double> {
    return mapValues { (voter, resolutions) ->
        resolutions.count { resolution ->
            val vote = resolution.votes.voteFor(voter)
            (vote == VoteKind.FOR && resolution.result == targetResultFor) ||
                    (vote == VoteKind.AGAINST && resolution.result == targetResultAgainst)
        }.toDouble() / resolutions.count().toDouble()
    }
}

private fun <K, V> Iterable<Map.Entry<K, V>>.mapToPairs(): List<Pair<K, V>> {
    return map { it.toPair() }
}

fun main() {
    val resolutionsList = findAssessments().map { resolve(it) }

    val proposals = resolutionsList.flatMap { it.proposals }.toProposalSet()

    val proposalResolutionsByNumber = proposals.associateWith { proposal ->
        resolutionsList.filter { it.proposals.contains(proposal.number) }.map { it.resolutionOf(proposal.number) }
    }

    val proposalResolutions = proposalResolutionsByNumber.values.flatten()

    val proposalsByAuthor = proposals.groupByAuthor()
    val proposalsByCoauthor = proposals.groupByCoauthor()

    val allAuthors = proposalsByAuthor.keys
    val allCoauthors = proposalsByCoauthor.keys
    val allVoters = proposalResolutions.asSequence().flatMap { it.votes.voters }.toSet()

    val adoptedProposals =
        proposalResolutionsByNumber
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

    val endorsements =
        proposalResolutions
            .asSequence()
            .map { it.votes }
            .flatMap { votes -> votes.voters.flatMap { voter -> votes.voteDescriptionsFor(voter) } }
            .filterNotNull()
            .filter { it.kind == "endorsement" }
            .groupBy { it.parameters.getValue("endorsee") }
            .mapKeys { (name, _) -> Person(name = name) }
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic("voter_endorsement_counts", stat.entries.sortedBy { it.key.name }.mapToPairs())
            }

    val resolutionsByVoter =
        allVoters
            .associateWith { voter ->
                proposalResolutions.filter { resolution -> resolution.votes.voters.contains(voter) }
            }

    val votesByVoter =
        resolutionsByVoter
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic("voter_votes", stat.entries.sortedByDescending { it.value }.mapToPairs())
            }

    fun <V> Iterable<Map.Entry<Person, V>>.sortedByVoteCount(): List<Map.Entry<Person, V>> {
        return sortedByDescending { votesByVoter.getValue(it.key) }
    }

    val votesPresentByVoter =
        resolutionsByVoter
            .mapValues { (voter, resolutions) ->
                resolutions.count { resolution ->
                    resolution.votes.voteFor(voter) == VoteKind.PRESENT
                }
            }
            .also { stat ->
                writeStatistic("voter_votes_present", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val votesPresentRateByVoter =
        votesPresentByVoter
            .mapValues { (name, presentVotes) ->
                presentVotes.toDouble() / votesByVoter.getValue(name).toDouble()
            }
            .also { stat ->
                writeStatistic("voter_votes_present_rate", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val voterAgreementRate =
        resolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.ADOPTED,
                targetResultAgainst = ProposalResult.REJECTED,
            )
            .also { stat ->
                writeStatistic("voter_agreement_rate", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val voterDisagreementRate =
        resolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.REJECTED,
                targetResultAgainst = ProposalResult.ADOPTED,
            )
            .also { stat ->
                writeStatistic("voter_disagreement_rate", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val voterAverageVotingStrength =
        resolutionsByVoter
            .mapValues { (voter, resolutions) ->
                resolutions.sumOf {
                    it.votingStrengths.trailForPerson(voter).final.raw
                }.intValueExact().toDouble() / resolutions.count().toDouble()
            }
            .also { stat ->
                writeStatistic("voter_average_strength", stat.entries.sortedByVoteCount().mapToPairs())
            }

    ggsave(
        CorrPlot(
            data = allVoters
                .sortedByDescending { votesByVoter.getValue(it) }
                .associateWith { voter ->
                    proposalResolutions.map { resolution ->
                        if (resolution.votes.voters.contains(voter))
                            when (resolution.votes.voteFor(voter)) {
                                VoteKind.FOR -> 1
                                VoteKind.PRESENT -> 0
                                VoteKind.AGAINST -> -1
                            }
                        else
                            null
                    }
                }
                .mapKeys { (voter, _) -> voter.name },
            title = "Voter agreement",
        ).tiles().build(),
        filename = "voter_agreement.svg",
        path = "graphs",
    )
}
