sign-ai-backend · sign-ai-stt · sign-ai-android

# Qué cambió en develop y qué conviene conservar

Comparación entre la versión previa al 14 de octubre y la rama `develop` actual, contrastada con lo documentado en `sign-ai-docs`. Autor de los cambios: diegostrg (10 commits en backend, 4 en STT, unos 25 en Android).

Revisión hecha leyendo el código y los documentos. No se ejecutó nada ni se modificó ningún repositorio.

**Resumen.** El cambio central es pasar de traducir al cerrar la frase a traducir por fragmentos mientras la persona habla. Casi todo lo demás (caché en memoria, claves con contexto, valores del STT, segundo modelo, `docker-compose`) existe para sostener ese cambio. El esquema de `semantic_cache` no cambió.

**Base recomendada.** `origin/main` del backend ya contiene lo útil (normalización del catálogo y frames `text`/`end`), sin nada de tiempo real. En STT, el commit `e1da961`.

Veredicto

## Qué conservaría

Lo demás vuelve a la versión anterior.

### Sí

1. Normalización de tildes y mayúsculas del catálogo (ya en `origin/main`).
2. Frames `text` y `end` del cliente Android (ya en `origin/main`).
3. `LLM_BASE_URL`, vacío por defecto.
4. Esquema de `semantic_cache` y envío continuo de audio, que no cambiaron.

### Opcional, si sobra tiempo

- Métricas y fixtures de audio, para las pruebas.
- Calentamiento del LLM al arrancar.

## Antes de la reunión

### Para no afirmar de más

- **Prompt.** El documento de LLM registra la falta de la lista de glosas como una limitación (“el modelo puede inventar glosas”), no como una decisión de excluirla. Lo defendible es que el cambio debe evaluarse con PR-LLM antes de adoptarlo.
- **Tamaño de Whisper.** El plan de pruebas dice “los tamaños de Whisper a comparar”. Si ya lo decidieron con pruebas, ten esos resultados a mano.
- **El prompt sí lleva las glosas.** No están escritas en el archivo; `main.py` las inyecta desde la tabla `catalog` al arrancar.

> **Actualizado el 2026-10-21.** Los dos puntos del prompt quedaron atrás: la
> decisión **D-7** (owner, 2026-10-16) quitó la lista de glosas del prompt, y es
> lo que se va a implementar. Ver `docs/PLAN-MEDICION.md` §3 (D-7).

### Para verificar tú

- Filas con `||` en `original_text` de `semantic_cache`.
- SQL de las tres columnas nuevas de `catalog`. No hay migración en el repositorio.
- Tus mediciones del `lookup`, contra la de \~100 ms por viaje a Neon que usa Diego.
- Revisión de Android: no se hizo a fondo.