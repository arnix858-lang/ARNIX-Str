import json
import re
from typing import Optional
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from starlette.concurrency import run_in_threadpool

BASE_URL = "https://mg.lordfilm.md/"
HOST = (urlparse(BASE_URL).hostname or "").lower()
UA = ("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
HEADERS = {
    "User-Agent": UA,
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
}
CATEGORIES = {
    "Фильмы": "/filmy/",
    "Сериалы": "/serialy/",
    "Мультфильмы": "/multfilmy/",
    "Аниме": "/anime/",
}

app = FastAPI(title="ARNIX Cinema — LordFilm bridge", version="3.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])


def safe_lordfilm_url(value: object, base: str = BASE_URL) -> str:
    raw = str(value or "").strip()
    if not raw or raw.startswith(("javascript:", "data:")):
        return ""
    url = urljoin(base, raw)
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    if parsed.scheme not in ("http", "https") or not host:
        return ""
    if host != HOST and not host.endswith("." + HOST):
        return ""
    return url


def fetch(url: str, referer: str = BASE_URL) -> requests.Response:
    headers = dict(HEADERS)
    headers["Referer"] = referer
    response = requests.get(url, headers=headers, timeout=(12, 30))
    response.raise_for_status()
    return response


def text(node) -> str:
    return node.get_text(" ", strip=True) if node else ""


def poster_from(node, page_url: str) -> str:
    if not node:
        return ""
    value = (node.get("data-src") or node.get("data-original") or
             node.get("data-lazy-src") or node.get("src") or "")
    return urljoin(page_url, value) if value else ""


def parse_cards(html: str, category: str, page_url: str):
    soup = BeautifulSoup(html, "html.parser")
    cards = soup.select(
        ".shortstory, .short-item, .movie-item, .film-item, .item, "
        ".movie, .th-item, .b-content__inline_item, article"
    )
    if not cards:
        cards = []
        for link in soup.select("a[href]"):
            href = link.get("href", "")
            if re.search(r"/(?:filmy|serialy|multfilmy|anime)/[^/?#]+\.html", href, re.I):
                cards.append(link)

    results, seen = [], set()
    for card in cards:
        link = card if card.name == "a" else card.select_one(
            "a[href*='.html'], .shortstory-title a[href], .title a[href], a[href]"
        )
        if not link:
            continue
        url = safe_lordfilm_url(link.get("href"), page_url)
        if not url or url in seen:
            continue
        image_node = card.select_one("img") if hasattr(card, "select_one") else None
        title = (link.get("title") or text(link) or
                 (image_node.get("alt", "") if image_node else "")).strip()
        if not title:
            continue
        description = text(card.select_one(
            ".shortstory-content, .shortstory-text, .description, .desc, .text"
        )) if hasattr(card, "select_one") else ""
        rating = text(card.select_one(".rating, .rate, .b-rating")) if hasattr(card, "select_one") else ""
        year = text(card.select_one(".year, .date, .info")) if hasattr(card, "select_one") else ""
        try:
            rating_value = float(re.search(r"\d+(?:[.,]\d+)?", rating).group(0).replace(",", ".")) if rating else 0.0
        except (AttributeError, ValueError):
            rating_value = 0.0
        results.append({
            "title": title, "url": url, "image": poster_from(image_node, page_url),
            "rating": rating_value, "description": description,
            "category": category, "year": year,
        })
        seen.add(url)
    return results


def category_url(category: str, page: int) -> str:
    path = CATEGORIES.get(category, CATEGORIES["Фильмы"])
    url = urljoin(BASE_URL, path.lstrip("/"))
    if page > 1:
        url = urljoin(url.rstrip("/") + "/", f"page/{page}/")
    return url


def catalog_data(category: str, page: int):
    url = category_url(category, page)
    response = fetch(url)
    items = parse_cards(response.text, category, response.url)
    if not items and category == "Фильмы":
        alternate = urljoin(BASE_URL, "filmy/")
        response = fetch(alternate)
        items = parse_cards(response.text, category, response.url)
    if not items:
        raise RuntimeError("На странице LordFilm не найдены карточки. Возможно, сайт изменил разметку или включил защиту.")
    return {"results": items[:60], "page": page, "category": category, "count": len(items)}


def search_data(query: str, page: int):
    url = urljoin(BASE_URL, "?do=search&subaction=search&story=" + requests.utils.quote(query))
    if page > 1:
        url += "&search_start=" + str(page)
    response = fetch(url)
    items = parse_cards(response.text, "Поиск", response.url)
    return {"results": items[:60], "page": page, "count": len(items)}


def details_data(url: str):
    target = safe_lordfilm_url(url)
    if not target:
        raise ValueError("Недопустимая ссылка LordFilm")
    response = fetch(target)
    soup = BeautifulSoup(response.text, "html.parser")
    title = text(soup.select_one("h1, .shortstory-title, .film-title")) or text(soup.title)
    description = text(soup.select_one(
        ".full-text, .shortstory-content, .film-description, .description, "
        "[itemprop='description'], .fdesc"
    ))
    poster_node = soup.select_one(
        ".fimg img, .film-poster img, .poster img, img[itemprop='image'], "
        ".shortstory img, .full-story img"
    )
    poster = poster_from(poster_node, response.url)
    rating_node = soup.select_one("[itemprop='ratingValue'], .rating, .rate")
    rating_match = re.search(r"\d+(?:[.,]\d+)?", text(rating_node))
    rating = float(rating_match.group(0).replace(",", ".")) if rating_match else 0.0
    genre_nodes = soup.select(".genre a, .genres a, a[rel='tag']")
    genres = ", ".join(dict.fromkeys(text(x) for x in genre_nodes if text(x)))
    # Keep episode/season structure when it is explicitly present in page markup.
    seasons = []
    season_nodes = soup.select("[data-season], .season-item, .seasons-list .season")
    for index, node in enumerate(season_nodes, 1):
        season_no = int(node.get("data-season") or index)
        episode_nodes = node.select("a[data-episode], .episode a, a[href*='episode']")
        episodes = []
        for ep_index, ep in enumerate(episode_nodes, 1):
            ep_no = int(ep.get("data-episode") or ep_index)
            episodes.append({"episode": ep_no, "title": text(ep) or f"Серия {ep_no}", "translations": []})
        if episodes:
            seasons.append({"season": season_no, "title": text(node.select_one(".season-title")) or f"Сезон {season_no}", "episodes": episodes})
    return {
        "title": title, "description": description, "poster": poster,
        "rating": rating, "votes": 0, "category": genres,
        "type": "", "seasons": seasons,
    }


def extract_direct_media(html: str, page_url: str):
    soup = BeautifulSoup(html, "html.parser")
    found = []
    # Only openly exposed media URLs are accepted; no DRM or access-control bypass.
    for node in soup.select("video[src], video source[src], source[src], a[href]"):
        raw = node.get("src") or node.get("href") or ""
        absolute = urljoin(page_url, raw)
        if re.search(r"\.(?:m3u8|mp4)(?:[?#]|$)", absolute, re.I):
            if absolute.startswith(("http://", "https://")) and absolute not in found:
                found.append(absolute)
    for match in re.findall(r"""https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""", html, re.I):
        if match not in found:
            found.append(match)
    return found


def stream_data(url: str, translation: Optional[str], season: Optional[str], episode: Optional[str]):
    target = safe_lordfilm_url(url)
    if not target:
        raise ValueError("Недопустимая ссылка LordFilm")
    response = fetch(target)
    soup = BeautifulSoup(response.text, "html.parser")
    sources = []
    # Some pages expose a native source directly; others expose a player iframe.
    sources.extend(extract_direct_media(response.text, response.url))
    frames = soup.select("iframe[src], iframe[data-src]")
    for index, frame in enumerate(frames, 1):
        frame_url = urljoin(response.url, frame.get("src") or frame.get("data-src") or "")
        parsed = urlparse(frame_url)
        if parsed.scheme not in ("http", "https"):
            continue
        try:
            frame_response = requests.get(
                frame_url, headers={**HEADERS, "Referer": response.url},
                timeout=(10, 20)
            )
            frame_response.raise_for_status()
            sources.extend(extract_direct_media(frame_response.text, frame_response.url))
        except requests.RequestException:
            continue
    unique = list(dict.fromkeys(sources))
    qualities = {}
    for source in unique:
        label = "HLS" if ".m3u8" in source.lower() else "MP4"
        qualities.setdefault(label, []).append(source)
    return {
        "title": text(soup.select_one("h1")) or text(soup.title) or "LordFilm",
        "translators": [],
        "qualities": qualities,
        "subtitles": [],
    }


@app.get("/health")
def health():
    return {"ok": True, "provider": "LordFilm catalog + public media-source parser", "catalog_host": BASE_URL}


@app.get("/poster")
async def poster(url: str):
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not (parsed.hostname or "").lower().endswith(("lordfilm.md",)):
        raise HTTPException(status_code=400, detail="Unsupported poster host")
    try:
        response = await run_in_threadpool(fetch, url, BASE_URL)
        content_type = response.headers.get("Content-Type", "image/jpeg").split(";")[0]
        if not content_type.startswith("image/"):
            raise ValueError("Not an image")
        return Response(response.content, media_type=content_type,
                        headers={"Cache-Control": "public, max-age=86400"})
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Poster fetch failed: {type(exc).__name__}") from exc


@app.get("/catalog")
async def catalog(category: str = "Фильмы", page: int = Query(1, ge=1, le=500)):
    try:
        return await run_in_threadpool(catalog_data, category, page)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"LordFilm catalog failed: {type(exc).__name__}: {exc}") from exc


@app.get("/search")
async def search(q: str = Query(min_length=2, max_length=120), page: int = Query(1, ge=1, le=100)):
    try:
        return await run_in_threadpool(search_data, q, page)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"LordFilm search failed: {type(exc).__name__}: {exc}") from exc


@app.get("/details")
async def details(url: str):
    try:
        return await run_in_threadpool(details_data, url)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"LordFilm details failed: {type(exc).__name__}: {exc}") from exc


@app.get("/stream")
async def stream(url: str, translation: Optional[str] = None,
                 season: Optional[str] = None, episode: Optional[str] = None):
    try:
        return await run_in_threadpool(stream_data, url, translation, season, episode)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"LordFilm media lookup failed: {type(exc).__name__}: {exc}") from exc
