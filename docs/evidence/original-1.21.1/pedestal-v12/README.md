# Pedestal v12 consumed-run diagnostic — not accepted evidence

The repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v12` consumed its only native
launch on 2026-09-04. The original published `0.1.7` client reached a fresh
integrated world, and the v1.4.1 harness failed closed at client tick 155 while
inspecting the dispenser fixtures. It published a failed report and its
report-hash-bound marker at 16:52:51 Europe/Madrid, before any screenshot.

The immediate mismatch was not a direction-specific Pedestal result. The
harness powered every dispenser with a redstone block during initial world
setup and inspected the fixtures after a fixed delay without first proving
that every fixture chunk was ticking. All eight fixtures at `x<0`, including
the occupied-carpet upward fixture at `x=-15`, remained unfired with both input
items. All four fixtures at `x>=0` fired exactly once. That exact chunk split
indicates redstone-scheduling nondeterminism in the harness as the
best-supported inference; the retained runtime cannot prove the scheduler cause
conclusively.
Assertions after this first failed inspection retained unexecuted defaults in
the report and are not separate mechanic failures.

The failure path called `client.scheduleStop()` after publishing the report.
The log then reached `Stopping!` and integrated-server shutdown, but stopped at
`Saving worlds`. The process remained alive until the controller reached its
exact 1,800-second ceiling, killed its owned process group, and returned exit
code `2`. The timeout and kill were directly witnessed in the parent terminal;
the archived controller log contains the complete streamed game output but no
post-process timeout line. Consequently `clean_shutdown=false`, no controller
verification exists, no screenshot exists, and v12 establishes no accepted
native Pedestal behavior.

The compact retained payload is:

- [`report.json`](reports/report.json), the exact 41,416-byte failed report with
  all 74 assertion records and the direct dispenser observations;
- [`done.marker`](reports/done.marker), the exact 104-byte failed marker bound
  to the report SHA-256;
- [`original-client.log`](controller/original-client.log), the complete
  19,386-byte controller log. The 18,958-byte runtime `latest.log` is its exact
  suffix after a 428-byte controller header and is not duplicated;
- [`diagnostic-manifest.json`](diagnostic-manifest.json), the fail-closed
  identity, hashes, outcome, and terminal-observation provenance.

The ignored runtime remains preserved at
`scripts/baseline/.state/runtimes/etherology-original-fabric-1.21.1-published-0.1.7-v12`
and must never be launched again. The next attempt is v13 with planned harness
version 1.4.2. It is not yet prepared, provisioned, or launched and has no
artifact, manifest, verifier, profile, or evidence hashes to claim.
