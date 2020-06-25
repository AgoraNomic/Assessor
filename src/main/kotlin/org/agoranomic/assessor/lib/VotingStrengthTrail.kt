package org.agoranomic.assessor.lib

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

data class VotingStrengthTrail(
    val initial: VotingStrength,
    val modifications: ImmutableList<VotingStrengthModification>
) {
    constructor(
        start: VotingStrength,
        modifications: List<VotingStrengthModification>
    ) : this(
        start,
        modifications.toImmutableList()
    )

    val modificationDescriptions
        get() = modifications.map { it.description }

    val final
        get() = modifications.fold(initial) { acc, modification -> modification.transform(acc) }

    /**
     * Returns a list containing each step along with the value at that point. The only value for which the second value
     * of the pair is null is the first (representing the initial value, which has no description).
     *
     * For example, if the initial strength is 3 and the modifications are (+2) and (*4), the result will be the
     * following:
     * - (null, 3)
     * - ((+2), 5)
     * - ((*4), 20)
     */
    fun stepsWithValues(): List<Pair<VotingStrengthModification?, VotingStrength>> {
        @OptIn(ExperimentalStdlibApi::class)
        return modifications.scan((null as VotingStrengthModification?) to initial) { acc, modification ->
            modification to (modification.transform(acc.second))
        }
    }

    /**
     * Returns a list containing each modification along with the value at that point. This is equivalent to
     * [stepsWithValues], excluding the first entry.
     *
     * For example, if the initial strength is 3 and the modifications are (+2) and (*4), the result will be the
     * following:
     * - ((+2), 5)
     * - ((*4), 20)
     */
    fun modificationsWithValue(): List<Pair<VotingStrengthModification, VotingStrength>> {
        val steps = stepsWithValues()
        check(steps.isNotEmpty())
        check(steps.first().first == null)

        val modifications = steps.drop(1)
        check(modifications.all { it.first != null })

        return modifications.map { (it.first!!) to it.second }
    }

    fun withAppended(modification: VotingStrengthModification): VotingStrengthTrail {
        return VotingStrengthTrail(initial, modifications + modification)
    }

    fun withAppended(newModifications: Iterable<VotingStrengthModification>): VotingStrengthTrail {
        return VotingStrengthTrail(initial, modifications + newModifications)
    }
}

data class VotingStrengthTrailForPersons(
    val default: VotingStrength,
    private val override: ImmutableMap<Person, VotingStrengthTrail>
) {
    companion object {
        fun emptyWithDefault(default: VotingStrength) = VotingStrengthTrailForPersons(default, mapOf())
    }

    constructor(
        default: VotingStrength,
        data: Map<Person, VotingStrengthTrail>
    ) : this(
        default,
        data.toImmutableMap()
    )

    private fun defaultTrail() = VotingStrengthTrail(default, emptyList())

    val overriddenPersons
        get() = override.keys

    fun trailForPerson(person: Person): VotingStrengthTrail {
        return override.getOrElse(person) { defaultTrail() }
    }

    fun finalStrengthForPerson(person: Person): VotingStrength = trailForPerson(person).final

    fun withAppendedToAll(modification: VotingStrengthModification) = VotingStrengthTrailForPersons(
        default,
        override.mapValues { (_, trail) -> trail.withAppended(modification) }
    )

    fun withAppendedToAll(modifications: Iterable<VotingStrengthModification>) = VotingStrengthTrailForPersons(
        default,
        override.mapValues { (_, trail) -> trail.withAppended(modifications) }
    )

    fun overridesMap(): Map<Person, VotingStrengthTrail> = override
    fun overrideStrengthsMap() = overridesMap().mapValues { (_, trail) -> trail.final }
}
