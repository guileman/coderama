#!/usr/bin/env python3
"""
Junta las alertas recientes de la ANMAT (alimentos, cosméticos y domisanitarios)
y las publica en <salida>/alertas.json para que la app las cruce por marca y nombre.

Es un proceso de mejor esfuerzo: si la página cambia de formato, la app sigue
funcionando sin alertas.
"""
from __future__ import annotations

import argparse
import html
import json
import os
import re
import time
import unicodedata
import urllib.request

BASE = "https://www.argentina.gob.ar"
CATEGORIAS = {
    "alimentos": "/anmat/alertas/alimentos",
    "cosmeticos": "/anmat/alertas/cosmeticos",
    "domisanitarios": "/anmat/alertas/domisanitarios",
}
UA = {"User-Agent": "Mozilla/5.0 (compatible; Coderama/1.0; datos abiertos)"}
MESES = {m: i + 1 for i, m in enumerate(
    "enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre".split())}

ENLACE = re.compile(r'<a[^>]+href="(?P<href>(?:https://www\.argentina\.gob\.ar)?/noticias/[^"#?]+)"[^>]*>(?P<texto>.*?)</a>',
                    re.S | re.I)
FECHA = re.compile(r"(\d{1,2}) de (\w+) de (\d{4})", re.I)


def limpiar(t: str) -> str:
    t = re.sub(r"<[^>]+>", " ", t)
    return re.sub(r"\s+", " ", html.unescape(t)).strip()


def normalizar(t: str) -> str:
    t = unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", t).strip()


def bajar(url: str) -> str:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def claves(titulo: str) -> list[str]:
    """Extrae nombres entre comillas y lo que sigue a 'marca'."""
    encontrados = re.findall(r'[“"«]([^”"»]{3,80})[”"»]', titulo)
    encontrados += re.findall(r"marcas? ([A-ZÁÉÍÓÚÑ0-9][\wÁÉÍÓÚÑáéíóúñ&\.\- ]{1,40})", titulo)
    salida = []
    for e in encontrados:
        n = normalizar(re.sub(r"\bmarcas?\b", " ", e, flags=re.I))
        if len(n) >= 3 and n not in salida:
            salida.append(n)
    return salida


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", default="sitio")
    args = ap.parse_args()
    alertas, vistos = [], set()
    for categoria, ruta in CATEGORIAS.items():
        for pagina in range(3):
            url = BASE + ruta + (f"?page={pagina}" if pagina else "")
            try:
                contenido = bajar(url)
            except Exception as e:  # noqa: BLE001
                print("No se pudo leer", url, e)
                break
            nuevos = 0
            for m in ENLACE.finditer(contenido):
                href = m.group("href")
                if not href.startswith("http"):
                    href = BASE + href
                texto = limpiar(m.group("texto"))
                if href in vistos or len(texto) < 15:
                    continue
                fecha = None
                f = FECHA.search(texto)
                if f and f.group(2).lower() in MESES:
                    fecha = f"{f.group(3)}-{MESES[f.group(2).lower()]:02d}-{int(f.group(1)):02d}"
                    texto = FECHA.sub("", texto).strip(" -–:")
                vistos.add(href)
                nuevos += 1
                alertas.append({"categoria": categoria, "titulo": texto, "fecha": fecha,
                                "url": href, "claves": claves(texto)})
            if not nuevos:
                break
            time.sleep(1)
    os.makedirs(args.salida, exist_ok=True)
    with open(os.path.join(args.salida, "alertas.json"), "w", encoding="utf-8") as f:
        json.dump({"actualizado": time.strftime("%Y-%m-%d"), "alertas": alertas}, f, ensure_ascii=False)
    print(f"{len(alertas)} alertas guardadas")


if __name__ == "__main__":
    main()
