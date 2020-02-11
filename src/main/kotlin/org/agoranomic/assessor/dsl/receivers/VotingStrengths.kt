package org.agoranomic.assessor.dsl.receivers

import kotlinx.collections.immutable.ImmutableList
import org.agoranomic.assessor.dsl.AssessmentDSL
import org.agoranomic.assessor.lib.*

@AssessmentDSL
class ProposalStrengthReceiver(val globalStrengths: VotingStrengthMap) {
    val strengthMap = mutableMapOf<Person, VotingStrength>()

    infix fun Person.strength(value: Int) {
        require(!strengthMap.containsKey(this)) { "Cannot set strength when it has already been set" }
        strengthMap[this] = VotingStrength(value)
    }

    infix fun Person.add(value: VotingStrength) {
        if (!strengthMap.containsKey(this)) strengthMap[this] = globalStrengths[this].value
        strengthMap[this] = strengthMap.getOrFail(this) + value
    }

    infix fun Person.add(value: Int) = this add VotingStrength(value)

    fun compile(): Map<Person, VotingStrengthWithComment> {
        return strengthMap.mapValues { (_, v) -> VotingStrengthWithComment(v) }
    }
}

interface VotingStrengthCommentable {
    infix fun comment(comment: String)
}

@AssessmentDSL
interface VotingStrengthReceiver {
    val proposals: List<Proposal>

    infix fun Person.strength(votingStrength: VotingStrength): VotingStrengthCommentable
    infix fun Person.strength(votingStrength: Int) = strength(VotingStrength(votingStrength))

    fun proposal(number: ProposalNumber, block: ProposalStrengthReceiver.() -> Unit)
    fun default(strength: VotingStrength)
    fun default(strength: Int) = default(VotingStrength(strength))
}

@AssessmentDSL
class VotingStrengthReceiverImpl(override val proposals: ImmutableList<Proposal>) : VotingStrengthReceiver {
    private var defaultStrength: VotingStrength? = null
    private var globalStrengths = mutableMapOf<Person, MutableVotingStrength>()
    private var overrideStrengthBlocks = mutableMapOf<ProposalNumber, ProposalStrengthReceiver.() -> Unit>()

    private data class MutableVotingStrength(
        val value: VotingStrength,
        var comment: String? = null
    ) : VotingStrengthCommentable {
        override fun comment(comment: String) {
            this.comment = comment
        }

        fun compile() = VotingStrengthWithComment(value, comment)
    }

    override infix fun Person.strength(votingStrength: VotingStrength): VotingStrengthCommentable {
        require(!globalStrengths.containsKey(this)) { "Voting strength specified twice for player ${this.name}" }

        val strength = MutableVotingStrength(votingStrength)
        globalStrengths[this] = strength
        return strength
    }

    override fun proposal(number: ProposalNumber, block: ProposalStrengthReceiver.() -> Unit) {
        require(!overrideStrengthBlocks.containsKey(number))
        overrideStrengthBlocks[number] = block
    }

    override fun default(strength: VotingStrength) {
        this.defaultStrength = strength
    }

    fun compile(): Map<ProposalNumber, VotingStrengthMap> {
        val defaultStrength = defaultStrength ?: error("Must specify default voting strength")
        val globalStrengths = globalStrengths.mapValues { (_, strength) -> strength.compile() }
        val globalStrengthMap = SimpleVotingStrengthMap(defaultStrength, globalStrengths)

        return proposals.map { it.number }.associateWith { proposal ->
            val proposalStrengthReceiver = ProposalStrengthReceiver(globalStrengthMap)
            val block = overrideStrengthBlocks[proposal]

            if (block != null) {
                proposalStrengthReceiver.block()
                OverrideVotingStrengthMap(globalStrengthMap, proposalStrengthReceiver.compile())
            } else {
                globalStrengthMap
            }
        }
    }
}