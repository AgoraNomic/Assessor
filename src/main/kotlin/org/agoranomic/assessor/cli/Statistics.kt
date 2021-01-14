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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val FILE_CHARSET = Charsets.UTF_8

private fun ProposalSet.authorCountMap(): Map<Person, Int> {
    return this.map { it.author }.toSet().associateWith { person -> this.count { it.author == person } }
}

private fun ProposalSet.coauthorCountMap(): Map<Person, Int> {
    return this
        .flatMap { it.coauthors }
        .toSet()
        .associateWith { person -> this.count { it.coauthors.contains(person) } }
}

@JvmName("writeStatisticBigDecimal")
private fun writeStatistic(name: String, statistic: Map<Person, BigDecimal>) {
    Files.writeString(
        Path.of("$name.txt"),
        statistic.entries.sortedBy { it.key.name }.joinToString("\n") { "${it.key.name}: ${it.value}" },
        FILE_CHARSET,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

@JvmName("writeStatisticBigInteger")
private fun writeStatistic(name: String, statistic: Map<Person, BigInteger>) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.mapValues { (_, v) -> v.toBigDecimal() }
    writeStatistic(name, transformed)
}

@JvmName("writeStatisticDouble")
private fun writeStatistic(name: String, statistic: Map<Person, Double>, scale: Int = 2) {
    // For some reason overload resolution fails if this variable doesn't exist /shrug
    val transformed = statistic.mapValues { (_, v) -> v.toBigDecimal().setScale(scale, RoundingMode.HALF_UP) }
    writeStatistic(name, transformed)
}


@JvmName("writeStatisticInt")
private fun writeStatistic(name: String, statistic: Map<Person, Int>) {
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

fun main() {
    val resolutionsList = findAssessments().map { resolve(it) }
    val proposals = resolutionsList.flatMap { it.proposals }.toProposalSet()
    val proposalsByAuthor = proposals.groupByAuthor()
    val proposalsByCoauthor = proposals.groupByCoauthor()

    val proposalResolutions = proposals.associateWith { proposal ->
        resolutionsList.filter { it.proposals.contains(proposal.number) }.map { it.resolutionOf(proposal.number) }
    }

    val adoptedProposals =
        proposalResolutions
            .entries
            .filter { (_, resolutions) -> resolutions.any { it.result == ProposalResult.ADOPTED } }
            .map { it.key }
            .toProposalSet()

    val adoptedProposalsByAuthor = adoptedProposals.groupByAuthor()
    val adoptedProposalsByCoauthor = adoptedProposals.groupByCoauthor()

    val writtenCountsByAuthor =
        proposalsByAuthor.mapValuesToCounts().also { writeStatistic("author_written", it) }

    val writtenCountsByCoauthor =
        proposalsByCoauthor.mapValuesToCounts().also { writeStatistic("coauthor_written", it) }

    val allAuthors = writtenCountsByAuthor.keys
    val allCoauthors = writtenCountsByCoauthor.keys

    val adoptedCountsByAuthor =
        adoptedProposalsByAuthor.mapValuesToCounts().also { writeStatistic("author_adopted", it) }

    val adoptedCountsByCoauthor =
        adoptedProposalsByCoauthor.mapValuesToCounts().also { writeStatistic("coauthor_adopted", it) }

    val adoptedRateByAuthor = allAuthors.associateWith {
        (adoptedCountsByAuthor[it] ?: 0).toDouble() / (writtenCountsByAuthor.getValue(it)).toDouble()
    }.also { writeStatistic("author_adopted_rate", it) }

    val adoptedRateByCoauthor = allCoauthors.associateWith {
        (adoptedCountsByCoauthor[it] ?: 0).toDouble() / (writtenCountsByCoauthor.getValue(it)).toDouble()
    }.also { writeStatistic("coauthor_adopted_rate", it) }

    val adoptedWordsByAuthor = adoptedProposalsByAuthor.mapValues { (_, v) ->
        v.sumOf { it.textWords().toBigInteger() }
    }.also { writeStatistic("author_adopted_words", it) }

    val endorsements =
        resolutionsList
            .asSequence()
            .flatMap { it.proposalResolutions }
            .map { it.votes }
            .flatMap { votes -> votes.voters.flatMap { voter -> votes.voteDescriptionsFor(voter) } }
            .filterNotNull()
            .filter { it.kind == "endorsement" }
            .groupBy { it.parameters.getValue("endorsee") }
            .mapKeys { (name, _) -> Person(name = name) }
            .mapValuesToCounts()
            .also { writeStatistic("endorsement_counts", it) }
}
