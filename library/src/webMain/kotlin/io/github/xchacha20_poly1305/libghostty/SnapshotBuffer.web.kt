package io.github.xchacha20_poly1305.libghostty

internal actual class SnapshotBuffer actual constructor(capacity: Int) {
    val bytes = ByteArray(capacity)

    private var cursor = 0

    actual fun capacity(): Int = bytes.size

    actual fun position(): Int = cursor

    actual fun position(value: Int) {
        cursor = value
    }

    private fun firstByteAt(index: Int): Int = bytes[index].toInt() and 0xFF

    private fun getShort(offset: Int): Short {
        return (firstByteAt(offset) or (firstByteAt(offset + 1) shl 8)).toShort()
    }

    actual fun getInt(offset: Int): Int {
        return firstByteAt(offset) or
                (firstByteAt(offset + 1) shl 8) or
                (firstByteAt(offset + 2) shl 16) or
                (firstByteAt(offset + 3) shl 24)
    }

    actual fun getLong(offset: Int): Long {
        val low = getInt(offset).toLong() and 0xFFFFFFFFL
        val high = getInt(offset + 4).toLong()
        return low or (high shl 32)
    }

    actual val short: Short
        get() = getShort(cursor).also {
            cursor += 2
        }

    actual val int: Int
        get() = getInt(cursor).also {
            cursor += 4
        }

    actual val long: Long
        get() = getLong(cursor).also {
            cursor += 8
        }

    actual val char: Char
        get() = short.toInt().toChar()
}