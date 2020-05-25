package org.agoranomic.assessor.dsl.receivers

import org.agoranomic.assessor.dsl.AssessmentDsl
import org.agoranomic.assessor.dsl.DslInit
import org.agoranomic.assessor.dsl.DslValue
import org.agoranomic.assessor.lib.AssessmentData
import org.agoranomic.assessor.lib.AssessmentQuorum
import org.agoranomic.assessor.lib.proposal_set.ImmutableProposalSet

@AssessmentDsl
interface AssessmentReceiver {
    fun strengths(block: GlobalVotingStrengthReceiverInit)
    fun voting(block: MultiPersonVotesReceiverInit)
    fun quorum(value: AssessmentQuorum)
    fun name(value: String)

    object Version0

    val v0 get() = Version0

    object Version1

    val v1 get() = Version1

    fun proposals(v0: Version0, block: ProposalsReceiverV0Init)
    fun proposals(v1: Version1, block: ProposalsReceiverV1Init)
}

typealias AssessmentReceiverInit = DslInit<AssessmentReceiver>

fun AssessmentReceiver.quorum(value: Int) = quorum(AssessmentQuorum(value))

interface AssessmentCompiler {
    fun compile(init: AssessmentReceiverInit): AssessmentData
}

private class DefaultAssessmentReceiver(
    val proposalsCompilerV0: ProposalsCompilerV0 = DefaultProposalsCompilerV0(),
    val proposalsCompilerV1: ProposalsCompilerV1 = DefaultProposalsCompilerV1(),
    val multiPersonVotesCompiler: MultiPersonVotesCompiler = DefaultMultiPersonVotesCompiler(),
    val globalVotingStrengthCompiler: GlobalVotingStrengthCompiler = DefaultGlobalVotingStrengthCompiler()
) : AssessmentReceiver {
    private val votingStrengthsBlockValue = DslValue.namedOf<GlobalVotingStrengthReceiverInit>("strengths block")
    private val proposalsBlockValue = DslValue.namedOf<() -> ImmutableProposalSet>("proposals block")
    private val proposalVotesBlockValue = DslValue.namedOf<MultiPersonVotesReceiverInit>("voting block")
    private val quorumValue = DslValue.namedOf<AssessmentQuorum>("assessment quorum")
    private val nameValue = DslValue.namedOf<String>("assessment name")

    override fun strengths(block: GlobalVotingStrengthReceiverInit) {
        votingStrengthsBlockValue.set(block)
    }

    override fun proposals(v0: AssessmentReceiver.Version0, block: ProposalsReceiverV0Init) {
        @Suppress("MoveLambdaOutsideParentheses") // Lambda is the value, so it should be in parentheses
        proposalsBlockValue.set({ proposalsCompilerV0.compile(block) })
    }

    override fun proposals(v1: AssessmentReceiver.Version1, block: ProposalsReceiverV1Init) {
        @Suppress("MoveLambdaOutsideParentheses") // Lambda is the value, so it should be in parentheses
        proposalsBlockValue.set({ proposalsCompilerV1.compile(block) })
    }

    override fun voting(block: MultiPersonVotesReceiverInit) {
        proposalVotesBlockValue.set(block)
    }

    override fun quorum(value: AssessmentQuorum) {
        quorumValue.set(value)
    }

    override fun name(value: String) {
        nameValue.set(value)
    }

    fun compile(): AssessmentData {
        val name = nameValue.get()
        val quorum = quorumValue.get()

        val proposalsBlock = proposalsBlockValue.get()
        val proposals = proposalsBlock()

        val proposalVotesBlock = proposalVotesBlockValue.get()
        val pendingVoteMap = multiPersonVotesCompiler.compile(proposals, proposalVotesBlock)

        val votingStrengthsBlock = votingStrengthsBlockValue.get()
        val votingStrengths = globalVotingStrengthCompiler.compile(proposals, votingStrengthsBlock)

        for (proposalNumber in pendingVoteMap.proposalsWithVotes()) {
            if (proposals.find { it.number == proposalNumber } == null) error("Votes specified for unknown proposal $proposalNumber")
        }

        return AssessmentData(
            name,
            quorum,
            votingStrengths,
            proposals,
            pendingVoteMap
        )
    }
}

@AssessmentDsl
class DefaultAssessmentCompiler : AssessmentCompiler {
    override fun compile(init: AssessmentReceiverInit): AssessmentData {
        return DefaultAssessmentReceiver().also(init).compile()
    }
}
