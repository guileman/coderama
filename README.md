# Coderama

**Escaneá, compará y cuidate.** App para Android que lee el código de barras de un producto y muestra:

- **Precios** en los supermercados de tu zona, con datos oficiales de Precios Claros (SEPA).
- **Nutrición** con gráficos, sellos de la Ley de Etiquetado Frontal y cuánto de tu requerimiento diario cubre.
- **Alertas** por dieta (sin TACC, sin lactosa, vegano, etc.), aptitud para niños, calorías, sustancias peligrosas en limpieza y cosmética, y avisos de la ANMAT.
- **Ficha** del producto, con lectura de la etiqueta de eficiencia energética en electrodomésticos.

Si el producto no está en ninguna base, se le saca una foto a la etiqueta y la app lee el texto en el propio teléfono.

## Cómo instalarla

1. Entrá a la pestaña **Releases** del repositorio desde el teléfono.
2. Descargá `coderama.apk` de la versión **ultima**.
3. Abrilo y aceptá instalar desde fuentes desconocidas.

Cada vez que se sube código, GitHub Actions compila un APK nuevo (workflow **Compilar APK**). Las versiones nuevas se instalan encima de la anterior sin perder los datos.

## Configuración única: precios

Los precios se procesan todos los días en GitHub y se publican en GitHub Pages. Hay que activarlo una sola vez:

1. **Settings → Pages → Build and deployment → Source: GitHub Actions.**
2. **Actions → Datos de precios (SEPA) → Run workflow** para la primera carga (tarda unos minutos).

Después corre solo todos los días a las 11:17 (hora argentina).

## Estructura

| Carpeta | Qué hay |
|---|---|
| `app/` | App Android (Kotlin + Jetpack Compose, CameraX, ML Kit) |
| `scripts/procesar_sepa.py` | Baja la base SEPA y genera un archivo de precios por localidad |
| `scripts/alertas_anmat.py` | Junta las alertas recientes de la ANMAT |
| `.github/workflows/` | Compilación del APK y actualización diaria de precios |

## Fuentes de datos

- Precios Claros – Base SEPA, Secretaría de Comercio (CC BY 4.0).
- Open Food Facts, Open Beauty Facts y Open Products Facts (ODbL).
- Alertas ANMAT (argentina.gob.ar).

La información es orientativa: no reemplaza el rótulo del producto ni la consulta con un profesional de la salud.
