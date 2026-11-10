#!/usr/bin/env python3
"""PR-HUE-01, PR-HUE-02 y PR-HUE-03 · Huella gramatical.

Corre dentro del contenedor del backend:

    docker compose exec -T backend python - \
        < docs/plan-pruebas/arnes/hue.py

- HUE-01 compara tres formas de clasificar el tipo de frase: las reglas del
  sistema, las señales morfológicas de spaCy y el modelo de lenguaje.
- HUE-02 apaga una regla a la vez (interrogativa, negación, imperativa) y mide
  cuánto aporta cada una.
- HUE-03 mide la exactitud de las reglas por tipo de frase.
"""

from __future__ import annotations

import csv
import json
import os
from pathlib import Path

from app.config.settings import Settings
from app.domain.models import PhraseType
from app.nlp import gloss_analyzer as ga

DATOS = Path(os.environ.get("DATOS", "/datos"))
EVIDENCIA = Path(os.environ.get("EVIDENCIA", "/evidencia"))

TIPOS = {
    "Afirmación": PhraseType.AFFIRMATIVE,
    "Pregunta de sí o no": PhraseType.YES_NO_QUESTION,
    "Pregunta de contenido": PhraseType.CONTENT_QUESTION,
    "Negación": PhraseType.NEGATIVE,
    "Orden": PhraseType.IMPERATIVE,
}


def cargar(nombre: str) -> list[list[str]]:
    return json.loads((DATOS / f"{nombre}.json").read_text(encoding="utf-8"))


def entradas() -> list[dict]:
    """Las 200 entradas: 100 oraciones, cada una con y sin puntuación."""
    filas = []
    for identificador, tipo, con_puntuacion, sin_puntuacion in (f[:4] for f in cargar("CO-02")):
        esperado = TIPOS[tipo]
        filas.append({"id": identificador, "variante": "con", "texto": con_puntuacion, "esperado": esperado})
        filas.append({"id": identificador, "variante": "sin", "texto": sin_puntuacion, "esperado": esperado})
    return filas


def clasificar_reglas(texto: str, usar_interrogativa: bool = True, usar_negacion: bool = True,
                      usar_imperativa: bool = True) -> PhraseType:
    normalizado = ga._normalize(texto)
    senales: list[PhraseType] = []
    if usar_interrogativa:
        if ga._has_interrogative_word(normalizado) and (
            ga._has_question_mark(texto) or ga._starts_with_interrogative_word(texto)
        ):
            senales.append(PhraseType.CONTENT_QUESTION)
        elif ga._has_question_mark(texto):
            senales.append(PhraseType.YES_NO_QUESTION)
    if usar_imperativa and ga._has_imperative(normalizado):
        senales.append(PhraseType.IMPERATIVE)
    if usar_negacion and ga._has_negation(normalizado):
        senales.append(PhraseType.NEGATIVE)
    if not senales:
        return PhraseType.AFFIRMATIVE
    for tipo in (
        PhraseType.CONTENT_QUESTION,
        PhraseType.YES_NO_QUESTION,
        PhraseType.IMPERATIVE,
        PhraseType.NEGATIVE,
    ):
        if tipo in senales:
            return tipo
    return PhraseType.AFFIRMATIVE


def clasificar_spacy(nlp, texto: str) -> PhraseType:
    """La misma pregunta pero con las señales morfológicas del modelo."""
    documento = nlp(texto)
    negacion = any(
        token.morph.get("Polarity") == ["Neg"] or token.lemma_.lower() in ga.NEGATION_PARTICLES
        for token in documento
    )
    imperativa = any(token.morph.get("Mood") == ["Imp"] for token in documento)
    marca = any(c in texto for c in "?¿")
    interrogativa = any(
        token.tag_ in {"pron", "adv"} and token.lower_ in ga.INTERROGATIVE_WORDS for token in documento
    )
    if interrogativa and marca:
        return PhraseType.CONTENT_QUESTION
    if marca:
        return PhraseType.YES_NO_QUESTION
    if imperativa:
        return PhraseType.IMPERATIVE
    if negacion:
        return PhraseType.NEGATIVE
    return PhraseType.AFFIRMATIVE


def clasificar_llm(cliente, modelo: str, texto: str) -> PhraseType:
    peticion = (
        "Clasifica el tipo de esta oración en español. Responde solo con una de estas "
        "cinco etiquetas exactas: AFIRMACION, PREGUNTA_SI_NO, PREGUNTA_CONTENIDO, "
        "NEGACION, ORDEN.\n\nOración: " + texto
    )
    respuesta = cliente.chat.completions.create(
        model=modelo,
        temperature=0,
        messages=[{"role": "user", "content": peticion}],
    )
    etiqueta = respuesta.choices[0].message.content.strip().upper()
    etiqueta = (
        etiqueta.replace("Á", "A").replace("É", "E").replace("Í", "I")
        .replace("Ó", "O").replace("Ú", "U").replace("Ñ", "N")
    )
    if "SI_NO" in etiqueta or "SI O NO" in etiqueta:
        return PhraseType.YES_NO_QUESTION
    if "CONTENIDO" in etiqueta:
        return PhraseType.CONTENT_QUESTION
    if "NEGAC" in etiqueta:
        return PhraseType.NEGATIVE
    if "ORDEN" in etiqueta or "IMPERAT" in etiqueta:
        return PhraseType.IMPERATIVE
    return PhraseType.AFFIRMATIVE


def exactitud(filas: list[dict], clasificador) -> dict:
    aciertos = 0
    por_tipo: dict[str, list[int]] = {}
    fallos = []
    for fila in filas:
        obtenido = clasificador(fila)
        acierto = obtenido == fila["esperado"]
        aciertos += int(acierto)
        por_tipo.setdefault(fila["esperado"].value, []).append(int(acierto))
        if not acierto:
            fallos.append(
                {"id": fila["id"], "variante": fila["variante"], "texto": fila["texto"],
                 "esperado": fila["esperado"].value, "obtenido": obtenido.value}
            )
    return {
        "entradas": len(filas),
        "aciertos": aciertos,
        "porcentaje": round(100 * aciertos / len(filas), 1),
        "por_tipo": {t: f"{sum(v)}/{len(v)}" for t, v in por_tipo.items()},
        "fallos": fallos,
    }


def main() -> None:
    EVIDENCIA.mkdir(parents=True, exist_ok=True)
    filas = entradas()
    ajustes = Settings()

    from spacy import load as cargar_spacy

    modelo_spacy = cargar_spacy("es_core_news_md", exclude=["parser", "ner", "lemmatizer", "senter"])

    from openai import OpenAI

    cliente = OpenAI(api_key=ajustes.llm_api_key, base_url=ajustes.llm_base_url or None)

    reglas = exactitud(filas, lambda f: clasificar_reglas(f["texto"]))
    spacy = exactitud(filas, lambda f: clasificar_spacy(modelo_spacy, f["texto"]))
    llm = exactitud(filas, lambda f: clasificar_llm(cliente, ajustes.llm_model, f["texto"]))

    resumen_hue01 = {
        "reglas": {k: reglas[k] for k in ("entradas", "aciertos", "porcentaje", "por_tipo")},
        "spacy_md": {k: spacy[k] for k in ("entradas", "aciertos", "porcentaje", "por_tipo")},
        "llm": {"modelo": ajustes.llm_model, **{k: llm[k] for k in ("entradas", "aciertos", "porcentaje", "por_tipo")}},
        "fallos_reglas": reglas["fallos"],
    }

    ablaciones = {
        "completa": clasificar_reglas,
        "sin_interrogativa": lambda t: clasificar_reglas(t, usar_interrogativa=False),
        "sin_negacion": lambda t: clasificar_reglas(t, usar_negacion=False),
        "sin_imperativa": lambda t: clasificar_reglas(t, usar_imperativa=False),
    }
    resumen_hue02 = {}
    for nombre, funcion in ablaciones.items():
        datos = exactitud(filas, lambda f, fn=funcion: fn(f["texto"]))
        resumen_hue02[nombre] = {k: datos[k] for k in ("entradas", "aciertos", "porcentaje", "por_tipo")}

    resumen_hue03 = {k: reglas[k] for k in ("entradas", "aciertos", "porcentaje", "por_tipo")}
    resumen_hue03["fallos"] = reglas["fallos"]

    for nombre, datos in (
        ("PR-HUE-01", resumen_hue01),
        ("PR-HUE-02", resumen_hue02),
        ("PR-HUE-03", resumen_hue03),
    ):
        (EVIDENCIA / f"{nombre}_E_20261110_01.json").write_text(
            json.dumps(datos, ensure_ascii=False, indent=1), encoding="utf-8"
        )
        with (EVIDENCIA / f"{nombre}_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
            w = csv.writer(f, delimiter=";")
            w.writerow(["metodo", "entradas", "aciertos", "porcentaje", "por_tipo"])
            if nombre == "PR-HUE-01":
                for clave in ("reglas", "spacy_md", "llm"):
                    d = datos[clave]
                    w.writerow([clave, d["entradas"], d["aciertos"], d["porcentaje"], json.dumps(d["por_tipo"], ensure_ascii=False)])
            elif nombre == "PR-HUE-02":
                for clave, d in datos.items():
                    w.writerow([clave, d["entradas"], d["aciertos"], d["porcentaje"], json.dumps(d["por_tipo"], ensure_ascii=False)])
            else:
                d = resumen_hue03
                w.writerow(["reglas", d["entradas"], d["aciertos"], d["porcentaje"], json.dumps(d["por_tipo"], ensure_ascii=False)])

    print(json.dumps({"HUE-01": resumen_hue01, "HUE-02": resumen_hue02, "HUE-03": resumen_hue03}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
