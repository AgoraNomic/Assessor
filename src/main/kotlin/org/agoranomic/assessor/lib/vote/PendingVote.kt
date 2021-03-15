package org.agoranomic.assessor.lib.vote

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.ProposalNumber

data class SinglePersonPendingVoteMap(private val map: ImmutableMap<ProposalNumber, ResolvingVote>) {
    constructor(map: Map<ProposalNumber, ResolvingVote>) : this(map.toImmutableMap())

    val proposals get() = map.keys

    fun voteFor(proposalNumber: ProposalNumber) =
        map[proposalNumber] ?: throw IllegalArgumentException("No vote for proposal $proposalNumber")

    fun hasVoteFor(proposal: ProposalNumber) = proposals.contains(proposal)

    fun toMap(): ImmutableMap<ProposalNumber, ResolvingVote> = map
}

data class MultiPersonPendingVoteMap(private val data: ImmutableMap<Person, SinglePersonPendingVoteMap>) {
    constructor(map: Map<Person, SinglePersonPendingVoteMap>) : this(map.toImmutableMap())

    val voters get() = data.keys

    fun proposalsWithVotes() = data.values.flatMap { it.proposals }.distinct()

    fun votesFor(person: Person) =
        data[person] ?: throw IllegalArgumentException("No votes for person ${person.name}")

    fun hasVotesFor(person: Person) = voters.contains(person)

    fun toMap(): ImmutableMap<Person, SinglePersonPendingVoteMap> = data
}
