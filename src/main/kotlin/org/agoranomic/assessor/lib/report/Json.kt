package org.agoranomic.assessor.lib.report

import kotlinx.serialization.json.*
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.*
import org.agoranomic.assessor.lib.resolve.*
import org.agoranomic.assessor.lib.vote.SimplifiedSingleProposalVoteMap
import org.agoranomic.assessor.lib.vote.VoteKind
import org.agoranomic.assessor.lib.vote.VoteStepDescription
import org.agoranomic.assessor.lib.vote.VoteStepMachineDescription
import org.agoranomic.assessor.lib.voting_strength.VotingStrength
import org.agoranomic.assessor.lib.voting_strength.VotingStrengthModificationDescription
import org.agoranomic.assessor.lib.voting_strength.VotingStrengthTrailForPersons
import java.math.BigDecimal
import java.math.BigInteger

private fun json(int: Int) = JsonLiteral(int)
private fun json(bigInteger: BigInteger) = JsonLiteral(bigInteger)
private fun json(bigDecimal: BigDecimal) = JsonLiteral(bigDecimal.toString()) // Avoid floating point insanity
private fun json(string: String) = JsonLiteral(string)
private fun json(list: List<JsonElement>) = JsonArray(list)

private fun json(person: Person) = json(person.name)

@JvmName("jsonOfPlayers")
private fun json(iterable: Iterable<Person>) = json(iterable.map { json(it) })

private fun json(proposalNumber: ProposalNumber) = json(proposalNumber.raw)
private fun json(proposalVersion: ProposalVersionNumber) = json(proposalVersion.raw)
private fun json(proposalAI: ProposalAI) = json(proposalAI.raw)

private fun json(url: AssessmentUrl) = json(url.raw)

private fun json(metadata: AssessmentMetadata) = json {
    "name" to json(metadata.name)

    val url = metadata.url
    if (url != null) "url" to json(url)
}

private fun json(proposal: Proposal) = json {
    "version" to json(proposal.version)
    "number" to json(proposal.number)
    "ai" to json(proposal.ai)
    "title" to json(proposal.title)
    "author" to json(proposal.author)
    "coauthors" to json(proposal.coauthors)
    "text" to json(proposal.text)
}

@JvmName("jsonOfProposals")
private fun json(iterable: Iterable<Proposal>) = json(iterable.map { json(it) })

private fun json(quorum: Quorum) = json(quorum.count())
private fun json(quorum: ProposalQuorum) = json(quorum.generic())
private fun json(quorum: AssessmentQuorum) = json(quorum.generic())

private fun json(votingStrength: VotingStrength) = json(votingStrength.raw)

private fun json(description: VotingStrengthModificationDescription) = json {
    "readable" to json(description.readable)
    "kind" to json(description.kind)
    "parameters" to json(description.parameters.entries.map { (name, value) ->
        json {
            "name" to json(name)
            "value" to json(value)
        }
    })
}

private fun json(votingStrengths: VotingStrengthTrailForPersons) =
    json(votingStrengths.overridesMap().map { (person, trail) ->
        json {
            "person" to json(person)
            "initial" to json(trail.initial)
            "modifications" to json(trail.modificationsWithValue().map { (modification, strength) ->
                json {
                    "description" to json(modification.description)
                    "current_strength" to json(strength)
                }
            })
            "final" to json(trail.final)
        }
    })

private fun json(propsalResult: ProposalResult) = json(propsalResult.name)

private fun json(vote: VoteKind) = json(vote.name)

private fun json(voteStepDescription: VoteStepDescription): JsonObject? {
    fun JsonObjectBuilder.handleMachineData(machineData: VoteStepMachineDescription) {
        "kind" to json(machineData.kind)
        "parameters" to json(machineData.parameters.map { (name, value) ->
            json {
                "name" to json(name)
                "value" to json(value)
            }
        })
    }

    return when (voteStepDescription) {
        is VoteStepDescription.None -> null

        is VoteStepDescription.MachineOnly -> json {
            handleMachineData(voteStepDescription.data)
        }

        is VoteStepDescription.WithReadable -> json {
            "readable" to json(voteStepDescription.readable)
            handleMachineData(voteStepDescription.machine)
        }
    }
}

@JvmName("jsonVoteDescription")
private fun json(voteDescription: List<VoteStepDescription>) = jsonArray {
    voteDescription.mapNotNull { json(it) }.forEach { +it }
}

private fun json(voteMap: SimplifiedSingleProposalVoteMap) = jsonArray {
    for (player in voteMap.voters) {
        +json {
            "voter" to json(player)
            "vote" to json(voteMap.voteFor(player))
            "comments" to json(voteMap.voteDescriptionsFor(player))
        }
    }
}

private fun json(resolutionData: ResolutionData) = json {
    "result" to json(resolutionData.result)
    "strength_for" to json(resolutionData.aiStrengths.strengthFor)
    "strength_against" to json(resolutionData.aiStrengths.strengthAgainst)
    "votes" to json(resolutionData.votes)
}

fun json(resolutionMap: ProposalResolutionMap) = json {
    "metadata" to json(resolutionMap.metadata)
    "proposals" to json(resolutionMap.proposals)
    "quorum" to json(resolutionMap.quorum)
    "resolutions" to json(resolutionMap.proposals.map { proposal ->
        val proposalNumber = proposal.number
        val resolution = resolutionMap.resolutionOf(proposalNumber)

        json {
            "proposal" to json(proposalNumber)
            "resolution" to json(resolution)
            "strengths" to json(resolutionMap.votingStrengthsFor(proposalNumber))
        }
    })
}
