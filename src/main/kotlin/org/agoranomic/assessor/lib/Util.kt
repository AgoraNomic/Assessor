package org.agoranomic.assessor.lib

import java.math.BigDecimal
import java.math.BigInteger

fun <K, V> Map<K, V>.getOrFail(key: K): V {
    if (containsKey(key)) {
        return get(key) as V
    }

    error("Missing expected key in map: $key")
}

inline fun <reified E : Enum<E>> Collection<E>.isExhaustive(): Boolean {
    val collection = this
    return enumValues<E>().all { enumValue -> collection.contains(enumValue) }
}

inline fun <reified E : Enum<E>> Collection<E>.requireExhaustive() {
    val collection = this
    for (value in enumValues<E>()) {
        require(collection.contains(value)) { "Collection was required to be exhaustive, but did not contain $value" }
    }
}

fun <T> Collection<T>.repeatingElements(): Set<T> {
    val alreadySeen = mutableSetOf<T>()
    val duplicates = mutableSetOf<T>()

    for (element in this) {
        if (alreadySeen.contains(element)) {
            duplicates += element
        } else {
            alreadySeen += element
        }
    }

    return duplicates
}

fun <T> Collection<T>.allAreDistinct(): Boolean {
    val list = this.toList()
    val distinctList = list.distinct()

    // If all are distinct, then list and distinctList have the same size, since no elements will have been removed.
    return list.size == distinctList.size
}

fun <T> Collection<T>.requireAllAreDistinct() {
    require(allAreDistinct()) {
        "All elements were required to be distinct, but found duplicate elements: ${repeatingElements()}"
    }
}

fun <T, K> Collection<T>.allAreDistinctBy(selector: (T) -> K): Boolean {
    return this.map(selector).allAreDistinct()
}

fun <T, K> Collection<T>.requireAllAreDistinctBy(selector: (T) -> K) {
    this.map(selector).requireAllAreDistinct()
}

operator fun BigDecimal.plus(other: Int) = this.plus(other.toBigDecimal())
operator fun Int.plus(other: BigDecimal) = (this.toBigDecimal()).plus(other)

operator fun BigDecimal.minus(other: Int) = this.minus(other.toBigDecimal())
operator fun Int.minus(other: BigDecimal) = (this.toBigDecimal()).minus(other)

operator fun BigDecimal.times(other: Int) = this.times(other.toBigDecimal())
operator fun Int.times(other: BigDecimal) = (this.toBigDecimal()).times(other)

operator fun BigDecimal.compareTo(other: Int) = this.compareTo(other.toBigDecimal())
operator fun Int.compareTo(other: BigDecimal) = (this.toBigDecimal()).compareTo(other)

operator fun BigInteger.plus(other: Int) = this.plus(other.toBigInteger())
operator fun Int.plus(other: BigInteger) = (this.toBigInteger()).plus(other)

operator fun BigInteger.minus(other: Int) = this.minus(other.toBigInteger())
operator fun Int.minus(other: BigInteger) = (this.toBigInteger()).minus(other)

operator fun BigInteger.times(other: Int) = this.times(other.toBigInteger())
operator fun Int.times(other: BigInteger) = (this.toBigInteger()).times(other)

operator fun BigInteger.div(other: Int) = this.div(other.toBigInteger())
operator fun Int.div(other: BigInteger) = (this.toBigInteger()).div(other)

operator fun BigInteger.rem(other: Int) = this.rem(other.toBigInteger())
operator fun Int.rem(other: BigInteger) = (this.toBigInteger()).rem(other)

operator fun BigInteger.compareTo(other: Int) = this.compareTo(other.toBigInteger())
operator fun Int.compareTo(other: BigInteger) = (this.toBigInteger()).compareTo(other)

operator fun BigInteger.plus(other: BigDecimal) = this.toBigDecimal().plus(other)
operator fun BigDecimal.plus(other: BigInteger) = this.plus(other.toBigDecimal())

operator fun BigInteger.minus(other: BigDecimal) = this.toBigDecimal().minus(other)
operator fun BigDecimal.minus(other: BigInteger) = this.minus(other.toBigDecimal())

operator fun BigInteger.times(other: BigDecimal) = this.toBigDecimal().times(other)
operator fun BigDecimal.times(other: BigInteger) = this.times(other.toBigDecimal())

operator fun BigInteger.compareTo(other: BigDecimal) = this.toBigDecimal().compareTo(other)
operator fun BigDecimal.compareTo(other: BigInteger) = this.compareTo(other.toBigDecimal())
