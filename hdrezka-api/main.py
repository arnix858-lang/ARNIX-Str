from typing import Optional
from urllib.parse import quote, urljoin, urlparse

import requests
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from starlette.concurrency import run_in_threadpool

from HdRezkaApi import HdRezkaApi
from HDrezka import HDrezka as KristalHDRezka

app = FastAPI(title="ARNIX Cinema — HDRezka bridge", version="2.2.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])

HDREZKA_HOST = __import__("os").environ.get("HDREZKA_HOST", "https://rezka.ag/").rstrip("/") + "/"
PUBLIC_API_BASE = __import__("os").environ.get(
    "PUBLIC_API_BASE", "https://arnix-cinema-hdrezka-api.onrender.com"
).rstrip("/")
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
)


def allowed_rezka_host(host: str) -> bool:
    host = (host or "").lower().rstrip(".")
    configured = (urlparse(HDREZKA_HOST).hostname or "").lower()
    return (
        host in {"rezka.ag", "hdrezka.ag"}
        or host.endswith(".rezka.ag")
        or host.endswith(".hdrezka.ag")
        or host == configured
        or (configured and host.endswith("." + configured))
    )


def absolute_url(value: object) -> str:
    raw = str(value or "").strip()
    if not raw:
        return ""
    result = urljoin(HDREZKA_HOST, raw)
    parsed = urlparse(result)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return ""
    return result


def item_value(item, *names, default=""):
    for name in names:
        value = item.get(name) if isinstance(item, dict) else getattr(item, name, None)
        if value is not None and str(value).strip():
            return value
    return default


def app_image_url(value: object) -> str:
    image = absolute_url(value)
    if not image:
        return ""
    host = (urlparse(image).hostname or "").lower()
    # Route HDRezka-hosted posters through the bridge so hotlink protection
    # and missing Referer headers do not leave empty poster cards in Coil.
    if allowed_rezka_host(host):
        return f"{PUBLIC_API_BASE}/poster?url={quote(image, safe='')}"
    return image


def kristal_item_json(item):
    title = str(item_value(item, "title", "name")).strip()
    target_url = absolute_url(item_value(item, "url", "href"))
    image = item_value(item, "img_url", "poster", "image", "image_url", "thumbnail")
    rating = item_value(item, "rates", "rating", default=0.0)
    return {
        "title": title,
        "url": target_url,
        "image": app_image_url(image),
        "description": str(item_value(item, "description", default="") or ""),
        "year": str(item_value(item, "year", "release_year", default="") or ""),
        "rating": float(rating or 0.0),
        "category": str(item_value(item, "entity", "category", "genre", default="") or ""),
    }


def kristal_catalog(category: str, page: int):
    client = KristalHDRezka()
    normalized = category.lower()
    if category == "Сериалы" or normalized == "series":
        navigator = client.series()
    elif category == "Мультфильмы" or normalized == "cartoons":
        navigator = client.cartoons()
    elif category == "Аниме" or normalized in ("anime", "animation"):
        navigator = client.animation()
    else:
        navigator = client.films()
    items = navigator.page(page).get()
    return [kristal_item_json(item) for item in items if item_value(item, "url")]


def kristal_search(query: str, page: int):
    navigator = KristalHDRezka.search(query).page(page)
    items = navigator.get()
    return [kristal_item_json(item) for item in items if item_value(item, "url")]


@app.get("/health")
def health():
    return {
        "ok": True,
        "provider": "kristal374/hdrezka-api catalog + HdRezkaApi stream extractor",
        "catalog_host": HDREZKA_HOST,
        "poster_proxy": True,
    }


@app.get("/poster")
async def poster(url: str):
    parsed = urlparse(url)
    if parsed.scheme != "https" or not allowed_rezka_host(parsed.hostname or ""):
        raise HTTPException(status_code=400, detail="Unsupported poster host")

    def download():
        response = requests.get(
            url,
            headers={
                "User-Agent": USER_AGENT,
                "Referer": HDREZKA_HOST,
                "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            },
            timeout=(10, 25),
        )
        response.raise_for_status()
        content_type = response.headers.get("Content-Type", "image/jpeg").split(";")[0]
        if not content_type.startswith("image/"):
            raise ValueError("The source did not return an image")
        return response.content, content_type

    try:
        body, content_type = await run_in_threadpool(download)
        return Response(
            content=body,
            media_type=content_type,
            headers={"Cache-Control": "public, max-age=86400"},
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Poster download failed: {type(exc).__name__}") from exc


@app.get("/catalog")
async def catalog(category: str = "Фильмы", page: int = Query(1, ge=1, le=500)):
    try:
        results = await run_in_threadpool(kristal_catalog, category, page)
        return {"results": results, "page": page, "category": category, "count": len(results)}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka catalog unavailable: {type(exc).__name__}") from exc


@app.get("/search")
async def search(q: str = Query(min_length=2, max_length=120), page: int = Query(1, ge=1, le=100)):
    try:
        results = await run_in_threadpool(kristal_search, q, page)
        return {"results": results, "page": page, "count": len(results)}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka search failed: {type(exc).__name__}") from exc


@app.get("/details")
async def details(url: str):
    parsed = urlparse(url)
    if parsed.scheme != "https" or not allowed_rezka_host(parsed.hostname or ""):
        raise HTTPException(status_code=400, detail="Unsupported HDRezka URL")

    def extract():
        movie = KristalHDRezka().get(url=url)
        info = getattr(movie, "info_table", None)
        ratings = getattr(info, "rates", []) or []
        rating = next(
            (item for item in ratings if str(getattr(item, "name", "")).lower() == "hdrezka"),
            None,
        )
        genres = [str(item) for item in (getattr(info, "genre", []) or [])]
        poster_url = app_image_url(item_value(movie, "img_url", "poster", "image"))
        return {
            "title": str(item_value(movie, "title", "name")),
            "description": str(getattr(movie, "description", "") or ""),
            "poster": poster_url,
            "rating": float(getattr(rating, "rates", 0.0) or 0.0),
            "votes": int(getattr(rating, "votes", 0) or 0),
            "category": ", ".join(genres),
            "type": str(getattr(movie, "status", "") or ""),
            "seasons": [],
        }

    try:
        return await run_in_threadpool(extract)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka details failed: {type(exc).__name__}") from exc


@app.get("/stream")
async def stream(
    url: str,
    translation: Optional[str] = None,
    season: Optional[str] = None,
    episode: Optional[str] = None,
):
    parsed = urlparse(url)
    if parsed.scheme != "https" or not allowed_rezka_host(parsed.hostname or ""):
        raise HTTPException(status_code=400, detail="Unsupported HDRezka URL")

    def extract():
        film = HdRezkaApi(url)
        if not film.ok:
            raise RuntimeError(str(film.exception or "Cannot open title"))
        if season is not None and episode is not None:
            source = film.getStream(season, episode, translation=translation)
        else:
            source = film.getStream(translation=translation)
        videos = getattr(source, "videos", {}) or {}
        subtitles_obj = getattr(source, "subtitles", None)
        subtitles = []
        if subtitles_obj is not None:
            for lang in getattr(subtitles_obj, "keys", []) or []:
                try:
                    subtitles.append({"language": str(lang), "url": subtitles_obj(lang)})
                except Exception:
                    pass
        return {
            "title": str(getattr(film, "name", "")),
            "translators": [
                {"id": str(key), "name": str(value.get("name", key))}
                for key, value in (getattr(film, "translators", {}) or {}).items()
            ],
            "qualities": {
                str(quality): [str(link) for link in links if str(link).startswith("https://")]
                for quality, links in videos.items()
            },
            "subtitles": subtitles,
        }

    try:
        return await run_in_threadpool(extract)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Cannot extract stream: {type(exc).__name__}") from exc
