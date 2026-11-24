# Lightweight Render test deployment.
# Uses the official upstream image; does not fork or modify Lampac binaries.
FROM ghcr.io/lampac-nextgen/lampac:latest

COPY --chown=lampac:lampac init.conf /lampac/init.conf

# Render's default web-service port.
EXPOSE 10000
