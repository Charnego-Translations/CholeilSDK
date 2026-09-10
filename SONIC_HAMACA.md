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

Edita `sonic_scene_positions.txt`. Las coordenadas son offsets en píxeles
respecto a la posición original de SonicGil:

```text
sonic_x=-24
sonic_y=0

izquierdo_x=-40
izquierdo_y=-8
derecho_x=16
derecho_y=-8
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
- Tamaño original: 3456 bytes = 108 tiles. Con laterales: 4672 bytes = 146 tiles.
- Tres poses de 36 tiles cada una.
- Cada pose usa cuatro sprites Mega Drive de 3x3 tiles para formar un mosaico
  de 48x48.
- En VRAM aparecen en `0x6EC0-0x7C3F`; la tabla de sprites alterna los cuatro
  índices de tile para animar la mano y los pies.
- Los offsets X/Y de los cuatro sprites se modifican desde
  `sonic_scene_positions.txt`; admiten precisión de un píxel.

## Laterales estáticos y huellas (corrección)

Los editables son `special_gfx_out/sonic_lateral_izquierdo_EDITAME.png` y
`special_gfx_out/sonic_lateral_derecho_EDITAME.png`, **24x48 cada uno**.
Son tiles del fondo, no sprites ni animaciones. Los archivos `x4_VISTA`
son ampliaciones y no se insertan.

Cada lateral tiene X/Y independientes en `sonic_scene_positions.txt`, siempre
múltiplos de 8. Las seis coordenadas son relativas a la posición original
del personaje; mover a Gil no arrastra automáticamente los laterales.
Un tile completamente vacío deja intacto el fondo original. Los tiles
dibujados se colocan detrás de Gil. Las tres figuras son sólidas: sus zonas
de colisión se generan a partir de sus respectivas coordenadas.

**No se cambia ninguna paleta.** Los PNG conservan los 16 colores originales
de la línea 1 de la playa. Mantén el PNG indexado y esa paleta, sin añadir
colores ni suavizado. El SDK rechaza una paleta diferente o una colocación
que invada tiles de otra línea de paleta. `sonic_laterales_PALETA.png` es la
referencia. La paleta del personaje tampoco se modifica.

La versión anterior reutilizaba metatiles `0x3E0–0x3EF` (y una primera
versión, `0x3F1–0x3FC`). **No estaban libres**: la rutina de pisadas de la ROM
en `0x00B204` calcula IDs `0x3E0–0x3FF` al caminar, aunque no aparezcan como
referencias estáticas. En `0x00B22A` ejecuta `ADDI.W #$3E0,D0`.

Ahora la escena reserva **`0x3C0–0x3DF`**, comprobando que sus definiciones
y atributos de terreno estén vacíos y que el mapa original no los referencie.
La reserva anterior `0x3D0–0x3DF` se ha ampliado para incluir las celdas
de colisión de Gil, sin ocupar las huellas ni más espacio gráfico en VRAM.
Se restauran las 32 definiciones de huellas y sus atributos de terreno si se
inserta desde un mapa generado por las versiones antiguas.

No hay que confundir IDs de metatile (16x16) con índices de tile VRAM (8x8):

- Personaje: tiles VRAM `0x376–0x3E1`, sin cambiar sus 108 tiles de animación.
- Separación: dos tiles, `0x3E2–0x3E3`.
- Lateral izquierdo: VRAM `0x3E4–0x3F5`.
- Lateral derecho: VRAM `0x3F6–0x407`.
- Mapa LZ: `0x178E7A`, tabla de terreno en `+0x2000`, celdas desde `+0x2C04`.
- Posición original superior izquierda: tile de mundo `(98,78)`.

El pipeline vuelve a generar los dos paneles y el mapa antes de insertar.
El movimiento vertical usa los cuatro campos Y reales (`0x2F858`,
`0x2F860`, `0x2F868`, `0x2F870`); no toca el puntero de animación en `0x2F850`.

### Colisión de Gil y las muchachas

Gil tiene una caja de 48x48 y cada muchacha una de 24x48, ancladas en las
mismas X/Y que sus gráficos. El motor resuelve la colisión por metatiles
de **16x16**: las cajas se redondean hacia fuera a esa cuadrícula. No es
colisión por píxel; los huecos transparentes dentro de cada caja también
quedan bloqueados. Al recolocar una figura y regenerar la ROM se elimina
su colisión anterior y se crea en la posición nueva, sin dejar paredes invisibles
en el lugar antiguo.

Las celdas son copias locales con terreno `0x0003`, el mismo tipo de obstáculo
no transitable que usan metatiles originales de la playa como `0x7B/0x7C`.
No se modifica la definición compartida del suelo ni se añaden efectos de
daño, rotura o pisadas. Fuera de las cajas se conserva el terreno original.
Los gráficos, las paletas y las animaciones no cambian.

### Comprobar

Después de compilar y ejecutar `CholeilSDK i`:

```text
java -cp target/classes net.krusher.graphics.SonicHammockGraphics verify-scene Choleil.md
```

Comprueba los PNG, posiciones, referencias de mapa, paletas, las tres zonas
sólidas y las 32
definiciones/atributos de las huellas. `SonicHammockGraphicsTest` cubre la
reserva, detección de corrupción, migración de ambas versiones anteriores
y recolocación X/Y sin tocar otros campos del sprite. También comprueba
que las tres figuras bloqueen el paso, que el suelo exterior quede intacto
y que moverlas elimine sus colisiones antiguas.
Suite completa ejecutada con Java 24: **87 pruebas correctas, ninguna omitida**.

Prueba realizada en BizHawk 2.11.1 con una copia de la ROM: recarga real de
la habitación, desplazamiento, pausa/salida y caminata en arena. Se registraron
453 llamadas a la rutina de huellas. Sus 32 definiciones en RAM, sus píxeles
en VRAM y la CRAM completa coinciden con la ROM original. Los tiles de los
paneles permanecen idénticos al bloque insertado tras caminar y pausar;
en la ejecución de control, esos slots VRAM estaban vacíos.

Prueba adicional de solidez en BizHawk: tres aproximaciones desde abajo,
las dos muchachas desde los extremos exteriores, Gil desde arriba y Gil
después de pausar/salir. **Las siete quedan bloqueadas**; en la ROM anterior
las siete atravesaban las figuras. La caminata posterior sigue generando
huellas (129 llamadas). Las definiciones/atributos de las 32 huellas, sus
píxeles, los gráficos de las figuras y toda la CRAM permanecen idénticos a
la versión anterior, antes y después de pausar.

Un savestate antiguo conserva RAM/VRAM antiguas: para validar hay que
recargar la habitación, no limitarse a cargar ese estado sobre la ROM nueva.
Desde una habitación, el cambio normal usa el indicador RAM `0xA4D6`;
`0xA4DE` por sí solo no vuelve a cargar sus gráficos. No modificar las
coordenadas de cámara sin actualizar su buffer circular; dejar que el juego
la siga después de recolocar al jugador.

La ROM y el IPS generado son salidas locales ignoradas por Git. **No subir IPS.**
