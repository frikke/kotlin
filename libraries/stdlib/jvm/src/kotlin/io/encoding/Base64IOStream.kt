/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UnusedReceiverParameter")

package kotlin.io.encoding

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.encoding.Base64.Default.bitsPerByte
import kotlin.io.encoding.Base64.Default.bitsPerSymbol
import kotlin.io.encoding.Base64.Default.bytesPerGroup
import kotlin.io.encoding.Base64.Default.symbolsPerGroup

//import java.io.Reader
//import java.io.Writer


//public fun Reader.wrapForDecoding(base64: Base64): InputStream { TODO() }
//public fun Writer.wrapForEncoding(base64: Base64): OutputStream { TODO() }

/**
 * Returns an input stream that decodes symbols from this input stream using the specified [base64].
 *
 * Reading from the returned input stream leads to reading some symbols from the underlying input stream.
 * The symbols are decoded using the specified [base64] and the resulting bytes are returned.
 * Symbols are decoded in 4-symbol blocks.
 *
 * Closing the returned input stream will close the underlying input stream.
 */
public fun InputStream.wrapForDecoding(base64: Base64): InputStream {
    return DecodeInputStream(this, base64)
}

/**
 * Returns an output stream that encodes bytes using the specified [base64] and writes the result to this output stream.
 *
 * The byte data written to the returned output stream is encoded using the specified [base64]
 * and the resulting symbols are written to the underlying output stream.
 * Bytes are encoded in 3-byte blocks.
 *
 * The returned output stream should be promptly closed after use,
 * during which it will flush all possible leftover symbols to the underlying
 * output stream. Closing the returned output stream will close the underlying
 * output stream.
 */
public fun OutputStream.wrapForEncoding(base64: Base64): OutputStream {
    return EncodeOutputStream(this, base64)
}


// TODO: handle mime decode
private class DecodeInputStream(
    private val input: InputStream,
    private val base64: Base64
) : InputStream() {
    private var isClosed = false
    private var isEOF = false
    private val singleByteBuffer = ByteArray(1)

    private val symbolBuffer = ByteArray(1024)  // a multiple of symbolsPerGroup

    private val byteBuffer = ByteArray(1024)
    private var byteBufferStartIndex = 0
    private var byteBufferEndIndex = 0
    private val byteBufferLength: Int
        get() = byteBufferEndIndex - byteBufferStartIndex

    override fun read(): Int {
        return when (read(singleByteBuffer, 0, 1)) {
            -1 -> -1
            1 -> singleByteBuffer[0].toInt() and 0xFF
            else -> error("Unreachable")
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset + length > buffer.size) {
            throw IndexOutOfBoundsException("offset: $offset, length: $length, buffer size: ${buffer.size}")
        }
        if (isClosed) {
            throw IOException("The input stream is closed.")
        }
        if (isEOF) {
            return -1
        }
        if (length == 0) {
            return 0
        }

        if (byteBufferLength >= length) {
            byteBuffer.copyInto(
                buffer,
                destinationOffset = offset,
                startIndex = byteBufferStartIndex,
                endIndex = byteBufferStartIndex + length
            )
            byteBufferStartIndex += length
            shiftByteBufferToStart()

            return length
        }

        val bytesNeeded = length - byteBufferLength
        val groupsNeeded = (bytesNeeded + bytesPerGroup - 1) / bytesPerGroup
        var symbolsNeeded = groupsNeeded * symbolsPerGroup

        var bufferOffset = offset

        while (!isEOF && symbolsNeeded > 0) {
            var symbolBufferLength = 0
            val symbolsToRead = minOf(symbolBuffer.size, symbolsNeeded)
            while (!isEOF && symbolBufferLength < symbolsToRead) {
                when (val read = input.read(symbolBuffer, symbolBufferLength, symbolsToRead - symbolBufferLength)) {
                    0 ->
                        error("The wrapped input stream read 0 symbols")
                    -1 ->
                        isEOF = true
                    else ->
                        symbolBufferLength += read
                }
            }

            check(isEOF || symbolBufferLength == symbolsToRead)

            symbolsNeeded -= symbolBufferLength

            byteBufferEndIndex += base64.decodeIntoByteArray(
                symbolBuffer,
                byteBuffer,
                destinationOffset = byteBufferEndIndex,
                startIndex = 0,
                endIndex = symbolBufferLength
            )

            val bytesToCopy = minOf(byteBufferLength, length - (bufferOffset - offset))
            byteBuffer.copyInto(
                buffer,
                bufferOffset,
                startIndex = byteBufferStartIndex,
                endIndex = byteBufferStartIndex + bytesToCopy
            )

            bufferOffset += bytesToCopy
            byteBufferStartIndex += bytesToCopy

            shiftByteBufferToStart()
        }

        return if (bufferOffset - offset == 0 && isEOF) -1 else bufferOffset - offset
    }

    override fun available(): Int {
        if (isClosed) {
            return 0
        }
        return (input.available() * bitsPerSymbol) / bitsPerByte
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            input.close()
        }
    }

    // private functions

    private fun shiftByteBufferToStart() {
        if (byteBufferStartIndex == byteBufferEndIndex) {
            byteBufferStartIndex = 0
            byteBufferEndIndex = 0
        } else {
            // byte buffer should always have enough capacity to accommodate all symbols from symbol buffer
            val byteBufferCapacity = byteBuffer.size - byteBufferEndIndex
            val symbolBufferCapacity = symbolBuffer.size / symbolsPerGroup * bytesPerGroup
            if (symbolBufferCapacity > byteBufferCapacity) {
                byteBuffer.copyInto(byteBuffer, 0, byteBufferStartIndex, byteBufferEndIndex)
                byteBufferEndIndex -= byteBufferStartIndex
                byteBufferStartIndex = 0
            }
        }
    }
}

private class EncodeOutputStream(
    private val output: OutputStream,
    private val base64: Base64
) : OutputStream() {
    private var isClosed = false
    private val singleByteBuffer = ByteArray(1)

    private val symbolBuffer = ByteArray(1024)

    private val byteBuffer = ByteArray(bytesPerGroup)
    private var byteBufferLength = 0

    override fun write(b: Int) {
        singleByteBuffer[0] = b.toByte()
        write(singleByteBuffer, 0, 1)
    }

    override fun write(source: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset + length > source.size) {
            throw IndexOutOfBoundsException("offset: $offset, length: $length, source size: ${source.size}")
        }
        if (isClosed) {
            throw IOException("The output stream is closed.")
        }
        if (length == 0) {
            return
        }

        check(byteBufferLength < bytesPerGroup)

        var bytesWritten = 0

        if (byteBufferLength != 0) {
            val bytesToCopy = minOf(bytesPerGroup - byteBufferLength, length)
            source.copyInto(byteBuffer, destinationOffset = byteBufferLength, offset, offset + bytesToCopy)
            bytesWritten += bytesToCopy
            byteBufferLength += bytesToCopy

            if (byteBufferLength < bytesPerGroup) {
                return
            } else {
                check(byteBufferLength == bytesPerGroup)
                encodeByteBufferIntoOutput()
            }
        }

        while (bytesWritten + bytesPerGroup <= length) {
            val groupCapacity = symbolBuffer.size / symbolsPerGroup
            val groupsToEncode = minOf(groupCapacity, (length - bytesWritten) / bytesPerGroup)

            val symbolsEncoded = base64.encodeIntoByteArray(
                source,
                symbolBuffer,
                destinationOffset = 0,
                startIndex = offset + bytesWritten,
                endIndex = offset + bytesWritten + groupsToEncode * bytesPerGroup
            )
            check(symbolsEncoded == groupsToEncode * symbolsPerGroup)
            output.write(symbolBuffer, 0, symbolsEncoded)

            bytesWritten += groupsToEncode * bytesPerGroup
        }

        source.copyInto(byteBuffer, destinationOffset = 0, offset + bytesWritten, offset + length)
        byteBufferLength = length - bytesWritten
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true

            if (byteBufferLength != 0) {
                encodeByteBufferIntoOutput()
            }

            output.close()
        }
    }

    // private functions

    private fun encodeByteBufferIntoOutput() {
        val symbolsEncoded = base64.encodeIntoByteArray(
            byteBuffer,
            symbolBuffer,
            destinationOffset = 0,
            startIndex = 0,
            byteBufferLength
        )
        check(symbolsEncoded == symbolsPerGroup)
        output.write(symbolBuffer, 0, symbolsPerGroup)
        byteBufferLength = 0
    }
}