package org.agoranomic.assessor.cli

import jetbrains.letsPlot.*
import jetbrains.letsPlot.bistro.corr.CorrPlot
import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.geom.geom_hline
import jetbrains.letsPlot.geom.geom_tile
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.*
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

private fun countVotesOfKindByVoter(
    voteKind: VoteKind,
    voters: Set<Person>,
    resolutions: List<ResolutionData>,
): Map<Person, Int> {
    return voters.associateWith { voter ->
        resolutions.count { resolution ->
            resolution.votes.voters.contains(voter) && (resolution.votes.voteFor(voter) == voteKind)
        }
    }
}

private fun writeVoteKindsByVoterGraph(
    voters: List<Person>,
    voteCountsByVoterByVoteKind: Map<VoteKind, Map<Person, Int>>,
    proposalResolutionsCount: Int,
) {
    data class VoterVoteKindCountSpecification(
        val voter: Person,
        val voteCount: Int,
        val kind: VoteKind,
    )

    val voteKindOrder = listOf(VoteKind.FOR, VoteKind.AGAINST, VoteKind.PRESENT)

    val voterKindSpecificationList =
        voters
            .flatMap { voter ->
                VoteKind.values().map { voteKind ->
                    VoterVoteKindCountSpecification(
                        voter = voter,
                        kind = voteKind,
                        voteCount = voteCountsByVoterByVoteKind.getValue(voteKind).getValue(voter),
                    )
                }
            }
            .sortedBy { voteKindOrder.indexOf(it.kind) }

    val voterKindData = mapOf(
        "voter" to voterKindSpecificationList.map { it.voter.name },
        "count" to voterKindSpecificationList.map { it.voteCount },
        "kind" to voterKindSpecificationList.map { it.kind.name },
    )

    ggsave(
        lets_plot(voterKindData) +
                geom_bar(
                    stat = Stat.identity,
                    sampling = sampling_none,
                    position = Pos.stack,
                ) {
                    x = "voter"
                    y = "count"
                    fill = "kind"
                } +
                ggsize(width = voters.size * 45 + 10, height = 500) +
                scale_fill_discrete(
                    name = "Vote Kind",
                    limits = listOf("FOR", "AGAINST", "PRESENT")
                ) +
                scale_x_discrete(
                    name = "Voter",
                    limits = voters.map { it.name },
                ) +
                scale_y_continuous(
                    name = "Votes",
                    limits = 0 to proposalResolutionsCount,
                ) +
                theme().legendPosition_top() +
                geom_hline(yintercept = proposalResolutionsCount, linetype = "dashed", color = "red"),
        filename = "vote_kinds.svg",
        path = "graphs",
    )
}

private fun writeVoterAuthorAgreementGraph(
    voters: List<Person>,
    authors: List<Person>,
    resolutionsByProposal: Map<Proposal, List<ResolutionData>>,
    votesByVoter: Map<Person, Int>,
) {
    data class VoterAuthorSpecification(
        val voter: Person,
        val author: Person,
    )

    val voterAuthorAgreementRates =
        voters
            .flatMap { voter ->
                authors.map { author ->
                    VoterAuthorSpecification(voter = voter, author = author)
                }
            }
            .map { specification ->
                specification to
                        resolutionsByProposal
                            .asIterable()
                            .filter { it.key.author == specification.author }
                            .flatMap { it.value }
                            .mapNotNull {
                                if (it.votes.voters.contains(specification.voter))
                                    when (it.votes.voteFor(specification.voter)) {
                                        VoteKind.FOR -> +1
                                        VoteKind.AGAINST -> -1
                                        VoteKind.PRESENT -> 0
                                    }
                                else
                                    null
                            }
                            .average()
            }

    val authorDataList = voterAuthorAgreementRates.map { it.first.author }
    val voterDataList = voterAuthorAgreementRates.map { it.first.voter }
    val rateDataList = voterAuthorAgreementRates.map { it.second }

    ggsave(
        lets_plot() +
                geom_tile(
                    data = mapOf(
                        "voter" to voterDataList.map { it.name },
                        "author" to authorDataList.map { it.name },
                        "rate" to rateDataList,
                    ),
                    showLegend = true,
                ) {
                    x = "author"
                    y = "voter"
                    fill = "rate"
                } +
                scale_fill_gradient2(
                    low = "#B3412C",
                    mid = "#EDEDED",
                    high = "#326C81",
                    limits = -1.0 to +1.0,
                ) +
                scale_x_discrete(
                    limits = authors.map { it.name },
                ) +
                scale_y_discrete(
                    limits = voterDataList.distinct().sortedBy { votesByVoter.getValue(it) }.map { it.name },
                ) +
                ggsize(authors.size * 35 + 60, voters.size * 30 + 10),
        filename = "voter_author_agreement_rates.svg",
        path = "graphs",
    )
}

private fun writeVoterMutualAgreementGraph(
    voters: List<Person>,
    proposalResolutions: List<ResolutionData>,
) {
    ggsave(
        CorrPlot(
            data = voters
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

private fun writeVoteKindCountsStat(
    voters: List<Person>,
    voteCountsByVoterByVoteKind: Map<VoteKind, Map<Person, Int>>,
) {
    writeStatistic(
        "voter_votes_present",
        voters.map { it to voteCountsByVoterByVoteKind.getValue(VoteKind.PRESENT).getValue(it) }
    )
}

private fun writeVoteKindRatesStat(
    voters: List<Person>,
    voteCountRatesByVoterByVoteKind: Map<VoteKind, Map<Person, Double>>,
) {
    writeStatistic(
        "voter_votes_present_rate",
        voters.map { it to voteCountRatesByVoterByVoteKind.getValue(VoteKind.PRESENT).getValue(it) },
    )
}

private fun writeVoteKindData(
    voters: List<Person>,
    voteCountsByVoter: Map<Person, Int>,
    proposalResolutions: List<ResolutionData>,
) {
    val voteCountsByVoterByVoteKind = VoteKind.values().associateWith { kind ->
        countVotesOfKindByVoter(kind, voters.toSet(), proposalResolutions)
    }

    val voteCountRatesByVoterByVoteKind =
        voteCountsByVoterByVoteKind
            .mapValues { (voteKind, countsMap) ->
                countsMap.mapValues { (voter, count) ->
                    count.toDouble() / voteCountsByVoter.getValue(voter).toDouble()
                }
            }

    writeVoteKindCountsStat(
        voters = voters,
        voteCountsByVoterByVoteKind = voteCountsByVoterByVoteKind,
    )

    writeVoteKindRatesStat(
        voters = voters,
        voteCountRatesByVoterByVoteKind = voteCountRatesByVoterByVoteKind,
    )

    writeVoteKindsByVoterGraph(
        voters = voters,
        voteCountsByVoterByVoteKind = voteCountsByVoterByVoteKind,
        proposalResolutionsCount = proposalResolutions.size,
    )
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

    val endorsementCountsByEndorsee =
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

    val proposalResolutionsByVoter =
        allVoters
            .associateWith { voter ->
                proposalResolutions.filter { resolution -> resolution.votes.voters.contains(voter) }
            }

    val voteCountsByVoter =
        proposalResolutionsByVoter
            .mapValuesToCounts()
            .also { stat ->
                writeStatistic("voter_votes", stat.entries.sortedByDescending { it.value }.mapToPairs())
            }

    val sortedVoters = allVoters.sortedByDescending { voteCountsByVoter.getValue(it) }

    fun <V> Iterable<Map.Entry<Person, V>>.sortedByVoteCount(): List<Map.Entry<Person, V>> {
        return sortedByDescending { voteCountsByVoter.getValue(it.key) }
    }

    val voterAgreementRate =
        proposalResolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.ADOPTED,
                targetResultAgainst = ProposalResult.REJECTED,
            )
            .also { stat ->
                writeStatistic("voter_agreement_rate", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val voterDisagreementRate =
        proposalResolutionsByVoter
            .mapToResolutionVoteToResultRates(
                targetResultFor = ProposalResult.REJECTED,
                targetResultAgainst = ProposalResult.ADOPTED,
            )
            .also { stat ->
                writeStatistic("voter_disagreement_rate", stat.entries.sortedByVoteCount().mapToPairs())
            }

    val voterAverageVotingStrength =
        proposalResolutionsByVoter
            .mapValues { (voter, resolutions) ->
                resolutions.sumOf {
                    it.votingStrengths.trailForPerson(voter).final.raw
                }.intValueExact().toDouble() / resolutions.count().toDouble()
            }
            .also { stat ->
                writeStatistic("voter_average_strength", stat.entries.sortedByVoteCount().mapToPairs())
            }

    writeVoterMutualAgreementGraph(
        voters = sortedVoters,
        proposalResolutions = proposalResolutions,
    )

    writeVoteKindData(sortedVoters, voteCountsByVoter, proposalResolutions)

    writeVoterAuthorAgreementGraph(
        voters = sortedVoters,
        authors = sortedAuthors,
        resolutionsByProposal = resolutionsByProposal,
        votesByVoter = voteCountsByVoter,
    )
}
