#!/usr/bin/env python3
"""STT local para el mock: whisper.cpp sobre los chunks que manda la app.

El backend real delega la transcripción en el repo `../sign-ai-stt`
(faster-whisper). Para no depender de ese contenedor, el mock usa el
`whisper-cli` y los modelos GGML que ya están en
`~/.cache/opencode-voice/`, sin descargar nada.

La app manda PCM16LE mono 16 kHz en chunks de 30 ms, igual que har��a con el
STT real, así que este módulo es el equivalente local de ese servicio.
"""

from __future__ import annotations

import array
import io
import os
import pathlib
import shutil
import subprocess
import tempfile
import wave

CACHE = pathlib.Path(os.getenv("OPENCODE_VOICE_CACHE", pathlib.Path.home() / ".cache/opencode-voice"))

WHISPER_CLI = CACHE / "engines/whisper.cpp/linux-x64/whisper-cli"
WHISPER_MODEL = CACHE / "models/ggml-large-v3-turbo-q5_0.bin"
FALLBACK_MODELS = [
    CACHE / "models/ggml-large-v3-turbo.bin",
    CACHE / "models/ggml-small_q5_1.bin",
    CACHE / "models/ggml-small-q5_1.bin",
    CACHE / "models/ggml-large-v3-turbo-q5_0.bin",
]

SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2

# Silencio: RMS por debajo de esto cuenta como pausa. Es el mismo criterio
# simplest que usa el STT del backend (MIN_RMS=0.008), sin el VAD.
SILENCE_RMS = 0.008
SILENCE_FRAMES = 50          # 50 * 30 ms = 1500 ms, el valor del SDD
MAX_FRAMES = 267             # ~8 s, el MAX_BUFFER_MS del STT
MIN_SPEECH_FRAMES = 10       # ~300 ms: por debajo es ruido, no una frase


def available() -> bool:
    return WHISPER_CLI.is_file() and any(m.is_file() for m in FALLBACK_MODELS)


def model_path() -> pathlib.Path | None:
    if WHISPER_MODEL.is_file():
        return WHISPER_MODEL
    for candidate in FALLBACK_MODELS:
        if candidate.is_file():
            return candidate
    return None


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
    """Envuelve PCM16LE mono en un WAV, que es lo que whisper-cli lee."""
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(BYTES_PER_SAMPLE)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(pcm)
    return buffer.getvalue()


def transcribe(pcm: bytes, language: str = "es") -> str:
    """Transcribe PCM16LE 16 kHz. Returns "" if there is nothing to hear."""
    if not pcm or not available():
        return ""
    model = model_path()
    if model is None:
        return ""

    with tempfile.TemporaryDirectory(prefix="signai-stt-") as tmp:
        wav_path = pathlib.Path(tmp) / "audio.wav"
        wav_path.write_bytes(pcm_to_wav(pcm))
        try:
            completed = subprocess.run(
                [
                    str(WHISPER_CLI),
                    "-m", str(model),
                    "-l", language,
                    "-t", "4",
                    "-nt",           # sin timestamps
                    "-np",           # sin progreso
                    str(wav_path),
                ],
                capture_output=True,
                text=True,
                timeout=120,
            )
        except subprocess.TimeoutExpired:
            return ""
    if completed.returncode != 0:
        print(f"[stt] whisper-cli salió con {completed.returncode}: {completed.stderr[:200]}")
        return ""
    return completed.stdout.strip()


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

    def pcm(self) -> bytes:
        return b"".join(self.chunks)

    def reset(self) -> None:
        self.chunks.clear()
        self.silence = 0
        self.voice = 0


__all__ = [
    "Utterance",
    "available",
    "model_path",
    "pcm_to_wav",
    "transcribe",
    "SILENCE_RMS",
]
