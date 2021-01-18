package org.agoranomic.assessor.stats

import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.resolve.ResolutionData

fun writeEndorsementsData(proposalResolutions: List<ResolutionData>) {
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
