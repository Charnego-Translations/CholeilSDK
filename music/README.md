# Música de la carrera del kart

Fuente: `kart_race.vgm`, copia sin modificar del archivo que aportó Antxiko,
`A-Team - 01 - Title.vgm`. Aunque lleva extensión `.vgm`, está comprimido
con gzip. Sus metadatos GD3 indican **Title**, **The A-Team**, Sega Master
System, Jeroen Tel (1992), extracción de Maxim. Se conservan esos metadatos
en el archivo fuente.

SHA-256: `a36490e0e5627111830bdc1d405742929a735dcf15b28a7c57aa03c6988da661`.

## Uso

La reconstrucción normal `choleil i` incorpora el tema automáticamente.
Con las clases compiladas también sirve, sin Maven:

```powershell
java -cp target/classes net.krusher.CholeilSDK i
java -cp target/classes net.krusher.graphics.KartRaceMusic verify Choleil.md
```

Para desactivarlo, guarda el VGM fuera de esta ruta y reconstruye desde el
original. No se retira de una ROM ya parcheada ni de una sesión del emulador
sin reconstruir y volver a cargar la ROM. La inserción aislada está deshabilitada:
necesita conocer las reservas de gráficos y texto de la compilación completa.

Funciona independientemente de `hide_driver` y del PNG del kart. Empieza al
subir, incluye la cuenta atrás y la llegada, y al terminar o abandonar con
Start el cargador del mapa recupera la música original. No modifica la tabla
compartida de canciones de los mapas ni el código Z80 de los efectos.

## Formato y temporización

Este archivo usa únicamente el PSG SN76489: 5.032 escrituras, 1.322.118 muestras
a 44.100 Hz, bucle completo de 29,98 segundos nominales. No contiene YM2612.
Se reproducen sus registros directamente en el PSG de Mega Drive, no como PCM.

El importador admite VGM 1.10–1.50 PSG monofónico, comprimido o sin comprimir,
con bucle válido, reloj 3.579.545 o 3.546.895 Hz, feedback 9 y anchura 16.
Admite escrituras `50`, estéreo `4F FF` (todos los canales), esperas
`61/62/63/70..7F` y fin `66`. Rechaza otros chips/comandos, estéreo parcial,
cabeceras inconsistentes, archivos truncados y secuencias demasiado densas.
No es un reproductor universal de VGM.

Las esperas se acumulan en muestras y se cuantizan al fotograma: 882 en PAL,
735 en NTSC, conservando la fracción pendiente. La afinación y duración real
dependen del reloj/frecuencia de vídeo de la consola; no se altera el reloj PSG.
Hay dos fotogramas de margen inicial para que el controlador original procese
la orden de parada. La comprobación en emulador se ha realizado en PAL;
la acumulación NTSC se comprueba por tests, no por una sesión jugada.

## Reserva y seguridad

`KartRaceMusic` engancha los 8 bytes de `0x020692`, entrada del actor `0x27`.
Conserva registros y SR y ejecuta las dos instrucciones desplazadas. Usa
únicamente los 12 bytes finales del actor (`+0x34..+0x3F`) para identidad del
tema, puntero y espera; **no usa `+0x30`, que es el contador de la carrera**.
Cambiar el VGM invalida esa identidad en snapshots anteriores y reinicia el tema.

El bloque ocupa 12.280 bytes con este VGM. Se reserva antes de la intro en uno
de sus grandes huecos verificados, excluyendo las reservas completas de gráficos
y texto, y comprobando que no se hayan escrito otros datos. La intro recibe
la reserva completa, incluidos ceros/relleno. En esta compilación queda en
`0x11BF6A..0x11EF61`; no asumir esa dirección para otras ediciones gráficas.

Código y música permanecen por debajo de 2 MiB para que los snapshots antiguos
de BizHawk/GPGX no oculten el reproductor al restaurar su mapa de memoria.
La intro utiliza además una reserva exclusiva en `0xF8000..0xFD000`; la
construcción final debe medir exactamente 2 MiB y falla si cualquier pieza se
derrama fuera. No se modifica el límite de ROM de la cabecera usado por la
autocomprobación de Soleil.

La compilación valida de nuevo hook, código y todos los paquetes después de
los demás parches, antes de publicar `Choleil.md`; si falta espacio o hay un
conflicto, falla sin publicar una ROM parcial. `KartRaceMusicTest` comprueba
conversión exacta, bucles PAL/NTSC, reservas, corrupción y fallos sin escritura.

Comprobado en BizHawk: arranque desde cero, snapshot antiguo a pie, cuenta
atrás, conducción, un bucle completo, salida con Start y transición de llegada.
La llegada se forzó en la prueba; no se jugó una victoria completa.
También se capturaron audio y 574 escrituras PSG consecutivas, coincidentes
con el VGM y sus tiempos cuantizados. Falta la escucha y prueba del usuario.

Las ROM, IPS, capturas de audio y savestates de prueba son salidas locales:
**no se incluyen en Git**. El VGM sí es una fuente necesaria de este cambio.
