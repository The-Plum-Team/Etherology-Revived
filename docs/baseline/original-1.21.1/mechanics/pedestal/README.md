# Pedestal baseline contract

This directory records the native `published-0.1.7` Pedestal baseline contract.
The repository-owned Fabric 1.21.1 v11 profile consumed its sole launch on
2026-09-04, reached a fresh integrated world, and then exceeded the
controller's 1,800-second deadline before publishing a report or screenshot.
V11 is diagnostic history, not accepted runtime evidence. The fresh v12
profile later consumed its own sole launch and also failed closed; it is
separate diagnostic history and cannot be reused. The v13 profile then
consumed its sole launch and failed closed after its gallery capture; it also
remains diagnostic history and cannot be reused. The separate v14 profile then
consumed its sole launch and completed normally. Its schema-4 report passed all
74 ordered assertions and its immutable
[`pedestal-v14`](../../../../evidence/original-1.21.1/pedestal-v14/) archive is
the accepted native Pedestal baseline. None of the four profiles may be reused.

The dedicated scenario is designed to record:

- the `etherology:pedestal` block, item, and
  `etherology:pedestal_block_entity` registry identities, all 1,024 states,
  default properties, horizontal facings, outline shapes, recipe, advancement,
  loot, tags, and 64 byte-pinned client/data resources;
- real `BlockItem` standalone, waterlogged, two-high, and three-high placement,
  including the `full`, `bottom`, `middle`, and `top` shape transitions and the
  block-entity presence rule;
- carpet placement, no-op, swap, retrieval, ordinary item placement/retrieval,
  the two one-item inventory slots, serialized `Items`/`removed` data, and the
  closed `SidedInventory` contract;
- ordinary-item dispensing into a Pedestal from all six directions and carpet
  dispensing from the four horizontal directions, plus upward carpet
  dispensing into an occupied carpet slot and empty display slot;
- the item/carpet drops and stale block-entity removal caused by converting a
  populated `full` Pedestal to `bottom`, plus ordinary replacement with air;
- a forced save, full disconnect/reopen, exact state/inventory equality, and
  four unedited 1920x1080 framebuffer captures after stable renders.

Read-only bytecode inspection of the hash-pinned published `0.1.7` Etherology
JAR (`2,743,963` bytes; SHA-256
`38de3c1aad47fc715c2226266dec4c70c02d16370034a4e0350508131ac15c43`)
confirms that the empty-carpet-slot path stores and decrements the carpet,
takes the dispenser direction's opposite, and passes it through
`PedestalBlockEntity.setCarpetColor` to the Pedestal's horizontal-only facing
property. The scenario records `UP` and `DOWN` as explicit
`hash-pinned-published-0.1.7-bytecode-not-executed-safety-guard` limitations and
never executes a vertical carpet against an empty carpet slot. Item dispensing
still exercises all six directions; ordinary empty-slot carpet dispensing
exercises north, south, west, and east; and the occupied-carpet fallthrough is
exercised upward.

The harness reaches the original only through registries and ordinary
Minecraft block/item/inventory/interaction APIs. Dotted original class names
are observations to compare, not linked implementation types.

The accepted run published these four unedited 1920x1080 framebuffers after
120 stable completed renders each:

1. [`pedestal-gallery.png`](../../../../evidence/original-1.21.1/pedestal-v14/screenshots/pedestal-gallery.png)
2. [`pedestal-transition-drops.png`](../../../../evidence/original-1.21.1/pedestal-v14/screenshots/pedestal-transition-drops.png)
3. [`pedestal-persistence-initial.png`](../../../../evidence/original-1.21.1/pedestal-v14/screenshots/pedestal-persistence-initial.png)
4. [`pedestal-persistence-reopened.png`](../../../../evidence/original-1.21.1/pedestal-v14/screenshots/pedestal-persistence-reopened.png)

The v1.4.0 harness passed its clean build, 47 Java tests, remap, and artifact
validation. The v11 manifest pins its `339,617` bytes and SHA-256
`09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57`.
Its run emitted no direct camera telemetry, but two saved player snapshots
preserve an identical pose away from the exact camera contract. Source
inspection shows the render-ready failure returning to the client-mirror stage
while resetting the per-stage timer, which explains the eventual outer timeout
as an inference rather than mechanic evidence. The compact diagnostic record is
[`pedestal-v11`](../../../../evidence/original-1.21.1/pedestal-v11/). The
ignored v11 runtime remains preserved and must never be launched again.

The v12 v1.4.1 harness preserved the exact 74-assertion contract and camera
coordinates. It cleared input after each client tick, restored the exact player
pose at `GameRenderer.render` HEAD before the framebuffer was drawn, and no
longer returned from render readiness to a stage that reset the watchdog. Its
51 Java tests, remap, artifact validation, and two reproducible clean builds
passed. Both builds produced a `340,250`-byte JAR with SHA-256
`a99809d6443a4757c860e98d2f09e1d5775667a69e331a7e631930eb5728c7eb`.

The v12 native run reached a fresh integrated world and failed at client tick
155 during the first dispenser inspection. The harness powered all dispensers
during immediate setup and inspected them after a fixed delay without proving
that every fixture chunk was ticking. All eight fixtures at `x<0`, including
the occupied-carpet upward fixture at `x=-15`, remained unfired with both
inputs. All four fixtures at `x>=0` fired exactly once. This exact chunk split
indicates redstone-scheduling nondeterminism in the harness as the
best-supported inference, not accepted direction-specific Pedestal behavior;
the retained runtime cannot prove the scheduler cause conclusively. The
remaining lifecycle and capture assertions were never exercised and retained
default failed values.

The harness published its failed report and marker at 16:52:51 Europe/Madrid,
then called `client.scheduleStop()`. Shutdown stalled after `Saving worlds`
until the controller reached 1,800 seconds, killed its owned process group, and
returned exit code `2`. There was no clean shutdown, controller verification,
or screenshot. The compact record is
[`pedestal-v12`](../../../../evidence/original-1.21.1/pedestal-v12/); its
preserved runtime must never be launched again. V13 replaces redstone-powered
fixture scheduling with one explicit vanilla scheduled tick per placed
dispenser, using the actual placed state and still entering the published
Etherology mixin. Its 51 tests and two clean reproducibility builds produced a
`340,155`-byte JAR with SHA-256
`82e443947ae46b20a6c1e3cc10aedeadb2ed34450cc929b22e9405e2b5c45e04`.
The controller verifies the failed marker against its regular report before a
15-second failure-shutdown grace.

The v13 native run completed the gallery phase and wrote the sole retained
`pedestal-gallery.png` 1920x1080 capture. It then spent 6,000 stage ticks in
`WAITING_FOR_CLIENT_MIRROR` for `transition-drops` before failing closed. Its
report records 49 of 74 assertions true; the transition capture, persistence,
restart, reopened-world, and three later screenshot assertions were not
completed. The native client and controller shut down cleanly, but clean
shutdown cannot make an incomplete scenario acceptable. The run is diagnostic
only and its compact archive is
[`pedestal-v13`](../../../../evidence/original-1.21.1/pedestal-v13/). The
preserved v13 runtime must never be launched again.

V13 proved the five exact client transition block/block-entity predicates, but
capture readiness also required a transient client item-entity drop map to
equal the already-authoritative server drop map. The archive does not record
the transient client map, so the mismatch's cause remains unknown. V14 changed
only that equality to diagnostic data recorded at mirror readiness and before
capture. Authoritative server drop assertions, the exact client block-state
snapshot, all five client stale-block-entity/removal and replacement-air
checks, and the complete 74-assertion contract remained mandatory. The v14
v1.4.3 harness passed 51 Java tests and two reproducible clean builds. Both
builds produced a `340,723`-byte JAR with SHA-256
`9a329ff219f4403c8880597ed851a73843c74adf81ac4b5561b6708cf82129b6`.

The sole v14 native launch completed the gallery, transition/drop, initial
persistence, and reopened-persistence phases in 2,297 client ticks. The
schema-4 report passed 74 of 74 assertions, the client shut down cleanly, and a
full disconnect plus integrated-server restart preserved the exact Pedestal
state and inventory. At both mirror readiness and pre-capture, the diagnostic
client item-entity map exactly contained one blue carpet, diamond, emerald, and
red carpet, matching the authoritative server drops. This exact v14 success
does not establish why the consumed v13 attempt stalled.

The report, completion marker, controller log and verification, four captures,
and selected saved-world proof are preserved byte-for-byte under
[`docs/evidence/original-1.21.1/pedestal-v14`](../../../../evidence/original-1.21.1/pedestal-v14/).
The ignored v14 runtime remains preserved for integrity verification only and
must never be launched again.
