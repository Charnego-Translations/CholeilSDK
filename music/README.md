# Música de la carrera del kart

Fuente: `kart_race.vgm`, copia sin modificar del archivo aportado por Antxiko,
`a-team_md.vgm` (30.684 bytes). Es un VGM 1.50 nativo de Mega Drive, no el
tema PSG de Master System que se usaba antes.

SHA-256: `76cbf36687584f119cba079773af4946e177431e38abcc3a1a89a242ce5b37cb`.

## Uso

La reconstrucción normal `choleil i` incorpora el tema automáticamente. Con
las clases compiladas también sirve, sin Maven:

```powershell
java -cp target/classes net.krusher.CholeilSDK i
java -cp target/classes net.krusher.graphics.KartRaceMusic verify Choleil.md
```

Para desactivarlo, guarda el VGM fuera de esta ruta y reconstruye desde la ROM
original. La inserción aislada está deshabilitada porque necesita conocer las
reservas de gráficos, texto e intro de la compilación completa.

El tema empieza al subir a la fragoneta, incluye la cuenta atrás y la carrera,
y se repite. Al terminar o abandonar con Start, el cargador del mapa recupera
la música original. No modifica la tabla de canciones compartida por otras
salas y es independiente de `hide_driver` y del PNG del vehículo.

## Formato y reproducción

El VGM dura 3.005.856 muestras a 44.100 Hz: **68,16 segundos** con bucle
completo. Contiene 8.318 escrituras en el orden original:

- 5.105 al PSG SN76489, reloj 3.579.545 Hz.
- 2.112 al puerto 0 del YM2612.
- 1.101 al puerto 1 del YM2612, reloj 7.670.454 Hz.

El reproductor conserva cada escritura y su tiempo exacto. El 68000 detiene el
tema original, solicita brevemente el bus del Z80 cuando toca emitir datos,
escribe ambos chips y libera el bus en el mismo fotograma. Mientras posee el
bus enmascara interrupciones y usa una espera fija entre dirección y dato del
YM2612; no consulta su bit `busy`. El PSG se escribe directamente como antes.

Las esperas se acumulan en muestras y se cuantizan al fotograma: 882 en PAL y
735 en NTSC, conservando siempre la deuda restante. El importador admite VGM
1.10–1.50, crudo o gzip, con bucle, PSG Sega y YM2612 de Mega Drive. Para este
formato compacto, las esperas deben ser múltiplos exactos de 441 muestras y no
puede haber más de 255 escrituras en un mismo instante. Rechaza otros chips,
relojes, estéreo parcial, comandos desconocidos, secuencias demasiado densas,
cabeceras incoherentes y archivos truncados.

## Compresión y espacio

No se usa Toshio: ese compresor sirve para bloques gráficos que se descomprimen
enteros, mientras que la música debe leerse durante la carrera. `KartRaceMusic`
aplica un formato reproducible específico para streaming:

- esperas expresadas en unidades de 441 muestras;
- tipo de chip empaquetado con 2 bits por escritura;
- diccionario de 208 paquetes repetidos y literales para el resto.

El flujo convertido ocupa **14.501 bytes**. Código, cabecera y datos forman un
bloque de **15.525 bytes**, frente a los 30.684 bytes del VGM fuente. Queda en
`0x11BF6A..0x11FC0E`, dentro del primer hueco verificado y con 1.009 bytes libres
hasta `0x120000`. La intro recibe la reserva completa y no se desplaza fuera del
cartucho. La ROM final sigue midiendo exactamente **16 Mbit / 2.097.152 bytes**.

El código engancha los 8 bytes de `0x020692`, entrada del actor `0x27`, conserva
registros y SR, y reproduce las dos instrucciones desplazadas. Usa únicamente
los 12 bytes finales del actor (`+0x34..+0x3F`) para identidad del tema, puntero
y deuda temporal; no toca `+0x30`, que es el contador de carrera. Al cambiar el
VGM, la identidad también cambia y los snapshots viejos reinician la canción.

## Comprobaciones

`KartRaceMusicTest` reconstruye los 8.318 eventos y compara chip, registro,
valor y muestra con el VGM; también verifica el diccionario, el bucle PAL/NTSC,
la asignación de espacio, corrupciones y fallos sin escritura.

En BizHawk se cargó la carrera durante 500 fotogramas: el reproductor ejecutó
510 veces y emitió 1.006 escrituras PSG, 403 pares al puerto 0 y 270 pares al
puerto 1 del YM2612 sin cuelgue. Las primeras escrituras observadas coinciden
con la inicialización del VGM. Otra ejecución de 3.800 fotogramas confirmó que
el puntero completa los 68,16 segundos, vuelve una vez al inicio del bucle y
sigue emitiendo datos. Esto confirma ejecución y datos; la valoración audible
final corresponde a la prueba del usuario.

Las ROM, IPS, capturas, audio y savestates son salidas locales y **no se incluyen
en Git**. El VGM sí es una fuente necesaria del cambio.
