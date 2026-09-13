# Kart de Iibis / carrera contra Charlie

## Entrega para Scorpion

`references/fragoneta.png` conserva el diseño facilitado por Antxiko, sin
modificarlo. Es una **referencia de 2135×736**, no un PNG directamente insertable:
hay que adaptar sus dibujos al interior de las cajas de 32×32 y a los colores
de `special_gfx_out/kart_EDITAME.png`. El editable incluido sigue conteniendo
el kart original; la fragoneta **todavía no está integrada en la ROM**.

## Qué editar

**`special_gfx_out/kart_EDITAME.png`** es la fuente del vehículo completo.
Tiene **298×67 píxeles**, 9 columnas y 2 filas. Cada dibujo ocupa un interior
de **32×32** con una rejilla de 1 píxel que nunca se inserta en la ROM.

- Fila superior: fotograma A. Fila inferior: fotograma B.
- Columnas: las 9 orientaciones originales, numeradas de 0 a 8.
- El kart aparcado junto a Charlie usa la quinta columna de la primera fila.
- Interiores: `x = 1 + columna*33`, `y = 1 + fila*33`, ancho/alto 32.
- `kart_x4_VISTA.png` es una vista ampliada, **no la fuente de edición**.
- `kart_PALETA.png` muestra los 16 índices de izquierda a derecha.

Hay **18 dibujos almacenados**, no 32 dibujos independientes. El juego los
refleja para obtener **16 direcciones × 2 fotogramas**. Las direcciones 9–15
reutilizan horizontalmente las columnas 7–1 de su fotograma. Cambiar una de
esas columnas modifica también su vista reflejada.

El kart aparcado y el conducido comparten estos gráficos. **Corona se dibuja
por separado** y no está dentro de esta hoja; Charlie tampoco.

## Paleta y transparencia

El rosa `#FF00FF` representa el índice transparente 0, solamente en el editor.
No se escribe ese color en la paleta de Mega Drive. Los otros 15 índices son
los colores de la paleta original de ROM `0x000548`, confirmada en CRAM durante
la carrera. El importador escribe los índices gráficos, **no cambia la paleta**.

Mantener las dimensiones y los colores del PNG. No usar suavizado ni
transparencias parciales. También se acepta alfa completamente transparente
como índice 0. Un color ajeno a la paleta o una medida incorrecta detiene la
reconstrucción; no se aproxima silenciosamente a otro color.

## Cómo llega a la ROM

La reconstrucción normal `choleil i` ya incluye el kart:

1. Quita los separadores y convierte los 18 dibujos a tiles por columnas.
2. Sincroniza los 288 tiles en `gfx_out/gfx_067f58.png`.
3. Reutiliza la compresión/reubicación del SDK y protege su reserva frente a
   la intro, incluso si parte del dibujo son bytes que parecen espacio libre.
4. Comprueba el bloque final siguiendo el puntero real del cartucho, también
   cuando se ha movido. Solo entonces publica `Choleil.md`.

El editable amigable tiene prioridad sobre la hoja de `gfx_out/`. Borrar el
editable desactiva esa sincronización, pero **no borra la última edición ya
sincronizada** en `gfx_out/`; no es una orden de restaurar el kart original.

Con las clases ya compiladas se puede reconstruir sin Maven:

```powershell
java -cp target/classes net.krusher.CholeilSDK i
```

Comandos específicos, también disponibles sin ejecutar la extracción global:

```powershell
# Cuidado: extract sobrescribe los dos PNG indicados; copia antes tus dibujos.
java -cp target/classes net.krusher.graphics.KartGraphics extract "Soleil (Spain).md"
java -cp target/classes net.krusher.graphics.KartGraphics verify Choleil.md
```

La vista ampliada se genera con `extract` a partir de la ROM indicada. Para
actualizarla después de editar sin sobrescribir tu fuente, usa rutas separadas:

```powershell
java -cp target/classes net.krusher.graphics.KartGraphics extract Choleil.md target/kart-comprobacion.png special_gfx_out/kart_x4_VISTA.png
```

## Direcciones verificadas en la ROM española

| Elemento | Valor |
| --- | --- |
| Mapa del circuito | `0x1B`, entrada 0 |
| Tipo de actor | `0x27` |
| Bloque LZ-Toshio original | `0x067F58` |
| Tamaño comprimido original, con cabecera | 5.729 bytes |
| Tamaño descomprimido | 9.216 bytes: 288 tiles |
| Puntero al bloque | `0x0590DC`, relativo a `0x059000` |
| VRAM observada | `0x6080..0x847F`, tiles `0x304..0x423` |
| Tabla de montaje | `0x020D1A..0x020D99`, 32 entradas |
| Selección de orientación y fotograma | `0x020766..0x020788` |

No confundir los índices 114/115 de los diálogos con el mapa: el circuito
observado es `0x1B`. En BizHawk se comprobaron el aparcado, la conducción y las
32 selecciones de dibujo. `KartGraphicsTest` comprueba además la disposición
de tiles, todos los dibujos, rejilla, paleta, errores y reubicación.

Los IPS y las ROM generadas son salidas locales y **no se incluyen en Git**.
