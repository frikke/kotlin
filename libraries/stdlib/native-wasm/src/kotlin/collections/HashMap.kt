/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.collections

public actual class HashMap<K, V> private constructor(
    // keys in insert order
    private var keysArray: Array<K>,
    // values in insert order, allocated only when actually used, always null in pure HashSet
    private var valuesArray: Array<V>?,
    // hash of a key by its index, -1 if a key at that index was removed
    private var presenceArray: IntArray,
    // (index + 1) of a key by its hash, 0 if there is no key with that hash, -1 if collision chain continues to the hash-1
    private var hashArray: IntArray,
    // max length of a collision chain
    private var maxProbeDistance: Int,
    // index of the next key to be inserted
    private var length: Int
) : MutableMap<K, V> {
    private var hashShift: Int = computeShift(hashSize)

    /**
     * The number of times this map is structurally modified.
     *
     * A modification is considered to be structural if it changes the map size,
     * or otherwise changes it in a way that iterations in progress may return incorrect results.
     *
     * This value can be used by iterators of the [keys], [values] and [entries] views
     * to provide fail-fast behavior when a concurrent modification is detected during iteration.
     * [ConcurrentModificationException] will be thrown in this case.
     */
    private var modCount: Int = 0

    private var _size: Int = 0
    override actual val size: Int
        get() = _size

    private var keysView: HashMapKeys<K>? = null
    private var valuesView: HashMapValues<V>? = null
    private var entriesView: HashMapEntrySet<K, V>? = null

    private var isReadOnly: Boolean = false

    // ---------------------------- functions ----------------------------

    /**
     * Creates a new empty [HashMap].
     */
    public actual constructor() : this(INITIAL_CAPACITY)

    /**
     * Creates a new empty [HashMap] with the specified initial capacity.
     *
     * Capacity is the maximum number of entries the map is able to store in current internal data structure.
     * When the map gets full by a certain default load factor, its capacity is expanded,
     * which usually leads to rebuild of the internal data structure.
     *
     * @param initialCapacity the initial capacity of the created map.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative.
     */
    public actual constructor(initialCapacity: Int) : this(
            arrayOfUninitializedElements(initialCapacity),
            null,
            IntArray(initialCapacity),
            IntArray(computeHashSize(initialCapacity)),
            INITIAL_MAX_PROBE_DISTANCE,
            0)

    /**
     * Creates a new [HashMap] filled with the contents of the specified [original] map.
     */
    public actual constructor(original: Map<out K, V>) : this(original.size) {
        putAll(original)
    }

    /**
     * Creates a new empty [HashMap] with the specified initial capacity and load factor.
     *
     * Capacity is the maximum number of entries the map is able to store in current internal data structure.
     * Load factor is the measure of how full the map is allowed to get in relation to
     * its capacity before the capacity is expanded, which usually leads to rebuild of the internal data structure.
     *
     * @param initialCapacity the initial capacity of the created map.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     * @param loadFactor the load factor of the created map.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative or [loadFactor] is non-positive.
     */
    public actual constructor(initialCapacity: Int, loadFactor: Float) : this(initialCapacity) {
        require(loadFactor > 0) { "Non-positive load factor: $loadFactor" }
    }

    @PublishedApi
    internal fun build(): Map<K, V> {
        checkIsMutable()
        isReadOnly = true
        return if (size > 0) this else EmptyHolder.value()
    }

    override actual fun isEmpty(): Boolean = _size == 0
    override actual fun containsKey(key: K): Boolean = findKey(key) >= 0
    override actual fun containsValue(value: V): Boolean = findValue(value) >= 0

    override actual operator fun get(key: K): V? {
        val index = findKey(key)
        if (index < 0) return null
        return valuesArray!![index]
    }

    @IgnorableReturnValue
    override actual fun put(key: K, value: V): V? {
        checkIsMutable()
        val index = addKey(key)
        val valuesArray = allocateValuesArray()
        if (index < 0) {
            val oldValue = valuesArray[-index - 1]
            valuesArray[-index - 1] = value
            return oldValue
        } else {
            valuesArray[index] = value
            return null
        }
    }

    override actual fun putAll(from: Map<out K, V>) {
        checkIsMutable()
        putAllEntries(from.entries)
    }

    @IgnorableReturnValue
    override actual fun remove(key: K): V? {
        checkIsMutable()
        val index = findKey(key)
        if (index < 0) return null
        val oldValue = valuesArray!![index]
        removeEntryAt(index)
        return oldValue
    }

    override actual fun clear() {
        checkIsMutable()
        // O(length) implementation for hashArray cleanup
        for (i in 0..length - 1) {
            val hash = presenceArray[i]
            if (hash >= 0) {
                hashArray[hash] = 0
                presenceArray[i] = TOMBSTONE
            }
        }
        keysArray.resetRange(0, length)
        valuesArray?.resetRange(0, length)
        _size = 0
        length = 0
        registerModification()
    }

    override actual val keys: MutableSet<K> get() {
        val cur = keysView
        return if (cur == null) {
            val new = HashMapKeys(this)
            keysView = new
            new
        } else cur
    }

    override actual val values: MutableCollection<V> get() {
        val cur = valuesView
        return if (cur == null) {
            val new = HashMapValues(this)
            valuesView = new
            new
        } else cur
    }

    override actual val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() {
        val cur = entriesView
        return if (cur == null) {
            val new = HashMapEntrySet(this)
            entriesView = new
            new
        } else cur
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                (other is Map<*, *>) &&
                contentEquals(other)
    }

    override fun hashCode(): Int {
        var result = 0
        val it = entriesIterator()
        while (it.hasNext()) {
            result += it.nextHashCode()
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder(2 + _size * 3)
        sb.append("{")
        var i = 0
        val it = entriesIterator()
        while (it.hasNext()) {
            if (i > 0) sb.append(", ")
            it.nextAppendString(sb)
            i++
        }
        sb.append("}")
        return sb.toString()
    }

    // ---------------------------- private ----------------------------

    private val capacity: Int get() = keysArray.size
    private val hashSize: Int get() = hashArray.size

    private fun registerModification() {
        modCount += 1
    }

    internal fun checkIsMutable() {
        if (isReadOnly) throw UnsupportedOperationException()
    }

    private fun ensureExtraCapacity(n: Int) {
        if (shouldCompact(extraCapacity = n)) {
            compact(updateHashArray = true)
        } else {
            ensureCapacity(length + n)
        }
    }

    private fun shouldCompact(extraCapacity: Int): Boolean {
        val spareCapacity = this.capacity - length
        val gaps = length - size
        return spareCapacity < extraCapacity                // there is no room for extraCapacity entries
                && gaps + spareCapacity >= extraCapacity    // removing gaps prevents capacity expansion
                && gaps >= this.capacity / 4                // at least 25% of current capacity is occupied by gaps
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity < 0) throw OutOfMemoryError()    // overflow
        if (minCapacity > this.capacity) {
            val newSize = AbstractList.newCapacity(this.capacity, minCapacity)
            keysArray = keysArray.copyOfUninitializedElements(newSize)
            valuesArray = valuesArray?.copyOfUninitializedElements(newSize)
            presenceArray = presenceArray.copyOf(newSize)
            val newHashSize = computeHashSize(newSize)
            if (newHashSize > hashSize) rehash(newHashSize)
        }
    }

    private fun allocateValuesArray(): Array<V> {
        val curValuesArray = valuesArray
        if (curValuesArray != null) return curValuesArray
        val newValuesArray = arrayOfUninitializedElements<V>(capacity)
        valuesArray = newValuesArray
        return newValuesArray
    }

    // Null-check for escaping extra boxing for non-nullable keys.
    private fun hash(key: K) = if (key == null) 0 else (key.hashCode() * MAGIC) ushr hashShift

    private fun compact(updateHashArray: Boolean) {
        var i = 0
        var j = 0
        val valuesArray = valuesArray
        while (i < length) {
            val hash = presenceArray[i]
            if (hash >= 0) {
                keysArray[j] = keysArray[i]
                if (valuesArray != null) valuesArray[j] = valuesArray[i]
                if (updateHashArray) {
                    presenceArray[j] = hash
                    hashArray[hash] = j + 1
                }
                j++
            }
            i++
        }
        keysArray.resetRange(j, length)
        valuesArray?.resetRange(j, length)
        length = j
        //check(length == size) { "Internal invariant violated during compact: length=$length != size=$size" }
    }

    private fun rehash(newHashSize: Int) {
//        require(newHashSize > hashSize) { "Rehash can only be executed with a grown hash array" }

        registerModification()
        if (length > _size) compact(updateHashArray = false)
        hashArray = IntArray(newHashSize)
        hashShift = computeShift(newHashSize)

        var i = 0
        while (i < length) {
            if (!putRehash(i++)) {
                throw IllegalStateException(
                    "This cannot happen with fixed magic multiplier and grow-only hash array. Have object hashCodes changed?"
                )
            }
        }
    }

    private fun putRehash(i: Int): Boolean {
        var hash = hash(keysArray[i])
        var probesLeft = maxProbeDistance
        while (true) {
            val index = hashArray[hash]
            if (index == 0) {
                hashArray[hash] = i + 1
                presenceArray[i] = hash
                return true
            }
            if (--probesLeft < 0) return false
            if (hash-- == 0) hash = hashSize - 1
        }
    }

    private fun findKey(key: K): Int {
        var hash = hash(key)
        var probesLeft = maxProbeDistance
        while (true) {
            val index = hashArray[hash]
            if (index == 0) return TOMBSTONE
            if (index > 0 && keysArray[index - 1] == key) return index - 1
            if (--probesLeft < 0) return TOMBSTONE
            if (hash-- == 0) hash = hashSize - 1
        }
    }

    private fun findValue(value: V): Int {
        var i = length
        while (--i >= 0) {
            if (presenceArray[i] >= 0 && valuesArray!![i] == value)
                return i
        }
        return TOMBSTONE
    }

    internal fun addKey(key: K): Int {
        checkIsMutable()
        retry@ while (true) {
            var hash = hash(key)
            // put is allowed to grow maxProbeDistance with some limits (resize hash on reaching limits)
            val tentativeMaxProbeDistance = (maxProbeDistance * 2).coerceAtMost(hashSize / 2)
            var probeDistance = 0
            while (true) {
                val index = hashArray[hash]
                if (index <= 0) { // claim or reuse hash slot
                    if (length >= capacity) {
                        ensureExtraCapacity(1)
                        continue@retry
                    }
                    val putIndex = length++
                    keysArray[putIndex] = key
                    presenceArray[putIndex] = hash
                    hashArray[hash] = putIndex + 1
                    _size++
                    registerModification()
                    if (probeDistance > maxProbeDistance) maxProbeDistance = probeDistance
                    return putIndex
                }
                if (keysArray[index - 1] == key) {
                    return -index
                }
                if (++probeDistance > tentativeMaxProbeDistance) {
                    rehash(hashSize * 2) // cannot find room even with extra "tentativeMaxProbeDistance" -- grow hash
                    continue@retry
                }
                if (hash-- == 0) hash = hashSize - 1
            }
        }
    }

    @IgnorableReturnValue
    internal fun removeKey(key: K): Boolean {
        checkIsMutable()
        val index = findKey(key)
        if (index < 0) return false
        removeEntryAt(index)
        return true
    }

    private fun removeEntryAt(index: Int) {
        keysArray.resetAt(index)
        valuesArray?.resetAt(index)
        removeHashAt(presenceArray[index])
        presenceArray[index] = TOMBSTONE
        _size--
        registerModification()
    }

    private fun removeHashAt(removedHash: Int) {
        var hash = removedHash
        var hole = removedHash // will try to patch the hole in hash array
        var probeDistance = 0
        var patchAttemptsLeft = (maxProbeDistance * 2).coerceAtMost(hashSize / 2) // don't spend too much effort
        while (true) {
            if (hash-- == 0) hash = hashSize - 1
            if (++probeDistance > maxProbeDistance) {
                // too far away -- can release the hole, bad case will not happen
                hashArray[hole] = 0
                return
            }
            val index = hashArray[hash]
            if (index == 0) {
                // end of chain -- can release the hole, bad case will not happen
                hashArray[hole] = 0
                return
            }
            if (index < 0) {
                // TOMBSTONE FOUND
                //   - <--- [ TS ] ------ [hole] ---> +
                //             \------------/
                //             probeDistance
                // move tombstone into the hole
                hashArray[hole] = TOMBSTONE
                hole = hash
                probeDistance = 0
            } else {
                val otherHash = hash(keysArray[index - 1])
                // Bad case:
                //   - <--- [hash] ------ [hole] ------ [otherHash] ---> +
                //             \------------/
                //             probeDistance
                if ((otherHash - hash) and (hashSize - 1) >= probeDistance) {
                    // move otherHash into the hole, move the hole
                    hashArray[hole] = index
                    presenceArray[index - 1] = hole
                    hole = hash
                    probeDistance = 0
                }
            }
            // check how long we're patching holes
            if (--patchAttemptsLeft < 0) {
                // just place tombstone into the hole
                hashArray[hole] = TOMBSTONE
                return
            }
        }
    }

    internal fun containsEntry(entry: Map.Entry<K, V>): Boolean {
        val index = findKey(entry.key)
        if (index < 0) return false
        return valuesArray!![index] == entry.value
    }

    internal fun getEntry(entry: Map.Entry<K, V>): MutableMap.MutableEntry<K, V>? {
        val index = findKey(entry.key)
        return if (index < 0 || valuesArray!![index] != entry.value) {
            null
        } else {
            EntryRef(this, index)
        }
    }

    internal fun getKey(key: K): K? {
        val index = findKey(key)
        return if (index >= 0) {
            keysArray[index]!!
        } else {
            null
        }
    }

    private fun contentEquals(other: Map<*, *>): Boolean = _size == other.size && containsAllEntries(other.entries)

    internal fun containsAllEntries(m: Collection<*>): Boolean {
        val it = m.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            try {
                @Suppress("UNCHECKED_CAST") // todo: get rid of unchecked cast here somehow
                if (entry == null || !containsEntry(entry as Map.Entry<K, V>))
                    return false
            } catch (e: ClassCastException) {
                return false
            }
        }
        return true
    }

    @IgnorableReturnValue
    private fun putEntry(entry: Map.Entry<K, V>): Boolean {
        val index = addKey(entry.key)
        val valuesArray = allocateValuesArray()
        if (index >= 0) {
            valuesArray[index] = entry.value
            return true
        }
        val oldValue = valuesArray[-index - 1]
        if (entry.value != oldValue) {
            valuesArray[-index - 1] = entry.value
            return true
        }
        return false
    }

    @IgnorableReturnValue
    private fun putAllEntries(from: Collection<Map.Entry<K, V>>): Boolean {
        if (from.isEmpty()) return false
        ensureExtraCapacity(from.size)
        val it = from.iterator()
        var updated = false
        while (it.hasNext()) {
            if (putEntry(it.next()))
                updated = true
        }
        return updated
    }

    @IgnorableReturnValue
    internal fun removeEntry(entry: Map.Entry<K, V>): Boolean {
        checkIsMutable()
        val index = findKey(entry.key)
        if (index < 0) return false
        if (valuesArray!![index] != entry.value) return false
        removeEntryAt(index)
        return true
    }

    @IgnorableReturnValue
    internal fun removeValue(element: V): Boolean {
        checkIsMutable()
        val index = findValue(element)
        if (index < 0) return false
        removeEntryAt(index)
        return true
    }

    internal fun keysIterator() = KeysItr(this)
    internal fun valuesIterator() = ValuesItr(this)
    internal fun entriesIterator() = EntriesItr(this)

    @kotlin.native.internal.CanBePrecreated
    private companion object {
        private const val MAGIC = -1640531527 // 2654435769L.toInt(), golden ratio
        private const val INITIAL_CAPACITY = 8
        private const val INITIAL_MAX_PROBE_DISTANCE = 2
        private const val TOMBSTONE = -1

        private fun computeHashSize(capacity: Int): Int = (capacity.coerceAtLeast(1) * 3).takeHighestOneBit()

        private fun computeShift(hashSize: Int): Int = hashSize.countLeadingZeroBits() + 1
    }

    internal object EmptyHolder {
        val value_ = HashMap<Nothing, Nothing>(0).also { it.isReadOnly = true }

        fun <K, V> value(): HashMap<K, V> {
            @Suppress("UNCHECKED_CAST")
            return value_ as HashMap<K, V>
        }
    }

    internal open class Itr<K, V>(
            internal val map: HashMap<K, V>
    ) {
        internal var index = 0
        internal var lastIndex: Int = -1
        private var expectedModCount: Int = map.modCount

        init {
            initNext()
        }

        internal fun initNext() {
            while (index < map.length && map.presenceArray[index] < 0)
                index++
        }

        fun hasNext(): Boolean = index < map.length

        fun remove() {
            checkForComodification()
            check(lastIndex != -1) { "Call next() before removing element from the iterator." }
            map.checkIsMutable()
            map.removeEntryAt(lastIndex)
            lastIndex = -1
            expectedModCount = map.modCount
        }

        internal fun checkForComodification() {
            if (map.modCount != expectedModCount)
                throw ConcurrentModificationException()
        }
    }

    internal class KeysItr<K, V>(map: HashMap<K, V>) : Itr<K, V>(map), MutableIterator<K> {
        override fun next(): K {
            checkForComodification()
            if (index >= map.length) throw NoSuchElementException()
            lastIndex = index++
            val result = map.keysArray[lastIndex]
            initNext()
            return result
        }

    }

    internal class ValuesItr<K, V>(map: HashMap<K, V>) : Itr<K, V>(map), MutableIterator<V> {
        override fun next(): V {
            checkForComodification()
            if (index >= map.length) throw NoSuchElementException()
            lastIndex = index++
            val result = map.valuesArray!![lastIndex]
            initNext()
            return result
        }
    }

    internal class EntriesItr<K, V>(map: HashMap<K, V>) : Itr<K, V>(map),
            MutableIterator<MutableMap.MutableEntry<K, V>> {
        override fun next(): EntryRef<K, V> {
            checkForComodification()
            if (index >= map.length) throw NoSuchElementException()
            lastIndex = index++
            val result = EntryRef(map, lastIndex)
            initNext()
            return result
        }

        internal fun nextHashCode(): Int {
            if (index >= map.length) throw NoSuchElementException()
            lastIndex = index++
            val result = map.keysArray[lastIndex].hashCode() xor map.valuesArray!![lastIndex].hashCode()
            initNext()
            return result
        }

        fun nextAppendString(sb: StringBuilder) {
            if (index >= map.length) throw NoSuchElementException()
            lastIndex = index++
            val key = map.keysArray[lastIndex]
            if (key === map) sb.append("(this Map)") else sb.append(key)
            sb.append('=')
            val value = map.valuesArray!![lastIndex]
            if (value === map) sb.append("(this Map)") else sb.append(value)
            initNext()
        }
    }

    internal class EntryRef<K, V>(
        private val map: HashMap<K, V>,
        private val index: Int
    ) : MutableMap.MutableEntry<K, V> {
        private val expectedModCount = map.modCount

        override val key: K
            get() {
                checkForComodification()
                return map.keysArray[index]
            }

        override val value: V
            get() {
                checkForComodification()
                return map.valuesArray!![index]
            }

        override fun setValue(newValue: V): V {
            checkForComodification()
            map.checkIsMutable()
            val valuesArray = map.allocateValuesArray()
            val oldValue = valuesArray[index]
            valuesArray[index] = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> &&
                    other.key == key &&
                    other.value == value

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()

        override fun toString(): String = "$key=$value"

        private fun checkForComodification() {
            if (map.modCount != expectedModCount)
                throw ConcurrentModificationException("The backing map has been modified after this entry was obtained.")
        }
    }
}

internal class HashMapKeys<E> internal constructor(
        private val backing: HashMap<E, *>
) : MutableSet<E>, kotlin.native.internal.KonanSet<E>, AbstractMutableSet<E>() {

    override val size: Int get() = backing.size
    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun contains(element: E): Boolean = backing.containsKey(element)
    override fun getElement(element: E): E? = backing.getKey(element)
    override fun clear() = backing.clear()
    override fun add(element: E): Boolean = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<E>): Boolean = throw UnsupportedOperationException()
    override fun remove(element: E): Boolean = backing.removeKey(element)
    override fun iterator(): MutableIterator<E> = backing.keysIterator()

    override fun removeAll(elements: Collection<E>): Boolean {
        backing.checkIsMutable()
        return super.removeAll(elements)
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        backing.checkIsMutable()
        return super.retainAll(elements)
    }
}

internal class HashMapValues<V> internal constructor(
        val backing: HashMap<*, V>
) : MutableCollection<V>, AbstractMutableCollection<V>() {

    override val size: Int get() = backing.size
    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun contains(element: V): Boolean = backing.containsValue(element)
    override fun add(element: V): Boolean = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<V>): Boolean = throw UnsupportedOperationException()
    override fun clear() = backing.clear()
    override fun iterator(): MutableIterator<V> = backing.valuesIterator()
    override fun remove(element: V): Boolean = backing.removeValue(element)

    override fun removeAll(elements: Collection<V>): Boolean {
        backing.checkIsMutable()
        return super.removeAll(elements)
    }

    override fun retainAll(elements: Collection<V>): Boolean {
        backing.checkIsMutable()
        return super.retainAll(elements)
    }
}

/**
 * Note: intermediate class with [E] `: Map.Entry<K, V>` is required to support
 * [contains] for values that are [Map.Entry] but not [MutableMap.MutableEntry],
 * and probably same for other functions.
 * This is important because an instance of this class can be used as a result of [Map.entries],
 * which should support [contains] for [Map.Entry].
 * For example, this happens when upcasting [MutableMap] to [Map].
 *
 * The compiler enables special type-safe barriers to methods like [contains], which has [UnsafeVariance].
 * Changing type from [MutableMap.MutableEntry] to [E] makes the compiler generate barriers checking that
 * argument `is` [E] (so technically `is` [Map.Entry]) instead of `is` [MutableMap.MutableEntry].
 *
 * See also [KT-42248](https://youtrack.jetbrains.com/issue/KT-42428).
 */
internal abstract class HashMapEntrySetBase<K, V, E : Map.Entry<K, V>> internal constructor(
        val backing: HashMap<K, V>
) : MutableSet<E>, kotlin.native.internal.KonanSet<E>, AbstractMutableSet<E>() {

    override val size: Int get() = backing.size
    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun contains(element: E): Boolean = backing.containsEntry(element)
    override fun getElement(element: E): E? = getEntry(element)
    protected abstract fun getEntry(element: Map.Entry<K, V>): E?
    override fun clear() = backing.clear()
    override fun add(element: E): Boolean = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<E>): Boolean = throw UnsupportedOperationException()
    override fun remove(element: E): Boolean = backing.removeEntry(element)
    override fun containsAll(elements: Collection<E>): Boolean = backing.containsAllEntries(elements)

    override fun removeAll(elements: Collection<E>): Boolean {
        backing.checkIsMutable()
        return super.removeAll(elements)
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        backing.checkIsMutable()
        return super.retainAll(elements)
    }
}

internal class HashMapEntrySet<K, V> internal constructor(
        backing: HashMap<K, V>
) : HashMapEntrySetBase<K, V, MutableMap.MutableEntry<K, V>>(backing) {

    override fun getEntry(element: Map.Entry<K, V>): MutableMap.MutableEntry<K, V>? = backing.getEntry(element)

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = backing.entriesIterator()
}

// This hash map keeps insertion order.
public actual typealias LinkedHashMap<K, V> = HashMap<K, V>
