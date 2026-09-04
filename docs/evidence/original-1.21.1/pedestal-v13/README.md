# Pedestal v13 consumed-run diagnostic — not accepted evidence

The repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v13` consumed its sole native
launch on 2026-09-04. The original published `0.1.7` client reached a fresh
integrated world with the v1.4.2 harness. The gallery phase completed and wrote
one native 1920×1080 framebuffer capture. The run is diagnostic only: it did
not complete the 74-assertion scenario and is not accepted as Pedestal behavior
evidence.

The actual harness lifecycle failure occurred in
`WAITING_FOR_CLIENT_MIRROR` for the `transition-drops` phase. That stage began
at client tick 1,996 and hit its 6,000-stage-tick limit at client tick 7,996.
The exact published failure was:

```
Timed out in WAITING_FOR_CLIENT_MIRROR after 6000 client ticks; capture_phase=transition-drops; camera=first_person=true;x=0.5;y=121.0;z=-15.5;yaw=0.0;pitch=10.0;on_ground=true
```

The failed report records 49 of 74 assertions true and 25 false. The false
records at and after the interrupted transition phase include fail-closed or
unexecuted defaults for the transition capture, persistence, restart, reopened
world, and later screenshots. They are consequences of the lifecycle stopping
point, not 25 independent Pedestal mechanic failures. The underlying cause of
the client-mirror timeout is not proven by this archive.

After atomically publishing the failed report and its report-hash-bound marker,
the harness requested shutdown. The controller log records normal integrated
server teardown and ends with `All dimensions are saved`; the native process
and controller both ended without a timeout or forced process-group kill. The
controller then correctly rejected the archive because these three required
screenshots were missing:

- `pedestal-transition-drops.png`
- `pedestal-persistence-initial.png`
- `pedestal-persistence-reopened.png`

Therefore clean native shutdown does not imply successful scenario acceptance,
and no controller verification was published.

The compact retained payload is:

- [`report.json`](reports/report.json), the exact 51,222-byte failed report with
  all 74 assertion records and the lifecycle failure;
- [`done.marker`](reports/done.marker), the exact 104-byte failed marker bound
  to the report SHA-256;
- [`pedestal-gallery.png`](screenshots/pedestal-gallery.png), the sole exact
  284,835-byte native framebuffer capture; it is retained diagnostically and is
  not accepted on its own;
- [`original-client.log`](controller/original-client.log), the complete exact
  21,738-byte controller log. The 21,310-byte runtime `latest.log` is its exact
  suffix after a 428-byte controller header and is not duplicated;
- [`diagnostic-manifest.json`](diagnostic-manifest.json), the fail-closed
  identity, hashes, outcome, source bindings, and terminal-observation
  provenance.

The ignored runtime remains preserved at
`scripts/baseline/.state/runtimes/etherology-original-fabric-1.21.1-published-0.1.7-v13`
and must never be launched again. Any successor attempt must use a newly
provisioned repository-owned profile and evidence target.
