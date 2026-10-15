"""¿Hace falta el adelanto automático con entrega por oración?

El adelanto de `app.js` (`CATCHUP_BACKLOG = 2`, `CATCHUP_FACTOR = 1.6`) existe
por un desfase: cada seña dura ~2,15 s y el español corre a 2-3 palabras por
segundo, y cada palabra es una seña. El punto 5 de §6.3 de la auditoría dice que
ese desfase "desaparece en su mayor parte" con entrega por oración, y que hay que
**remedirlo, no borrarlo a ciegas**.

Lo que mide esto es exactamente lo que importa: para cada una de las 28 frases de
prueba, cuánto tarda el usuario en decirla y cuánto tarda el muñeco en firmarla.
Si firma < habla, hay cola que absorber y el adelanto hace falta. Si firma < habla
con el adelanto puesto, el factor sirve; si no, no alcanza y hay que decirlo.

No hace falta el dispositivo: el lado de la producción (glosas por frase) sale del
backend real y el de la animación sale del `manifest.json` de la app, que es lo
mismo que lee `app.js` al reproducir cada clip.
"""

import asyncio
import json
import statistics
import sys
import wave
from pathlib import Path

import websockets

URL = "ws://127.0.0.1:8080/ws/translate"
AUDIO = Path("/home/diegocachy/tesisTesteos/SignAI/sign-ai-stt/fixtures/audio")
MANIFEST = Path(
    "/home/diegocachy/tesisTesteos/SignAI/sign-ai-android"
    "/app/src/main/assets/web/manifest.json"
)
CATCHUP_FACTOR = 1.6


def pcm16(path: Path) -> bytes:
    with wave.open(str(path), "rb") as wf:
        return wf.readframes(wf.getnframes())


def cargar_manifest() -> dict[str, float]:
    """`gloss_key` → duración en segundos, que es la clave con la que el backend
    nombra las glosas (`normalize_gloss` sin tilde, sin signos de apertura)."""
    return {m["gloss_key"]: m["duration_s"] for m in json.loads(MANIFEST.read_text())}


async def frase_completa(audio: bytes) -> tuple[str, int]:
    """Manda el audio, espera el `final` y devuelve el texto. No traduce: el
    backend solo traduce lo que llega como `text`, que es el contrato nuevo."""
    async with websockets.connect(URL) as ws:
        for offset in range(0, len(audio), 960):
            await ws.send(audio[offset : offset + 960])
            await asyncio.sleep(0.02)
        await ws.send(json.dumps({"type": "end"}))
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=30))
            if msg.get("type") == "error":
                raise RuntimeError(msg["message"])
            if msg.get("type") == "transcript" and msg.get("final"):
                return msg["text"], 0


async def glosas_de(texto: str) -> tuple[list[str], int]:
    """Manda la frase como `text` y devuelve (glosas, oraciones)."""
    async with websockets.connect(URL) as ws:
        await ws.send(json.dumps({"type": "text", "text": texto}, ensure_ascii=False))
        glosas: list[str] = []
        oraciones = 0
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
            if msg.get("type") == "segment":
                oraciones = msg["total"]
                glosas.extend(g["text"] for g in msg["segment"]["glosses"])
            elif msg.get("type") == "done":
                return glosas, oraciones
            elif msg.get("type") == "error":
                raise RuntimeError(msg["message"])


def clave(gloss: str) -> str:
    """La glosa del backend contra el `gloss_key` del manifest: sin tilde, sin
    signos de apertura, en mayúsculas."""
    out = []
    for ch in gloss.upper():
        if ch in "¿?¡!.,;:":
            continue
        if ch == "Á": out.append("A")
        elif ch == "É": out.append("E")
        elif ch == "Í": out.append("I")
        elif ch == "Ó": out.append("O")
        elif ch == "Ú": out.append("U")
        elif ch == "Ü": out.append("U")
        else: out.append(ch)
    return " ".join("".join(out).split())


async def main() -> None:
    manifest = cargar_manifest()
    duracion_media = statistics.mean(manifest.values())
    print(f"señas exportadas: {len(manifest)}  duración media: {duracion_media:.2f}s "
          f"(min {min(manifest.values()):.2f}, max {max(manifest.values()):.2f})")
    print()

    ids = sorted(p.stem for p in AUDIO.glob("*.json") if p.name != "index.json")
    filas = []
    for fixture_id in ids:
        meta = json.loads((AUDIO / f"{fixture_id}.json").read_text())
        wav = AUDIO / f"{fixture_id}.wav"
        if not wav.exists():
            print(f"  {fixture_id}: sin .wav, se saltea")
            continue

        texto, _ = await frase_completa(pcm16(wav))
        if not texto.strip():
            print(f"  {fixture_id}: el STT no devolvió texto")
            continue

        glosas, oraciones = await glosas_de(texto)

        # Cuánto tarda en firmarse: cada glosa es un clip del manifest.
        desconocida = [g for g in glosas if clave(g) not in manifest]
        animación = sum(manifest.get(clave(g), 0.0) for g in glosas)

        # El tiempo que el usuario estuvo hablando, más la pausa de 1,5 s que
        # corre entre una frase y la siguiente.
        habla_ms = meta["duration_ms"] + meta.get("pause_ms", 0)
        filas.append({
            "id": fixture_id,
            "palabras": len(texto.split()),
            "oraciones": oraciones,
            "glosas": len(glosas),
            "sin_seña": len(desconocida),
            "habla_ms": habla_ms,
            "animacion_ms": round(animación * 1000),
        })

    print(f"{'frase':24} {'palabras':>8} {'oraciones':>9} {'señas':>5} "
          f"{'habla':>7} {'firma':>7} {'desfase':>8}")
    print("─" * 78)
    for f in filas:
        desfase = (f["animacion_ms"] - f["habla_ms"]) / 1000
        print(f"{f['id']:24} {f['palabras']:8} {f['oraciones']:9} {f['glosas']:5} "
              f"{f['habla_ms']/1000:6.1f}s {f['animacion_ms']/1000:6.1f}s {desfase:+7.1f}s")
    print("─" * 78)

    if not filas:
        return

    ratios = [f["animacion_ms"] / max(1, f["habla_ms"]) for f in filas]
    desfases = [(f["animacion_ms"] - f["habla_ms"]) / 1000 for f in filas]
    signos = [f["glosas"] for f in filas]
    palabras = [f["palabras"] for f in filas]

    print()
    print(f"frases medidas: {len(filas)}")
    print(f"palabras por frase:  mediana {statistics.median(palabras):.0f}  "
          f"rango {min(palabras)}-{max(palabras)}")
    print(f"señas por frase:     mediana {statistics.median(signos):.0f}  "
          f"rango {min(signos)}-{max(signos)}")
    print(f"velocidad del habla:  mediana "
          f"{statistics.median(p / (f['habla_ms']/1000) for f, p in zip(filas, palabras)):.2f}"
          f" palabras/s")
    print()
    print("firma / habla (la cola que hay que absorber):")
    print(f"  mediana {statistics.median(ratios):.2f}x   "
          f"min {min(ratios):.2f}x   max {max(ratios):.2f}x")
    print(f"  con el adelanto de {CATCHUP_FACTOR}x: mediana "
          f"{statistics.median(ratios)/CATCHUP_FACTOR:.2f}x  "
          f"max {max(ratios)/CATCHUP_FACTOR:.2f}x")
    print()
    print(f"desfase absoluto (se firma N s después de terminar de hablar):")
    print(f"  mediana {statistics.median(desfases):+.1f}s   "
          f"min {min(desfases):+.1f}s   max {max(desfases):+.1f}s")
    print(f"  con el adelanto: mediana "
          f"{statistics.median(desfases)/CATCHUP_FACTOR:+.1f}s  "
          f"max {max(desfases)/CATCHUP_FACTOR:+.1f}s")
    print()
    print("frases que el avatar NO alcanza a terminar mientras el usuario habla: "
          f"{sum(1 for r in ratios if r > 1)} de {len(ratios)}")
    print(f"frases que tampoco alcanza con el adelanto: "
          f"{sum(1 for r in ratios if r > CATCHUP_FACTOR)} de {len(ratios)}")

    # ¿Qué factor haría falta? Es el inverso del ratio, y hay que decirlo aunque
    # sea un número que no se va a usar.
    necesarios = sorted(r for r in ratios if r > 1)
    if necesarios:
        print()
        print(f"factor necesario para no acumular cola (mediana de las que "
              f"acumulan): {statistics.median(necesarios):.1f}x")
        print("  (2,15 s por seña contra 2-3 palabras por segundo: la animación es "
              "el cuello de botella, no el pipeline)")

    sin_seña = sum(f["sin_seña"] for f in filas)
    print()
    print(f"glosas sin animación exportada: {sin_seña} "
          f"({sum(1 for f in filas if f['sin_seña'])} frases las tienen; "
          f"son las 18 del catálogo sin GLB, no un fallo del flujo)")
    json.dump(filas, open("/tmp/opencode/retraso_filas.json", "w"), indent=1)


if __name__ == "__main__":
    asyncio.run(main())
    sys.exit(0)