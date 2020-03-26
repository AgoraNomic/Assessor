package org.agoranomic.assessor.lib

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.agoranomic.assessor.dsl.receivers.PendingVote

enum class VoteKind { PRESENT, AGAINST, FOR }

sealed class Vote {
    abstract val comment: String?
    abstract fun copyWithComment(newComment: String?): Vote

    abstract fun simplified(): SimpleVote
}

data class InextricableVote(override val comment: String?) : Vote() {
    override fun copyWithComment(newComment: String?) = copy(comment = newComment)
    override fun simplified(): SimpleVote = SimpleVote(
        VoteKind.PRESENT,
        comment = if (comment != null) "Inextricable: $comment" else "Inextricable"
    )
}

data class SimpleVote(val kind: VoteKind, override val comment: String?) : Vote() {
    override fun copyWithComment(newComment: String?) = copy(comment = newComment)
    override fun simplified(): SimpleVote = this
}

data class SingleProposalVoteMap(private val data: ImmutableMap<Person, Vote>) {
    constructor(map: Map<Person, Vote>) : this(map.toImmutableMap())

    val voters get() = data.keys
    val voteCount get() = voters.size

    operator fun get(person: Person) = data[person] ?: throw IllegalArgumentException("Player is not a voter")

    fun simplified(): SimplifiedSingleProposalVoteMap {
        return SimplifiedSingleProposalVoteMap(data.mapValues { (_, vote) -> vote.simplified() })
    }
}

data class MultiProposalVoteMap(private val data: ImmutableMap<ProposalNumber, SingleProposalVoteMap>) {
    constructor(map: Map<ProposalNumber, SingleProposalVoteMap>) : this(map.toImmutableMap())

    val proposals get() = data.keys

    operator fun get(proposal: ProposalNumber) =
        data[proposal] ?: throw IllegalArgumentException("No votes for proposal $proposal")
}

data class SinglePersonPendingVoteMap(val map: ImmutableMap<ProposalNumber, PendingVote>) {
    constructor(map: Map<ProposalNumber, PendingVote>) : this(map.toImmutableMap())

    val proposals get() = map.keys

    fun voteFor(proposalNumber: ProposalNumber) =
        map[proposalNumber] ?: throw IllegalArgumentException("No vote for proposal $proposalNumber")

    fun hasVoteFor(proposal: ProposalNumber) = proposals.contains(proposal)
}

data class MultiPersonPendingVoteMap(val map: ImmutableMap<Person, SinglePersonPendingVoteMap>) {
    constructor(map: Map<Person, SinglePersonPendingVoteMap>) : this(map.toImmutableMap())

    init {
        require(map.isNotEmpty())
    }

    val voters get() = map.keys

    fun proposalsWithVotes() = map.values.flatMap { it.proposals }.distinct()

    fun votesFor(person: Person) =
        map[person] ?: throw IllegalArgumentException("No votes for person ${person.name}")

    fun hasVotesFor(person: Person) = voters.contains(person)
}

data class LookupProposal(val func: (ProposalNumber) -> Proposal) {
    operator fun invoke(number: ProposalNumber) = func(number)
    operator fun invoke(number: Int) = this(ProposalNumber(number))
}

interface VoteContext {
    val lookupProposal: LookupProposal
    fun resolve(proposal: Proposal, voter: Person): Vote?
}

typealias ResolveFunc = (proposal: Proposal, voter: Person) -> Vote?

data class StandardVoteContext(
    val resolveFunc: ResolveFunc,
    override val lookupProposal: LookupProposal
) : VoteContext {
    override fun resolve(proposal: Proposal, voter: Person): Vote? = resolveFunc(proposal, voter)
}

typealias VoteFunc = (proposal: Proposal, context: VoteContext) -> Vote?
