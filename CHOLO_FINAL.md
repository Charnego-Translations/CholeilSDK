# Cholo, enemigo final

El enemigo luminoso que aparece en `choloFinal.State` usa dos bloques Toshio.
El retrato base esta en `0x0A2F56`: al cargarse ocupa los tiles VRAM
`0x324-0x363` y se monta como cuatro sprites de 32x32 para formar una imagen
total de 64x64. El ojo animado procede del bloque `0x0A33E0`; sus estados de
16x16 llegan a VRAM `0x364-0x373`.

El grafico editable es:

- `special_gfx_out/cholo_final_EDITAME.png` (64x64, tamano real)
- `special_gfx_out/cholo_final_x8_VISTA.png` (ampliacion, no se reinserta)
- `special_gfx_out/cholo_final_boca_EDITAME.png` (64x16: cerrado,
  abriendose, abierto y capa trasera, de izquierda a derecha)
- `special_gfx_out/cholo_final_boca_x8_VISTA.png` (los cuatro estados ampliados)

El retrato representa la cara cerrada de Simeone, centrada dentro del halo y
con los ojos mirando siempre hacia abajo, donde se encuentra Corona durante el
combate. Esos ojos pertenecen al retrato base y no cambian entre estados. La
unica diferencia animada es la boca. El estado cerrado del ojo original
(`0x364`) y su antigua capa trasera (`0x370`) son transparentes, por lo que
queda visible la boca cerrada del retrato base. Cuando Dodo empieza a sacar el
ojo se usa `0x368`, ahora una boca entreabierta; el ojo completamente expuesto
usa `0x36C`, ahora la boca abierta con la lengua. La punta rosa de la lengua
ocupa exactamente el centro golpeable del ojo original.

Para colocar todos los estados a la altura anatomica de la boca, el pipeline
cambia sus campos Y (`0x02E0B8`, `0x02E0F6`, `0x02E134` y `0x02E142`) de `-8`
a `+2`, es decir, los baja 10 pixeles. No se modifica la logica ni la caja de
impacto del enemigo.

La paleta es la linea 3 capturada en la pelea final. El pipeline conserva esa
paleta: solo modifica indices de tiles, nunca CRAM. El color negro exterior es
el indice transparente. No se deben cambiar las dimensiones de los PNG ni
sustituir sus paletas. En especial, la animacion de la boca depende de conservar
los indices repetidos de la paleta del PNG, no solo sus colores visibles.

Antes de recomprimir, `CholoFinalGraphics` convierte el mosaico amigable al
tile sheet generico `gfx_out/gfx_0a2f56.png`. Si el bloque crece al comprimirse,
el insertador puede recolocarlo mediante su puntero de tabla en `0x05935C`.
La boca se copia sobre los tiles 0-15 del bloque generico
`gfx_out/gfx_0a33e0.png`: 0-3 cerrado, 4-7 abriendose, 8-11 abierto y 12-15
capa trasera. Se conservan intactos sus otros 20 tiles. Su puntero de tabla
esta en `0x059360`. Al terminar, el pipeline descomprime las dos direcciones
vivas y comprueba tanto los 64 tiles del retrato como los cuatro estados de la
boca contra los PNG editables.

El pipeline mantiene la ROM final en exactamente 16 Mbit / 2.097.152 bytes. Si
una futura edicion no cabe, la compilacion falla en vez de publicar una ROM mas
grande.
