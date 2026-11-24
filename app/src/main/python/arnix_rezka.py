import json
from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch

# This parser runs inside the Android app. It does not expose a network server.
DEFAULT_ORIGIN = "https://hdrezka.ag"


def search_json(query):
    items = HdRezkaSearch(DEFAULT_ORIGIN)(query)
    results = []
    for item in items[:30]:
        url = str(item.get("url", ""))
        if url.startswith("https://") and "hdrezka." in url:
            results.append({
                "title": str(item.get("title", "")),
                "url": url,
                "rating": item.get("rating")
            })
    return json.dumps({"results": results}, ensure_ascii=False)


def stream_json(url, translation=None):
    if not url.startswith("https://") or "hdrezka." not in url:
        raise ValueError("Unsupported HDRezka address")
    film = HdRezkaApi(url)
    if not film.ok:
        raise RuntimeError(str(film.exception or "Cannot open title"))
    translators = film.translators or {}
    source = film.getStream(translation=translation)
    videos = getattr(source, "videos", {}) or {}
    subtitle_data = getattr(getattr(source, "subtitles", None), "subtitles", {}) or {}
    subtitles = []
    for code, value in subtitle_data.items():
        if isinstance(value, dict):
            subtitles.append({
                "language": str(value.get("title", code)),
                "url": str(value.get("link", ""))
            })
    return json.dumps({
        "title": str(getattr(film, "name", "")),
        "translators": [
            {"id": str(key), "name": str(value.get("name", key))}
            for key, value in translators.items()
        ],
        "qualities": {
            str(quality): [str(link) for link in links if str(link).startswith("https://")]
            for quality, links in videos.items()
        },
        "subtitles": subtitles
    }, ensure_ascii=False)
