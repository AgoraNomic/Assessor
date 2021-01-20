package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.geom.geom_text
import jetbrains.letsPlot.geom.geom_tile
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.scale_fill_gradient
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_discrete
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData
import org.agoranomic.assessor.lib.vote.machineIfPresent

private data class EndorsementCountSpecification(
    val endorser: String,
    val endorsee: String,
    val count: Int,
)

private data class EndorsementTotals(
    val endorserTimesByPerson: ImmutableMap<String, Int>,
    val endorseeTimesByPerson: ImmutableMap<String, Int>,
) {
    init {
        require(endorserTimesByPerson.keys == endorseeTimesByPerson.keys)
    }

    val persons
        get() = endorseeTimesByPerson.keys

    constructor(
        endorserTimesByPerson: Map<String, Int>,
        endorseeTimesByPerson: Map<String, Int>,
    ) : this(
        endorserTimesByPerson = endorserTimesByPerson.toImmutableMap(),
        endorseeTimesByPerson = endorseeTimesByPerson.toImmutableMap(),
    )
}

private fun endorsementSpecificationsOf(
    proposalResolutions: List<ResolutionData>,
): List<EndorsementCountSpecification> {
    return proposalResolutions
        .flatMap { resolution ->
            resolution
                .votes
                .voters
                .map { voter ->
                    voter.name to
                            resolution
                                .votes
                                .voteDescriptionsFor(voter)
                                .mapNotNull { it.machineIfPresent }
                                .filter { it.kind == "endorsement" }
                                .also { check(it.size <= 1) }
                }
        }
        .groupBy { it.first }
        .mapValues { (_, v) -> v.flatMap { it.second } }
        .mapValues { (_, v) -> v.groupBy { it.parameters.getValue("endorsee") }.mapValuesToCounts() }
        .flatMap { (endorser, endorseeMap) ->
            endorseeMap.map { (endorsee, count) ->
                EndorsementCountSpecification(
                    endorser = endorser,
                    endorsee = endorsee,
                    count = count
                )
            }
        }
}

private fun endorsementTotalsFor(
    voters: List<Person>,
    endorsementSpecifications: List<EndorsementCountSpecification>,
): EndorsementTotals {
    fun countEndorsementsGroupingBy(
        selector: (EndorsementCountSpecification) -> String,
    ): Map<String, Int> {
        return endorsementSpecifications
            .groupBy(selector)
            .mapValues { (_, v) -> v.sumOf { it.count } }
            .let { data ->
                voters.associate { it.name to data.getOrDefault(it.name, 0) }
            }
    }

    val endorserTimesByPerson = countEndorsementsGroupingBy { it.endorser }
    val endorseeTimesByPerson = countEndorsementsGroupingBy { it.endorsee }

    return EndorsementTotals(
        endorserTimesByPerson = endorserTimesByPerson,
        endorseeTimesByPerson = endorseeTimesByPerson,
    )
}

private fun writeEndorsementsGraph(
    voters: List<Person>,
    endorsementSpecifications: List<EndorsementCountSpecification>,
) {
    val voterNames = voters.map { it.name }

    val endorserData = endorsementSpecifications.map { it.endorser }
    val endorseeData = endorsementSpecifications.map { it.endorsee }
    val countData = endorsementSpecifications.map { it.count }

    writeGraph(
        "endorsement_counts",
        lets_plot(
            data = mapOf(
                "endorser" to endorserData,
                "endorsee" to endorseeData,
                "count" to countData,
            )
        ) {
            x = "endorsee"
            y = "endorser"
        } +
                geom_tile(
                    showLegend = true,
                ) {
                    fill = "count"
                } +
                geom_text {
                    label = "count"
                } +
                scale_fill_gradient(
                    low = "#EDEDED",
                    high = "#326C81",
                ) +
                scale_x_discrete(
                    limits = voterNames,
                ) +
                scale_y_discrete(
                    limits = voterNames.reversed(),
                ) +
                ggsize(voters.size * 40 + 60, voters.size * 40 + 70),
    )
}

private fun writeEndorseeVsEndorserGraph(
    endorsementTotals: EndorsementTotals,
) {
    data class SingleEntrySpecification(
        val person: String,
        val count: Int,
        val kind: String,
    )

    val endorseeEntries = endorsementTotals.endorseeTimesByPerson.map {
        SingleEntrySpecification(
            person = it.key,
            count = it.value,
            kind = "ENDORSEE",
        )
    }

    val endorserEntries = endorsementTotals.endorserTimesByPerson.map {
        SingleEntrySpecification(
            person = it.key,
            count = it.value,
            kind = "ENDORSER",
        )
    }

    val allEntries = endorseeEntries + endorserEntries


    writeGraph(
        "endorsee_endorser",
        lets_plot(
            data = mapOf(
                "person" to allEntries.map { it.person },
                "count" to allEntries.map { it.count },
                "kind" to allEntries.map { it.kind },
            ),
        ) +
                geom_bar(stat = Stat.identity, position = Pos.dodge, sampling = sampling_none) {
                    x = "person"
                    y = "count"
                    fill = "kind"
                } +
                ggsize(endorsementTotals.persons.size * 60 + 60, 1000),
    )
}

private fun writeEndorseeTimesStatistic(endorsementTotals: EndorsementTotals) {
    writeStatistic(
        "voter_endorsement_counts",
        endorsementTotals.endorseeTimesByPerson.mapKeys { (k, _) -> Person(name = k) },
    )
}

fun writeEndorsementsData(voters: List<Person>, proposalResolutions: List<ResolutionData>) {
    val endorsementsData = endorsementSpecificationsOf(proposalResolutions)
    val endorsementTotals = endorsementTotalsFor(voters, endorsementsData)

    writeEndorsementsGraph(voters, endorsementsData)
    writeEndorseeVsEndorserGraph(endorsementTotals)
    writeEndorseeTimesStatistic(endorsementTotals)
}
