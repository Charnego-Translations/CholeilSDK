# Gráfico final «Fin.»

El dibujo cursivo que aparece después de los créditos se edita en:

`special_gfx_out/fin_EDITAME.png`

La imagen mide **96x24 píxeles**. No cambies sus dimensiones ni su paleta.
`special_gfx_out/fin_x4_VISTA.png` es únicamente una ampliación para verla
mejor; el parche no inserta ese archivo.

El lienzo ampliado se muestra centrado en pantalla: empieza en X=80, 24
píxeles a la izquierda del gráfico original. El dibujo original queda en el
centro del nuevo lienzo, con 24 píxeles transparentes a cada lado, hasta que
se edite.

## Cómo está guardado

- bloque comprimido LZ-Toshio: `0x11BE50`
- tamaño comprimido original: 282 bytes
- tamaño descomprimido original: 576 bytes, 18 tiles
- tamaño descomprimido ampliado: 1152 bytes, 36 tiles
- el lienzo ampliado inicial se comprime a 350 bytes y se reubica
- composición: cuatro sprites de 3x3 tiles, colocados uno junto al otro
- orden interno: column-major, el orden de sprites de Mega Drive
- paleta original: `0x11A518`
- puntero del banco: `0x116E50`, valor relativo `0x5050` desde `0x116E00`

La extracción general genera el PNG ordenado y con la paleta real. Durante la
inserción, `FinGraphics` lo convierte de nuevo al orden interno y
`GraphicsInserter` se ocupa de recomprimirlo. Si el bloque crece, el mecanismo
normal de reubicación actualiza su puntero.

`FinGraphics` también adapta la rutina de presentación para dibujar los cuatro
sprites en X=80, 104, 128 y 152. El conjunto ocupa X=80-175 y conserva el mismo
centro que el original de 48 píxeles.

La paleta solo se usa para interpretar los colores del PNG: **no se modifica en
la ROM**.
