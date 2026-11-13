#!/usr/bin/env python3
"""PR-RES-01 y PR-RES-03 con el teléfono (Redmi Note 11 por USB).

    python docs/plan-pruebas/arnes/res_telefono.py res01 --salida ../data/evidencias/plan-pruebas/evidencias/RES/PR-RES-01_E_20261110_02
    python docs/plan-pruebas/arnes/res_telefono.py res03 --salida ../data/evidencias/plan-pruebas/evidencias/RES/PR-RES-03_E_20261110_02

Requisitos: el laboratorio `p8lab-*` arriba (backend en 8180, STT en 8100), la app `com.signai.webview`
instalada con `RECORD_AUDIO`, y `adb` (se usa la ruta completa del SDK, no está en el PATH).
La app apunta a `ws://127.0.0.1:8080/ws/translate`; el túnel es `adb reverse tcp:8080 tcp:8180`.

res01 · cada momento (hablando = micrófono encendido; representando = el avatar está haciendo las señas):
  corta el túnel USB con `adb kill-server` (como el 8/11), espera, lo restaura, y mide con el logcat del
  teléfono (`SignAiNet`, `SignAiState`; reloj del teléfono) cuándo detectó el corte, cuánto tardó en
  reconectar y cuántos intentos hizo; guarda lo que mostró la pantalla (uiautomator + captura) y manda una
  frase nueva para ver si la app queda lista sin reiniciarla.
res03 · enciende el micrófono, detiene el STT (`docker stop p8lab-stt`), observa el aviso, comprueba que el
  texto escrito se sigue traduciendo, vuelve a encender el STT y mide cuánto tarda la app en poder hablar
  otra vez, sin reiniciarla. No hay voz real: el micrófono está abierto pero nadie habla (no hay altavoz).
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
from pathlib import Path

ADB = r"C:\Users\Sergio\AppData\Local\Android\Sdk\platform-tools\adb.exe"
APP = "com.signai.webview"
MIC = (798, 2132)         # «Entrada por voz»
CAMPO = (291, 2132)       # «Escribe una frase...»
TRADUCIR = (288, 1510)    # botón «Traducir» con el teclado abierto (sube; cerrado está en y=2268)
LOG = ("SignAiNet", "SignAiState")


def adb(*args: str, timeout: int = 60) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=timeout).stdout


def docker(*args: str) -> str:
    return subprocess.run(["docker", *args], capture_output=True, text=True, encoding="utf-8").stdout.strip()


def ahora_telefono() -> float:
    return float(adb("shell", "date +%s.%N").strip())


def tocar(xy: tuple[int, int]) -> None:
    adb("shell", "input", "tap", str(xy[0]), str(xy[1]))


def preparar() -> None:
    adb("start-server")
    adb("reverse", "tcp:8080", "tcp:8180")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")  # el teléfono no tiene bloqueo de pantalla


def logcat(desde: float) -> list[tuple[float, str]]:
    salida = adb("logcat", "-d", "-v", "epoch", "-s", *LOG)
    lineas = []
    for linea in salida.splitlines():
        m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+(.*)", linea)
        if m and float(m.group(1)) >= desde:
            lineas.append((float(m.group(1)), m.group(2)))
    return lineas


def esperar_linea(patron: str, desde: float, tope: float) -> tuple[float, str] | None:
    t0 = time.time()
    while time.time() - t0 < tope:
        for t, l in logcat(desde):
            if re.search(patron, l):
                return t, l
        time.sleep(1)
    return None


def arrancar_app() -> None:
    adb("shell", "am", "force-stop", APP)
    adb("logcat", "-c")
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    esperar_linea(r"sesión abierta", desde=0, tope=40)
    time.sleep(8)  # el avatar termina de cargar


def pantalla(nombre: Path) -> list[str]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    textos = re.findall(r'text="([^"]+)"', xml)
    textos += ["[desc] " + d for d in re.findall(r'content-desc="([^"]+)"', xml)]
    png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout
    nombre.with_suffix(".png").write_bytes(png)
    return textos


def escribir_y_traducir(texto: str) -> None:
    tocar(CAMPO)
    time.sleep(0.5)
    adb("shell", "input", "text", texto.replace(" ", "%s"))
    time.sleep(0.5)
    tocar(TRADUCIR)


def momento(nombre: str, salida: Path, preparar_corte, frase_nueva: str, modo: str = "tunel") -> dict:
    arrancar_app()
    r = {"momento": nombre, "corte": modo}
    preparar_corte()
    t_corte = ahora_telefono()
    if modo == "pausa":                      # el backend deja de responder; adb sigue vivo para ver la pantalla
        docker("pause", "p8lab-backend")
        esperar_linea(r"sesión perdida", desde=t_corte, tope=75)
        time.sleep(3)
        r["pantalla_durante_el_corte"] = pantalla(salida.with_name(salida.name + f"_{nombre}_durante"))
        docker("unpause", "p8lab-backend")
    else:
        adb("kill-server")                   # corta el túnel USB; la app queda sin enlace
        time.sleep(40)                       # tiempo para que la app detecte el corte sola (ping ≈20 s)
    preparar()
    t_restaura = ahora_telefono()
    r["t_corte"], r["t_restaura"] = t_corte, t_restaura
    time.sleep(8)
    lineas = logcat(t_corte - 1)
    det = next(((t, l) for t, l in lineas if re.search(r"session failed|sesión perdida|fallo:", l)), None)
    rec = next(((t, l) for t, l in lineas if t > t_restaura - 1 and "sesión abierta" in l), None)
    r["detecto_corte_ms"] = round(1000 * (det[0] - t_corte)) if det else None
    r["ms_hasta_reconectar_tras_restaurar"] = round(1000 * (rec[0] - t_restaura)) if rec else None
    r["intentos_fallidos_tras_restaurar"] = sum(1 for t, l in lineas if t > t_restaura and "session failed" in l)
    r["avisos"] = [l for t, l in lineas if re.search(r"aviso|fallo:", l)][:6]
    r["lineas"] = [f"{t:.3f} {l}" for t, l in lineas][:60]
    r["pantalla_tras_restaurar"] = pantalla(salida.with_name(salida.name + f"_{nombre}"))
    t_nueva = ahora_telefono()               # ¿queda lista para una frase nueva sin reiniciar la app?
    escribir_y_traducir(frase_nueva)
    seg = esperar_linea(r"segment \d+/\d+", desde=t_nueva, tope=25)
    r["frase_nueva_llego_segmento"] = bool(seg)
    r["lineas_frase_nueva"] = [f"{t:.3f} {l}" for t, l in logcat(t_nueva)][:12]
    return r


def res01(salida: Path, variante: str = "tunel") -> dict:
    preparar()

    def hablando() -> None:
        tocar(MIC)
        esperar_linea(r"micrófono=Dictation|dictado|recording", desde=ahora_telefono() - 5, tope=10)
        time.sleep(3)

    def representando() -> None:
        t = ahora_telefono()
        escribir_y_traducir("Mi abuelo quiere hablar con todos los ninos sobre el trabajo nuevo")
        esperar_linea(r"signStart", desde=t, tope=25)  # el avatar empieza a hacer las señas
        time.sleep(1.5)

    datos = {"alcance": "teléfono Redmi Note 11 por USB, laboratorio p8lab-*; corte con `adb kill-server`; "
                         "la ruta por micrófono con voz real no se mide (no hay altavoz)"}
    modo = "pausa" if variante == "pausa" else "tunel"
    datos["hablando"] = momento("hablando", salida, hablando, "Quiero hablar con mi mama", modo)
    datos["representando"] = momento("representando", salida, representando, "Buenos dias a todos", modo)
    return datos


def res03(salida: Path) -> dict:
    preparar()
    arrancar_app()
    r = {"alcance": "STT detenido con el micrófono encendido (sin voz real, no hay altavoz); laboratorio p8lab-*"}
    tocar(MIC)
    t_mic = ahora_telefono()
    esperar_linea(r"micrófono=Dictation|dictado|recording", desde=t_mic - 2, tope=10)
    time.sleep(4)
    t_stop = ahora_telefono()
    docker("stop", "p8lab-stt")
    aviso = esperar_linea(r"fallo:|aviso|Sin servicio|sesión perdida|session failed", desde=t_stop, tope=30)
    r["ms_hasta_el_aviso"] = round(1000 * (aviso[0] - t_stop)) if aviso else None
    time.sleep(3)
    r["lineas_con_el_stt_apagado"] = [f"{t:.3f} {l}" for t, l in logcat(t_stop)][:30]
    r["pantalla_con_el_stt_apagado"] = pantalla(salida.with_name(salida.name + "_stt_apagado"))
    t_texto = ahora_telefono()
    escribir_y_traducir("Buenas noches a todos")
    r["texto_se_traduce_con_el_stt_apagado"] = bool(esperar_linea(r"segment \d+/\d+", desde=t_texto, tope=25))
    t_enciende = ahora_telefono()
    docker("start", "p8lab-stt")
    reabre = esperar_linea(r"sesión abierta", desde=t_enciende, tope=120)
    r["s_hasta_reabrir_la_sesion"] = round(reabre[0] - t_enciende, 1) if reabre else None
    time.sleep(4)
    r["pantalla_tras_volver_el_stt"] = pantalla(salida.with_name(salida.name + "_stt_de_vuelta"))
    t_mic2 = ahora_telefono()
    tocar(MIC)
    r["mic_se_enciende_otra_vez"] = bool(esperar_linea(r"micrófono=Dictation|dictado|recording", desde=t_mic2, tope=10))
    r["lineas_final"] = [f"{t:.3f} {l}" for t, l in logcat(t_enciende)][:30]
    return r


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("prueba", choices=["res01", "res01_pausa", "res03"])
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    a = ap.parse_args()
    salida = Path(a.salida)
    if a.prueba == "res03":
        datos = res03(salida)
    else:
        datos = res01(salida, "pausa" if a.prueba == "res01_pausa" else "tunel")
    salida.with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2)[:6000])


if __name__ == "__main__":
    principal()
