# ARNIX Cinema — HDRezka bridge

Small Python service used by the Android app to search HDRezka and extract available video URLs via the `HdRezkaApi` package.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Endpoints:
- `GET /health`
- `GET /search?q=Название`
- `GET /stream?url=https%3A%2F%2Fhdrezka.ag%2F...`
- Series: add `season=1&episode=1`; optional `translation=<translator id>`.

## Deploy

Create a Render Web Service from this repository:
- Root Directory: `hdrezka-api`
- Runtime: Python
- Build Command: `pip install -r requirements.txt`
- Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`

The Android app is not connected until the deployed service URL is configured. HDRezka mirrors, anti-bot protection, geo restrictions, and stream host availability can affect extraction and playback. Use only content you are authorized to access.
