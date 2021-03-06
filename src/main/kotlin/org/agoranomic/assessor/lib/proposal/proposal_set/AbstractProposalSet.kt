package org.agoranomic.assessor.lib.proposal.proposal_set

import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.proposal.ProposalDataMismatchException
import org.agoranomic.assessor.lib.proposal.ProposalNumbers
import org.randomcat.util.requireDistinct

abstract class AbstractProposalSet : ProposalSet {
    companion object {
        /**
         * @throws ProposalDataMismatchException if there are two [Proposals][Proposal] in `this` that have the same
         * [number][Proposal.number] but otherwise different data.
         */
        @JvmStatic
        protected fun checkInitialList(list: List<Proposal>) {
            val distinctList = list.distinct()
            val distinctNumberList = distinctList.distinctBy { it.number }

            if (distinctList != distinctNumberList) {
                val differingList = distinctList - distinctNumberList
                check(differingList.isNotEmpty())

                val firstDiffering = differingList.first()
                val otherWithSameNumber = list.first { it.number == firstDiffering.number && it != firstDiffering }

                throw ProposalDataMismatchException(
                    next = firstDiffering,
                    original = otherWithSameNumber
                )
            }
        }
    }

    override fun numbers(): ProposalNumbers {
        val numbersSet = map { it.number }.requireDistinct()
        return ProposalNumbers(numbersSet)
    }

    final override fun equals(other: Any?): Boolean {
        if (other !is ProposalSet) return false

        return this.toSet() == other.toSet()
    }

    final override fun hashCode(): Int {
        return this.toSet().hashCode()
    }

    override fun toString(): String {
        return this.toSet().joinToString(separator = ", ", prefix = "[", postfix = "]")
    }
}

abstract class AbstractMutableProposalSet : AbstractProposalSet(), MutableProposalSet {
    /**
     * Add a proposal, assuming that all necessary checking has already been performed. Will not be called if this
     * [ProposalSet] already contains the [Proposal].
     */
    protected abstract fun addUnchecked(toAdd: Proposal)

    final override fun add(toAdd: Proposal) {
        if (contains(toAdd.number)) {
            checkMismatch(toAdd)
        } else {
            addUnchecked(toAdd)
        }
    }
}
