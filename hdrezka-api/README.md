# ARNIX Cinema — HDRezka bridge

FastAPI bridge for ARNIX Cinema. Catalog, search, poster URLs, and title metadata use the async [`ndenissov/HDRezka`](https://github.com/ndenissov/HDRezka) client (`hdrezka==5.2.0`). The existing `/stream` endpoint remains on `HdRezkaApi` so playback extraction is kept separate.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Endpoints:
- `GET /health`
- `GET /catalog?category=Фильмы&page=1` (also `Сериалы`, `Мультфильмы`, `Аниме`)
- `GET /search?q=Название&page=1`
- `GET /details?url=<HDRezka title URL>`
- `GET /stream?url=https%3A%2F%2Fhdrezka.ag%2F...`
- Series: add `season=1&episode=1`; optional `translation=<translator id>`.

## Deploy

Create a Render Web Service from this repository:
- Root Directory: `hdrezka-api`
- Runtime: Python
- Build Command: `pip install -r requirements.txt`
- Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`

Set Render environment variable `HDREZKA_HOST` to the HDRezka mirror that is reachable from the service (default: `https://rezka.ag/`). The Android catalog client currently calls `https://arnix-cinema-hdrezka-api.onrender.com`. HDRezka mirrors, anti-bot protection, geo restrictions, and stream host availability can affect extraction and playback. Use only content you are authorized to access.
