#!/usr/bin/env python3
"""
Procesa la base SEPA (Precios Claros) y genera archivos livianos por localidad
para la app Coderama.

Salida:
  <salida>/index.json                     provincias, localidades y cadenas
  <salida>/p/<ISO>/<localidad>.json       precios por código de barras

Formato de cada archivo de localidad:
  {
    "fecha": "2026-10-16",
    "sucursales": [[cadena, nombre, direccion], ...],
    "productos": {
      "7790070318169": ["Descripción", "Marca",
                        [[cadena, precio_min, precio_max, n_sucursales, idx_sucursal_mas_barata, promo_min], ...]]
    }
  }

Solo usa la biblioteca estándar de Python.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.request
import zipfile
from collections import Counter, defaultdict

CKAN = "https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios"
UA = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
    "Accept": "application/json, text/html, */*",
    "Accept-Language": "es-AR,es;q=0.9",
}
CKAN_ALTERNATIVOS = [
    CKAN,
    "https://datos.gob.ar/api/3/action/package_show?id=produccion-precios-claros---base-sepa",
]

PROVINCIAS = {
    "AR-C": "Ciudad Autónoma de Buenos Aires", "AR-B": "Buenos Aires", "AR-K": "Catamarca",
    "AR-H": "Chaco", "AR-U": "Chubut", "AR-X": "Córdoba", "AR-W": "Corrientes",
    "AR-E": "Entre Ríos", "AR-P": "Formosa", "AR-Y": "Jujuy", "AR-L": "La Pampa",
    "AR-F": "La Rioja", "AR-M": "Mendoza", "AR-N": "Misiones", "AR-Q": "Neuquén",
    "AR-R": "Río Negro", "AR-A": "Salta", "AR-J": "San Juan", "AR-D": "San Luis",
    "AR-Z": "Santa Cruz", "AR-S": "Santa Fe", "AR-G": "Santiago del Estero",
    "AR-V": "Tierra del Fuego", "AR-T": "Tucumán",
}

csv.field_size_limit(10_000_000)


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def slug(texto: str) -> str:
    t = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode().lower()
    t = re.sub(r"[^a-z0-9]+", "-", t).strip("-")
    return t or "sin-localidad"


PREFIJOS_LOCALIDAD = re.compile(r"^(ciudad autonoma de |ciudad de |cdad de |cdad |ciudad |villa de la ciudad de )")
SUFIJOS_LOCALIDAD = re.compile(r"(-| )(capital|centro|norte|sur|este|oeste|ciudad)$")


def clave_localidad(texto: str) -> str:
    """Agrupa variantes como 'Ciudad de Córdoba Norte' y 'CORDOBA CAPITAL' en 'cordoba'."""
    t = slug(texto).replace("-", " ")
    for _ in range(2):
        t = PREFIJOS_LOCALIDAD.sub("", t).strip()
        t = SUFIJOS_LOCALIDAD.sub("", t).strip()
    return slug(t)


def titulo(texto: str) -> str:
    t = (texto or "").strip()
    return t.title() if t.isupper() or t.islower() else t


def num(valor: str) -> float | None:
    if valor is None:
        return None
    v = valor.strip().replace(",", ".")
    if not v:
        return None
    try:
        f = float(v)
    except ValueError:
        return None
    return f if 0 < f < 100_000_000 else None


def ean_normalizado(codigo: str) -> str | None:
    d = re.sub(r"\D", "", codigo or "").lstrip("0")
    return d if 7 <= len(d) <= 14 else None


# ---------------------------------------------------------------- descarga

def abrir(url: str, timeout: int):
    try:
        return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout)
    except urllib.error.HTTPError as e:
        cuerpo = e.read(400).decode("utf-8", "replace")
        log(f"Error HTTP {e.code} en {url}: {cuerpo!r} {dict(e.headers)}")
        raise


def elegir_recurso() -> dict:
    log("Consultando catálogo CKAN")
    paquete = None
    for url in CKAN_ALTERNATIVOS:
        try:
            with abrir(url, 60) as r:
                paquete = json.load(r)["result"]
                break
        except Exception as e:  # noqa: BLE001
            log("No se pudo leer", url, e)
    if paquete is None:
        # Búsqueda en el portal nacional, que replica los datasets de los ministerios
        try:
            with abrir("https://datos.gob.ar/api/3/action/package_search?q=sepa&rows=20", 60) as r:
                resultados = json.load(r)["result"]["results"]
            for pk in resultados:
                zips = [x["url"] for x in pk.get("resources", []) if str(x.get("url", "")).lower().endswith(".zip")]
                log("Error-diag dataset:", pk.get("name"), "|", pk.get("title"), "| zips:", len(zips), zips[:2])
                if zips and paquete is None:
                    paquete = pk
        except Exception as e:  # noqa: BLE001
            log("Error en búsqueda alternativa", e)
    if paquete is None:
        sys.exit("No se pudo acceder al catálogo del SEPA")
    zips = [x for x in paquete["resources"] if str(x.get("url", "")).lower().endswith(".zip")]
    if not zips:
        sys.exit("No se encontraron ZIP en el dataset SEPA")
    zips.sort(key=lambda x: x.get("last_modified") or x.get("created") or "", reverse=True)
    elegido = zips[0]
    log("Recurso elegido:", elegido.get("name"), elegido.get("last_modified"), elegido["url"])
    return elegido


def descargar(url: str, destino: str):
    log("Descargando", url)
    with abrir(url, 600) as r, open(destino, "wb") as f:
        total = 0
        while True:
            bloque = r.read(1 << 20)
            if not bloque:
                break
            f.write(bloque)
            total += len(bloque)
    log(f"Descargados {total / 1e6:.0f} MB")


# ---------------------------------------------------------------- lectura

def leer_csv(datos: bytes):
    """Lee un CSV con separador '|' y descarta la línea final 'Última actualización'."""
    texto = io.TextIOWrapper(io.BytesIO(datos), encoding="utf-8-sig", errors="replace", newline="")
    lector = csv.reader(texto, delimiter="|", quotechar='"')
    cabecera = None
    for fila in lector:
        if not fila or not any(c.strip() for c in fila):
            continue
        if cabecera is None:
            cabecera = [c.strip().lower() for c in fila]
            continue
        if fila[0].lower().startswith(("última", "ultima", "última")):
            continue
        yield dict(zip(cabecera, fila))


def paquetes(zf: zipfile.ZipFile, prefijo: str = ""):
    """Devuelve grupos {comercio, sucursales, productos} con el contenido en bytes.
    Soporta ZIP anidados (uno por comercio) y carpetas."""
    grupos: dict[str, dict[str, zipfile.ZipInfo]] = defaultdict(dict)
    for info in zf.infolist():
        nombre = info.filename
        base = os.path.basename(nombre).lower()
        if info.is_dir():
            continue
        if "__macosx" in nombre.lower():
            continue
        if base.endswith(".zip"):
            try:
                with zf.open(info) as f:
                    interno = zipfile.ZipFile(io.BytesIO(f.read()))
            except (zipfile.BadZipFile, OSError) as e:
                # Algunos comercios suben archivos vacíos o dañados: se saltean
                log(f"Salteado {prefijo + nombre} ({info.file_size} bytes): {e}")
                continue
            yield from paquetes(interno, prefijo + nombre + "/")
            continue
        for tipo in ("comercio", "sucursales", "productos"):
            if base.startswith(tipo) and base.endswith(".csv"):
                carpeta = prefijo + os.path.dirname(nombre)
                grupos[carpeta][tipo] = info
    for carpeta, g in grupos.items():
        if "productos" in g and "sucursales" in g:
            # Se lee recién acá para no cargar todos los comercios en memoria a la vez
            yield carpeta, {tipo: zf.read(info) for tipo, info in g.items()}


# ---------------------------------------------------------------- proceso

def procesar(ruta_zip: str, salida: str, fecha: str):
    cadenas: dict[str, str] = {}
    # clave sucursal -> (iso, loc_slug, idx en lista de la localidad)
    sucursal_loc: dict[tuple, tuple] = {}
    localidades: dict[tuple, dict] = {}       # (iso, slug) -> info
    agregados: dict[tuple, dict] = defaultdict(dict)  # (iso, slug) -> {ean: {cadena: [min,max,n,idxmin,promo]}}
    descripciones: dict[str, tuple] = {}

    with zipfile.ZipFile(ruta_zip) as zf:
        for carpeta, g in paquetes(zf):
            try:
                nombres_bandera = {}
                if "comercio" in g:
                    for c in leer_csv(g["comercio"]):
                        k = f'{c.get("id_comercio","").strip()}-{c.get("id_bandera","").strip()}'
                        if not c.get("id_comercio", "").strip("\x00 "):
                            continue
                        nombre = (c.get("comercio_bandera_nombre") or c.get("comercio_razon_social") or k).strip()
                        nombres_bandera[k] = titulo(nombre)
                cadenas.update(nombres_bandera)

                n_suc = 0
                for s in leer_csv(g["sucursales"]):
                    k_cad = f'{s.get("id_comercio","").strip()}-{s.get("id_bandera","").strip()}'
                    if not s.get("id_comercio", "").strip("\x00 "):
                        continue
                    k_suc = (k_cad, s.get("id_sucursal", "").strip())
                    iso = (s.get("sucursales_provincia") or "").strip().upper()
                    if iso not in PROVINCIAS:
                        continue
                    loc_nombre = (s.get("sucursales_localidad") or "").strip()
                    loc_k = (iso, clave_localidad(loc_nombre))
                    info = localidades.setdefault(loc_k, {
                        "nombres": Counter(), "lat": [], "lon": [], "sucursales": [], "cadenas": set(),
                    })
                    info["nombres"][titulo(loc_nombre) or "Sin localidad"] += 1
                    try:
                        lat = float((s.get("sucursales_latitud") or "").replace(",", "."))
                        lon = float((s.get("sucursales_longitud") or "").replace(",", "."))
                    except ValueError:
                        lat = lon = None
                    if lat and lon and -56 < lat < -21 and -74 < lon < -53:
                        info["lat"].append(lat)
                        info["lon"].append(lon)
                    direccion = " ".join(x for x in [
                        (s.get("sucursales_calle") or "").strip(),
                        (s.get("sucursales_numero") or "").strip(),
                    ] if x)
                    info["sucursales"].append([k_cad, titulo(s.get("sucursales_nombre") or ""), titulo(direccion)])
                    info["cadenas"].add(k_cad)
                    sucursal_loc[k_suc] = (loc_k, len(info["sucursales"]) - 1)
                    cadenas.setdefault(k_cad, k_cad)
                    n_suc += 1

                n_prod = 0
                for p in leer_csv(g["productos"]):
                    if (p.get("productos_ean") or "1").strip() not in ("1", "true", "si", "sí"):
                        continue
                    ean = ean_normalizado(p.get("id_producto"))
                    precio = num(p.get("productos_precio_lista"))
                    if not ean or precio is None:
                        continue
                    k_cad = f'{p.get("id_comercio","").strip()}-{p.get("id_bandera","").strip()}'
                    destino = sucursal_loc.get((k_cad, p.get("id_sucursal", "").strip()))
                    if not destino:
                        continue
                    loc_k, idx_suc = destino
                    promo = min([x for x in (num(p.get("productos_precio_unitario_promo1")),
                                             num(p.get("productos_precio_unitario_promo2"))) if x] or [0]) or None
                    if ean not in descripciones:
                        descripciones[ean] = ((p.get("productos_descripcion") or "").strip(),
                                              titulo(p.get("productos_marca") or ""))
                    por_ean = agregados[loc_k].setdefault(ean, {})
                    a = por_ean.get(k_cad)
                    if a is None:
                        por_ean[k_cad] = [precio, precio, 1, idx_suc, promo]
                    else:
                        if precio < a[0]:
                            a[0], a[3] = precio, idx_suc
                        if precio > a[1]:
                            a[1] = precio
                        a[2] += 1
                        if promo and (a[4] is None or promo < a[4]):
                            a[4] = promo
                    n_prod += 1
                log(f"{carpeta[:60]:60s} sucursales={n_suc:5d} precios={n_prod:8d}")
            except Exception as e:  # noqa: BLE001
                log(f"Salteado {carpeta}: {type(e).__name__}: {e}")

    # ------------------------------------------------------------ escritura
    indice = {"fecha": fecha, "cadenas": cadenas, "provincias": []}
    por_prov = defaultdict(list)
    for (iso, loc_slug), info in localidades.items():
        productos = agregados.get((iso, loc_slug))
        if not productos:
            continue
        datos = {
            "fecha": fecha,
            "sucursales": info["sucursales"],
            "productos": {
                ean: [descripciones[ean][0], descripciones[ean][1],
                      [[c, round(v[0], 2), round(v[1], 2), v[2], v[3],
                        round(v[4], 2) if v[4] else None] for c, v in por_cad.items()]]
                for ean, por_cad in productos.items()
            },
        }
        carpeta = os.path.join(salida, "p", iso)
        os.makedirs(carpeta, exist_ok=True)
        with open(os.path.join(carpeta, f"{loc_slug}.json"), "w", encoding="utf-8") as f:
            json.dump(datos, f, ensure_ascii=False, separators=(",", ":"))
        lat = sum(info["lat"]) / len(info["lat"]) if info["lat"] else None
        lon = sum(info["lon"]) / len(info["lon"]) if info["lon"] else None
        por_prov[iso].append({
            "id": loc_slug,
            "nombre": max(info["nombres"].items(),
                          key=lambda x: (slug(x[0]) == loc_slug, x[1], not x[0].isascii()))[0],
            "lat": round(lat, 4) if lat else None,
            "lon": round(lon, 4) if lon else None,
            "sucursales": len(info["sucursales"]),
            "productos": len(productos),
            "cadenas": sorted(info["cadenas"]),
        })
    for iso, locs in sorted(por_prov.items(), key=lambda x: PROVINCIAS[x[0]]):
        locs.sort(key=lambda l: l["nombre"])
        indice["provincias"].append({"iso": iso, "nombre": PROVINCIAS[iso], "localidades": locs})
    with open(os.path.join(salida, "index.json"), "w", encoding="utf-8") as f:
        json.dump(indice, f, ensure_ascii=False, separators=(",", ":"))
    n_loc = sum(len(p["localidades"]) for p in indice["provincias"])
    log(f"Listo: {len(indice['provincias'])} provincias, {n_loc} localidades, {len(cadenas)} cadenas")
    tam = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(salida) for f in fs) / 1e6
    print(f"::notice::SEPA {fecha}: {len(indice['provincias'])} provincias, {n_loc} localidades, "
          f"{len(cadenas)} cadenas, {len(descripciones)} productos, {tam:.0f} MB publicados", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", default="sitio")
    ap.add_argument("--zip", help="Procesar un ZIP ya descargado (para pruebas)")
    args = ap.parse_args()
    os.makedirs(args.salida, exist_ok=True)
    with open(os.path.join(args.salida, ".nojekyll"), "w"):
        pass

    if args.zip:
        procesar(args.zip, args.salida, time.strftime("%Y-%m-%d"))
        return
    recurso = elegir_recurso()
    fecha = (recurso.get("last_modified") or recurso.get("created") or time.strftime("%Y-%m-%d"))[:10]
    with tempfile.TemporaryDirectory() as tmp:
        ruta = os.path.join(tmp, "sepa.zip")
        descargar(recurso["url"], ruta)
        procesar(ruta, args.salida, fecha)


if __name__ == "__main__":
    try:
        main()
    except BaseException as e:  # noqa: BLE001
        if isinstance(e, SystemExit) and not e.code:
            raise
        import traceback
        detalle = traceback.format_exc()
        # Anotación visible en GitHub Actions
        print("::error::" + detalle[-3000:].replace("%", "%25").replace("\r", "").replace("\n", "%0A"), flush=True)
        if isinstance(e, urllib.error.HTTPError) and e.code == 403:
            print("::error::El servidor del SEPA rechazó la conexión (403). Suele bloquear conexiones desde fuera "
                  "de Argentina: configurá un self-hosted runner (ver .github/workflows/datos-precios.yml).", flush=True)
        sys.exit(1)
