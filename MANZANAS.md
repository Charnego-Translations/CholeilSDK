# Manzanas normales y marcadores de vida

La manzana roja fija del mapa se localizo usando el snapshot 3. Ahora hay dos
editores normales separados:

- `apple_gfx_out/manzana_roja_EDITAME.png`
- `apple_gfx_out/manzana_verde_EDITAME.png`

Los dos miden 16x16. El magenta es transparente y hay que conservar exactamente
las dimensiones. Los archivos `*_x8_VISTA.png` son solo ampliaciones para verlos
mejor y no se reinsertan. El antiguo `manzana_mapa_EDITAME.png` queda como copia
de seguridad de la primera prueba, pero el pipeline ya no lo lee.

Sevilla usa ademas `apple_gfx_out/manzana_simetrica_EDITAME.png` (8x16).
El mapa refleja horizontalmente esa mitad para formar el objeto entero, por
lo que no puede mostrar el muslo diagonal de 16x16 sin rehacer el mapa.
Este editor contiene un muslo vertical propio; se puede retocar sin cambiar
su tamano. Las paletas del juego no se modifican. En Sevilla, Ibis y Rabesa
el parche adapta los colores del dibujo a los colores existentes de la zona.

### Apariciones que seguian rojas y estado del paquete

La rama conservaba el dibujo rojo original en el editor del mapa, mientras
que el editor de los sprites ya era verde. Se ha puesto el mismo dibujo verde
aprobado en ambos: no se cambia ninguna paleta ni la vida que dan los objetos.
Los dos PNG siguen siendo independientes y editables. Sus nombres historicos
`roja` y `verde` identifican las rutas de insercion, no fuerzan el color final.
Si se quiere el mismo dibujo en TODOS los objetos normales conocidos, hay que
editar los dos PNG; si se dibujan distintos, el juego los mostrara distintos.

El bloque `0x135322` se observo cargado al solicitar las salas `1A`, `6D` y
`78`. El bloque `0x14D3AE` se cargo al solicitar `31` y `32` (en el estado de
prueba, `31` se resuelve a `32`). No eran copias adicionales escondidas:
esas apariciones reutilizan los dos bloques ya catalogados.

### Construccion comprobada y espacio de los graficos

Al crecer un PNG, la recompresion puede obligar a mover todo su bloque. El
parcheador comunica ahora esas reservas a la intro para que no las pise, y
excluye la tabla viva de `0x03CE78` de los supuestos huecos libres.

El comando `i` verifica al final las seis copias del mapa, la copia sprite
comprimida, las dos raw y las cuatro posiciones doradas, siguiendo los
punteros que usa realmente el juego,
incluso si los bloques se han movido. Si una edicion no se puede insertar,
se aborta: no se acepta una mezcla silenciosa de dibujos nuevos y originales.
Se trabaja en `Choleil.building.md` y solo se reemplaza `Choleil.md` cuando
termina la verificacion. Una ROM anterior puede seguir existiendo tras un
error: no confundirla con una construccion nueva. No usar el archivo building.

La comprobacion automatica cubre los graficos conocidos, no certifica una
partida completa ni todos los estados posibles del juego. Los savestates
antiguos contienen VRAM antigua: para ver cambios hay que recargar la sala.
Los IPS son resultados locales del pipeline y no se incluyen en Git.

## Copias confirmadas

El dibujo capturado ocupa cuatro tiles consecutivos, en orden de lectura
normal (arriba izquierda, arriba derecha, abajo izquierda, abajo derecha).
La primera pareja esta duplicada en dos tilesets LZ-Toshio:

| bloque ROM | tiles internos | tamano descomprimido |
|---|---:|---:|
| `0x135322` | 459-462 | 15.872 bytes (496 tiles) |
| `0x14D3AE` | 390-393 | 15.360 bytes (480 tiles) |

El pipeline `i` copia automaticamente `manzana_roja_EDITAME.png` a esas dos
ubicaciones comprimidas antes de recomprimir los graficos.

Los cinco savestates adicionales han revelado otras rutas:

| partida | origen LZ de ROM | tiles internos | editor |
|---|---:|---:|---|
| `manzanaSevilla.State` | `0x1528E4` | 328 y 391, reflejados | `manzana_simetrica_EDITAME.png` |
| `manzanaIbis.State` | `0x12FE58` | 392, 393, 408, 409 | `manzana_roja_EDITAME.png` |
| `manzanaOtrolao.State` | `0x0F4800` | 0-3, orden por columnas | `manzana_roja_EDITAME.png` |
| `manzanaRabesa.State` | `0x1260CA` | 138, 139, 154, 155 | `manzana_roja_EDITAME.png` |

Un barrido de los 508 bloques graficos catalogados encontro ademas el bloque
`0x132946`, tiles 350, 351, 366 y 367. Es la misma manzana que el mapa
original dibuja *sobre* un tile de terreno, con fondo y sombra incorporados.
El parche borra solo la manzana antigua, conserva el terreno y la sombra, y
compone encima el editor rojo. Esta copia no aparecia en las cinco partidas.

Se volvio a escanear la ROM original de 2 MiB: contiene 508 bloques LZ validos,
exactamente los 508 del catalogo. La comparacion de formas de 16x16, ignorando
permutaciones de indices de color, encontro esas seis copias de mapa y el
paquete sprite. La busqueda equivalente de 24x24 encontro solamente los cuatro
fotogramas del bloque dorado `0x0A5644`; la variante reflejada de Sevilla
tambien se busco por sus dos tiles y no aparecio en otro bloque. Los dos
objetos raw de `0xF4700` y `0xF4780` son las unicas copias exactas de 128 bytes
de esa familia en la ROM original. Esto cubre todas las copias localizadas por
el barrido; una manzana con arte completamente distinto no quedaria demostrada
sin verla en el juego.

La de Otrolao es un sprite: en el savestate aparece en VRAM como los tiles
`0x6BA-0x6BD`. El bloque `0x0F4800` se carga tambien en las otras cuatro
pruebas, de modo que el cambio puede cubrir mas manzanas de esa familia.

Ademas, el cargador de esta familia copia de una vez `0x0F4600-0x0F47FF` a VRAM
`0x7F0-0x7FF`. Sus dos ultimas figuras son otras dos copias exactas de la
manzana roja, en `0x0F4700` y `0x0F4780`, almacenadas en orden column-major.
El pipeline copia `manzana_verde_EDITAME.png` solamente en estas dos posiciones
raw. Asi roja y verde dejan de sobrescribirse entre ellas. Son las posiciones
preparadas para objetos sprite, como las manzanas soltadas por enemigos.

La edicion inicial verde reutiliza los verdes ya presentes en la misma paleta.
No se modifica la logica: las manzanas siguen dando la misma vida y solo cambia
su aspecto.

## Que son las manzanas verdes

Las manzanas verdes no son objetos que haya que recoger. Son los iconos con los
que el juego representa la vida de los enemigos. Las rojas del marcador son la
vida del protagonista, y las doradas aumentan su vida maxima.

Los dibujos rojos y verdes del marcador tambien estan localizados en el bloque
`0x0F2000`: el grupo rojo ocupa los tiles 144-159 y el verde los tiles 160-175.
Son graficos de 8x8 repetidos para formar el indicador, no el objeto 16x16 que
aparece colocado en el mapa.

## Alcance confirmado

Quedan localizadas seis copias del objeto rojo de mapa, el sprite comprimido,
las dos posiciones raw separadas para la verde y los iconos rojos/verdes del
marcador. Una manzana soltada por un
enemigo aun no se ha capturado en BizHawk; falta confirmar visualmente que esas
posiciones raw son las que emplea ese caso concreto.

## Manzana dorada: editor independiente

El snapshot 5, grabado en Choleil con la dorada junto al agua, permitio seguir
la entrada real de la tabla de sprites. Es un sprite de 24x24 (3x3 tiles).
Primero se confirmaron estas dos posiciones:

| fotograma | tiles VRAM | fuente descomprimida |
|---|---:|---:|
| brillo arriba | `0x4D4-0x4DC` | bloque `0x0A5644`, tiles 9-17 |
| brillo izquierda | `0x4E6-0x4EE` | bloque `0x0A5644`, tiles 27-35 |

La busqueda byte a byte de los 288 bytes de cada fotograma no encuentra una
copia raw en la ROM. Al descomprimir todos los bloques catalogados, ambos
aparecen exactamente una vez y en esas posiciones.

El archivo comodo para editar los DOS fotogramas es:

`apple_gfx_out/manzana_dorada_EDITAME.png`

Mide 48x24: dos figuras de 24x24, una al lado de la otra. El magenta es
transparente y no se deben cambiar las dimensiones. La ampliacion
`manzana_dorada_x8_VISTA.png` es solo de consulta y no se reinserta. El pipeline
`i` coloca automaticamente ambas figuras en `gfx_out/gfx_0a5644.png` antes de
recomprimir el bloque. Ahora tambien cubre las otras dos posiciones fisicas:
los tiles 0-8 reciben la primera pose y los tiles 18-26 reciben la segunda.
Asi las cuatro posiciones muestran el muslo sin cambiar la paleta ni el editor.
`manzanaDorada.State` usa precisamente los tiles 0-8, antes sin editar.

Esto separa la dorada confirmada de los PNG de la moneda. El grafico de
`0x0F4680` fue una falsa identificacion: se carga con el paquete de objetos y
se parece a lo que hay en pantalla, pero la tabla de sprites demuestra que la
dorada visible no lo referencia.

La prueba final se hizo recargando la sala del snapshot 5 para obligar a
BizHawk a leer los graficos de la ROM reconstruida; la dorada volvio a aparecer
correctamente. El editor queda verificado byte a byte en las cuatro posiciones.

Este resultado cubre la variante observada en Choleil. No se afirma aun que
una variante de otra subzona use el mismo bloque: si aparece distinta, se
seguira su entrada de sprite y se añadira como otra copia al mismo editor.
