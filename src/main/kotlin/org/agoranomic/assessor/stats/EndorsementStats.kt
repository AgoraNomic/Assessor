package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.geom_bar
import jetbrains.letsPlot.geom.geom_text
import jetbrains.letsPlot.geom.geom_tile
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.sampling.sampling_none
import jetbrains.letsPlot.scale.scale_fill_gradient
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_discrete
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData

private data class EndorsementCountSpecification(
    val endorser: String,
    val endorsee: String,
    val count: Int,
)

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
                                .filterNotNull()
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

private fun writeEndorsementsGraph(
    voters: List<Person>,
    endorsementSpecifications: List<EndorsementCountSpecification>,
) {
    val voterNames = voters.map { it.name }

    val endorserData = endorsementSpecifications.map { it.endorser }
    val endorseeData = endorsementSpecifications.map { it.endorsee }
    val countData = endorsementSpecifications.map { it.count }

    ggsave(
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
        filename = "endorsement_counts.svg",
        path = "graphs",
    )
}

private fun writeEndorseeVsEndorserGraph(
    voters: List<Person>,
    endorsementSpecifications: List<EndorsementCountSpecification>,
) {
    data class EndorseeEndorserCountSpecification(
        val person: String,
        val endorseeCount: Int,
        val endorserCount: Int,
    )

    data class SingleEntrySpecification(
        val person: String,
        val count: Int,
        val kind: String,
    )

    val voterNames = voters.map { it.name }

    val data =
        voters
            .map { person ->
                val name = person.name

                EndorseeEndorserCountSpecification(
                    name,
                    endorseeCount = endorsementSpecifications.filter { it.endorsee == name }.sumOf { it.count },
                    endorserCount = endorsementSpecifications.filter { it.endorser == name }.sumOf { it.count },
                )
            }
            .filter {
                it.person in voterNames
            }
            .flatMap {
                listOf(
                    SingleEntrySpecification(
                        person = it.person,
                        count = it.endorseeCount,
                        kind = "ENDORSEE",
                    ),
                    SingleEntrySpecification(
                        person = it.person,
                        count = it.endorserCount,
                        kind = "ENDORSER",
                    ),
                )
            }

    ggsave(
        lets_plot(
            data = mapOf(
                "person" to data.map { it.person },
                "count" to data.map { it.count },
                "kind" to data.map { it.kind },
            ),
        ) +
                geom_bar(stat = Stat.identity, position = Pos.dodge, sampling = sampling_none) {
                    x = "person"
                    y = "count"
                    fill = "kind"
                } +
                ggsize(voters.size * 60 + 60, 1000),
        filename = "endorsee_endorser.svg",
        path = "graphs",
    )
}

fun writeEndorsementsData(voters: List<Person>, proposalResolutions: List<ResolutionData>) {
    val endorsementsData = endorsementSpecificationsOf(proposalResolutions)

    writeEndorsementsGraph(voters, endorsementsData)
    writeEndorseeVsEndorserGraph(voters, endorsementsData)

    writeStatistic(
        "voter_endorsement_counts",
        proposalResolutions
            .asSequence()
            .map { it.votes }
            .flatMap { votes -> votes.voters.flatMap { voter -> votes.voteDescriptionsFor(voter) } }
            .filterNotNull()
            .filter { it.kind == "endorsement" }
            .groupBy { it.parameters.getValue("endorsee") }
            .mapKeys { (name, _) -> Person(name = name) }
            .mapValuesToCounts()
            .entries
            .sortedBy { it.key.name }
            .mapToPairs()
    )
}
