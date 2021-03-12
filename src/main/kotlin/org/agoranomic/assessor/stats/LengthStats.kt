package org.agoranomic.assessor.stats

import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.geom.geom_histogram
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.scale.scale_fill_discrete
import jetbrains.letsPlot.scale.scale_x_continuous
import org.agoranomic.assessor.lib.proposal.Proposal
import org.agoranomic.assessor.lib.resolve.ProposalResult
import org.agoranomic.assessor.lib.resolve.ResolutionData

fun buildLengthStats(
    resolutionsByProposal: Map<Proposal, List<ResolutionData>>,
) = buildStatistics {
    data class DataEntry(val words: Int, val adopted: Boolean)

    val proposalEntries =
        resolutionsByProposal
            .map { entry ->
                DataEntry(entry.key.textWords(), entry.value.any { it.result == ProposalResult.ADOPTED })
            }
            .sortedByDescending { if (it.adopted) 1 else 0 } // Dirty hack to force order

    yieldGraph(
        name = "proposal_length_bins",
        ggplot() +
                geom_histogram(
                    data = mapOf(
                        "words" to proposalEntries.map { it.words },
                        "adopted" to proposalEntries.map { it.adopted }.map { if (it) "ADOPTED" else "NON-ADOPTED" },
                    ),
                    stat = Stat.bin(binWidth = 100, boundary = 0),
                ) {
                    x = "words"
                    fill = "adopted"
                } +
                scale_x_continuous(name = "Proposal words", limits = 0 to null) +
                scale_fill_discrete(name = "Adopted?") +
                ggtitle("Count by proposal words") +
                ggsize(1200, 800)
    )
}
