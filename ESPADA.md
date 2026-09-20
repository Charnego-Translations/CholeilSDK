# Espada de Corona

Hay **ocho variantes** de espada, cada una con un dibujo estático y cuatro
fotogramas para los espadazos. El octavo es la Espada Sagrada del rey.

- `special_gfx_out/espada_corona_EDITAME.png`: cuatro poses de ataque por fila,
  133×265 píxeles. Sigue siendo el PNG original editado por Scorpion-MSX.
- `special_gfx_out/espada_corona_estatica_EDITAME.png`: una pose estática por
  fila, 34×265 píxeles. Se preparó a partir de la primera pose de cada fila
  del PNG de Scorpion para que la espada no vuelva al dibujo original antes
  de atacar. Puede editarse **independientemente** de los espadazos.

Cada casilla encierra un sprite de 32×32. El rosa es el índice transparente de
Mega Drive. La cuadrícula de 1 píxel queda fuera del sprite y el insertor la
descarta. Mantén las dimensiones y la paleta de los PNG. Los archivos
`*_x4_VISTA.png` son solo vistas ampliadas; no se insertan.

## Localización confirmada

| Variante | Estática (512 bytes) | Cuatro poses de ataque (2048 bytes) |
| --- | --- | --- |
| 1 | `0x54000-0x541FF` | `0x54200-0x549FF` |
| 2 | `0x54A00-0x54BFF` | `0x54C00-0x553FF` |
| 3 | `0x55400-0x555FF` | `0x55600-0x55DFF` |
| 4 | `0x55E00-0x55FFF` | `0x56000-0x567FF` |
| 5 | `0x56800-0x569FF` | `0x56A00-0x571FF` |
| 6 | `0x57200-0x573FF` | `0x57400-0x57BFF` |
| 7 | `0x57C00-0x57DFF` | `0x57E00-0x585FF` |
| 8 (Sagrada) | `0x58600-0x587FF` | `0x58800-0x58FFF` |

No se trata de un hueco de `0x200` bytes **después** de cada banco: el dibujo
estático está **antes** de las cuatro poses. En `navajaAlbacete.State`, el
sprite visible al cargar usa los tiles `0x7A0-0x7AF`, que coinciden byte a
byte con `0x58600-0x587FF`. Los espadazos usan los tiles `0x7B0`, `0x7C0`,
`0x7D0` y `0x7E0`, que coinciden con las cuatro poses de `0x58800-0x58FFF`.
Cada sprite tiene 4×4 tiles en orden column-major. El juego reutiliza las
poses de ataque con volteos para cubrir las direcciones.

La paleta del editor procede de la línea CRAM 0 capturada durante un
espadazo y coincide con la de `navajaAlbacete.State`. El insertor modifica
únicamente los bytes de tiles indicados en la tabla, **nunca la paleta**.

Para reconstruir solo el nuevo editable estático desde la primera pose del
PNG de ataque, sin alterar este último:

`java -cp target/classes net.krusher.graphics.CoronaSwordGraphics derive-static`

El comando `extract` extrae ambos editables de una ROM, por lo que, como en
el resto del proyecto, sobrescribe los PNG actuales con los dibujos que
encuentre en ella. El comando `verify` comprueba ambos PNG contra la ROM.
