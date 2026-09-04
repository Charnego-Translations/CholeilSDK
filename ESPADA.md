# Espada de Corona

Las ocho variantes de espada y sus cuatro dibujos de animación se editan juntas en:

`special_gfx_out/espada_corona_EDITAME.png`

El PNG mide **133x265 píxeles**. Cada fila es una variante de espada y contiene
cuatro poses editables de 32x32, de izquierda a derecha: vertical, diagonal,
casi horizontal y horizontal. El fondo rosa representa el índice transparente
de Mega Drive. La cuadrícula de 1 píxel encierra cada pose por fuera y es solo
una guía: el insertor la descarta, aunque se pinte accidentalmente. No cambies
las dimensiones ni la paleta.
`espada_corona_x4_VISTA.png` es solo una ampliación para verla cómodamente y no
se inserta.

## Localización confirmada

- datos sin compresión, ocho bancos:
  - variante 1: `0x54200-0x549FF`
  - variante 2: `0x54C00-0x553FF`
  - variante 3: `0x55600-0x55DFF`
  - variante 4: `0x56000-0x567FF`
  - variante 5: `0x56A00-0x571FF`
  - variante 6: `0x57400-0x57BFF`
  - variante 7: `0x57E00-0x585FF`
  - variante 8: `0x58800-0x58FFF`
- cada banco contiene cuatro poses consecutivas de 512 bytes
- los bancos empiezan cada `0xA00`; los `0x200` bytes restantes entre bancos
  no pertenecen a las espadas y el insertor no los toca
- cada pose: sprite de 4x4 tiles en orden column-major
- al atacar se cargan en VRAM como tiles `0x7B0`, `0x7C0`, `0x7D0` y `0x7E0`
- el juego cubre todos los ángulos seleccionando una pose y aplicando volteos
  horizontal/vertical; no existen ocho dibujos independientes

La paleta del PNG procede de la línea CRAM 0 capturada en el snapshot 4. Solo
sirve para conservar correctamente los índices de color al editar. El
insertor escribe exclusivamente los 2048 bytes de tiles de cada variante y
**no modifica la paleta de la ROM**.
