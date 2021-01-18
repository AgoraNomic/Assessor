package org.agoranomic.assessor.stats

import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.geom_text
import jetbrains.letsPlot.geom.geom_tile
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.scale.scale_fill_gradient
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_discrete
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData

private fun writeEndorsementsGraph(voters: List<Person>, proposalResolutions: List<ResolutionData>) {
    val endorsementsByEndorseeByEndorser =
        proposalResolutions
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
                    }
            }
            .groupBy { it.first }
            .mapValues { (_, v) -> v.flatMap { it.second } }
            .mapValues { (_, v) -> v.groupBy { it.parameters.getValue("endorsee") }.mapValuesToCounts() }

    data class EndorsementCountSpecification(
        val endorser: String,
        val endorsee: String,
        val count: Int,
    )

    val voterNames = voters.map { it.name }

    val fullData =
        endorsementsByEndorseeByEndorser
            .flatMap { (endorser, endorseeMap) ->
                endorseeMap.map { (endorsee, count) ->
                    EndorsementCountSpecification(
                        endorser = endorser,
                        endorsee = endorsee,
                        count = count
                    )
                }
            }
            .filter { voterNames.contains(it.endorser) && voterNames.contains(it.endorsee) }

    val endorserData = fullData.map { it.endorser }
    val endorseeData = fullData.map { it.endorsee }
    val countData = fullData.map { it.count }

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

fun writeEndorsementsData(voters: List<Person>, proposalResolutions: List<ResolutionData>) {
    writeEndorsementsGraph(voters, proposalResolutions)

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
