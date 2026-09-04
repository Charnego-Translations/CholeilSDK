# Gráfico final «Fin.»

El dibujo cursivo que aparece después de los créditos se edita en:

`special_gfx_out/fin_EDITAME.png`

La imagen mide **48x24 píxeles**. No cambies sus dimensiones ni su paleta.
`special_gfx_out/fin_x4_VISTA.png` es únicamente una ampliación para verla
mejor; el parche no inserta ese archivo.

## Cómo está guardado

- bloque comprimido LZ-Toshio: `0x11BE50`
- tamaño comprimido original: 282 bytes
- tamaño descomprimido: 576 bytes, 18 tiles
- composición: dos sprites de 3x3 tiles, colocados uno junto al otro
- orden interno: column-major, el orden de sprites de Mega Drive
- paleta original: `0x11A518`
- puntero del banco: `0x116E50`, valor relativo `0x5050` desde `0x116E00`

La extracción general genera el PNG ordenado y con la paleta real. Durante la
inserción, `FinGraphics` lo convierte de nuevo al orden interno y
`GraphicsInserter` se ocupa de recomprimirlo. Si el bloque crece, el mecanismo
normal de reubicación actualiza su puntero.

La paleta solo se usa para interpretar los colores del PNG: **no se modifica en
la ROM**.
