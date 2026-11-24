from typing import Optional
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool

from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch
from hdrezka import HDRezkaClient

app = FastAPI(title="ARNIX Cinema — HDRezka bridge", version="2.0.0")
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

def item_json(item):
    info = item.info
    return {
        "title": str(item.name or "").strip(),
        "url": str(item.url or ""),
        "image": str(item.poster or ""),
        "description": "",
        "year": str(getattr(info, "year", "") or ""),
        "rating": 0.0,
        "category": ", ".join(x.strip() for x in str(getattr(info, "genre", "") or "").split(",") if x.strip()),
    }

@app.get("/health")
def health():
    return {"ok": True, "provider": "HDRezkaClient + HdRezkaApi", "catalog_host": HDREZKA_HOST}

@app.get("/catalog")
async def catalog(category: str = "Фильмы", page: int = Query(1, ge=1, le=500)):
    path = CATEGORY_PATHS.get(category, CATEGORY_PATHS["Фильмы"])
    try:
        async with HDRezkaClient(host=HDREZKA_HOST) as client:
            items = await client.page(path).get_page(page)
            results = [item_json(x) for x in items if x.name and x.url]
            return {"results": results, "page": page, "category": category, "count": len(results)}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka catalog unavailable: {type(exc).__name__}") from exc

@app.get("/search")
async def search(q: str = Query(min_length=2, max_length=120), page: int = Query(1, ge=1, le=100)):
    try:
        async with HDRezkaClient(host=HDREZKA_HOST) as client:
            items = await client.search(q).get_page(page)
            return {"results": [item_json(x) for x in items if x.name and x.url], "page": page}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka search failed: {type(exc).__name__}") from exc

@app.get("/details")
async def details(url: str):
    if not valid_catalog_url(url):
        raise HTTPException(status_code=400, detail="Unsupported HDRezka URL")
    try:
        async with HDRezkaClient(host=HDREZKA_HOST) as client:
            post = await client.post(url)
            info = post.info
            rating_data = (getattr(info, "ratings", {}) or {}).get("hdrezka")
            poster_data = getattr(info, "poster", None)
            poster_url = (getattr(poster_data, "full", "") or getattr(poster_data, "preview", "")) if poster_data else ""
            votes = getattr(rating_data, "votes", 0)
            return {
                "title": str(post.name or getattr(info, "title", "")),
                "description": str(getattr(info, "description", "") or ""),
                "poster": str(poster_url or ""),
                "rating": float(getattr(rating_data, "rating", 0.0) or 0.0),
                "votes": int(votes or 0),
                "category": ", ".join(str(x) for x in getattr(info, "genre", ()) if str(x)),
                "type": str(post.type or ""),
                "seasons": [],
            }
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka details failed: {type(exc).__name__}") from exc

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
