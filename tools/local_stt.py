#!/usr/bin/env python3
"""STT local para el mock: transcripción del audio que manda la app.

El backend real delega la transcripción en el repo `../sign-ai-stt`
(faster-whisper). Para no depender de ese contenedor, el mock usa los motores
y los modelos que ya están en `~/.cache/opencode-voice/`, sin descargar nada.

La app manda PCM16LE mono 16 kHz en chunks de 30 ms, igual que haría con el
STT real, así que este módulo es el equivalente local de ese servicio.

## Qué motor se usa

Por defecto **Qwen3-ASR 0.6B** con el binario `opencode-voice-transcribe`.
Medido sobre una frase de 2 s en esta máquina:

| motor | modelo | tiempo | español |
|---|---|---|---|
| `transcribe-cpp` | `Qwen3-ASR-0.6B-Q4_K_M` | **~0.9 s** | ✔ |
| `whisper.cpp` | `ggml-large-v3-turbo-q5_0` | ~5.4 s | ✔ |
| `transcribe-cpp` | `parakeet-tdt-0.6b-v3` | ~0.65 s | ✘ (responde en cirílico) |

Ojo con Parakeet: es el más rápido de los tres pero el modelo que hay acá es el
ruso, y para español contesta basura. No activarlo por defecto.

Si el binario de `transcribe-cpp` o el modelo no están, se cae a `whisper.cpp`
con el modelo grande, que es más lento pero más exacto. Se puede forzar con
variables de entorno:

    SIGNALL_STT_ENGINE=transcribe-cpp|whisper
    SIGNALL_STT_MODEL=/ruta/al/modelo
"""

from __future__ import annotations

import array
import io
import os
import pathlib
import subprocess
import tempfile
import wave

CACHE = pathlib.Path(os.getenv("OPENCODE_VOICE_CACHE", pathlib.Path.home() / ".cache/opencode-voice"))

# ── Motor 1: transcribe-cpp (rápido, multilingüe) ────────────────────────────
TRANSCRIBE_CLI = CACHE / "engines/transcribe-cpp/linux-x64/opencode-voice-transcribe"
TRANSCRIBE_MODEL = CACHE / "models/Qwen3-ASR-0.6B-Q4_K_M.gguf"

# ── Motor 2: whisper.cpp (lento pero más exacto) ─────────────────────────────
WHISPER_CLI = CACHE / "engines/whisper.cpp/linux-x64/whisper-cli"
WHISPER_MODELS = [
    CACHE / "models/ggml-large-v3-turbo-q5_0.bin",
    CACHE / "models/ggml-large-v3-turbo.bin",
    CACHE / "models/ggml-small_q5_1.bin",
]

SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2

ENGINE = os.getenv("SIGNALL_STT_ENGINE", "")

# Silencio: RMS por debajo de esto cuenta como pausa. Es el mismo criterio
# simplest que usa el STT del backend (MIN_RMS=0.008), sin el VAD.
SILENCE_RMS = 0.008
SILENCE_FRAMES = 50          # 50 * 30 ms = 1500 ms, el valor del SDD
MAX_FRAMES = 267             # ~8 s, el MAX_BUFFER_MS del STT
MIN_SPEECH_FRAMES = 10       # ~300 ms: por debajo es ruido, no una frase


def _engine() -> str:
    if ENGINE:
        return ENGINE
    if TRANSCRIBE_CLI.is_file() and TRANSCRIBE_MODEL.is_file():
        return "transcribe-cpp"
    if WHISPER_CLI.is_file() and any(m.is_file() for m in WHISPER_MODELS):
        return "whisper"
    return ""


def available() -> bool:
    return bool(_engine())


def engine_name() -> str:
    engine = _engine()
    if engine == "transcribe-cpp":
        return f"Qwen3-ASR ({TRANSCRIBE_MODEL.name}) vía transcribe-cpp"
    if engine == "whisper":
        model = next((m for m in WHISPER_MODELS if m.is_file()), None)
        return f"whisper.cpp ({model.name if model else '?'})"
    return "ninguno (no hay motor en %s)" % CACHE


def _rms(chunk: bytes) -> float:
    samples = array.array("h")
    usable = len(chunk) - (len(chunk) % BYTES_PER_SAMPLE)
    if usable <= 0:
        return 0.0
    samples.frombytes(chunk[:usable])
    if not samples:
        return 0.0
    total = sum(s * s for s in samples)
    return (total / len(samples)) ** 0.5 / 32768.0


def pcm_to_wav(pcm: bytes) -> bytes:
    """Envuelve PCM16LE mono en un WAV, que es lo que los motores leen."""
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(BYTES_PER_SAMPLE)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(pcm)
    return buffer.getvalue()


def _run(command: list[str]) -> str:
    try:
        completed = subprocess.run(
            command, capture_output=True, text=True, timeout=120
        )
    except subprocess.TimeoutExpired:
        print("[stt] el motor tardó más de 120 s, se aborta")
        return ""
    if completed.returncode != 0:
        print(f"[stt] el motor salió con {completed.returncode}: {completed.stderr[:200]}")
        return ""
    return completed.stdout.strip()


def transcribe(pcm: bytes, language: str = "es") -> str:
    """Transcribe PCM16LE 16 kHz. Devuelve "" si no hay nada que entender."""
    engine = _engine()
    if not pcm or not engine:
        return ""

    with tempfile.TemporaryDirectory(prefix="signall-stt-") as tmp:
        wav_path = pathlib.Path(tmp) / "audio.wav"
        wav_path.write_bytes(pcm_to_wav(pcm))

        if engine == "transcribe-cpp":
            model = pathlib.Path(os.getenv("SIGNALL_STT_MODEL", TRANSCRIBE_MODEL))
            return _run([
                str(TRANSCRIBE_CLI),
                "--model", str(model),
                "--file", str(wav_path),
                "--language", language,
            ])

        model = pathlib.Path(os.getenv("SIGNALL_STT_MODEL", "")) if os.getenv("SIGNALL_STT_MODEL") else next(
            (m for m in WHISPER_MODELS if m.is_file()), None
        )
        if model is None:
            return ""
        return _run([
            str(WHISPER_CLI),
            "-m", str(model),
            "-l", language,
            "-t", "4",
            "-nt",      # sin timestamps
            "-np",      # sin progreso
            str(wav_path),
        ])


class Utterance:
    """Acumula los chunks de una frase y decide cuándo termina.

    Igual que el STT del backend: se cierra por silencio (1500 ms) o por
    buffer lleno (~8 s), lo que llegue primero.
    """

    def __init__(self) -> None:
        self.chunks: list[bytes] = []
        self.silence = 0
        self.voice = 0

    def add(self, chunk: bytes) -> None:
        self.chunks.append(chunk)
        if _rms(chunk) >= SILENCE_RMS:
            self.voice += 1
            self.silence = 0
        else:
            self.silence += 1

    @property
    def should_finalize(self) -> bool:
        if self.voice >= MIN_SPEECH_FRAMES and self.silence >= SILENCE_FRAMES:
            return True
        return len(self.chunks) >= MAX_FRAMES

    @property
    def has_voice(self) -> bool:
        return self.voice > 0

    def pcm(self) -> bytes:
        return b"".join(self.chunks)

    def reset(self) -> None:
        self.chunks.clear()
        self.silence = 0
        self.voice = 0


__all__ = [
    "Utterance",
    "available",
    "engine_name",
    "pcm_to_wav",
    "transcribe",
    "SILENCE_RMS",
]
