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

## Los dos laterales

> **Todavía no funciona.** En el juego los paneles siguen pisando tiles de VRAM
> que la consola está usando. Queda aparcado: lo de abajo describe cómo está
> pensado, pero la lista de huecos elegidos no es de fiar.


`special_gfx_out/sonic_lateral_EDITAME.png` mide **24x48** y es el dibujo que
aparece a **los dos lados** de Jesús Gil. Solo se dibuja una vez: el de la
derecha es el mismo, reflejado por el SDK con el bit de espejo horizontal de la
Mega Drive. Por eso no ocupa el doble de memoria y por eso siempre queda
simétrico.

- Son tiles del fondo, no sprites, así que no se animan.
- El PNG lleva incrustada la paleta original de esos tiles (línea CRAM 1 de la
  playa). Usa solo esos 16 colores, no cambies el tamaño y dibuja con lápiz
  duro, sin suavizado.
- Un tile completo de 8x8 que se quede entero en el color 0 no se toca: ahí
  sigue viéndose la playa original.
- La prioridad se deja como fondo, de modo que los dibujos quedan **detrás** de
  Jesús Gil.
- `sonic_laterales_PALETA.png` es una tira con los 16 colores, solo de consulta.
- El archivo que se genera la primera vez lleva un dibujo de relleno, a
  propósito feo, para que se vea dónde caen los paneles antes de ponerse a
  dibujar. Una vez que existe, el SDK **no lo vuelve a tocar**.

### Colocarlos

En `sonic_scene_positions.txt`:

```text
lateral_x=-24
lateral_y=0
```

`lateral_x` es lo que se separa el panel izquierdo del borde de Jesús Gil, y el
derecho se coloca solo, en espejo. Ambos valores son múltiplos de 8 porque son
tiles. Conviene además que `lateral_y` sea múltiplo de 16: así el panel entra
en tres filas de metatiles en vez de cuatro, y el mapa comprimido no crece de
más. Si un panel se sale de la zona que usa la paleta original, la compilación
se detiene y lo dice.

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
- Los laterales **no** amplían ningún bloque. Sus 18 tiles se escriben en
  huecos que ya existen dentro del tileset de la sala (bloque LZ-Toshio
  `0x135322`, entrada 11 del archivo de tilesets en `0x120000`).
- Elegir esos huecos tiene truco, y se falló dos veces. El bloque tiene 496
  tiles pero el juego solo transfiere unos 486: la cola es relleno que nunca
  llega, y la consola reutiliza esa VRAM para otras cosas. Un tile **en blanco**
  no demuestra nada, porque en blanco en la ROM y en blanco en VRAM es lo mismo
  se haya transferido o no. Solo vale un hueco con **dibujo de verdad** que
  aparezca idéntico en la VRAM de un savestate: eso prueba que la transferencia
  llega hasta ahí. Los 18 elegidos están todos por debajo del tile `0x1E5` y no
  los usa ningún metatile de la sala, así que machacar su dibujo no se ve.
- El mapa de la sala (`0x178E7A`) se reconstruye entero desde la ROM original
  en cada compilación, así que mover los paneles no deja restos.
