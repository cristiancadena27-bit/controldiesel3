# Control de diésel (versión 2.2)

App Android (Kotlin + Jetpack Compose) que estima el diésel de la camioneta sin OBD.

## Novedades de la v2
- Pestañas: Inicio, Historial y Ajustes (secciones desplegables).
- Colores nuevos, icono propio y confirmaciones al tocar los botones.
- Vehículo: nombre, modelo, año, placa, capacidad y características.
- Historial de viajes con imagen del recorrido, km, diésel y dólares; historial de cargas con fecha y valor.
- Costo en dólares de cada recorrido (usa el precio de la última carga o el precio por galón de Ajustes).

## Cómo compilar (GitHub Actions)
1. Sube el contenido de esta carpeta al repositorio (reemplaza los archivos). Incluye `.github/workflows/build.yml` y `app/debug.keystore`.
2. Pestaña Actions > Build APK > Run workflow.
3. Descarga el artefacto ControlDiesel-APK (contiene app-debug.apk).

## Actualizar sin perder datos
- La v2 se firma con `app/debug.keystore`, que va en el repositorio. Así cada APK nuevo se instala encima del anterior y conserva los datos.
- La primera vez (la v1 se firmó con otra clave) hay que desinstalar la app antes de instalar la v2.

## Novedades de la v2.2 (registro automático)
- El recorrido ahora arranca también con el Bluetooth del vehículo (Ajustes > Detección automática).
- Si el sistema cierra la app a mitad de camino, el recorrido se recupera y se guarda.
- El GPS parado ya no suma kilómetros falsos.
- Registro de eventos en Ajustes para ver por qué arrancó o no.

## Novedades de la v2.1
- Mantenimiento (Ajustes): aceite, filtros, balanceo y alineación por km; en Inicio se ve cuánto falta y llega una notificación antes de que venza.
- Avisos a otros celulares con ntfy (Ajustes > Avisos a otros celulares): el otro teléfono instala la app ntfy y se suscribe al tema.
- Se quitó el envío por WhatsApp; en el historial queda el botón "Compartir resumen".
- Permiso nuevo: internet (solo para enviar los avisos).
