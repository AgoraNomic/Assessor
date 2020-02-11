package org.agoranomic.assessor.dsl.receivers

import kotlinx.collections.immutable.toImmutableList
import org.agoranomic.assessor.dsl.AssessmentDSL
import org.agoranomic.assessor.lib.*

@AssessmentDSL
interface AssessmentReceiver {
    fun strengths(block: VotingStrengthReceiver.() -> Unit)
    fun proposals(block: ProposalsReceiver.() -> Unit)
    fun voting(block: VotingReceiver.() -> Unit)
    fun quorum(value: Int)
    fun name(value: String)
}

@AssessmentDSL
class AssessmentReceiverImpl : AssessmentReceiver {
    private var votingStrengthsBlock: (VotingStrengthReceiver.() -> Unit)? = null
    private val proposals = mutableListOf<Proposal>()
    private var proposalVotes = mutableMapOf<ProposalNumber, SingleProposalVoteMap>()
    private var quorum: Int? = null
    private var name: String? = null

    override fun strengths(block: VotingStrengthReceiver.() -> Unit) {
        require(votingStrengthsBlock == null) { "Voting strengths specified twice" }
        votingStrengthsBlock = block
    }

    override fun proposals(block: ProposalsReceiver.() -> Unit) {
        val receiver = ProposalsReceiverImpl()
        receiver.block()
        proposals += receiver.compile()
    }

    override fun voting(block: VotingReceiver.() -> Unit) {
        val receiver = VotingReceiverImpl(proposals)
        receiver.block()
        proposalVotes.putAll(receiver.compile())
    }

    override fun quorum(value: Int) {
        require(quorum == null) { "Quorum specified twice" }

        quorum = value
    }

    override fun name(value: String) {
        require(name == null) { "Name specified twice" }

        name = value
    }

    private fun compileVotingStrengths(): Map<ProposalNumber, VotingStrengthMap> {
        val votingStrengthsBlock = votingStrengthsBlock ?: error("Must specify voting strengths")

        val receiver = VotingStrengthReceiverImpl(proposals.toImmutableList())
        receiver.votingStrengthsBlock()
        return receiver.compile()
    }

    fun compile(): AssessmentData {
        val name = name ?: error("Must specify name")
        val quorum = quorum ?: error("Must specify quorum")
        val votingStrengths = compileVotingStrengths()

        for (proposalNumber in proposalVotes.keys) {
            if (proposals.find { it.number == proposalNumber } == null) error("Votes specified for unknown proposal $proposalNumber")
        }

        for (proposal in proposals.map { it.number }) {
            if (!(proposalVotes.containsKey(proposal))) error("Votes not specified for proposal $proposal")
        }

        return AssessmentData(
            name,
            quorum,
            votingStrengths,
            proposals.toSet(),
            MultiProposalVoteMap(proposalVotes)
        )
    }
}