# Sonic y la hamaca de Playa Anémona

## Archivo que se edita

`special_gfx_out/sonic_hamaca_EDITAME.png` mide 144x48 y contiene las tres poses completas,
de izquierda a derecha. Cada pose mide 48x48 e incluye a Sonic, la hamaca y
las partes que se mueven (mano y pies).

La secuencia real dura 32 fotogramas y usa cada pose durante 8:

```text
centro -> izquierda -> centro -> derecha -> repetir
```

La pose central es, por tanto, la postura neutra que aparece entre los dos
extremos del movimiento.

- No cambies el tamaño del PNG.
- Dibuja con lápiz duro, sin suavizado ni colores intermedios.
- El magenta marca el índice transparente del sprite. No es un color visible
  en el juego.
- Usa únicamente colores que ya estén en el PNG; la paleta de Mega Drive es
  fija.
- `special_gfx_out/sonic_hamaca_x4_VISTA.png` es solo una ampliación para mirar. No se inserta.

## Laterales estáticos

La escena dispone ahora de dos archivos adicionales, ambos de **24x48**:

- `special_gfx_out/sonic_lateral_izquierdo_EDITAME.png`
- `special_gfx_out/sonic_lateral_derecho_EDITAME.png`

Son tiles del plano de fondo, no sprites, y por tanto no se animan. Sus
posiciones son independientes de la posición del personaje.

Los dos PNG llevan incrustada la paleta original de esos tiles del fondo
(línea CRAM 1 de Playa Benalmádena), que es distinta de la paleta de SonicGil.
El SDK no cambia la paleta del escenario: cada tile nuevo hereda exactamente
los bits de paleta que tenía esa misma posición del fondo. La prioridad se
mantiene como fondo para que los dibujos queden detrás de Jesús Gil.
Usa únicamente esos 16 colores, no cambies las dimensiones y dibuja con lápiz
duro, sin suavizado. Un tile completo de 8x8 que permanezca en el color 0 de
la paleta deja intacto el fondo original; al pintar otro color dentro de ese
tile, este pasa a formar parte del gráfico estático.

`special_gfx_out/sonic_laterales_PALETA.png` muestra los 16 colores originales
en una tira de muestras. Es solo una referencia y no se inserta en la ROM.

Los archivos terminados en `_x4_VISTA.png` son ampliaciones de consulta y no se
insertan en la ROM.

## Recolocar los tres elementos

Edita `sonic_scene_positions.txt`. Las seis coordenadas son offsets en píxeles
respecto a la posición original de SonicGil:

```text
sonic_x=-24
sonic_y=0
izquierdo_x=-40
izquierdo_y=-8
derecho_x=16
derecho_y=-8
```

X positivo mueve a la derecha e Y positivo mueve hacia abajo. Jesús Gil puede
moverse píxel a píxel. Los laterales deben usar múltiplos de 8 porque son tiles.
Los valores iniciales dejan ambos dibujos 8 píxeles detrás de Jesús Gil y 8
píxeles más arriba. El SDK detiene la compilación si una posición sale de la
zona compatible con la paleta original.

## Meter la edición en la ROM

Desde `CholeilSDK`, compila como acostumbres y ejecuta el modo de inserción:

```text
java -Dfile.encoding=UTF-8 -cp target/classes net.krusher.CholeilSDK i
```

Antes de recomprimir los gráficos, el SDK reorganiza automáticamente el
EDITAME animado y los dos laterales al orden interno del juego, actualiza
`gfx_out/gfx_05ebe8.png` y coloca los laterales en el tilemap de Playa Anémona.

## Localización técnica

- Bloque LZ-Toshio: ROM `0x05EBE8`.
- Tamaño descomprimido: 3456 bytes = 108 tiles.
- Tres poses de 36 tiles cada una.
- Cada pose usa cuatro sprites Mega Drive de 3x3 tiles para formar un mosaico
  de 48x48.
- En VRAM aparecen en `0x6EC0-0x7C3F`; la tabla de sprites alterna los cuatro
  índices de tile para animar la mano y los pies.
- Los offsets X/Y de los cuatro sprites se modifican desde
  `sonic_scene_positions.txt`; Jesús Gil admite precisión de un píxel.
- Los 36 tiles estáticos de los laterales se añaden al mismo bloque y ocupan
  VRAM `0x7C80-0x80FF` (tiles `0x3E4-0x407`).
- Los 36 tiles originales de ambas zonas usan la línea de paleta 1. El SDK
  copia sin modificar los bits originales de paleta de cada posición.
- El mapa/metatiles de la sala está en el bloque LZ-Toshio `0x178E7A`. Se usan
  hasta dieciséis metatiles libres (`0x3E0-0x3EF`) para colocar los dos
  rectángulos sin alterar el resto de Playa Anémona.
