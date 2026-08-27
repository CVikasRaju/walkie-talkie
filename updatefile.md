# iTantra Project Progress & AI Handover State

**Project:** iTantra (ISRO SIH26173)  
**Description:** A fully offline, peer-to-peer multilingual walkie-talkie Android application using Jetpack Compose, Kotlin Coroutines/Flows, offline AI (VAD, STT, TTS), and P2P Bluetooth RFCOMM / Wi-Fi Direct.  
**Last Updated:** August 27, 2026

---

## 1. Executive Summary

This file serves as a complete handover document for any developer or AI assistant continuing work on the **iTantra** codebase. All foundational architecture, binary framing, streaming audio pipeline, Bluetooth store-and-forward transport, and UI/ViewModel layers have been implemented and verified.

---

## 2. Completed Phases & Implementations

### Phase 1: Protocol Validation, State Machine & UI Setup
- **Network Framing (iBFS-v1)**:
  - `ProtocolCodec.kt` (`app/src/main/java/com/itantra/network/ProtocolCodec.kt`): Full byte-aligned implementation of the 14-byte overhead framing protocol (Magic `0x49 0x54`, 4-bit nibbles for Version/Type/Priority/Language, uint32 Sequence ID, uint16 Payload length, extended GPS/Source-lang flags, and CRC-16-CCITT).
  - `ProtocolCodecTest.kt` (`app/src/test/java/com/itantra/network/ProtocolCodecTest.kt`): 8 comprehensive unit tests covering round-trips, emergency priority, GPS payload, translation relay, CRC corruption, and frame truncation. **All 8 tests pass cleanly.**
- **UI Language Prioritization**:
  - `MainActivity.kt` (`app/src/main/java/com/itantra/ui/MainActivity.kt`): Defaulted `selectedLanguage` to `Language.HINDI` with `Language.KANNADA` as the secondary quick-select chip. The remaining 8 Indian languages are accessible via a deprioritized dropdown submenu.

---

### Phase 2: Streaming Pipeline, ViewModel, Audio I/O & Bluetooth Transport
- **Streaming Pipeline Core**:
  - `TransceiverService.kt` (`app/src/main/java/com/itantra/core/TransceiverService.kt`): Restructured from a one-shot skeleton into a real-time streaming pipeline using `Channel<ShortArray>(capacity = Channel.BUFFERED)`.
  - Continuous 30ms PCM frames (480 samples @ 16kHz) flow from mic capture through `vadEngine.isSpeech()`.
  - Utterance boundary detection: 600ms of continuous silence (`SILENCE_THRESHOLD_FRAMES = 20`) triggers STT transcription, binary encoding, and transmission.
- **Audio Capture & Playback**:
  - `startMicCapture()`: Configured Android `AudioRecord` (16kHz, Mono, 16-bit PCM, buffer calculated dynamically) reading 480-sample chunks directly with blocking read backpressure.
  - `onFrameReceived()`: Implemented `AudioTrack` playback for inbound synthesized audio. Supports emergency override (`USAGE_ALARM`, `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) when `packet.priority == Priority.EMERGENCY`.
- **Bluetooth Transport & Message Queue**:
  - `BluetoothTransport.kt` (`app/src/main/java/com/itantra/network/BluetoothTransport.kt`): Implemented length-prefixed binary framing over RFCOMM sockets with exponential backoff reconnect logic (`1s -> 2s -> 4s -> ... -> 16s`).
  - `MessageQueue.kt` (`app/src/main/java/com/itantra/network/MessageQueue.kt`): Store-and-forward queue. Outbound packets queue if peer is offline and are automatically drained via `messageQueue.drainAsFrames()` upon connection. Sequence ID deduplication for mesh relay.
- **ViewModel & UI Reactive Wiring**:
  - `TransceiverViewModel.kt` (`app/src/main/java/com/itantra/ui/TransceiverViewModel.kt`): Manages `ServiceConnection` lifecycle with `TransceiverService` and exposes `StateFlow<TransceiverState>` and `StateFlow<Language>`.
  - `MainActivity.kt` (`app/src/main/java/com/itantra/ui/MainActivity.kt`): Dynamically binds to `TransceiverViewModel`. PTT button visually updates based on state:
    - `TransceiverOff`: "OFF"
    - `Idle`: "HOLD\nTO TALK" (Primary)
    - `Recording`: "RECORDING\nRELEASE TO SEND" (Red / Error)
    - `Processing`: "PROCESSING\n..." (Tertiary)
    - `Transmitting`: "SENDING\n..." (Secondary)
    - `ReceivingPlayback`: "RECEIVING\n..." (Alert / Primary Container)
    - `ConnectionLost`: "OFFLINE\nQUEUED"
  - Fixed compiler warnings (replaced deprecated `Divider` with `HorizontalDivider`, removed unused variables).

---

## 3. Current Repository Architecture & File Mapping

```
iTantra/
├── app/
│   ├── build.gradle.kts                        # App build config (Kotlin 1.9, Compose BOM 2024.06.00)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml             # Foreground service, Bluetooth, Audio & Location permissions
│       │   └── java/com/itantra/
│       │       ├── core/
│       │       │   ├── TransceiverService.kt   # Foreground service owning mic, VAD, STT/TTS, Bluetooth, StateFlow
│       │       │   └── TransceiverState.kt     # Sealed class state machine + transition validator
│       │       ├── ml/
│       │       │   └── Engines.kt              # VadEngine, SttEngine, TtsEngine interfaces & Mock implementations
│       │       ├── network/
│       │       │   ├── BluetoothTransport.kt   # RFCOMM socket transport with auto-reconnect backoff
│       │       │   ├── MessageQueue.kt         # Store-and-forward buffer & sequence dedup
│       │       │   └── ProtocolCodec.kt        # iBFS-v1 binary packet encoder/decoder & CRC16
│       │       └── ui/
│       │           ├── MainActivity.kt         # Jetpack Compose UI (PTT Hold button, Language chips)
│       │           └── TransceiverViewModel.kt # ViewModel managing ServiceConnection & StateFlows
│       └── test/java/com/itantra/network/
│           └── ProtocolCodecTest.kt            # 8 JUnit unit tests for network protocol
├── docs/
│   ├── ARCHITECTURE.md                         # Pipeline, single-language resident policy, state machine
│   └── NETWORK_PROTOCOL.md                     # iBFS-v1 binary packet layout spec
└── updatefile.md                               # This handover file
```

---

## 4. Verification & Build Status

- **Build Tool**: Gradle 8.14 (Wrapper configured with local cache)
- **JDK Requirement**: Java 17 (e.g. Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr`)
- **Compilation**: Clean (`./gradlew compileDebugKotlin` passes with 0 errors and 0 warnings)
- **Unit Tests**: `./gradlew testDebugUnitTest` runs 8 tests in `ProtocolCodecTest`:
  - `basic round trip preserves all fields`: PASS
  - `emergency priority round trips correctly`: PASS
  - `extended payload with GPS round trips`: PASS
  - `extended payload with source language for translation relay round trips`: PASS
  - `corrupted magic bytes are rejected`: PASS
  - `corrupted payload byte fails CRC check`: PASS
  - `truncated frame is rejected`: PASS
  - `frame overhead matches spec — 14 bytes for empty text`: PASS

---

## 5. Next Planned Work: Phase 3 (Sherpa-ONNX ML Integration)

When continuing from here, the next developer / AI model should execute **Phase 3**:

1. **Add `sherpa-onnx` Dependency**:
   - Add the official `com.k2fsa.sherpa.onnx:sherpa-onnx:<version>` AAR/dependency in `app/build.gradle.kts`.
2. **Implement Real Neural Engines in `ml/Engines.kt`**:
   - Implement `SherpaSttEngine` implementing `SttEngine`:
     - Load offline ASR models from assets (`assets/models/stt/<lang>/` with tokens, encoder, decoder).
     - Transcribe 16kHz PCM audio buffers.
     - Release native pointers cleanly upon language switch.
   - Implement `SherpaTtsEngine` implementing `TtsEngine`:
     - Load offline VITS TTS models from assets (`assets/models/tts/<lang>/`).
     - Synthesize text to 16kHz mono PCM `ShortArray`.
     - Implement `release()` for native C++ memory cleanup.
3. **Enforce Single-Language Resident Policy in `TransceiverService.kt`**:
   - Swap `MockSttEngine` / `MockTtsEngine` references to `SherpaSttEngine` / `SherpaTtsEngine`.
   - In `switchLanguage()`, verify the old language's native model is released *before* mapping the new language to respect the RAM footprint budget.
   - Add fallback logic: if asset files are absent, log warning and degrade gracefully without crashing.
4. **Validation**:
   - Re-run `./gradlew compileDebugKotlin` and `./gradlew testDebugUnitTest`.

---

## 6. How to Run & Test

To compile and run tests on Windows PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```
