package org.agoranomic.assessor.stats

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.proposal.proposal_set.toProposalSet
import org.agoranomic.assessor.lib.resolve.AssessmentData
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.resolve

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

class ProposalsDerivedDataCache(val proposals: ImmutableProposalSet) {
    constructor(proposals: ProposalSet) : this(proposals.toImmutableProposalSet())

    val groupedByAuthor = proposals.groupByAuthor()
    val groupedByCoauthor = proposals.groupByCoauthor()

    val countsByAuthor = groupedByAuthor.mapValuesToCounts()
    val countsByCoauthor = groupedByCoauthor.mapValuesToCounts()
}

class AssessmentsDerivedDataCache(val assessments: ImmutableList<AssessmentData>) {
    constructor(assessments: List<AssessmentData>) : this(assessments.toImmutableList())

    val allAssessmentResolutions = assessments.map { resolve(it) }

    val allProposalsData = ProposalsDerivedDataCache(assessments.flatMap { it.proposals }.toProposalSet())
    val allProposals = allProposalsData.proposals

    val allAuthors = allProposals.map { it.author }.distinct()
    val allCoauthors = allProposals.flatMap { it.coauthors }.distinct()

    val allVoters = assessments.flatMap { it.votes.voters }.distinct()

    val resolutionsByProposal = allProposals.associateWith { proposal ->
        allAssessmentResolutions
            .filter { it.proposals.contains(proposal.number) }
            .map { it.resolutionOf(proposal.number) }
    }

    val proposalResolutions = resolutionsByProposal.values.flatten()

    val adoptedProposalsData = ProposalsDerivedDataCache(
        resolutionsByProposal
            .entries
            .filter { (_, resolutions) -> resolutions.any { it.result == ProposalResult.ADOPTED } }
            .map { it.key }
            .toProposalSet()
    )

    val adoptedProposals = adoptedProposalsData.proposals

    val proposalResolutionsByVoter = allVoters.associateWith { voter ->
        proposalResolutions.filter { resolution -> resolution.votes.voters.contains(voter) }
    }

    val voteCountsByVoter = proposalResolutionsByVoter.mapValuesToCounts()
}
