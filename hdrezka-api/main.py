from typing import Optional
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool

from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch
from hdrezka import HDRezkaClient
from HDrezka import HDRezka as KristalHDRezka

app = FastAPI(title="ARNIX Cinema — HDRezka bridge", version="2.1.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])

DEFAULT_ORIGIN = "https://hdrezka.ag"
HDREZKA_HOST = __import__("os").environ.get("HDREZKA_HOST", "https://rezka.ag/").rstrip("/") + "/"
CATEGORY_PATHS = {
    "Фильмы": "/films/", "films": "/films/", "movies": "/films/",
    "Сериалы": "/series/", "series": "/series/",
    "Мультфильмы": "/cartoons/", "cartoons": "/cartoons/",
    "Аниме": "/animation/", "anime": "/animation/", "animation": "/animation/",
}

def valid_rezka_url(value: str) -> bool:
    parsed = urlparse(value)
    host = (parsed.hostname or "").lower()
    return parsed.scheme == "https" and host in {"hdrezka.ag", "rezka.ag"}

def valid_catalog_url(value: str) -> bool:
    parsed = urlparse(value)
    host = (parsed.hostname or "").lower()
    allowed = (urlparse(HDREZKA_HOST).hostname or "").lower()
    return parsed.scheme == "https" and (host in {"hdrezka.ag", "rezka.ag"} or host == allowed or host.endswith("." + allowed))

def kristal_item_json(item):
    return {
        "title": str(getattr(item, "title", "") or "").strip(),
        "url": str(getattr(item, "url", "") or ""),
        "image": str(getattr(item, "img_url", "") or ""),
        "description": "",
        "year": str(getattr(item, "year", "") or ""),
        "rating": float(getattr(item, "rates", 0.0) or 0.0),
        "category": str(getattr(item, "entity", "") or ""),
    }

def kristal_catalog(category: str, page: int):
    client = KristalHDRezka()
    normalized = category.lower()
    if category == "Сериалы" or normalized == "series": navigator = client.series()
    elif category == "Мультфильмы" or normalized == "cartoons": navigator = client.cartoons()
    elif category == "Аниме" or normalized in ("anime", "animation"): navigator = client.animation()
    else: navigator = client.films()
    return [kristal_item_json(x) for x in navigator.page(page).get() if getattr(x, "url", None)]

def kristal_search(query: str, page: int):
    navigator = KristalHDRezka.search(query).page(page)
    return [kristal_item_json(x) for x in navigator.get() if getattr(x, "url", None)]
@app.get("/health")
def health():
    return {"ok": True, "provider": "kristal374/hdrezka-api catalog + HdRezkaApi stream extractor", "catalog_host": HDREZKA_HOST}

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
    if not valid_catalog_url(url): raise HTTPException(status_code=400, detail="Unsupported HDRezka URL")
    def extract():
        movie = KristalHDRezka().get(url=url)
        info = getattr(movie, "info_table", None)
        ratings = getattr(info, "rates", []) or []
        rating = next((x for x in ratings if str(getattr(x, "name", "")).lower() == "hdrezka"), None)
        genres = [str(x) for x in (getattr(info, "genre", []) or [])]
        return {"title": str(getattr(movie, "title", "") or ""), "description": str(getattr(movie, "description", "") or ""), "poster": str(getattr(movie, "img_url", "") or ""), "rating": float(getattr(rating, "rates", 0.0) or 0.0), "votes": int(getattr(rating, "votes", 0) or 0), "category": ", ".join(genres), "type": str(getattr(movie, "status", "") or ""), "seasons": []}
    try: return await run_in_threadpool(extract)
    except Exception as exc: raise HTTPException(status_code=502, detail=f"HDRezka details failed: {type(exc).__name__}") from exc
@app.get("/stream")
async def stream(url: str, translation: Optional[str] = None, season: Optional[str] = None, episode: Optional[str] = None):
    if not valid_rezka_url(url):
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
                try: subtitles.append({"language": str(lang), "url": subtitles_obj(lang)})
                except Exception: pass
        return {
            "title": str(getattr(film, "name", "")),
            "translators": [{"id": str(k), "name": str(v.get("name", k))} for k, v in (getattr(film, "translators", {}) or {}).items()],
            "qualities": {str(q): [str(link) for link in links if str(link).startswith("https://")] for q, links in videos.items()},
            "subtitles": subtitles,
        }
    try:
        return await run_in_threadpool(extract)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Cannot extract stream: {type(exc).__name__}") from exc
