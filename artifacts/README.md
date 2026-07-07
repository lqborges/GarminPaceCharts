# Build Artifacts

APKs are distributed via `onedrive:apk/` (rclone), not committed to git.

Build and upload:

```bash
scripts/garmin-pace-ops.sh release
```

Or manually: `./gradlew assembleDebug`, then `rclone copyto` the `.apk` and `.zip` from
`artifacts/` to `onedrive:apk/`.
