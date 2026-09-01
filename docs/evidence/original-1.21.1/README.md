# Original Fabric 1.21.1 evidence

This archive preserves native evidence from the separately isolated original
Etherology runtime. It is the `published-0.1.7` binary reference, not the
unbound `source-0.1.8` tree.

## `phase0-smoke-v1`

The repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v1` completed its one native
run on 2026-08-31 at 17:35 Europe/Madrid. The controller verified all 30 ordered
assertions, 120 consecutive ready renders, the unedited 1920x1080 framebuffer,
the forced world save, clean client shutdown, immutable launcher inputs, and
report-before-marker publication.

![Original 0.1.7 phase-zero fixture](phase0-smoke-v1/screenshots/phase0-smoke.png)

The image shows the live integrated-world fixture: Ethereal Storage on the
left, the Empowerment Table in the center, the Brewing Cauldron on the right,
and the animated Armillary Sphere behind the table. It proves the exact four
blocks and block-entity types were present on both server and client and that
their original models rendered. It does not by itself prove brewing,
empowerment, transmutation, inventory, or Ether-transfer behavior.

- Screenshot SHA-256:
  `aa69505119804f49936b6f0549566a25744010c96a0a5ac4f9064f3c4b0cafdb`
- [`report.json`](phase0-smoke-v1/reports/report.json): exact assertion record
- [`done.marker`](phase0-smoke-v1/reports/done.marker): report-hash-bound final
  publication marker
- [`verification.json`](phase0-smoke-v1/controller/verification.json):
  controller verification record
- [`original-client.log`](phase0-smoke-v1/controller/original-client.log):
  complete native client log
- [`archive-manifest.json`](phase0-smoke-v1/archive-manifest.json): hashes,
  capture times, controller identity, runtime locks, and artifact provenance

The client made the normal failed Realms authorization attempt associated with
its deliberately offline token; this appears in the complete log and did not
affect the integrated world or any of the 30 accepted assertions. The skin cache
remained absent.

## `forest-lantern-v2`

The fresh repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v2` completed its sole native
run on 2026-08-31 at 22:51 Europe/Madrid. The controller verified 36 ordered
assertions, 120 consecutive ready renders, one unedited 1920x1080 framebuffer,
the forced world save, clean client shutdown, immutable launcher inputs, and
report-before-marker publication. Provisioning and launch consulted zero
external game profiles, and the mutable skin cache remained absent.

![Original 0.1.7 Forest Lantern gallery](forest-lantern-v2/screenshots/forest-lantern.png)

The live integrated-world gallery contains all 20 combinations of age `0..4`
and horizontal facing. The machine record additionally proves the exact block
and item registries, properties and default state, distinct network state IDs,
client/server mirrors, shears speed `15.0`, empty immature loot, the one-item
mature drop, the three crumb cooking recipes, the leather recipe, and a real
seeded `PlayerEntity.jump()` that broke one mature lantern and dropped exactly
one lantern. The gallery states were deliberately arranged for comparison; the
image is not evidence of natural growth or world generation.

- Screenshot SHA-256:
  `733aa553bb841c76e471bd9e5f65a3a3c178a095241ed33261f63a1eebf1aa1f`
- [`report.json`](forest-lantern-v2/reports/report.json): exact 36-assertion record
- [`done.marker`](forest-lantern-v2/reports/done.marker): report-hash-bound final
  publication marker
- [`verification.json`](forest-lantern-v2/controller/verification.json):
  controller verification record
- [`original-client.log`](forest-lantern-v2/controller/original-client.log):
  complete native client log
- [`archive-manifest.json`](forest-lantern-v2/archive-manifest.json): hashes,
  capture times, controller identity, runtime locks, and artifact provenance

## `attrahite-block-registry-v4`

The fresh repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v4` completed its sole native
run on 2026-09-01 at 01:56 Europe/Madrid. The controller accepted all 49 ordered
assertions, 120 consecutive ready renders, the unedited 1920x1080 framebuffer,
the forced world save, clean client shutdown, immutable launcher inputs, and
report-before-marker publication. The consumed v3 profile was not reused, no
external game profile was consulted, and the mutable skin cache remained
absent.

![Original 0.1.7 Attrahite gallery](attrahite-block-registry-v4/screenshots/attrahite-block-registry.png)

The live integrated-world gallery shows the four registered blocks: Attrahite,
Attrahite Bricks, the brick slab, and the brick stairs. The machine record also
proves their four block items, runtime classes, exact default states and state
counts, stable network IDs, block and item tags, nine recipes, four loot tables,
Silk Touch behavior, deterministic plain-tool and Fortune III outcomes, and
saved-world persistence. It does not establish natural ore generation.

- Screenshot SHA-256:
  `61a405e33b09c2d55c117776c8c1f1bae8906be14f8104b676553e85eb97ab09`
- [`report.json`](attrahite-block-registry-v4/reports/report.json): exact
  49-assertion record
- [`done.marker`](attrahite-block-registry-v4/reports/done.marker):
  report-hash-bound final publication marker
- [`verification.json`](attrahite-block-registry-v4/controller/verification.json):
  controller verification record
- [`original-client.log`](attrahite-block-registry-v4/controller/original-client.log):
  complete native client log
- [`archive-manifest.json`](attrahite-block-registry-v4/archive-manifest.json):
  hashes, capture times, controller identity, runtime locks, and artifact
  provenance
