/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.text

import kotlin.native.internal.escapeAnalysis.Escapes
import kotlin.native.internal.GCUnsafeCall
import kotlin.native.internal.escapeAnalysis.PointsTo

/**
 * Returns the index within this string of the first occurrence of the specified character, starting from the specified offset.
 */
@GCUnsafeCall("Kotlin_String_indexOfChar")
@Escapes.Nothing
internal actual external fun String.nativeIndexOf(ch: Char, fromIndex: Int): Int

/**
 * Returns the index within this string of the first occurrence of the specified substring, starting from the specified offset.
 */
@GCUnsafeCall("Kotlin_String_indexOfString")
@Escapes.Nothing
internal actual external fun String.nativeIndexOf(str: String, fromIndex: Int): Int

/**
 * Returns the index within this string of the last occurrence of the specified character.
 */
@GCUnsafeCall("Kotlin_String_lastIndexOfChar")
@Escapes.Nothing
internal actual external fun String.nativeLastIndexOf(ch: Char, fromIndex: Int): Int

/**
 * Returns the index within this string of the last occurrence of the specified character, starting from the specified offset.
 */
internal actual fun String.nativeLastIndexOf(str: String, fromIndex: Int): Int {
    val count = length
    val otherCount = str.length
    if (fromIndex < 0 || otherCount > count) return -1

    var start = fromIndex.coerceAtMost(count - otherCount)
    if (otherCount == 0) return start

    val firstChar = str[0]
    while (true) {
        val candidate = nativeLastIndexOf(firstChar, start)
        if (candidate == -1) return -1
        if (unsafeRangeEquals(candidate, str, 0, otherCount)) return candidate
        start = candidate - 1
    }
}

/**
 * Returns `true` if this string is equal to [other], optionally ignoring character case.
 *
 * Two strings are considered to be equal if they have the same length and the same character at the same index.
 * If [ignoreCase] is true, the result of `Char.uppercaseChar().lowercaseChar()` on each character is compared.
 *
 * @param ignoreCase `true` to ignore character case when comparing strings. By default `false`.
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String?.equals(other: String?, ignoreCase: Boolean = false): Boolean {
    if (this === null)
        return other === null
    if (other === null)
        return false
    return if (!ignoreCase)
        this.equals(other)
    else if (length != other.length)
        false
    else
        unsafeRangeEqualsIgnoreCase(0, other, 0, length)
}

/**
 * Returns a new string with all occurrences of [oldChar] replaced with [newChar].
 *
 * @sample samples.text.Strings.replace
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.replace(oldChar: Char, newChar: Char, ignoreCase: Boolean = false): String {
    return if (!ignoreCase)
        replace(oldChar, newChar)
    else
        replaceIgnoreCase(oldChar, newChar)
}

@GCUnsafeCall("Kotlin_String_replace")
@Escapes.Nothing
private external fun String.replace(oldChar: Char, newChar: Char): String

private fun String.replaceIgnoreCase(oldChar: Char, newChar: Char): String {
    val charArray = this.toCharArray()
    val oldCharUpper = oldChar.uppercaseChar()
    val oldCharLower = oldCharUpper.lowercaseChar()

    for (index in 0 until length) {
        val thisChar = this[index]
        if (thisChar != oldChar && thisChar.uppercaseChar().let { it != oldCharUpper && it.lowercaseChar() != oldCharLower }) {
            continue
        }
        charArray[index] = newChar
    }

    return charArray.concatToString()
}

/**
 * Returns a new string obtained by replacing all occurrences of the [oldValue] substring in this string
 * with the specified [newValue] string.
 *
 * @sample samples.text.Strings.replace
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.replace(oldValue: String, newValue: String, ignoreCase: Boolean = false): String =
        splitToSequence(oldValue, ignoreCase = ignoreCase).joinToString(separator = newValue)

/**
 * Returns a new string with the first occurrence of [oldChar] replaced with [newChar].
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.replaceFirst(oldChar: Char, newChar: Char, ignoreCase: Boolean = false): String {
    val index = indexOf(oldChar, ignoreCase = ignoreCase)
    return if (index < 0) this else this.replaceRange(index, index + 1, newChar.toString())
}

/**
 * Returns a new string obtained by replacing the first occurrence of the [oldValue] substring in this string
 * with the specified [newValue] string.
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.replaceFirst(oldValue: String, newValue: String, ignoreCase: Boolean = false): String {
    val index = indexOf(oldValue, ignoreCase = ignoreCase)
    return if (index < 0) this else this.replaceRange(index, index + oldValue.length, newValue)
}

/**
 * Returns the substring of this string starting at the [startIndex] and ending right before the [endIndex].
 *
 * @param startIndex the start index (inclusive).
 * @param endIndex the end index (exclusive).
 *
 * @throws IndexOutOfBoundsException when [startIndex] is negative, [endIndex] exceeds the length if the string, or
 *  if [startIndex] is greater than [endIndex].
 *
 * @sample samples.text.Strings.substringByStartAndEndIndices
 */
@kotlin.internal.InlineOnly
public actual inline fun String.substring(startIndex: Int, endIndex: Int): String =
        subSequence(startIndex, endIndex) as String

/**
 * Returns a substring of this string that starts at the specified [startIndex] and continues to the end of the string.
 *
 * @param startIndex the start index (inclusive).
 *
 * @throws IndexOutOfBoundsException when [startIndex] is negative or exceeds the length if the string.
 *
 * @sample samples.text.Strings.substringFromStartIndex
 */
@kotlin.internal.InlineOnly
public actual inline fun String.substring(startIndex: Int): String =
        subSequence(startIndex, this.length) as String

/**
 * Returns `true` if this string starts with the specified prefix.
 *
 * @param prefix the prefix from which this string should start with.
 * @param ignoreCase the flag indicating if the string characters should be compared with the [prefix] characters
 *  in a case-insensitive manner; by default, comparison is case-sensitive.
 *
 * @sample samples.text.Strings.startsWithPrefixCaseSensitive
 * @sample samples.text.Strings.startsWithPrefixCaseInsensitive
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.startsWith(prefix: String, ignoreCase: Boolean = false): Boolean =
        regionMatches(0, prefix, 0, prefix.length, ignoreCase)

/**
 * Returns `true` if a substring of this string starting at the specified offset [startIndex] starts with the specified prefix.
 *
 * @param prefix the prefix from which this string's substring beginning at [startIndex] should start with.
 * @param startIndex the start index (inclusive).
 * @param ignoreCase the flag indicating if the string characters should be compared with the [prefix] characters
 *  in a case-insensitive manner; by default, comparison is case-sensitive.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is negative or exceeds the length of the string.
 *
 * @sample samples.text.Strings.startsWithPrefixAtPositionCaseSensitive
 * @sample samples.text.Strings.startsWithPrefixAtPositionCaseInsensitive
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.startsWith(prefix: String, startIndex: Int, ignoreCase: Boolean = false): Boolean =
        regionMatches(startIndex, prefix, 0, prefix.length, ignoreCase)

/**
 * Returns `true` if this string ends with the specified suffix.
 *
 * @param suffix the suffix with which this string should end with.
 * @param ignoreCase the flag indicating if the string characters should be compared with the [suffix] characters
 *  in a case-insensitive manner; by default, comparison is case-sensitive.
 *
 * @sample samples.text.Strings.endsWithSuffixCaseSensitive
 * @sample samples.text.Strings.endsWithSuffixCaseInsensitive
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.endsWith(suffix: String, ignoreCase: Boolean = false): Boolean =
        regionMatches(length - suffix.length, suffix, 0, suffix.length, ignoreCase)

/**
 * Returns `true` if the specified range in this char sequence is equal to the specified range in another char sequence.
 * @param thisOffset the start offset in this char sequence of the substring to compare.
 * @param other the string against a substring of which the comparison is performed.
 * @param otherOffset the start offset in the other char sequence of the substring to compare.
 * @param length the length of the substring to compare.
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun CharSequence.regionMatches(
        thisOffset: Int, other: CharSequence, otherOffset: Int, length: Int,
        ignoreCase: Boolean = false): Boolean {
    return if (this is String && other is String) {
        this.regionMatches(thisOffset, other, otherOffset, length, ignoreCase)
    } else {
        regionMatchesImpl(thisOffset, other, otherOffset, length, ignoreCase)
    }
}

/**
 * Returns `true` if the specified range in this string is equal to the specified range in another string.
 * @param thisOffset the start offset in this string of the substring to compare.
 * @param other the string against a substring of which the comparison is performed.
 * @param otherOffset the start offset in the other string of the substring to compare.
 * @param length the length of the substring to compare.
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.regionMatches(
        thisOffset: Int, other: String, otherOffset: Int, length: Int,
        ignoreCase: Boolean = false): Boolean {
    if (length < 0 || thisOffset < 0 || otherOffset < 0
            || thisOffset + length > this.length
            || otherOffset + length > other.length) {
        return false
    }
    return if (!ignoreCase)
        unsafeRangeEquals(thisOffset, other, otherOffset, length)
    else
        unsafeRangeEqualsIgnoreCase(thisOffset, other, otherOffset, length)
}

// Bounds must be checked before calling this method
@GCUnsafeCall("Kotlin_String_unsafeRangeEquals")
@Escapes.Nothing
private external fun String.unsafeRangeEquals(thisOffset: Int, other: String, otherOffset: Int, length: Int): Boolean

// Bounds must be checked before calling this method
private fun String.unsafeRangeEqualsIgnoreCase(thisOffset: Int, other: String, otherOffset: Int, length: Int): Boolean {
    for (index in 0 until length) {
        val thisCharUpper = this[thisOffset + index].uppercaseChar()
        val otherCharUpper = other[otherOffset + index].uppercaseChar()
        if (thisCharUpper != otherCharUpper && thisCharUpper.lowercaseChar() != otherCharUpper.lowercaseChar()) {
            return false
        }
    }
    return true
}

/**
 * Returns a copy of this string converted to upper case using the rules of the default locale.
 */
@Deprecated("Use uppercase() instead.", ReplaceWith("uppercase()"))
@DeprecatedSinceKotlin(warningSince = "1.5", errorSince = "2.1")
public actual fun String.toUpperCase(): String = uppercaseImpl()

/**
 * Returns a copy of this string converted to upper case using Unicode mapping rules of the invariant locale.
 *
 * This function supports one-to-many and many-to-one character mapping,
 * thus the length of the returned string can be different from the length of the original string.
 *
 * @sample samples.text.Strings.uppercase
 */
@SinceKotlin("1.5")
public actual fun String.uppercase(): String = uppercaseImpl()

/**
 * Returns a copy of this string converted to lower case using the rules of the default locale.
 */
@Deprecated("Use lowercase() instead.", ReplaceWith("lowercase()"))
@DeprecatedSinceKotlin(warningSince = "1.5", errorSince = "2.1")
public actual fun String.toLowerCase(): String = lowercaseImpl()

/**
 * Returns a copy of this string converted to lower case using Unicode mapping rules of the invariant locale.
 *
 * This function supports one-to-many and many-to-one character mapping,
 * thus the length of the returned string can be different from the length of the original string.
 *
 * @sample samples.text.Strings.lowercase
 */
@SinceKotlin("1.5")
public actual fun String.lowercase(): String = lowercaseImpl()

/**
 * Returns a [CharArray] containing characters of this string.
 */
public actual fun String.toCharArray(): CharArray = toCharArray(this, CharArray(length), 0, 0, length)

@GCUnsafeCall("Kotlin_String_toCharArray")
@PointsTo(
        0x000000,
        0x000000,
        0x000000,
        0x000000,
        0x000000,
        0x000010,
) // the return value is destination
private external fun toCharArray(string: String, destination: CharArray, destinationOffset: Int, start: Int, size: Int): CharArray

/**
 * Returns a copy of this string having its first letter titlecased using the rules of the default locale,
 * or the original string if it's empty or already starts with a title case letter.
 *
 * The title case of a character is usually the same as its upper case with several exceptions.
 * The particular list of characters with the special title case form depends on the underlying platform.
 *
 * @sample samples.text.Strings.capitalize
 */
@Deprecated("Use replaceFirstChar instead.", ReplaceWith("replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }"))
@DeprecatedSinceKotlin(warningSince = "1.5")
public actual fun String.capitalize(): String {
    return if (isNotEmpty() && this[0].isLowerCase()) substring(0, 1).uppercase() + substring(1) else this
}

/**
 * Returns a copy of this string having its first letter lowercased using the rules of the default locale,
 * or the original string if it's empty or already starts with a lower case letter.
 *
 * @sample samples.text.Strings.decapitalize
 */
@Deprecated("Use replaceFirstChar instead.", ReplaceWith("replaceFirstChar { it.lowercase() }"))
@DeprecatedSinceKotlin(warningSince = "1.5")
public actual fun String.decapitalize(): String {
    return if (isNotEmpty() && !this[0].isLowerCase()) substring(0, 1).lowercase() + substring(1) else this
}

/**
 * Returns a string containing this char sequence repeated [n] times.
 * @throws [IllegalArgumentException] when n < 0.
 * @sample samples.text.Strings.repeat
 */
public actual fun CharSequence.repeat(n: Int): String {
    require(n >= 0) { "Count 'n' must be non-negative, but was $n." }

    return when (n) {
        0 -> ""
        1 -> this.toString()
        else -> {
            when (length) {
                0 -> ""
                1 -> this[0].let { char -> CharArray(n) { char }.concatToString() }
                else -> {
                    val sb = StringBuilder(n * length)
                    for (i in 1..n) {
                        sb.append(this)
                    }
                    sb.toString()
                }
            }
        }
    }
}

/**
 * Converts the characters in the specified array to a string.
 */
@Deprecated("Use CharArray.concatToString() instead", ReplaceWith("chars.concatToString()"))
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.5")
public actual fun String(chars: CharArray): String = chars.concatToString()

/**
 * Converts the characters from a portion of the specified array to a string.
 *
 * @throws IndexOutOfBoundsException if either [offset] or [length] are less than zero
 * or `offset + length` is out of [chars] array bounds.
 */
@Deprecated("Use CharArray.concatToString(startIndex, endIndex) instead", ReplaceWith("chars.concatToString(offset, offset + length)"))
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.5")
public actual fun String(chars: CharArray, offset: Int, length: Int): String {
    if (offset < 0 || length < 0 || offset + length > chars.size)
        throw IndexOutOfBoundsException()

    return unsafeStringFromCharArray(chars, offset, length)
}

/**
 * Concatenates characters in this [CharArray] into a String.
 */
@SinceKotlin("1.3")
public actual fun CharArray.concatToString(): String = unsafeStringFromCharArray(this, 0, size)

/**
 * Concatenates characters in this [CharArray] or its subrange into a String.
 *
 * @param startIndex the beginning (inclusive) of the subrange of characters, 0 by default.
 * @param endIndex the end (exclusive) of the subrange of characters, size of this array by default.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the size of this array.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 */
@SinceKotlin("1.3")
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun CharArray.concatToString(startIndex: Int = 0, endIndex: Int = this.size): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, size)
    return unsafeStringFromCharArray(this, startIndex, endIndex - startIndex)
}

@GCUnsafeCall("Kotlin_String_unsafeStringFromCharArray")
// The return value may be an empty string, which is statically allocated and immutable;
// we can treat it as non-escaping
@Escapes.Nothing
internal actual external fun unsafeStringFromCharArray(array: CharArray, start: Int, size: Int) : String

/**
 * Returns a [CharArray] containing characters of this string or its substring.
 *
 * @param startIndex the beginning (inclusive) of the substring, 0 by default.
 * @param endIndex the end (exclusive) of the substring, length of this string by default.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the length of this string.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 */
@SinceKotlin("1.3")
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.toCharArray(startIndex: Int = 0, endIndex: Int = this.length): CharArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    val rangeSize = endIndex - startIndex
    return toCharArray(this, CharArray(rangeSize), 0, startIndex, rangeSize)
}

/**
 * Copies characters from this string into the [destination] character array and returns that array.
 *
 * @param destination the array to copy to.
 * @param destinationOffset the position in the array to copy to.
 * @param startIndex the start offset (inclusive) of the substring to copy.
 * @param endIndex the end offset (exclusive) of the substring to copy.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of this string builder indices or when `startIndex > endIndex`.
 * @throws IndexOutOfBoundsException when the subrange doesn't fit into the [destination] array starting at the specified [destinationOffset],
 *  or when that index is out of the [destination] array indices range.
 */
@IgnorableReturnValue
@SinceKotlin("2.0")
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.toCharArray(
        destination: CharArray,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = length
): CharArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    val rangeSize = endIndex - startIndex
    AbstractList.checkBoundsIndexes(destinationOffset, destinationOffset + rangeSize, destination.size)
    return toCharArray(this, destination, destinationOffset, startIndex, rangeSize)
}

/**
 * Decodes a string from the bytes in UTF-8 encoding in this array.
 *
 * Malformed byte sequences are replaced by the replacement char `\uFFFD`.
 */
@SinceKotlin("1.3")
public actual fun ByteArray.decodeToString(): String = unsafeStringFromUtf8(0, size)

/**
 * Decodes a string from the bytes in UTF-8 encoding in this array or its subrange.
 *
 * @param startIndex the beginning (inclusive) of the subrange to decode, 0 by default.
 * @param endIndex the end (exclusive) of the subrange to decode, size of this array by default.
 * @param throwOnInvalidSequence specifies whether to throw an exception on malformed byte sequence or replace it by the replacement char `\uFFFD`.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the size of this array.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 * @throws CharacterCodingException if the byte array contains malformed UTF-8 byte sequence and [throwOnInvalidSequence] is true.
 */
@SinceKotlin("1.3")
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun ByteArray.decodeToString(startIndex: Int = 0, endIndex: Int = this.size, throwOnInvalidSequence: Boolean = false): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, size)
    return if (throwOnInvalidSequence)
        unsafeStringFromUtf8OrThrow(startIndex, endIndex - startIndex)
    else
        unsafeStringFromUtf8(startIndex, endIndex - startIndex)
}

/**
 * Encodes this string to an array of bytes in UTF-8 encoding.
 *
 * Any malformed char sequence is replaced by the replacement byte sequence.
 *
 * @sample samples.text.Strings.encodeToByteArray
 */
@SinceKotlin("1.3")
public actual fun String.encodeToByteArray(): ByteArray = unsafeStringToUtf8(0, length)

/**
 * Encodes this string or its substring to an array of bytes in UTF-8 encoding.
 *
 * @param startIndex the beginning (inclusive) of the substring to encode, 0 by default.
 * @param endIndex the end (exclusive) of the substring to encode, length of this string by default.
 * @param throwOnInvalidSequence specifies whether to throw an exception on malformed char sequence or replace.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the length of this string.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 * @throws CharacterCodingException if this string contains malformed char sequence and [throwOnInvalidSequence] is true.
 *
 * @sample samples.text.Strings.encodeToByteArray
 */
@SinceKotlin("1.3")
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.encodeToByteArray(startIndex: Int = 0, endIndex: Int = this.length, throwOnInvalidSequence: Boolean = false): ByteArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    return if (throwOnInvalidSequence)
        unsafeStringToUtf8OrThrow(startIndex, endIndex - startIndex)
    else
        unsafeStringToUtf8(startIndex, endIndex - startIndex)
}

@GCUnsafeCall("Kotlin_ByteArray_unsafeStringFromUtf8")
// The return value may be an empty string, which is statically allocated and immutable;
// we can treat it as non-escaping
@Escapes.Nothing
internal external fun ByteArray.unsafeStringFromUtf8(start: Int, size: Int) : String

@GCUnsafeCall("Kotlin_ByteArray_unsafeStringFromUtf8OrThrow")
// The return value may be an empty string, which is statically allocated and immutable;
// we can treat it as non-escaping
@Escapes.Nothing
internal external fun ByteArray.unsafeStringFromUtf8OrThrow(start: Int, size: Int) : String

@GCUnsafeCall("Kotlin_String_unsafeStringToUtf8")
@Escapes.Nothing
internal external fun String.unsafeStringToUtf8(start: Int, size: Int) : ByteArray

@GCUnsafeCall("Kotlin_String_unsafeStringToUtf8OrThrow")
@Escapes.Nothing
internal external fun String.unsafeStringToUtf8OrThrow(start: Int, size: Int) : ByteArray

internal fun compareToIgnoreCase(thiz: String, other: String): Int {
    val length = minOf(thiz.length, other.length)

    for (index in 0 until length) {
        val thisLowerChar = thiz[index].uppercaseChar().lowercaseChar()
        val otherLowerChar = other[index].uppercaseChar().lowercaseChar()
        if (thisLowerChar != otherLowerChar) {
            return if (thisLowerChar < otherLowerChar) -1 else 1
        }
    }

    return if (thiz.length == other.length)
        0
    else if (thiz.length < other.length)
        -1
    else
        1
}

/**
 * Compares two strings lexicographically, optionally ignoring case differences.
 *
 * If [ignoreCase] is true, the result of `Char.uppercaseChar().lowercaseChar()` on each character is compared.
 */
@Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
public actual fun String.compareTo(other: String, ignoreCase: Boolean = false): Int {
    return if (!ignoreCase) this.compareTo(other)
    else compareToIgnoreCase(this, other)
}

/**
 * Returns `true` if the contents of this char sequence are equal to the contents of the specified [other],
 * i.e. both char sequences contain the same number of the same characters in the same order.
 *
 * @sample samples.text.Strings.contentEquals
 */
@SinceKotlin("1.5")
public actual infix fun CharSequence?.contentEquals(other: CharSequence?): Boolean = contentEqualsImpl(other)

/**
 * Returns `true` if the contents of this char sequence are equal to the contents of the specified [other], optionally ignoring case difference.
 *
 * @param ignoreCase `true` to ignore character case when comparing contents.
 *
 * @sample samples.text.Strings.contentEquals
 */
@SinceKotlin("1.5")
public actual fun CharSequence?.contentEquals(other: CharSequence?, ignoreCase: Boolean): Boolean {
    return if (ignoreCase)
        this.contentEqualsIgnoreCaseImpl(other)
    else
        this.contentEqualsImpl(other)
}

private val STRING_CASE_INSENSITIVE_ORDER = Comparator<String> { a, b -> a.compareTo(b, ignoreCase = true) }

public actual val String.Companion.CASE_INSENSITIVE_ORDER: Comparator<String>
    get() = STRING_CASE_INSENSITIVE_ORDER
