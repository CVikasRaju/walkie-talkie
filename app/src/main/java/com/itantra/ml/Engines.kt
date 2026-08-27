package com.itantra.ml

import com.itantra.network.Language

/**
 * Abstraction layer for the three neural engines described in docs/ML_PIPELINE.md.
 * The Mock* implementations below let the rest of the app (UI, networking, state
 * machine) be built and tested WITHOUT real model weights present — swap them for
 * Sherpa*Engine implementations once you've downloaded and quantized real models
 * per docs/ML_PIPELINE.md and scripts/fetch_models.py.
 *
 * DO NOT ship the Mock engines in a release build — they exist for development only.
 */

interface VadEngine {
    /** Feed a 30ms PCM frame (480 samples @ 16kHz). Returns true if speech detected. */
    fun isSpeech(pcmFrame: ShortArray): Boolean

    /** Called when 600ms+ of continuous silence follows speech — an utterance boundary. */
    fun onUtteranceComplete(callback: (ShortArray) -> Unit)
}

interface SttEngine {
    /** Must be called before transcribe() when the active language changes. */
    fun loadLanguage(language: Language)

    fun unloadCurrentLanguage()

    /**
     * Transcribes a chunk of 16kHz mono PCM audio to text.
     * Returns null if confidence is too low (see docs/ADDITIONAL_FEATURES.md #6,
     * adaptive fallback to raw audio — implement that branch at the call site).
     */
    fun transcribe(pcmAudio: ShortArray): SttResult?
}

data class SttResult(
    val text: String,
    val confidence: Float, // 0.0 - 1.0
)

interface TtsEngine {
    fun loadLanguage(language: Language)

    fun unloadCurrentLanguage()

    /** Synthesizes text to 16-bit PCM audio for playback. */
    fun synthesize(text: String): ShortArray
}

// ---------------------------------------------------------------------------
// Mock implementations — no real inference, deterministic stand-ins so the
// app builds and the UI/network flow can be exercised on day one.
// ---------------------------------------------------------------------------

class MockVadEngine : VadEngine {
    private var onComplete: ((ShortArray) -> Unit)? = null
    private val buffer = mutableListOf<Short>()
    private var silentFrameCount = 0
    private val silenceThresholdFrames = 20 // ~600ms at 30ms/frame

    override fun isSpeech(pcmFrame: ShortArray): Boolean {
        val amplitude = pcmFrame.map { kotlin.math.abs(it.toInt()) }.average()
        val speech = amplitude > 500 // crude energy threshold — NOT a real VAD
        if (speech) {
            buffer.addAll(pcmFrame.toList())
            silentFrameCount = 0
        } else {
            silentFrameCount++
            if (silentFrameCount >= silenceThresholdFrames && buffer.isNotEmpty()) {
                onComplete?.invoke(buffer.toShortArray())
                buffer.clear()
            }
        }
        return speech
    }

    override fun onUtteranceComplete(callback: (ShortArray) -> Unit) {
        onComplete = callback
    }
}

class MockSttEngine : SttEngine {
    private var currentLanguage: Language? = null

    override fun loadLanguage(language: Language) {
        currentLanguage = language
    }

    override fun unloadCurrentLanguage() {
        currentLanguage = null
    }

    override fun transcribe(pcmAudio: ShortArray): SttResult {
        // Deterministic placeholder so UI/network flow is testable end-to-end
        // without a real model. Replace with a real Sherpa-ONNX STT call.
        val lang = currentLanguage?.code ?: "unknown"
        return SttResult(text = "[mock transcription — $lang, ${pcmAudio.size} samples]", confidence = 0.99f)
    }
}

class MockTtsEngine : TtsEngine {
    private var currentLanguage: Language? = null

    override fun loadLanguage(language: Language) {
        currentLanguage = language
    }

    override fun unloadCurrentLanguage() {
        currentLanguage = null
    }

    override fun synthesize(text: String): ShortArray {
        // Returns 0.5s of silence as a placeholder PCM buffer (16kHz mono).
        // Replace with a real Sherpa-ONNX VITS TTS call.
        return ShortArray(8000)
    }
}

/*
 * ---------------------------------------------------------------------------
 * INTEGRATION POINT — real models
 * ---------------------------------------------------------------------------
 * Once you have run scripts/fetch_models.py and scripts/quantize_models.py
 * (see docs/ML_PIPELINE.md) and placed the resulting .onnx + tokens.txt files
 * under app/src/main/assets/models/{stt,tts}/<lang>/, implement:
 *
 *   class SherpaSttEngine(context: Context) : SttEngine { ... }
 *   class SherpaTtsEngine(context: Context) : TtsEngine { ... }
 *
 * using the sherpa-onnx Android AAR (see app/build.gradle.kts TODO) and its
 * Kotlin bindings. Follow the "Single-Language Resident Policy" in
 * docs/ARCHITECTURE.md §2.2 exactly — loadLanguage() must release the
 * previous language's native pointers before mapping the new one, or you
 * will blow the RAM footprint budget in docs/README.md §5.
 * ---------------------------------------------------------------------------
 */
