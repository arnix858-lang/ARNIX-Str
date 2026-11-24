# ARNIX Lampac — Render Free test

This folder deploys the official Lampac NextGen container with a reduced configuration.
It does not copy or modify upstream Lampac source code.

## Deploy

1. Open https://dashboard.render.com/blueprints
2. Connect the GitHub repository `arnix858-lang/ARNIX-Str`.
3. Select the `main` branch and the root `render.yaml` Blueprint.
4. Create the `arnix-lampac-test` web service using the Free plan.
5. After deployment, open the service URL and check `/api/chromium/ping`.
   A healthy response is `pong`.

The service listens on port 10000. The test config disables Chromium, Firefox,
and several optional modules to reduce memory usage while retaining the core
provider modules.

## Limitations

- Render Free has only 512 MB RAM and may restart or sleep when idle.
- Lampac may still exceed the memory limit; this deployment is an experiment,
  not a promise of stable 24/7 playback.
- The Free service filesystem is ephemeral. Do not store important data there.
- Some providers can stop working independently because their upstream sites
  change or block hosting IP addresses.
- Do not expose administrative endpoints or enable heavy modules on a public
  free instance without proper authentication and network protection.

Upstream project and AGPL-3.0 license:
https://github.com/lampac-nextgen/lampac
