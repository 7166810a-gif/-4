package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FftProcessor(val size: Int) {
    private val bits = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val bitReverse = IntArray(size)
    val hannWindow = FloatArray(size)

    init {
        require(size > 0 && (size and (size - 1)) == 0) { "FFT size must be power of two" }
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        for (i in 0 until size) {
            var rev = 0
            var x = i
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x shr 1
            }
            bitReverse[i] = rev
            hannWindow[i] = (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
        }
    }

    fun fft(real: FloatArray, imag: FloatArray) {
        for (i in 0 until size) {
            val j = bitReverse[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var halfSize = 1
        while (halfSize < size) {
            val step = size / (halfSize shl 1)
            var i = 0
            while (i < size) {
                var k = 0
                for (j in i until i + halfSize) {
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val m = j + halfSize
                    val tr = real[m] * c - imag[m] * s
                    val ti = real[m] * s + imag[m] * c
                    real[m] = real[j] - tr
                    imag[m] = imag[j] - ti
                    real[j] += tr
                    imag[j] += ti
                    k += step
                }
                i += halfSize shl 1
            }
            halfSize = halfSize shl 1
        }
    }

    fun ifft(real: FloatArray, imag: FloatArray) {
        for (i in 0 until size) {
            imag[i] = -imag[i]
        }
        fft(real, imag)
        val invN = 1f / size
        for (i in 0 until size) {
            real[i] *= invN
            imag[i] = -imag[i] * invN
        }
    }
}
