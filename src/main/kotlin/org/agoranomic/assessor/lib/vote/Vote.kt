package org.agoranomic.assessor.lib.vote

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.agoranomic.assessor.lib.Person
import org.agoranomic.assessor.lib.proposal.ProposalNumber

enum class VoteKind { PRESENT, AGAINST, FOR }

data class VoteStepDescription(
    val readable: String,
    val kind: String,
    val parameters: ImmutableMap<String, String>,
) {
    constructor(
        readable: String,
        kind: String,
        parameters: Map<String, String>,
    ) : this(
        readable = readable,
        kind = kind,
        parameters = parameters.toImmutableMap(),
    )
}

sealed class VoteStepResolution {
    data class Continue(val nextVote: ResolvingVote) : VoteStepResolution()
    data class Resolved(val resolution: VoteKind) : VoteStepResolution()
}

interface ResolvingVote {
    fun resolveStep(context: VoteContext): VoteStepResolution
    val currentStepDescription: VoteStepDescription?
}

data class ResolvedVote(val value: VoteKind) : ResolvingVote {
    override fun resolveStep(context: VoteContext): VoteStepResolution {
        return VoteStepResolution.Resolved(value)
    }

    override val currentStepDescription: VoteStepDescription?
        get() = null
}

data class CommentedResolvingVote(val comment: String, val nextVote: ResolvingVote) : ResolvingVote {
    override fun resolveStep(context: VoteContext): VoteStepResolution {
        return VoteStepResolution.Continue(nextVote)
    }

    override val currentStepDescription: VoteStepDescription
        get() = VoteStepDescription(
            readable = comment,
            kind = "commented",
            parameters = mapOf("comment" to comment),
        )
}

object InextricableResolvingVote : ResolvingVote {
    override fun resolveStep(context: VoteContext): VoteStepResolution {
        return VoteStepResolution.Continue(ResolvedVote(VoteKind.PRESENT))
    }

    override val currentStepDescription: VoteStepDescription
        get() = VoteStepDescription(
            readable = "Inextricable",
            kind = "inextricable",
            parameters = emptyMap(),
        )

}

tailrec fun ResolvingVote.finalResolution(voteContext: VoteContext): VoteKind {
    return when (val resolution = resolveStep(voteContext)) {
        is VoteStepResolution.Continue -> resolution.nextVote.finalResolution(voteContext)
        is VoteStepResolution.Resolved -> resolution.resolution
    }
}

fun ResolvingVote.resolveDescriptions(voteContext: VoteContext): List<VoteStepDescription?> {
    return generateSequence(this) {
        when (val resolution = it.resolveStep(voteContext)) {
            is VoteStepResolution.Continue -> resolution.nextVote
            is VoteStepResolution.Resolved -> null
        }
    }.map { it.currentStepDescription }.toList()
}

sealed class Vote {
    abstract val comment: String?
    abstract fun copyWithComment(newComment: String?): Vote

    abstract fun simplified(): SimpleVote

    abstract fun asResolvingVote(): ResolvingVote
}

data class InextricableVote(override val comment: String?) : Vote() {
    override fun copyWithComment(newComment: String?) = copy(comment = newComment)
    override fun simplified(): SimpleVote = SimpleVote(
        VoteKind.PRESENT,
        comment = if (comment != null) "Inextricable: $comment" else "Inextricable"
    )

    override fun asResolvingVote(): ResolvingVote {
        return if (comment != null)
            CommentedResolvingVote(comment = comment, nextVote = InextricableResolvingVote)
        else
            InextricableResolvingVote
    }
}

data class SimpleVote(val kind: VoteKind, override val comment: String?) : Vote() {
    override fun copyWithComment(newComment: String?) = copy(comment = newComment)
    override fun simplified(): SimpleVote = this

    override fun asResolvingVote(): ResolvingVote {
        return ResolvedVote(kind).let {
            if (comment != null) CommentedResolvingVote(comment = comment, nextVote = it) else it
        }
    }
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
