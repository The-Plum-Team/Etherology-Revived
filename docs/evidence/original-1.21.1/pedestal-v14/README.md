# Pedestal v14 accepted native baseline

This directory preserves the accepted result from the sole native launch of
the repository-owned
`etherology-original-fabric-1.21.1-published-0.1.7-v14` profile on 2026-09-04.
The original published `0.1.7` Fabric 1.21.1 client completed normally, all 74
ordered assertions passed, four unedited 1920×1080 Minecraft framebuffer
captures were published, the integrated world was fully restarted, and the
saved Pedestal state and inventory were exact after reopening.

The accepted visual record is:

- [`pedestal-gallery.png`](screenshots/pedestal-gallery.png), showing standalone,
  two-high, three-high, decorated, item-bearing, and waterlogged Pedestals;
- [`pedestal-transition-drops.png`](screenshots/pedestal-transition-drops.png),
  showing the post-transition block layout and spawned item drops;
- [`pedestal-persistence-initial.png`](screenshots/pedestal-persistence-initial.png),
  captured after the forced save;
- [`pedestal-persistence-reopened.png`](screenshots/pedestal-persistence-reopened.png),
  captured after a full disconnect and integrated-server restart.

The machine verifier accepted registry and block-item identity, all 1,024 block
states and unique raw IDs, resources, recipe/advancement/loot data, placement,
waterlogging, stack shapes, block-entity placement, exact voxel bounds, ten
player interaction branches, two-slot NBT, denied sided automation, dispenser
item placement in all six directions, horizontal carpet placement, occupied
and full-target fallback behavior, block-entity removal, exact drops, client
stale-reference removal, rendering, camera pose, native captures, and restart
persistence. The two persistence screenshots differ materially in only
`0.001283275462962963` of pixels under the verifier's threshold.

Both v14 client-drop checkpoints were explicitly recorded. The mirror-ready
and pre-capture maps each exactly contained one blue carpet, diamond, emerald,
and red carpet, matching the authoritative server snapshot. Their equality was
diagnostic-only and did not replace any of the 74 required assertions. The v13
archive does not contain its transient client map, so v14 success does not prove
why the earlier one-shot v13 attempt stalled.

One safety limitation remains explicit: an empty-carpet-slot vertical dispenser
path was not executed because the hash-pinned published bytecode reaches a
horizontal-only facing property. The guarded condition and the occupied upward
fallback were verified without invoking that unsafe branch.

[`report.json`](reports/report.json), [`done.marker`](reports/done.marker), the
complete [`controller log`](controller/original-client.log), and the captured
[`controller verification`](controller/verification.json) preserve the
authenticated result. `world-proof/` retains `level.dat` plus the negative and
nonnegative region/entity files containing the fixture area. Every archived
payload is byte-for-byte identical to the consumed runtime and is pinned by
`archive-manifest.json`.

The ignored v14 runtime remains preserved at
`scripts/baseline/.state/runtimes/etherology-original-fabric-1.21.1-published-0.1.7-v14`
and must never be launched again. The v11, v12, and v13 attempts remain separate
diagnostic history and are not accepted as Pedestal behavior evidence.
