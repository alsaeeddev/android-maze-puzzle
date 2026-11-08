package com.example.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates tiny retro sound effects at runtime and plays them through a [SoundPool].
 *
 * Fixes vs. the previous version:
 *  - A sound is only played once SoundPool reports that the sample finished loading
 *    (previously `isLoaded` was set right after `load()`, which is asynchronous, so the
 *    first effects were silently dropped).
 *  - Tones are phase-continuous, so the hint / win arpeggios no longer click on note changes.
 *  - Cache files are versioned (`_v2`) so old click-y files from earlier installs are replaced.
 *  - Shared state is @Volatile / concurrent, and the init scope is cancelled in [release].
 */
class SoundEffectManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var soundPool: SoundPool? = null

    private val loadedSamples: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    @Volatile private var moveSoundId = 0
    @Volatile private var bumpSoundId = 0
    @Volatile private var hintSoundId = 0
    @Volatile private var winSoundId = 0

    @Volatile
    var isSoundEnabled: Boolean = true

    init {
        scope.launch { initSounds() }
    }

    private fun initSounds() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSamples.add(sampleId)
        }

        if (!scope.isActive) {
            // release() was called while we were still starting up
            pool.release()
            return
        }
        soundPool = pool

        try {
            val cacheDir = context.cacheDir

            // Crisp 700Hz -> 350Hz retro chirp
            val moveFile = cachedWav(cacheDir, "as_move_v2.wav", 22050, 0.05f) { t ->
                Tone(freq = 700f - (t / 0.05f) * 350f, amp = 1f - t / 0.05f)
            }

            // Low 140Hz -> 60Hz wall thud
            val bumpFile = cachedWav(cacheDir, "as_bump_v2.wav", 22050, 0.08f) { t ->
                Tone(freq = 140f - (t / 0.08f) * 80f, amp = 1f - t / 0.08f)
            }

            // Dual bell chime C5 (523Hz) -> G5 (784Hz)
            val hintFile = cachedWav(cacheDir, "as_hint_v2.wav", 22050, 0.22f) { t ->
                val first = t < 0.11f
                val phase = if (first) t / 0.11f else (t - 0.11f) / 0.11f
                Tone(freq = if (first) 523f else 784f, amp = 1f - phase)
            }

            // Victory arpeggio: C5, E5, G5, C6
            val winFile = cachedWav(cacheDir, "as_win_v2.wav", 22050, 0.48f) { t ->
                val step = (t / 0.12f).toInt().coerceIn(0, 3)
                val freq = when (step) {
                    0 -> 523f
                    1 -> 659f
                    2 -> 784f
                    else -> 1046f
                }
                val phase = (t % 0.12f) / 0.12f
                Tone(freq = freq, amp = 1f - phase * 0.5f)
            }

            moveSoundId = pool.load(moveFile.absolutePath, 1)
            bumpSoundId = pool.load(bumpFile.absolutePath, 1)
            hintSoundId = pool.load(hintFile.absolutePath, 1)
            winSoundId = pool.load(winFile.absolutePath, 1)
        } catch (_: Exception) {
            // Graceful fallback: the game simply stays silent if the filesystem fails
        }
    }

    fun playMove() = play(moveSoundId, 0.45f)

    fun playBump() = play(bumpSoundId, 0.55f)

    fun playHint() = play(hintSoundId, 0.65f)

    fun playWin() = play(winSoundId, 0.85f)

    private fun play(soundId: Int, volume: Float) {
        if (!isSoundEnabled || soundId == 0 || soundId !in loadedSamples) return
        soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        scope.cancel()
        soundPool?.release()
        soundPool = null
        loadedSamples.clear()
    }

    // ---------------------------------------------------------------------------------------
    // WAV generation
    // ---------------------------------------------------------------------------------------

    private class Tone(val freq: Float, val amp: Float)

    private fun cachedWav(
        dir: File,
        name: String,
        sampleRate: Int,
        durationSec: Float,
        tone: (Float) -> Tone
    ): File {
        val file = File(dir, name)
        if (!file.exists() || file.length() == 0L) {
            file.writeBytes(generatePcmWav(sampleRate, durationSec, tone))
        }
        return file
    }

    private fun generatePcmWav(
        sampleRate: Int,
        durationSec: Float,
        tone: (Float) -> Tone
    ): ByteArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val dataSize = totalSamples * 2 // 16-bit mono = 2 bytes per sample
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)               // Subchunk1Size (16 for PCM)
        buffer.putShort(1)              // AudioFormat (1 for PCM)
        buffer.putShort(1)              // NumChannels (mono)
        buffer.putInt(sampleRate)       // SampleRate
        buffer.putInt(sampleRate * 2)   // ByteRate
        buffer.putShort(2)              // BlockAlign
        buffer.putShort(16)             // BitsPerSample

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Short fade in/out (~3ms) removes the pop at the very start and end of every clip
        val fadeSamples = (sampleRate * 0.003f).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            val current = tone(t)
            // Integrate the phase instead of using sin(2*pi*f*t): no discontinuity when f changes
            phase += 2.0 * PI * current.freq / sampleRate
            val fade = minOf(1f, i / fadeSamples.toFloat(), (totalSamples - 1 - i) / fadeSamples.toFloat())
            val value = (sin(phase) * current.amp * fade).toFloat().coerceIn(-1f, 1f)
            buffer.putShort((value * 32767).toInt().toShort())
        }

        return buffer.array()
    }
}
