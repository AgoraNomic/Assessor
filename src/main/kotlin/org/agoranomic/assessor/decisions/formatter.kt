package org.agoranomic.assessor.decisions

import org.agoranomic.assessor.lib.*

interface AssessmentFormatter {
    fun format(assessment: ProposalResolutionMap): String
}

data class HumanReadableFormatter(val config: ReportConfig) : AssessmentFormatter {
    override fun format(assessment: ProposalResolutionMap): String {
        return report(assessment, config)
    }
}

object JsonFormatter : AssessmentFormatter {
    override fun format(assessment: ProposalResolutionMap): String {
        return jsonReport(assessment)
    }
}