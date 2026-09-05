# Forge Slitherite dedicated-server v20 process-group handoff failure

Status: consumed, rejected, and retained for forensics. Never launch this
profile again. This directory is not accepted gameplay evidence.

The repository-owned Forge 1.20.1 dedicated-server profile v20 started once on
2026-09-05 from source commit
`f6f5fcbebb7b53bf9abc261ce3bc32573f32c873`. The in-server handoff authenticated
the actual Java 17 process and exact `-Xmx2048m` heap, but the controller stopped
before acknowledging it with:

`error: The server Java process is outside the owned Gradle launch PGID`

## Root cause

The controller owned PID/PGID `29021/29021` and launched the Gradle wrapper as
PID/PGID `29029/29029`. To honor the configured Gradle JVM settings, Gradle
created a single-use daemon as PID/PGID `29050/29050`. The JavaExec server was
PID `30221` with parent and PGID `29050`. The controller's v20 contract required
the server to share the wrapper's PGID, so it correctly rejected the different
group before starting the native memory monitor or publishing the
acknowledgement.

This was an ownership-authentication failure, not a memory-limit stop. No
authoritative `phys_footprint` sample was established. Diagnostic `/bin/ps`
sampling observed a peak aggregate RSS of 2,688,656 KiB (2.564 GiB): 1,592,864
KiB for the server, 1,001,104 KiB for the daemon, 81,104 KiB for the wrapper,
and 13,584 KiB for the controller. That was well below the 8/12/16-GiB warning,
hard-stop, and emergency thresholds.

## Retained exact bytes

The files beside this report are byte-for-byte copies from the consumed attempt.
The owner-private runtime copies remain in place.

- `run.attempted`: 95 bytes, original mode `0600`, SHA-256
  `a9281b174856c8ff81edb5814b8e17c8ff971091ae32375c0c3775888c8939d7`
- `profile-marker.json`: 699 bytes, original mode `0600`, SHA-256
  `676c31e4dd079034bfa6867ebd04d382c76dc2e28633139ea9f742a632dbb7b8`
- `java-memory-handoff.json`: 305 bytes, original mode `0600`, SHA-256
  `1eea04d9a04c2344a542c9b36cde8ef44a6a9dde4bc9f6bff1e8251d8334a3b3`
- `latest.log`: 6,486 bytes, SHA-256
  `746400ff46a5b04151813549084217fc11405cbe1903fedfd06cee6778acf56b`
- `debug.log`: 3,812,114 bytes, SHA-256
  `9aef63ef16a54aef23e5fb91f69336b8dec90d0277b7a43074de72b80dc86c65`

The handoff's run token is retained only as immutable forensic data. Its owning
processes are gone, its run lock was released, and the v20 attempt identity is
consumed, so it cannot authorize another launch.

## Absence and cleanup proof

The acknowledgement, guard readiness, and guard telemetry files are absent.
The server reached Forge mod loading but did not start a world, run the probe
lifecycle, create a report, publish a completion marker, or create an accepted
archive. The scenario report and log staging directories are empty.

After fail-closed cleanup, PIDs `29021`, `29029`, `29050`, and `30221` and PGIDs
`29021`, `29029`, and `29050` were all rechecked as absent. The run lock was
released only after cleanup; the launch-attempt marker remains. The ignored v20
runtime remains preserved and must not be reused.

Recovery requires a new profile identity and a controller that authenticates
the actual Gradle execution group before it acknowledges the server. This
failure record does not authorize or claim a v21 launch or acceptance.
