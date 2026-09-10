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

## Laterales: mitad superior animada y mitad inferior fija

Cada lateral sigue ocupando **24x48 píxeles** y sigue siendo fondo, no sprites.
Sus **24x24 píxeles superiores** (3x3 tiles de 8x8) tienen dos fotogramas.
Los 24x24 inferiores permanecen fijos.

Editables de la animación, en `special_gfx_out/`:

- `sonic_lateral_izquierdo_arriba_ANIM_EDITAME.png`
- `sonic_lateral_derecho_arriba_ANIM_EDITAME.png`

Cada PNG mide **48x24**: fotograma A a la izquierda y B a la derecha, cada uno
de 24x24. Se alternan A -> B -> A, con **8 fotogramas de juego por pose**.
Las dos muchachas comparten el ritmo, pero sus dibujos se editan por separado.
Inicialmente A y B son copias del dibujo existente: no se verá movimiento
hasta que se dibuje una diferencia en B. No se han retocado los dibujos.

La parte inferior se sigue editando en los PNG anteriores:
`sonic_lateral_izquierdo_EDITAME.png` y `sonic_lateral_derecho_EDITAME.png`,
de **24x48**. Edita ahí las filas **24 a 47**; la mitad superior que se inserta
procede ahora del fotograma A del nuevo PNG de animación, no de esas filas
antiguas. Los archivos `x4_VISTA` son ampliaciones de referencia y no se insertan.
La vista completa del lateral se actualiza con A y la parte inferior fija.

El modo de inserción normal `CholeilSDK i` integra todo. Si faltan los nuevos
PNG, los crea duplicando la mitad superior anterior; si ya existen, nunca
los sobrescribe. No cambies dimensiones ni paletas.

Cada lateral tiene X/Y independientes en `sonic_scene_positions.txt`, siempre
múltiplos de 8. Las seis coordenadas son relativas a la posición original
del personaje; mover a Gil no arrastra automáticamente los laterales.
Un tile completamente vacío en **ambos fotogramas** deja intacto el fondo original.
Un tile dibujado solo en B también queda incluido en el mapa. Los tiles
dibujados se colocan detrás de Gil. Las tres figuras son sólidas: sus zonas
de colisión se generan a partir de sus respectivas coordenadas.

**No se cambia ninguna paleta.** Los PNG conservan los 16 colores originales
de la línea 1 de la playa. Mantén el PNG indexado y esa paleta, sin añadir
colores ni suavizado. El SDK rechaza una paleta diferente o una colocación
que invada tiles de otra línea de paleta. `sonic_laterales_PALETA.png` es la
referencia. La paleta del personaje tampoco se modifica.

### Huellas (corrección)

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

El pipeline vuelve a generar los dos paneles (con A) y el mapa antes de insertar.
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
La colisión no depende del fotograma; las paletas y la animación de Gil no cambian.

### Implementación de los dos fotogramas

`SonicSideAnimation` actualiza únicamente los nueve tiles superiores ya
reservados de cada lateral: VRAM `0x3E4–0x3EC` y `0x3F6–0x3FE`.
No necesita tiles VRAM adicionales ni modifica sprites, paletas, posiciones,
colisiones o metatiles de huellas. Los otros nueve tiles de cada lateral
permanecen intactos.

El enganche está en ROM `0x006828`, en la llamada de actualización del juego;
conserva la llamada original a `0x006ABE`, los registros y el `TST` de transición.
Solo se ejecuta en la playa (`0x1A`), sin transición pendiente, cada ocho
fotogramas del contador de juego. La pausa usa otro bucle y no ejecuta la
copia. Al salir de la playa no se escriben estos slots en otras habitaciones.

Código y cuatro imágenes de 288 bytes ocupan 1408 bytes en el relleno original
`0x15DA3C–0x15DFBB`; los datos comienzan en `0x15DB3C`. Se instala después de
la intro. El inserter comprueba el enganche y todo el hueco antes de escribir;
si otra modificación los ha ocupado, falla en vez de pisarla. La ROM
permanece en 2 MiB. Cada actualización copia 576 bytes con la paleta existente.

### Comprobar

Después de compilar y ejecutar `CholeilSDK i`:

```text
java -cp target/classes net.krusher.graphics.SonicHammockGraphics verify-scene Choleil.md
java -cp target/classes net.krusher.graphics.SonicSideAnimation verify Choleil.md
```

Comprueba los PNG, posiciones, referencias de mapa, paletas, las tres zonas
sólidas y las 32
definiciones/atributos de las huellas. `SonicHammockGraphicsTest` cubre la
reserva, detección de corrupción, migración de ambas versiones anteriores
y recolocación X/Y sin tocar otros campos del sprite. También comprueba
que las tres figuras bloqueen el paso, que el suelo exterior quede intacto
y que moverlas elimine sus colisiones antiguas.
`SonicSideAnimationTest` cubre ambas composiciones, conservación de la mitad
inferior, tiles visibles solo en B, orden de los cuatro gráficos, dimensiones,
enganche, reserva ocupada e inserción repetible sin tocar datos ajenos.
Suite completa ejecutada con Java 24: **97 pruebas correctas, ninguna omitida**.

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

Prueba de animación en BizHawk 2.11.1: una copia de prueba con dibujos A/B
distinguibles produjo 64 muestras de A y 64 de B en 128 fotogramas, con
cambios cada ocho. Se comprobaron los 288 bytes de cada mitad superior en
cada muestra, sin cambiar las mitades inferiores, los gráficos de Gil ni
la CRAM. Cero cargas de esta animación durante la pausa y en otra habitación;
reanuda al salir de la pausa y al volver a la playa. Los colores de esa
prueba no están en los editables ni en la ROM final, cuyo B inicial es igual a A.
La ROM final supera también las siete aproximaciones de colisión y sigue
generando huellas (129 llamadas). Validación en emulador, no en consola real.

Un savestate antiguo conserva RAM/VRAM antiguas: para validar hay que
recargar la habitación, no limitarse a cargar ese estado sobre la ROM nueva.
Desde una habitación, el cambio normal usa el indicador RAM `0xA4D6`;
`0xA4DE` por sí solo no vuelve a cargar sus gráficos. No modificar las
coordenadas de cámara sin actualizar su buffer circular; dejar que el juego
la siga después de recolocar al jugador.

La ROM y el IPS generado son salidas locales ignoradas por Git. **No subir IPS.**
