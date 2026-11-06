"""Sonda: que devuelve el modelo CRUDO en las 13 frases que fallaron.

Acompana a PROPUESTA-MEJORA-GLOSADO-2026-11-05.md, seccion 9.

No toca el pipeline ni la cache: instancia el LLMClient con la config de
produccion y pide la respuesta sin reparar, para ver si el error viene del
modelo o del recorte contra el catalogo.

Se corre DENTRO del contenedor del backend:

    docker cp docs/plan-pruebas/sonda-glosado.py sign-ai-backend-backend-1:/tmp/
    docker exec -i sign-ai-backend-backend-1 python /tmp/sonda-glosado.py

Las 50 glosas del catalogo van una por linea; se verifica el tamano antes de
medir, porque una lista mal armada hace que el recorte dropee glosas que si
existen y la sonda mentiria (paso en la primera corrida: la primera linea quedo
pegada como una sola palabra y ABURRIDO/BIEN/BIENVENIDO desaparecieron del
vocabulario).
"""
import asyncio
import sys

sys.path.insert(0, "/app")

from app.config.settings import Settings
from app.nlp.llm_client import LLMClient
from app.validation.catalog_repository import normalize_gloss

GLOSAS = [
    "ABUELO", "ABURRIDO", "ADIOS", "BIEN", "BIENVENIDO",
    "BUENAS NOCHES", "BUENAS TARDES", "BUENOS DIAS", "COMER", "COMO",
    "COMO ESTAS", "CON GUSTO", "CONTENTO", "CUANDO", "DIFERENTE",
    "DONDE", "FAMILIA", "FELIZ", "GRACIAS", "HAMBRE",
    "HERMANO", "HOLA", "HOMBRE", "JUICIOSO", "MAL",
    "MAS O MENOS", "MUCHO", "MUJER", "NIÑA", "NIÑO",
    "NOMBRE", "NUNCA", "PERDON", "PERMISO", "PERSONAS",
    "POCO", "POR FAVOR", "POR QUE", "QUE", "QUIEN",
    "SENTIR", "SEÑA", "TIO", "TODOS", "TRABAJAR",
    "TRISTE", "TU", "USTEDES", "VIVIR", "YO",
]
VOCAB = {normalize_gloss(g): g for g in GLOSAS}

FRASES = [
    ("FR-01-001", "Hola, buenos días, bienvenidos.", "HOLA, BUENOS DIAS, BIENVENIDO"),
    ("FR-01-006", "Gracias a todos. Los niños y las niñas comen con gusto.",
     "GRACIAS, TODOS, NIÑO, NIÑA, COMER, CON GUSTO"),
    ("FR-01-008", "¿Estás bien?", "TU, BIEN"),
    ("FR-01-010", "Tu hermano está triste. ¿Se siente mal?",
     "TU, HERMANO, TRISTE, SENTIR, MAL"),
    ("FR-01-011", "Perdón. ¿Estás triste?", "PERDON, TU, TRISTE"),
    ("FR-01-012", "¿Tu hermano está aburrido?", "TU, HERMANO, ABURRIDO"),
    ("FR-01-013", "Hola, ¿cómo estás? ¿Cómo te llamas?",
     "HOLA, COMO ESTAS, COMO, TU, NOMBRE"),
    ("FR-01-015", "La mujer es juiciosa. ¿Quién es el hombre?",
     "MUJER, JUICIOSO, QUIEN, HOMBRE"),
    ("FR-01-016", "¿Por qué estás triste?", "POR QUE, TU, TRISTE"),
    ("FR-01-024", "Se siente muy bien. La niña nunca está triste.",
     "NIÑA, SENTIR, MUCHO, BIEN, NUNCA, TRISTE"),
    ("FR-01-026", "Con permiso. Déjame pasar, por favor.",
     "PERMISO, POR FAVOR"),
    ("FR-01-027", "Niños, trabajen todos.", "NIÑO, TODOS, TRABAJAR"),
    ("FR-01-029", "Hola, familia, siéntanse bienvenidos.",
     "HOLA, FAMILIA, BIENVENIDO"),
]


async def main() -> None:
    assert len(VOCAB) == 50, f"vocabulario mal armado: {len(VOCAB)}"
    print(f"vocabulario: {len(VOCAB)} glosas\n")
    s = Settings()
    client = LLMClient(
        s.llm_api_key, s.llm_model, s.llm_temperature, s.llm_timeout_ms,
        s.llm_max_retries, s.llm_base_url, VOCAB,
    )
    for test, frase, esperado in FRASES:
        resp = await client._chat.ainvoke(
            [("system", client._prompt), ("human", frase)]
        )
        crudo = resp.content.strip()
        parsed = client._parse_glosses(crudo)
        reparado = client._repair_glosses(parsed)
        perdidas = [g for g in parsed if normalize_gloss(g) not in VOCAB]
        print(f"### {test}")
        print(f"  frase     : {frase}")
        print(f"  esperado  : {esperado}")
        print(f"  crudo     : {crudo!r}")
        print(f"  reparado  : {', '.join(reparado)}")
        print(f"  se pierde : {', '.join(perdidas) if perdidas else '—'}")
        print(f"  exacta    : {'SI' if reparado == [g.strip() for g in esperado.split(',')] else 'NO'}")
        print()


asyncio.run(main())
