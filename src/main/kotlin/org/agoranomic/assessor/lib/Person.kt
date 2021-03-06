package org.agoranomic.assessor.lib

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import org.randomcat.util.requireDistinct

data class Person(val name: String)

data class Persons(val data: ImmutableSet<Person>) : Collection<Person> by data {
    constructor(data: Set<Person>) : this(data.toImmutableSet())
}

/**
 * Returns an empty [Persons].
 */
fun emptyPersons() = Persons(emptySet())

/**
 * Returns a [Persons] that contains the [Person]s in [persons]. Throws [IllegalArgumentException] if [persons] contains
 * any [Person] more than once.
 */
fun personsOf(vararg persons: Person) = Persons(persons.asList().requireDistinct())

operator fun Persons.plus(other: Person) = Persons(data + other)
operator fun Persons.minus(other: Person) = Persons(data - other)

operator fun Persons.plus(other: Persons) = Persons(this.data + other.data)
operator fun Persons.minus(other: Persons) = Persons(this.data - other.data)
