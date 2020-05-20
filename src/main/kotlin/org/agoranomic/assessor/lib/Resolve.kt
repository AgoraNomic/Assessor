package org.agoranomic.assessor.lib

import io.github.random_internet_cat.util.compareTo
import io.github.random_internet_cat.util.times
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.agoranomic.assessor.lib.proposal_set.ImmutableProposalSet
import org.agoranomic.assessor.lib.proposal_set.ProposalSet
import org.agoranomic.assessor.lib.proposal_set.toImmutableProposalSet
import org.agoranomic.assessor.lib.proposal_set.toProposalSet

enum class ProposalResult {
    FAILED_QUORUM("FAILED QUORUM"), REJECTED, ADOPTED;

    val readableName: String

    constructor() {
        this.readableName = name
    }

    constructor(readableName: String) {
        this.readableName = readableName
    }
}

data class SimplifiedSingleProposalVoteMap(private val data: ImmutableMap<Person, SimpleVote>) {
    constructor(map: Map<Person, SimpleVote>) : this(map.toImmutableMap())

    val voters get() = Persons(data.keys)
    val voteCount get() = voters.size

    val size get() = voteCount
    fun isEmpty() = size == 0
    fun isNotEmpty() = !isEmpty()

    operator fun get(p: Person) = data[p] ?: throw IllegalArgumentException("Player is not a voter")

    fun forEach(f: (Person, SimpleVote) -> Unit) {
        data.forEach(f)
    }

    fun personsWithVote(kind: VoteKind): Persons {
        return Persons(data.filterValues { vote -> vote.kind == kind }.keys)
    }

    fun votesWithComments(): SimplifiedSingleProposalVoteMap {
        return SimplifiedSingleProposalVoteMap(data.filterValues { it.comment != null })
    }
}

fun SimplifiedSingleProposalVoteMap.votersFor() = personsWithVote(VoteKind.FOR)
fun SimplifiedSingleProposalVoteMap.votersAgainst() = personsWithVote(VoteKind.AGAINST)

data class AIStrengths(val strengthFor: VotingStrength, val strengthAgainst: VotingStrength)

data class ResolutionData(
    val result: ProposalResult,
    val strengths: AIStrengths,
    val votes: SimplifiedSingleProposalVoteMap
)

private fun isAIAdopted(ai: ProposalAI, aiStrengths: AIStrengths): Boolean {
    val strengthFor = aiStrengths.strengthFor
    val strengthAgainst = aiStrengths.strengthAgainst

    return strengthFor.raw >= (ai.raw * strengthAgainst.raw) && (strengthFor > strengthAgainst)
}

private fun strengthWithVote(
    targetVote: VoteKind,
    votes: SimplifiedSingleProposalVoteMap,
    strengths: VotingStrengthMap
) =
    votes.personsWithVote(targetVote)
        .map { strengths[it] }
        .fold(VotingStrength.zero()) { acc, next -> acc + next.value }

private fun aiStrengthsFor(votes: SimplifiedSingleProposalVoteMap, strengths: VotingStrengthMap): AIStrengths {
    return AIStrengths(
        strengthFor = strengthWithVote(VoteKind.FOR, votes, strengths),
        strengthAgainst = strengthWithVote(VoteKind.AGAINST, votes, strengths)
    )
}

fun resolve(
    quorum: ProposalQuorum,
    votingStrengthMap: VotingStrengthMap,
    ai: ProposalAI,
    rawVotes: SingleProposalVoteMap
): ResolutionData {
    val simplifiedVotes = rawVotes.simplified()

    val aiStrengths = aiStrengthsFor(simplifiedVotes, votingStrengthMap)

    if (simplifiedVotes.voters.size < quorum.count()) {
        return ResolutionData(
            result = ProposalResult.FAILED_QUORUM,
            strengths = aiStrengths,
            votes = simplifiedVotes
        )
    }

    val isAdopted = isAIAdopted(ai, aiStrengths)

    // Resolution as specified in R955
    return ResolutionData(
        result = if (isAdopted) ProposalResult.ADOPTED else ProposalResult.REJECTED,
        strengths = aiStrengths,
        votes = simplifiedVotes
    )
}

data class ProposalResolutionMap(
    val assessmentName: String,
    val proposals: ImmutableProposalSet,
    private val resolutions: ImmutableMap<ProposalNumber, ResolutionData>,
    val quorum: AssessmentQuorum,
    val votingStrengths: ImmutableMap<ProposalNumber, VotingStrengthMap>
) {
    constructor(
        assessmentName: String,
        proposals: ProposalSet,
        resolutions: Map<ProposalNumber, ResolutionData>,
        quorum: AssessmentQuorum,
        votingStrengths: Map<ProposalNumber, VotingStrengthMap>
    ) : this(
        assessmentName,
        proposals.toImmutableProposalSet(),
        resolutions.toImmutableMap(),
        quorum,
        votingStrengths.toImmutableMap()
    )

    init {
        require(proposals.map { it.number }.toSet() == votingStrengths.keys.toSet())
    }

    fun resolutionOf(proposal: ProposalNumber) =
        resolutions[proposal] ?: throw IllegalArgumentException("No data for proposal")

    fun votingStrengthsFor(proposal: ProposalNumber) = votingStrengths[proposal]!!

    fun proposalsWithResult(result: ProposalResult) =
        proposals
            .filter { resolutionOf(it.number).result == result }
            .toProposalSet()

}

fun ProposalResolutionMap.adoptedProposals() = proposalsWithResult(ProposalResult.ADOPTED)

data class AssessmentData(
    val name: String,
    val quorum: AssessmentQuorum,
    val votingStrengths: ImmutableMap<ProposalNumber, VotingStrengthMap>,
    val proposals: ImmutableProposalSet,
    val votes: MultiPersonPendingVoteMap
) {
    constructor(
        name: String,
        quorum: AssessmentQuorum,
        votingStrengths: Map<ProposalNumber, VotingStrengthMap>,
        proposals: ProposalSet,
        votes: MultiPersonPendingVoteMap
    ) : this(
        name,
        quorum,
        votingStrengths.toImmutableMap(),
        proposals.toImmutableProposalSet(),
        votes
    )

    init {
        require(votingStrengths.keys.toSet() == proposals.map { it.number }.toSet())
    }

    fun votingStrengthsOf(proposal: ProposalNumber) = votingStrengths[proposal]!!
}

fun resolve(assessmentData: AssessmentData): ProposalResolutionMap {
    val resolvedVotes = resolveVotes(assessmentData.votes, assessmentData.proposals)

    val map = assessmentData.proposals.associateWith { proposal ->
        resolve(
            ProposalQuorum(assessmentData.quorum.generic()),
            assessmentData.votingStrengthsOf(proposal.number),
            proposal.ai,
            resolvedVotes[proposal.number]
        )
    }.mapKeys { (proposal, _) -> proposal.number }

    return ProposalResolutionMap(
        assessmentData.name,
        assessmentData.proposals,
        map,
        assessmentData.quorum,
        assessmentData.votingStrengths
    )
}
