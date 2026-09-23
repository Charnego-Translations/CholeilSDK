# Jeringuilla del Parque de Soleil / flor bailarina

La edición de este PNG se muestra como jeringuilla **solo en el Parque de
Soleil** (sala `0x32`, donde aparece la gitana). Las apariciones de esta misma
animación en cualquier otra sala conservan la flor bailarina original.

Editable principal: [`special_gfx_out/flor_parque_EDITAME.png`](special_gfx_out/flor_parque_EDITAME.png).
Mide **64×64 px**: cuatro fotogramas por fila, dos filas; cada fotograma ocupa
**16×32 px**. Los **24 px superiores** se animan de forma independiente. Los
**8 px inferiores** son el tallo/suelo compartido: aparecen repetidos en los
ocho fotogramas y, si se cambian, deben quedar idénticos en los ocho.

[`flor_parque_x4_VISTA.png`](special_gfx_out/flor_parque_x4_VISTA.png) es una vista
ampliada que no se reinserta. [`flor_parque_PALETA.png`](special_gfx_out/flor_parque_PALETA.png)
muestra los 16 índices, de izquierda a derecha. Conserva las dimensiones, el
modo de color indexado y **el orden exacto de la paleta** en `EDITAME.png`.
El insertador se detiene si el editor altera la paleta; no modifica CRAM.

| Índice | CRAM | RGB visto en BizHawk | Uso |
|---:|:---:|:---:|:---|
| 0 | `06CA` | `#AACC66` | Transparente en el plano |
| 1 | `0EEE` | `#EEEEEE` | |
| 2 | `0888` | `#888888` | |
| 3 | `04AE` | `#EEAA44` | |
| 4 | `026A` | `#AA6622` | |
| 5 | `0664` | `#446666` | |
| 6 | `0666` | `#666666` | |
| 7 | `0AAA` | `#AAAAAA` | |
| 8 | `0466` | `#666644` | |
| 9 | `08CC` | `#CCCC88` | |
| A | `06AA` | `#AAAA66` | |
| B | `0082` | `#228800` | |
| C | `02A6` | `#66AA22` | |
| D | `06CA` | `#AACC66` | Opaque; mismo RGB que índice 0 |
| E | `0488` | `#888844` | |
| F | `0AEC` | `#CCEEAA` | |

Los índices 0 y D parecen iguales en el PNG, pero **no son intercambiables**:
el 0 deja ver el fondo y el D no. Por eso el editor debe guardar el índice,
no solo el RGB. Si lo conviertes a RGB o una aplicación optimiza/reordena la
paleta, vuelve a exportarlo como PNG indexado con esta misma paleta.

## Integración

Con la ROM original `Soleil (Spain).md` en la raíz, la extracción normal (`x`)
genera el PNG. La compilación normal (`i`) guarda aparte los seis tiles de cada
uno de los ocho fotogramas editados y sincroniza el tallo con el bloque
comprimido del parque. El banco compartido de flores no se sobrescribe. También
se puede regenerar solo el PNG con:

```text
java -cp target/classes net.krusher.graphics.FlowerParkGraphics extract "Soleil (Spain).md"
```

No se versiona ni se distribuye la ROM ni ningún IPS.

## Selección por sala

El juego identifica esta animación con el slot `0x03` (que convierte en el
desplazamiento de tabla `0x0C`). La rutina original de
`0x01EBFA` obtiene su banco de tiles a partir de la tabla de `0x0D0000`. El
parche conserva esa ruta salvo cuando coinciden **slot `0x03` y sala
`0x0032`**; solo entonces usa los fotogramas editados guardados en
`0x000E96..0x001495`. Ese espacio era el gráfico del logo de arranque, que ya
no se muestra porque la intro personalizada lo salta, y queda reservado para
que la propia intro no lo reutilice.

Por tanto, `0x0D3C60..0x0D425F` vuelve a contener siempre los fotogramas
originales. Cualquier sala distinta de `0x32` cae necesariamente por esa ruta
original, sin depender de una lista de mapas conocidos.

## Localización comprobada

La partida `FlorParque.State` sitúa la flor en el plano A. Al capturar varios
fotogramas, los seis tiles variables de VRAM `0x220..0x225` coincidieron con
ocho bloques consecutivos de **192 bytes** en la ROM original, desde
`0x0D3C60` hasta `0x0D425F`. Hay poses visualmente repetidas, pero se exponen
los ocho bloques porque el juego recorre los ocho. El tallo compartido ocupa
los tiles de VRAM `0x1D2..0x1D3`; proviene del bloque LZ-Toshio `0x14D3AE`
en los desplazamientos descomprimidos `+0x1A40..+0x1A7F`. Ese tallo pertenece
al bloque gráfico específico del parque. La paleta es la línea 1 de CRAM
capturada en esa misma partida, no una paleta estimada.
