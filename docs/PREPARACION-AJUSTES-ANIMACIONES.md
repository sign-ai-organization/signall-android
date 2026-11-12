# Preparación local para ajustar las animaciones con IA

**Fecha:** miércoles 2026-11-11, 10:24 (UTC−5).  
**Estado:** preparación documental; no se ha corregido ninguna animación.

## Alcance autorizado

El owner quiere dar instrucciones a la IA para corregir movimientos existentes de
dedos, manos y cuerpo, sin aprender animación ni contratar animadores. No se crearán
señas desde cero. Puede haber correcciones pequeñas y otras más visibles, siempre
sobre las animaciones existentes.

**Esta sesión solo informa y prepara el terreno.** No autoriza pruebas de edición,
filtrado, importación/exportación de assets, cambios en la app ni reemplazos de GLB.
La documentación queda local, sin commit ni push. Para empezar las correcciones
hace falta una instrucción posterior del owner.

## Entorno disponible

| Elemento | Estado y origen de la comprobación |
|---|---|
| Sistema | CachyOS Linux, x86_64; lectura de `/etc/os-release` y arquitectura |
| GPU | NVIDIA RTX 4050 Laptop, 6141 MiB; consulta con `nvidia-smi` durante la investigación |
| RAM | Aproximadamente 15 GiB; lectura de `/proc/meminfo` durante la investigación |
| Espacio libre | Aproximadamente 63 GiB en `/home` al investigar; puede variar |
| Blender | **5.2.3 LTS**, confirmado por el owner y por `blender --version` en esta sesión |
| Herramientas existentes | Python 3, FFmpeg, `uv` y `uvx`, encontrados durante la investigación |

El equipo es adecuado como punto de partida para edición y procesamiento de clips.
Todavía no se ha probado la importación/exportación con este Blender ni medido su
rendimiento con los avatares. Que el ejecutable responda no valida el flujo completo.

## Herramientas elegidas

- **Blender + scripts Python preparados por la IA.** Blender incluye su API `bpy`:
  no hace falta instalarla con `pip` en el Python del sistema.
- **MCP no necesario para comenzar.** Podría añadirse si más adelante se necesita
  controlar una sesión gráfica de Blender, pero no está instalado ni configurado
  para este trabajo. La vía de scripts basta para automatizar operaciones.
- No se requieren por ahora modelos descargados, CUDA adicional, servicios de pago
  ni otra instalación. La IA de esta conversación dirige las herramientas; no se
  está preparando un modelo local generativo de movimiento.

Comandos de referencia, **sin necesidad de repetir la instalación**:

```bash
blender --version   # comprobar la versión instalada
blender             # abrir la interfaz cuando el owner lo quiera
```

El comando recomendado anteriormente fue `sudo pacman -Syu blender`: instala Blender
y actualiza el sistema completo. El owner ya confirmó la instalación. El agente no
ejecutó ese comando ni instalará complementos como parte de esta preparación.

## Fuentes y archivos que deben conservarse

- Origen vigente: `../signai-avatar/models/Juan.glb` y `Pauline.glb`, commit
  **`fc6f372` del 2026-11-04**. Solo lectura; el repo hermano queda intacto.
- Assets de la app: `app/src/main/assets/web/models/juan.glb` y `pauline.glb`.
  No se editan directamente: el empaquetado está en `tools/pack_avatars.py`.
- El flujo de producción se describe en
  `../signai-avatar/openspec/specs/animacion-cascadeur/spec.md` (2026-10-26).
  Sus cifras antiguas de 300 clips no describen la entrega actual.
- La inspección directa de los GLB actuales encontró **200 clips y 102 articulaciones
  por avatar**: 50 señas con cuatro variantes faciales. Los canales de huesos son
  iguales entre las cuatro variantes y se conservan al empaquetar para Android.
- `../material-3d/Para Exportar/` contiene **33 `.blend` y 33 `.fbx`**, contados en
  esta investigación. Son material anterior: no se asume que incluyan los últimos
  arreglos. No se encontraron `.casc` en la búsqueda del entorno compartido ni
  `.mp4` en `../data/`; no significa que no existan en el equipo de Samuel.

Referencia formal consultada: `../sign-ai-docs/latex/srs/main.tex:330–340`, última
modificación del archivo en commit `b23692c` del 2026-11-10: el avatar reproduce
señas preparadas y las concatena mediante interpolación. Esta preparación no cambia
ese alcance ni modifica los documentos formales.

## Procedimiento propuesto para otra sesión, todavía no ejecutado

1. El owner identifica una seña, el avatar y el movimiento que quiere corregir.
2. La IA prepara una copia de trabajo separada de los originales y registra su hash.
3. Antes de corregir, comprueba que importar y exportar sin modificaciones no altere
   el esqueleto, la duración, los clips, la cara ni el movimiento.
4. La IA aplica una corrección localizada, protegiendo el resto de la animación.
   No se aplica un filtro global a las 50 señas como primer paso.
5. Se compara el original con el resultado; el owner acepta, pide ajustes o descarta.
6. Solo con aprobación posterior se plantea integrar el resultado en el flujo de
   producción y empaquetarlo para la app. No hay promoción automática a los assets.

La automatización puede corregir movimientos, pero no certifica por sí sola que una
configuración de dedos o un contacto mantenga el significado en LSC. El owner revisa
el resultado; la validación lingüística no se da por realizada.

## Límites del diagnóstico actual

Se revisaron estructura y datos de los archivos, código y documentación. **No hay
una evaluación visual completa de las 50 señas ni una causa confirmada de la
tosquedad.** Tampoco hay resultados de mejora, pruebas de edición ni mediciones
nuevas de rendimiento en el teléfono. La mezcla de 100 ms existente en `app.js`
no equivale a corregir el movimiento interno de cada clip.

## Referencias prácticas

- [Blender: edición y suavizado de curvas](https://docs.blender.org/manual/en/latest/editors/graph_editor/fcurves/editing.html).
  La página `latest` puede describir una versión posterior a la 5.2.3 instalada;
  verificar la API real antes de automatizar una operación.
- [Cascadeur 2026.2: herramientas de edición](https://cascadeur.com/blog/view/cascadeur-2026-2-animation-layers-easing-new-languages).
- [MCP for Blender, integración de terceros opcional](https://github.com/ahujasid/blender-mcp).
  No instalarlo ni ejecutar su instalador salvo autorización posterior.
