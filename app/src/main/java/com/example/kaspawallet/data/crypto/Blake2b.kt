package com.example.kaspawallet.data.crypto

/**
 * Pure Kotlin BLAKE2b implementation conforming to RFC 7693 and Kaspa consensus requirements.
 * Supports keyed hashing with domain separators such as "TransactionSigningHash" and "TransactionID".
 */
class Blake2b(
    key: ByteArray = ByteArray(0),
    private val digestSize: Int = 32
) {
    init {
        require(digestSize in 1..64) { "Digest size must be between 1 and 64 bytes" }
        require(key.size <= 64) { "Key size cannot exceed 64 bytes" }
    }

    private val h = LongArray(8)
    private val buf = ByteArray(128)
    private var bufLen = 0
    private var t0 = 0L
    private var t1 = 0L

    init {
        System.arraycopy(IV, 0, h, 0, 8)
        // Parameter block: digest length in byte 0, key length in byte 1, fanout 1 in byte 2, depth 1 in byte 3
        h[0] = h[0] xor (0x01010000L or ((key.size.toLong() and 0xFFL) shl 8) or (digestSize.toLong() and 0xFFL))

        if (key.isNotEmpty()) {
            val keyBlock = ByteArray(128)
            System.arraycopy(key, 0, keyBlock, 0, key.size)
            update(keyBlock, 0, 128)
        }
    }

    fun update(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        var pos = offset
        var remaining = length

        while (remaining > 0) {
            if (bufLen == 128) {
                incrementCounter(128)
                compress(buf, 0, false)
                bufLen = 0
            }
            val toCopy = minOf(remaining, 128 - bufLen)
            System.arraycopy(data, pos, buf, bufLen, toCopy)
            bufLen += toCopy
            pos += toCopy
            remaining -= toCopy
        }
    }

    fun update(b: Byte) {
        if (bufLen == 128) {
            incrementCounter(128)
            compress(buf, 0, false)
            bufLen = 0
        }
        buf[bufLen++] = b
    }

    fun finalize(): ByteArray {
        incrementCounter(bufLen)
        // Pad buffer with zeros up to 128 bytes
        for (i in bufLen until 128) {
            buf[i] = 0
        }
        compress(buf, 0, true)

        val out = ByteArray(digestSize)
        val temp = ByteArray(64)
        for (i in 0 until 8) {
            val word = h[i]
            val base = i * 8
            temp[base] = (word and 0xFFL).toByte()
            temp[base + 1] = ((word ushr 8) and 0xFFL).toByte()
            temp[base + 2] = ((word ushr 16) and 0xFFL).toByte()
            temp[base + 3] = ((word ushr 24) and 0xFFL).toByte()
            temp[base + 4] = ((word ushr 32) and 0xFFL).toByte()
            temp[base + 5] = ((word ushr 40) and 0xFFL).toByte()
            temp[base + 6] = ((word ushr 48) and 0xFFL).toByte()
            temp[base + 7] = ((word ushr 56) and 0xFFL).toByte()
        }
        System.arraycopy(temp, 0, out, 0, digestSize)
        return out
    }

    private fun incrementCounter(inc: Int) {
        val oldT0 = t0
        t0 += inc.toLong()
        if (java.lang.Long.compareUnsigned(t0, oldT0) < 0) {
            t1 += 1L
        }
    }

    private fun compress(block: ByteArray, offset: Int, isLast: Boolean) {
        val v = LongArray(16)
        System.arraycopy(h, 0, v, 0, 8)
        System.arraycopy(IV, 0, v, 8, 8)

        v[12] = v[12] xor t0
        v[13] = v[13] xor t1
        if (isLast) {
            v[14] = v[14] xor -1L
        }

        val m = LongArray(16)
        for (i in 0 until 16) {
            val idx = offset + i * 8
            m[i] = (block[idx].toLong() and 0xFFL) or
                    ((block[idx + 1].toLong() and 0xFFL) shl 8) or
                    ((block[idx + 2].toLong() and 0xFFL) shl 16) or
                    ((block[idx + 3].toLong() and 0xFFL) shl 24) or
                    ((block[idx + 4].toLong() and 0xFFL) shl 32) or
                    ((block[idx + 5].toLong() and 0xFFL) shl 40) or
                    ((block[idx + 6].toLong() and 0xFFL) shl 48) or
                    ((block[idx + 7].toLong() and 0xFFL) shl 56)
        }

        for (r in 0 until 12) {
            val sigmaRow = SIGMA[r]
            g(0, 4, 8, 12, m[sigmaRow[0]], m[sigmaRow[1]], v)
            g(1, 5, 9, 13, m[sigmaRow[2]], m[sigmaRow[3]], v)
            g(2, 6, 10, 14, m[sigmaRow[4]], m[sigmaRow[5]], v)
            g(3, 7, 11, 15, m[sigmaRow[6]], m[sigmaRow[7]], v)

            g(0, 5, 10, 15, m[sigmaRow[8]], m[sigmaRow[9]], v)
            g(1, 6, 11, 12, m[sigmaRow[10]], m[sigmaRow[11]], v)
            g(2, 7, 8, 13, m[sigmaRow[12]], m[sigmaRow[13]], v)
            g(3, 4, 9, 14, m[sigmaRow[14]], m[sigmaRow[15]], v)
        }

        for (i in 0 until 8) {
            h[i] = h[i] xor v[i] xor v[i + 8]
        }
    }

    private fun g(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long, v: LongArray) {
        v[a] = v[a] + v[b] + x
        v[d] = rotr64(v[d] xor v[a], 32)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 24)
        v[a] = v[a] + v[b] + y
        v[d] = rotr64(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 63)
    }

    private fun rotr64(w: Long, c: Int): Long = (w ushr c) or (w shl (64 - c))

    companion object {
        private val IV = longArrayOf(
            0x6a09e667f3bcc908L, -0x4498517a7b3558c5L /* 0xbb67ae8584caa73bL */,
            0x3c6ef372fe94f82bL, -0x5ab00ac5a0e2c90fL /* 0xa54ff53a5f1d36f1L */,
            0x510e527fade682d1L, -0x64fa9773d4c193e1L /* 0x9b05688c2b3e6c1fL */,
            0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
        )

        private val SIGMA = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
            intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
            intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
            intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
            intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
            intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
            intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
            intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
            intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3)
        )

        /**
         * Keyed Blake2b-256 for Kaspa Transaction Signing Hash
         */
        fun transactionSigningHash(): Blake2b {
            return Blake2b("TransactionSigningHash".toByteArray(Charsets.UTF_8), 32)
        }

        /**
         * Keyed Blake2b-256 for Kaspa Transaction ID
         */
        fun transactionIdHash(): Blake2b {
            return Blake2b("TransactionID".toByteArray(Charsets.UTF_8), 32)
        }
    }
}
