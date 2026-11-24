import json
import re
from urllib.parse import urljoin, urlparse
import requests
from bs4 import BeautifulSoup
from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch

DEFAULT_ORIGIN = "https://hdrezka.ax"
CATEGORY_PATHS = {
    "Фильмы": "/movies",
    "Сериалы": "/series",
    "Мультфильмы": "/cartoons",
    "Аниме": "/animation",
}

def _safe_url(value):
    url = urljoin(DEFAULT_ORIGIN, str(value or ""))
    host = (urlparse(url).hostname or "").lower()
    return url if url.startswith("https://") and (host == "hdrezka.ax" or host.endswith(".hdrezka.ax") or host == "hdrezka.ag" or host.endswith(".hdrezka.ag")) else ""

def _rating(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0

def _image_url(value):
    # Poster images may be hosted on a separate CDN.
    image = urljoin(DEFAULT_ORIGIN, str(value or "").strip())
    parsed = urlparse(image)
    return image if parsed.scheme in ("http", "https") else ""


def _item(title, url, image="", rating=0, description="", category="", year=""):
    return {
        "title": str(title or "").strip(),
        "url": _safe_url(url),
        "image": _image_url(image),
        "rating": _rating(rating),
        "description": str(description or "").strip(),
        "category": str(category or ""),
        "year": str(year or "")
    }

def _parse_cards(html, category):
    soup = BeautifulSoup(html, "html.parser")
    found = []
    cards = soup.select(
        ".b-content__inline_item, .b-content__inline_item-cover, "
        ".short-item, .movie-item, .film-item, .catalog-item"
    )

    # Newer pages may no longer use the legacy DLE card wrapper.
    if not cards:
        cards = []
        for link in soup.select("a[href]"):
            href = str(link.get("href", ""))
            if re.search(r"\\.html(?:$|[?#])", href) or re.search(
                r"/(?:films?|movies|series|cartoons|animation)/[^/?#]+", href
            ):
                cards.append(link)

    seen = set()
    for card in cards:
        if getattr(card, "name", "") == "a":
            link = card
            container = card
            for _ in range(5):
                if container.parent is None:
                    break
                container = container.parent
                if container.select_one("img"):
                    break
        else:
            link = (card.select_one(".b-content__inline_item-link a[href]")
                    or card.select_one("a[href]"))
            container = card
        if not link:
            continue

        url = _safe_url(link.get("href", ""))
        if not url or url in seen:
            continue

        image_node = container.select_one("img") if hasattr(container, "select_one") else None
        title = (link.get("title") or link.get_text(" ", strip=True)
                 or (image_node.get("alt", "") if image_node else "")
                 or (image_node.get("title", "") if image_node else ""))
        if not title:
            continue

        image = ""
        if image_node:
            image = (image_node.get("data-src") or image_node.get("data-original")
                     or image_node.get("data-lazy-src") or image_node.get("src") or "")
        desc_node = container.select_one(
            ".b-content__inline_item-text, .b-content__inline_item-desc, "
            ".description, .short-description"
        ) if hasattr(container, "select_one") else None
        rating_node = container.select_one(".b-rating, .rating") if hasattr(container, "select_one") else None
        year_node = container.select_one(
            ".b-content__inline_item-info, .info, .year"
        ) if hasattr(container, "select_one") else None
        item = _item(
            title, url, image,
            rating_node.get_text(" ", strip=True) if rating_node else 0,
            desc_node.get_text(" ", strip=True) if desc_node else "",
            category,
            year_node.get_text(" ", strip=True) if year_node else ""
        )
        if item["title"] and item["url"]:
            found.append(item)
            seen.add(url)
    return found

def catalog_json(category="Фильмы", page=1):
    category = category if category in CATEGORY_PATHS else "Фильмы"
    page = max(1, min(int(page), 100))
    url = urljoin(DEFAULT_ORIGIN, CATEGORY_PATHS[category])
    if page > 1:
        url = urljoin(url, "page/" + str(page) + "/")
    response = requests.get(url, timeout=25, headers={
        "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
        "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
        "Referer": DEFAULT_ORIGIN + "/"
    })
    response.raise_for_status()
    items = _parse_cards(response.text, category)
    if not items:
        fallback_path = "/films/" if CATEGORY_PATHS[category] == "/movies" else CATEGORY_PATHS[category] + "/"
        fallback_url = urljoin(DEFAULT_ORIGIN, fallback_path)
        if page > 1:
            fallback_url = urljoin(fallback_url, "page/" + str(page) + "/")
        fallback = requests.get(fallback_url, timeout=20, headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        })
        fallback.raise_for_status()
        items = _parse_cards(fallback.text, category)
    if not items:
        raise RuntimeError("HDRezka вернула страницу без карточек. Возможно, зеркало недоступно или показана защита сайта.")
    return json.dumps({"results": items[:60], "category": category, "page": page}, ensure_ascii=False)

def search_json(query):
    query = str(query or "").strip()
    if len(query) < 2:
        return json.dumps({"results": []}, ensure_ascii=False)
    raw = HdRezkaSearch(DEFAULT_ORIGIN)(query, find_all=True)
    items = getattr(raw, "all", None)
    if items is None:
        items = raw if isinstance(raw, list) else []
    results = []
    seen = set()
    for item in items[:60]:
        url = _safe_url(item.get("url", ""))
        if not url or url in seen:
            continue
        seen.add(url)
        category = str(item.get("category", ""))
        results.append(_item(item.get("title"), url, item.get("image", ""),
                             item.get("rating", 0), item.get("description", ""), category))
    return json.dumps({"results": results}, ensure_ascii=False)

def details_json(url):
    url = _safe_url(url)
    if not url:
        raise ValueError("Недопустимая ссылка HDRezka")
    film = HdRezkaApi(url)
    if not film.ok:
        raise RuntimeError(str(film.exception or "Не удалось открыть страницу"))
    seasons = []
    for season in (getattr(film, "episodesInfo", None) or []):
        seasons.append({
            "season": int(season.get("season", 0)),
            "title": str(season.get("season_text", "")),
            "episodes": [{
                "episode": int(ep.get("episode", 0)),
                "title": str(ep.get("episode_text", "")),
                "translations": [{
                    "id": str(t.get("translator_id", "")),
                    "name": str(t.get("translator_name", ""))
                } for t in (ep.get("translations", []) or [])]
            } for ep in season.get("episodes", [])]
        })
    rating = getattr(film, "rating", None)
    return json.dumps({
        "title": str(getattr(film, "name", "")),
        "description": str(getattr(film, "description", "")),
        "poster": _safe_url(getattr(film, "thumbnailHQ", "") or getattr(film, "thumbnail", "")),
        "rating": _rating(getattr(rating, "value", 0)),
        "votes": int(getattr(rating, "votes", 0) or 0),
        "category": str(getattr(film, "category", "")),
        "type": str(getattr(film, "type", "")),
        "seasons": seasons
    }, ensure_ascii=False)

def _stream_for_url(url, translation=None, season=None, episode=None):
    film = HdRezkaApi(url)
    if not film.ok:
        raise RuntimeError("Страница фильма: " + str(film.exception or "не открылась"))

    is_series = season is not None and episode is not None
    if is_series:
        try:
            season_info = next(
                (s for s in (film.episodesInfo or []) if int(s.get("season", 0)) == int(season)),
                None
            )
            if not season_info:
                raise RuntimeError("Сезон " + str(season) + " не найден в карточке")
            episode_info = next(
                (e for e in season_info.get("episodes", []) if int(e.get("episode", 0)) == int(episode)),
                None
            )
            if not episode_info:
                raise RuntimeError("Серия " + str(episode) + " не найдена в сезоне " + str(season))
            available = episode_info.get("translations", []) or []
            translator_map = {
                str(t.get("translator_id")): str(t.get("translator_name", t.get("translator_id")))
                for t in available
            }
        except Exception as exc:
            raise RuntimeError("Не удалось получить список серий/озвучек: " + str(exc)) from exc
    else:
        translator_map = {
            str(key): str(value.get("name", key) if isinstance(value, dict) else value)
            for key, value in (getattr(film, "translators", {}) or {}).items()
        }

    if not translator_map:
        raise RuntimeError("HDRezka не вернула доступные озвучки")

    if translation is not None and str(translation).strip():
        requested = str(translation).strip()
        if requested in translator_map:
            translator_id = requested
        else:
            match = next((key for key, name in translator_map.items() if name == requested), None)
            if match is None:
                raise RuntimeError("Озвучка '" + requested + "' отсутствует. Доступно: " + ", ".join(translator_map.values()))
            translator_id = match
    else:
        try:
            ordered = film.sort_translators(
                {int(key): {"name": name} for key, name in translator_map.items()}
            )
            translator_id = str(next(iter(ordered.keys())))
        except Exception:
            translator_id = next(iter(translator_map.keys()))

    data = {
        "id": film.id,
        "translator_id": translator_id,
        "action": "get_stream" if is_series else "get_movie"
    }
    if is_series:
        data["season"] = str(season)
        data["episode"] = str(episode)

    endpoint = film.origin.rstrip("/") + "/ajax/get_cdn_series/"
    response = requests.post(
        endpoint,
        data=data,
        headers=film.HEADERS,
        cookies=film.cookies,
        proxies=film.proxy,
        timeout=(12, 30)
    )
    if response.status_code != 200:
        raise RuntimeError("AJAX HDRezka вернул HTTP " + str(response.status_code) + " (" + endpoint + ")")
    try:
        payload = response.json()
    except Exception as exc:
        raise RuntimeError("HDRezka AJAX вернул не JSON: " + response.text[:180]) from exc

    if not payload.get("success"):
        raise RuntimeError("HDRezka отклонила запрос потока: " + str(payload.get("message") or "success=false"))
    encoded = payload.get("url")
    if not encoded:
        raise RuntimeError("HDRezka ответила успешно, но поле url пустое")

    decoded = film.clearTrash(str(encoded))
    qualities = {}
    for part in decoded.split(","):
        if "[" not in part or "]" not in part:
            continue
        before, after = part.split("]", 1)
        quality = re.sub(r"<[^>]*>", "", before.split("[", 1)[-1]).strip()
        links = []
        for candidate in re.split(r"\s+or\s+", after):
            candidate = re.sub(r"<[^>]*>", "", candidate).strip().strip("\"'")
            candidate = candidate.replace("\\/", "/")
            candidate = candidate.rstrip("),;")
            if candidate.startswith(("http://", "https://")):
                links.append(candidate)
        if quality and links:
            qualities[quality] = links

    if not qualities:
        raise RuntimeError("HDRezka вернула ответ без распознанных видеоссылок (возможно, изменила формат потока)")
    subtitle_data = payload.get("subtitle") or ""
    subtitle_codes = payload.get("subtitle_lns") or ""
    subtitles = []
    if isinstance(subtitle_data, str) and subtitle_data:
        for index, link in enumerate(subtitle_data.split(",")):
            link = link.strip()
            if link.startswith("http"):
                code = subtitle_codes.split(",")[index].strip() if isinstance(subtitle_codes, str) and index < len(subtitle_codes.split(",")) else "sub"
                subtitles.append({"language": code, "url": link})

    return {
        "title": str(getattr(film, "name", "")),
        "translators": [{"id": key, "name": value} for key, value in translator_map.items()],
        "qualities": qualities,
        "subtitles": subtitles
    }


def stream_json(url, translation=None, season=None, episode=None):
    url = _safe_url(url)
    if not url:
        raise ValueError("Недопустимая ссылка HDRezka")

    parsed = urlparse(url)
    path = parsed.path
    query = ("?" + parsed.query) if parsed.query else ""
    hosts = [parsed.netloc]
    for host in ("hdrezka.ag", "hdrezka.ax"):
        if host not in hosts:
            hosts.append(host)

    errors = []
    for host in hosts:
        candidate = "https://" + host + path + query
        try:
            result = _stream_for_url(candidate, translation, season, episode)
            result["source_host"] = host
            return json.dumps(result, ensure_ascii=False)
        except Exception as exc:
            errors.append(host + ": " + str(exc))
            # Retry another official mirror only for an HTTP/mirror or AJAX failure.
            continue

    raise RuntimeError("Не удалось получить поток ни с одного зеркала HDRezka. " + " | ".join(errors))
