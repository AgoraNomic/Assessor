package org.agoranomic.assessor.decisions

import org.agoranomic.assessor.dsl.assessment
import org.agoranomic.assessor.dsl.receivers.ai
import org.agoranomic.assessor.dsl.receivers.quorum
import org.agoranomic.assessor.dsl.votes.blotPenalty
import org.agoranomic.assessor.dsl.votes.powerStone

@UseAssessment
fun assessment8558() = assessment {
    name("8558")
    quorum(3)

    strengths {
        default(3)
        min(0)
        max(15)

        blotPenalty(RLee, 7 / 3)
        powerStone(Jason, 3)
    }

    proposals(v4) {
        proposal(8558) {
            title("Silver Quill 2020")
            ai("2.0")
            author(Aris)
            ordinary()
            sponsored()

            text(
                """
[This enables me to award the Silver Quill, and is necessary
because one of the proposals was selected after the official deadline.
I set it up as a temporary rule because I still want to do the
big announcement where I actually award the title.]

Enact a power 2.0 rule, entitled "Silver Quill 2020", with
the following text:

  The Promotor CAN once, by announcement, award the patent title
  Silver Quill 2020 to nix for eir authorship of Proposal 8408,
  "Sets v1.4".

  The Promotor CAN once, by announcement, award the patent title
  Silver Quill 2020 to Aris for eir authorship of Proposal 8514,
  "The Buoyant Economy".

  The Promotor CAN cause this rule to repeal itself by announcement,
  and SHALL do so in a timely fashion after its enactment."""
            )
        }
    }
}
