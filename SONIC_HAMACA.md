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

## Recolocar a Jesús Gil

Edita `sonic_scene_positions.txt`. Las dos coordenadas son offsets en píxeles
respecto a la posición original de SonicGil:

```text
sonic_x=0
sonic_y=0
```

X positivo mueve a la derecha e Y positivo mueve hacia abajo. Se admite
precisión de un píxel. Con ambos valores a cero el personaje se queda
exactamente donde lo dejó el juego original.

## Meter la edición en la ROM

Desde `CholeilSDK`, compila como acostumbres y ejecuta el modo de inserción:

```text
java -Dfile.encoding=UTF-8 -cp target/classes net.krusher.CholeilSDK i
```

Antes de recomprimir los gráficos, el SDK reorganiza automáticamente el
EDITAME al orden interno del juego y actualiza `gfx_out/gfx_05ebe8.png`.

## Localización técnica

- Bloque LZ-Toshio: ROM `0x05EBE8`.
- Tamaño descomprimido: 3456 bytes = 108 tiles.
- Tres poses de 36 tiles cada una.
- Cada pose usa cuatro sprites Mega Drive de 3x3 tiles para formar un mosaico
  de 48x48.
- En VRAM aparecen en `0x6EC0-0x7C3F`; la tabla de sprites alterna los cuatro
  índices de tile para animar la mano y los pies.
- Los offsets X/Y de los cuatro sprites se modifican desde
  `sonic_scene_positions.txt`; admiten precisión de un píxel.
