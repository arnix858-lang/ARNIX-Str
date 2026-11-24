from typing import Optional
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool

from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch

app = FastAPI(title="ARNIX Cinema — HDRezka bridge", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

DEFAULT_ORIGIN = "https://hdrezka.ag"


def valid_rezka_url(value: str) -> bool:
    parsed = urlparse(value)
    host = (parsed.hostname or "").lower()
    return parsed.scheme == "https" and (host == "hdrezka.ag" or host.endswith(".hdrezka.ag"))


@app.get("/health")
def health():
    return {"ok": True, "provider": "HdRezkaApi"}


@app.get("/search")
async def search(q: str = Query(min_length=2, max_length=120)):
    try:
        results = await run_in_threadpool(lambda: HdRezkaSearch(DEFAULT_ORIGIN)(q))
        return {
            "results": [
                {
                    "title": str(item.get("title", "")),
                    "url": str(item.get("url", "")),
                    "rating": item.get("rating"),
                }
                for item in results[:20]
                if item.get("url") and valid_rezka_url(str(item["url"]))
            ]
        }
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"HDRezka search failed: {type(exc).__name__}") from exc


@app.get("/stream")
async def stream(
    url: str,
    translation: Optional[str] = None,
    season: Optional[str] = None,
    episode: Optional[str] = None,
):
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
