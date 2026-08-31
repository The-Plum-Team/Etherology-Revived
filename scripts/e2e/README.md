# Isolated Fabric 1.20.1 E2E client

This harness owns one purpose-built Etherology runtime and never resolves,
copies, registers, inspects, or launches an existing game profile. Its only
game directory is below the ignored repository path:

```text
scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v24/game/
```

The parent runtime must contain the exact provenance marker written by this
script. If the configured directory already exists without that marker, has a
different marker, or is a symlink, every lifecycle action fails closed. There
is no profile-path argument and no adopt, reset, or delete action.

The tracked v24 profile is the consumed repository-owned runtime for the
accepted `forest-lantern` capture. The v20 through v23 profiles, runtimes, and
accepted archives remain immutable history. `client.py validate` and
archive-only validation are safe because they do not mutate or launch a
runtime. Before another lifecycle action, advance the profile ID, runtime
directory, snapshot, tests, verifier, and archive target.

`fabric-1.20.1-profile.json` declares the complete root mod inventory required
by Etherology. Every dependency has an HTTPS source, exact byte size, SHA-256,
Fabric mod id, and version property. The script cross-checks those properties
against `gradle.properties` and cross-checks Minecraft, Java, loader, installer,
and production artifact values against `release/release-matrix.json`. The
separate Phase0 harness path is derived from the named `RemapJarTask` in
`fabric/build.gradle.kts`; an unrecognized task output declaration fails closed.

Fabric Shield Lib contains Fabric ASM and MidnightLib as nested JARs. GeckoLib
contains MCLib, Trinkets contains its upstream Cardinal Components fallback,
and Fabric API contains its modules. Fabric ASM, MixinSquared, and owo-sentinel
must also be nested in the production Etherology JAR. None of those are added
as a second root mod. The packaged E2E harness is the only additional local
root mod.

## Validate the consumed profile without launching

Validate the tracked configuration without creating or launching a game:

```bash
python3 -B scripts/e2e/client.py validate
```

The remaining commands record the accepted v24 workflow and the shape to use
only after every identity is advanced. They are not part of the build/unit-test
gate and must not relaunch v24.

## Prepare a future profile without launching

Install the pinned launcher helper into ignored repository state:

```bash
python3 -m pip install --target scripts/e2e/.state/python \
  -r scripts/e2e/requirements.txt
```

After advancing every profile literal, provision the new isolated runtime:

```bash
python3 -B scripts/e2e/client.py provision
```

`provision` downloads Minecraft and the hash-pinned dependency set into the new
repository-owned runtime, invokes the hash-pinned Fabric installer with
`-noprofile`, and does not launch Minecraft. It discovers Java 17 from
`ETHERLOGY_E2E_JAVA_17`, macOS Java Home, or Gradle's toolchain cache. It does
not search launcher application folders. It also records the inherited vanilla
client JAR explicitly because the pinned launcher helper otherwise generates a
Fabric classpath ending in a nonexistent version JAR.

## Pin future production and harness builds

Build both separate artifacts, run the focused harness tests, verify isolation,
then stage them together:

```bash
./gradlew :fabric:1.20.1:buildE2eHarness \
  :fabric:1.20.1:verifyE2eHarnessIsolation --no-daemon --console=plain
python3 -B scripts/e2e/client.py stage
python3 -B scripts/e2e/client.py check --scenario forest-lantern
```

`buildE2eHarness` runs the dispatcher/controller unit tests before validating the
separate remapped harness JAR. It does not launch Minecraft.

The production path comes only from `release/release-matrix.json`; the harness
path comes from the Gradle task declaration. `stage` checks the production root
id, version, exact Minecraft dependency, and embedded Fabric
ASM/MixinSquared/owo-sentinel ids. It separately requires the harness root id
`etherology_e2e_harness`, matching mod version, client-only environment, exact
Etherology and Minecraft dependencies, one expected client entrypoint, an
exact required client mixin/config for completed-render callbacks, an isolated
class package, and no nested JARs or production-class links.

Both source JARs are copied to temporary files and revalidated before either
isolated target is replaced. One schema-2 `artifact-lock.json` is published
last, with a separate size, SHA-256, source path, target name, and contained mod
inventory for each artifact. An interrupted or partial copy therefore cannot
pass `check`. `check` and `start` compare the lock against both isolated copies
and both current build outputs, so rebuilding either JAR requires another
explicit `stage` before launch. The mods directory must contain exactly the
pinned dependency roots plus these two local artifacts.
`check` resolves the final launch command and requires every classpath entry to
be a regular file below the isolated launcher root.

## Scenario selection

The packaged harness reads `etherology.e2e.scenario` exactly once during Fabric
client initialization. The property is a JVM argument, for example:

```text
-Detherology.e2e.scenario=phase0-smoke
```

An absent property intentionally defaults to `phase0-smoke`. `client.py` always
passes the selected id explicitly and records it in the owned process state:

```bash
python3 -B scripts/e2e/client.py check --scenario phase0-smoke
python3 -B scripts/e2e/client.py start --scenario phase0-smoke
```

The implemented scenarios are `phase0-smoke`, `storage-utilities`,
`ether-network`, `metal-block-registry`, and `forest-lantern`. The storage
scenario creates a fresh integrated world and exercises crate, shelf,
spill-barrel, and tuning-fork interactions and persistence. The ether scenario
creates a separate fresh world and exercises a
Spinner, directional channels, Ethereal Storage, a redstone gate, and a
Levitator force/retention path. The metal-block scenario captures the empty
three-pedestal fixture, places the exact azel, ethril, and ebony blocks directly
on the integrated-server thread, waits for the exact client mirror and 120
consecutive stable renders, then captures the populated fixture from the same
camera. The Forest Lantern scenario uses raw registry/property APIs to build a
four-row checker-backed matrix containing every age 0–4 × north/east/south/west
state, rejects one unsupported real `BlockItem` placement, performs four
supported mature real `BlockItem` placements, captures every cumulative
placement facing from one fixed camera after 120 stable completed renders, and
proves exact matrix and placement mirrors across a full save/disconnect/reopen.
Random ticks are disabled so the sixteen forced age 0–3 states cannot advance
during capture. An
explicit empty, whitespace-padded, or unknown value aborts harness
initialization before any client callbacks are registered; it is never silently
normalized or replaced with another scenario.
The stable Fabric entrypoint remains
`dev.theplumteam.etherology.e2e.fabric.PhaseZeroHarness`, so the isolated profile
identity and staged-artifact contract do not change.

## Recorded v24 client lifecycle

The accepted v24 run used these commands after `check` succeeded. Do not repeat
`start`; the durable attempt marker must reject reuse:

```bash
python3 -B scripts/e2e/client.py start --scenario forest-lantern
python3 -B scripts/e2e/client.py status
```

After read-only preflight succeeds, `start` durably reserves one profile-specific
attempt marker before creating the client log or process. That marker is never
removed and blocks another provision, stage, check, or start for v24, including
after an early launch failure. `status` remains available; `stop` is an
abort-only command and must not be used for a normally auto-completing capture.

The client uses a deterministic offline test identity, a 960x540 logical window,
and the repository-owned game directory. On the baseline Mac's 2x Retina display,
that logical size produces the required 1920x1080 composed framebuffer without
macOS clamping the window height. The controlled option set also pins fancy
graphics, clouds, particles, mipmaps, field of view, gamma, GUI scale, view
bobbing, raw mouse input, focus pausing, and the English locale. `start` wraps
it in macOS `caffeinate` and
stores only PID metadata and console logs below `scripts/e2e/.state/`. `stop`
signals a process only when its PID, Fabric version id, game directory, and
Knot client command all match the recorded state. `start` watches the first two
seconds for a dead process or fatal loader marker, and `status` reports those
markers as a failed client rather than a running game.

Before any launch, `start` inventories every `*-current.json` state below this
repository's ignored E2E root. A live client from an older test-profile revision
blocks the launch, and stale owned state is cleared. To stop every such client
after verifying each marker, game directory, PID, and Knot command, use:

```bash
python3 -B scripts/e2e/client.py stop-all-owned
```

Minecraft's `ScreenshotRecorder` writes the native captures directly into the
fail-closed scenario evidence tree created by `provision`:

```text
scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v24/evidence/
  <scenario>/
    reports/
    screenshots/
```

The scenario ids are pinned in the profile manifest and tested against
`docs/testing/E2E-CONTRACT.md`. The evidence marker also pins the composed
framebuffer contract to 1920x1080. A missing marker, extra scenario directory,
or symlink makes verification fail instead of adopting or mixing evidence.

## Safety tests

The tests are pure temporary-directory/configuration checks. They do not
download dependencies, create a game runtime, or launch a process:

```bash
python3 -B -m unittest scripts/e2e/test_client.py scripts/e2e/test_evidence.py \
  scripts/e2e/test_fabric_metal_block_evidence.py \
  scripts/e2e/test_fabric_forest_lantern_evidence.py
./gradlew :fabric:1.20.1:fabricMetalBlockRegistryEvidenceSafetyTest \
  --no-daemon --console=plain
./gradlew :fabric:1.20.1:fabricForestLanternEvidenceSafetyTest \
  --no-daemon --console=plain
```

## Accepted Fabric Forest Lantern evidence (v24)

The packaged v24 scenario and fail-closed verifier passed the native capture
with 68 exact ordered assertions and seven unedited
1920×1080 framebuffer PNGs: empty, the full twenty-state age×facing matrix,
cumulative north/east/south/west mature placements, and reopened persistence.
Every capture records 120 completed stable renders, exact twenty-cell
server/client snapshots and camera state, all thirteen effective assets, cutout
rendering and baked models, twenty unique raw state IDs, all sixteen forced
age 0–3 fixture states, real `BlockItem` action/stack/support evidence, and no
missing-texture magenta. Each of the five fixture/placement transitions must
materially change.

The following records the completed one-shot publication workflow. Manifest
creation must not be repeated; archive-only verification remains safe:

```bash
python3 -B scripts/e2e/fabric_forest_lantern_evidence.py --live
/bin/mkdir docs/evidence/fabric-1.20.1/forest-lantern-v24
/bin/cp -pR \
  scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v24/evidence/forest-lantern/. \
  docs/evidence/fabric-1.20.1/forest-lantern-v24/
python3 -B scripts/e2e/fabric_forest_lantern_evidence.py \
  --create-archive-manifest docs/evidence/fabric-1.20.1/forest-lantern-v24 \
  --capture-runtime scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v24 \
  --profile-manifest scripts/e2e/fabric-1.20.1-profile.json
```

Archive-only verification is repeatable and never consults live profile state:

```bash
python3 -B scripts/e2e/fabric_forest_lantern_evidence.py \
  --archive docs/evidence/fabric-1.20.1/forest-lantern-v24
./gradlew :fabric:1.20.1:validateFabricForestLanternV24Milestone \
  --no-daemon --console=plain
```

The archive and milestone now pass. Manifest creation binds the exact
active/snapshot profile bytes,
repository-owned runtime and destination, artifact lock, copied payload bytes,
and completion-marker publication order, and refuses replacement.

## Accepted Fabric metal-block-registry evidence (v23)

The consumed v23 runtime passed the dedicated verifier with 25 exact ordered
assertions, two unedited native 1920x1080 screenshots, 120 consecutive stable
renders before placement and 120 after placement, and a changed-pixel ratio of
`0.087510`. The assertions cover the loaded production mod; the three exact
block IDs and non-negative default-state network IDs; nine exact render
resources; both packaged root JARs; integrated-world and chunk readiness; the
exact empty server/client fixture; before render, camera, stability,
framebuffer, and screenshot state; exact placed server IDs and client mirror;
the corresponding after-capture state; forced save; and the isolated save
directory. The exact ordered assertion names and frozen payload digests are in
[`docs/evidence/fabric-1.20.1/README.md`](../../docs/evidence/fabric-1.20.1/README.md).

The production-JAR SHA-256 is
`5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd`,
the harness-JAR SHA-256 is
`0cc892f41399eec903af57c3270f19db027b0e7611e392a0fc817876e373b111`,
and the archive-manifest SHA-256 is
`69717273eac7b543378aa1a804573e27805e33b771601abba7c49923a5a42f44`.

The following command records the completed one-shot publication. Do not rerun
it: the copied four-file payload and existing manifest are immutable, and the
verifier refuses replacement.

```bash
python3 -B scripts/e2e/fabric_metal_block_evidence.py \
  --create-archive-manifest docs/evidence/fabric-1.20.1/metal-block-registry-v23 \
  --capture-runtime scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v23 \
  --profile-manifest scripts/e2e/fabric-1.20.1-profile.json
```

Archive-only validation is repeatable and does not read current source, build
outputs, or live runtime state:

```bash
python3 -B scripts/e2e/fabric_metal_block_evidence.py \
  --archive docs/evidence/fabric-1.20.1/metal-block-registry-v23
./gradlew :fabric:1.20.1:fabricMetalBlockRegistryEvidenceSafetyTest \
  --no-daemon --console=plain
./gradlew :fabric:1.20.1:validateFabricMetalBlockRegistryEvidenceArchiveIntegrity \
  --no-daemon --console=plain
```

Manifest creation rejects a different repository destination, runtime, profile,
archive inventory, payload byte, artifact lock, report contract, or publication
order, and it never replaces an existing manifest. This visual fixture proves
direct server-side placement and exact client rendering only. It does not prove
`BlockItem` inventory or player placement, mining or drops, tool-tier
enforcement, beacon activation, recipe execution, creative tabs, restart
persistence, multiplayer, or release readiness.

The v23 and v24 identities are consumed and remain archive-only history. Before
any later Fabric lifecycle action, advance every active profile, runtime,
snapshot, test, verifier, and archive literal to a fresh unused v25-or-newer
identity.

### Historical Phase 0 archive (v22)

The v22 command below records its already-completed one-time publication shape
and must not be rerun. Its archive-only validation remains safe. That historical
smoke passed 42 assertions and captured the four-machine fixture; it did not
show or interact with the three metal blocks.

```bash
python3 -B scripts/e2e/evidence.py \
  --create-archive-manifest docs/evidence/fabric-1.20.1/phase0-smoke-v22 \
  --capture-runtime scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v22 \
  --profile-manifest scripts/e2e/fabric-1.20.1-profile.json
python3 -B scripts/e2e/evidence.py \
  --archive docs/evidence/fabric-1.20.1/phase0-smoke-v22
```

## Forge 1.20.1 packaged client

The Forge lane is a separate repository-owned profile described by
`forge-1.20.1-profile.json`. It uses Java 17, Minecraft 1.20.1, Forge 47.4.9,
Architectury Forge 9.2.14, and GeckoLib Forge 4.7.4. Its installer and both
runtime dependency JARs are size- and SHA-256-pinned. It never reads or changes
an external launcher profile. The current runtime contract is the fresh isolated
`etherology-e2e-forge-1.20.1-v13` profile. Its exact ordered scenarios are
`ethereal-storage`, `ethereal-channel`, and `forest-lantern`; selection defaults
to storage. v13 is reserved for the next Forest Lantern capture and has not been
provisioned, staged, checked, or launched. The v12 identity is permanently
consumed by its one native Forest Lantern diagnostic: the report reached the
prepared scenario with 69 assertions, but only 12 passed and 57 cascaded from
one harness false negative before any screenshot. Production Forge registration
was correct; the probe queried the deprecated `RenderLayers` map and observed
solid instead of using `BakedModel#getRenderTypes`, which returns the exact
cutout render type. No v12 archive was accepted, and its snapshot and
repository-owned runtime remain immutable history. The v11 profile and Channel
archive also remain immutable.
The launcher-created runtime marker also records the tracked profile manifest's
repository path, exact byte size, and SHA-256. Every readiness and launch action
recomputes that descriptor and rejects a mismatched marker.

Validate the tracked contract, provision the isolated launcher, build the two
local artifacts, stage them, and run the fail-closed readiness check:

```bash
python3 -B scripts/e2e/forge_client.py validate
python3 -B scripts/e2e/forge_client.py provision
./gradlew :forge:1.20.1:buildE2eHarness --no-daemon --console=plain
python3 -B scripts/e2e/forge_client.py stage
python3 -B scripts/e2e/forge_client.py check --scenario forest-lantern
```

Only `start` launches Minecraft. The lifecycle commands manage processes only
after matching the Forge version id, isolated game directory, scenario property,
and BootstrapLauncher command:

```bash
python3 -B scripts/e2e/forge_client.py start --scenario forest-lantern
python3 -B scripts/e2e/forge_client.py status
```

After read-only preflight succeeds, `start` durably reserves one profile-specific
attempt marker before creating the client log or process. It is never removed
and blocks another provision, stage, check, or start for v13, including after an
early launch failure. v12 already has that durable marker and must never be
relaunched. `status` remains available; `stop` and `stop-all-owned`
are abort-only recovery commands, not part of successful capture publication.

The storage scenario writes five 1920x1080 composed-framebuffer captures and one
atomic report below `evidence/ethereal-storage/`. The channel scenario writes
exactly `ethereal-channel-gated.png`, `ethereal-channel-transferred.png`, and
`ethereal-channel-reopened.png` below `evidence/ethereal-channel/screenshots/`.
The Forest Lantern scenario writes exact empty, forced-stage, cumulative
north/east/south/west placement, and reopened PNGs. Each is 1920x1080 after 120
consecutive exact mirror/render/camera frames. Its report binds the twenty
age/facing states and raw IDs, thirteen effective resource SHA-256 values,
Forge cutout layer, luminance 8, real `BlockItem` outcomes, client/server
snapshots, staged JARs, and save/disconnect/reopen persistence. Ages 0–3 are
forced across all north/east/south/west facings against deterministic vanilla
logs and explicitly remain unsupported. Together with the four mature real
`BlockItem` placements, the capture exposes all twenty native age/facing states
in exact server/client mirrors before save and after reopen; the deferred
peach-log graph is not claimed by this bounded Forge slice.
Its v11 fixture uses redstone-fed powered repeaters on the full solid arena floor
as exact strong-power sources. It waits through the first scheduled server tick
before asserting ACTIVATED, `up=in`, and `east=out`; later power changes are also
observed through natural `NOTIFY_ALL` propagation, with no manual neighbor refresh.
Every channel screenshot requires 120 consecutive composed frames with the exact
role-specific client fixture, an empty terrain-build queue, and all ten fixture
positions render-ready. The first-person player camera must also remain at the
fixed fixture position, yaw, and pitch. Those conditions are rechecked and
recorded immediately before `ScreenshotRecorder` writes the framebuffer. The
visual verifier anchors the gated-to-transferred frames with lower and upper raw
pixel-change bounds plus a structural bound. For transferred-to-reopened, it
combines a high-delta pixel guard with whole-fixture and worst-window local
luminance-contrast bounds. That accepts the measured restart relighting while
still rejecting missing or moved fixture geometry and unrelated nonblank views.
Both publish the report first and `done.marker` last. After the game records a
normal shutdown, the scenario-specific verifier checks its exact assertion and
screenshot inventories, artifact lock and hashes, save/restart evidence,
log/crash state, PNG CRC and decoded-size bounds, and fixture-region visual
change:

```bash
python3 -B scripts/e2e/forge_evidence.py --scenario ethereal-storage
python3 -B scripts/e2e/forge_channel_evidence.py --scenario ethereal-channel
python3 -B scripts/e2e/forge_forest_lantern_evidence.py --live
```

After the harness stops itself, validate the live capture, copy only the report,
completion marker, and seven PNGs while preserving modification times, seal
them against that stopped owned capture, then validate the frozen archive:

```bash
python3 -B scripts/e2e/forge_forest_lantern_evidence.py --live
/bin/mkdir docs/evidence/forge-1.20.1/forest-lantern-v13
/bin/cp -pR \
  scripts/e2e/.state/runtimes/etherology-e2e-forge-1.20.1-v13/evidence/forest-lantern/. \
  docs/evidence/forge-1.20.1/forest-lantern-v13/
python3 -B scripts/e2e/forge_forest_lantern_evidence.py \
  --create-archive-manifest docs/evidence/forge-1.20.1/forest-lantern-v13 \
  --capture-runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-1.20.1-v13 \
  --profile-manifest scripts/e2e/forge-1.20.1-profile.json
python3 -B scripts/e2e/forge_forest_lantern_evidence.py \
  --archive docs/evidence/forge-1.20.1/forest-lantern-v13
```

After copying the seven accepted payload files (the report, completion marker,
and five PNGs) into a versioned `docs/evidence/forge-1.20.1/ethereal-storage-vN`
directory, create its immutable provenance inventory and verify it using only
tracked archive files:

```bash
python3 -B scripts/e2e/forge_evidence.py --create-archive-manifest docs/evidence/forge-1.20.1/ethereal-storage-vN
python3 -B scripts/e2e/forge_evidence.py --archive docs/evidence/forge-1.20.1/ethereal-storage-vN
```

Archive validation proves internal integrity and capture-time provenance; it
does not claim that current sources or later rebuilt artifacts still match the
capture. Establishing that requires a new isolated profile and native run.

The channel archive contains the report, completion marker, and three PNGs in
`docs/evidence/forge-1.20.1/ethereal-channel-v11`. Create or validate its own
manifest without weakening the storage verifier:

```bash
python3 -B scripts/e2e/forge_channel_evidence.py --create-archive-manifest docs/evidence/forge-1.20.1/ethereal-channel-v11
python3 -B scripts/e2e/forge_channel_evidence.py --archive docs/evidence/forge-1.20.1/ethereal-channel-v11
```

The channel harness copies the capture-time profile id, manifest size, and
manifest SHA-256 from the launcher-verified runtime marker into `report.json`.
Manifest creation rejects a report whose capture record differs from the tracked
profile, then derives the archive profile provenance from that report. Archive
validation compares the frozen report back to the archive manifest.

Archive-only validation establishes internal integrity, not current-build
equivalence. A gate can additionally compare the explicit current profile and
both current remapped JARs to the archive's recorded byte sizes and SHA-256
digests:

```bash
python3 -B scripts/e2e/forge_channel_evidence.py \
  --archive docs/evidence/forge-1.20.1/ethereal-channel-v11 \
  --current-production <production-remapped.jar> \
  --current-harness <harness-remapped.jar> \
  --current-profile scripts/e2e/forge-1.20.1-profile.json
```

Even that comparison does not compare current source files; it proves only that
the supplied profile and artifacts are byte-identical to the capture-time
records.

The Gradle tasks intentionally keep those two questions separate.
`validateForgeChannelEvidenceArchiveIntegrity` performs the archive-only check
and remains the accepted Channel evidence gate.
`validateForgeChannelCurrentArtifactDiagnostic` supplies the current remapped
JARs and profile to the stricter comparison. At the SharedSounds checkpoint the
latter fails as expected because an unrelated production change altered the
whole JAR after the Channel capture. That result is neither an archive failure
nor a Channel regression, and it does not claim current equality. Establishing
current equality requires another fresh isolated profile and native run.

## Accepted Forge 1.20.1 dedicated-server Forest Lantern probe (v16)

The prior v15 identity is permanently consumed. Its native run reached save,
reload, normal server stop, and report publication; 262 assertions passed and
four failed because the prepared oracle incorrectly expected `opaque=false`
instead of the upstream-derived native 1.20.1 `opaque=true` state flag. No v15
archive is accepted and v15 must never be relaunched.

The active server-only profile is the consumed, repository-owned
`etherology-e2e-forge-server-1.20.1-v16` identity. Its tracked manifest and v16
snapshot are byte-identical at 1,184 bytes with SHA-256
`82419a84d0bca220b5032f45fec053265ed5701594af32fd3721d02a66862332`.
Its native Forge run exited zero and the sealed schema-10 report passes all 266
ordered assertions: all v14 checks plus the exact Forest Lantern registry,
twenty states, unique non-negative server network IDs and shapes, tags, loot,
four recipes and advancements, real
four-facing `BlockItem` placement/support removal, shears speed `15.0` and
effective breaking delta across all twenty states, seeded real player-jump
retain/break/drop outcomes, real reload stability, and fresh players after
reload. This headless contract creates no screenshots. The archive is
`docs/evidence/forge-1.20.1/forest-lantern-server-v16`.

The recorded one-shot workflow was the following. It is provenance, not an
instruction to relaunch v16: the durable attempt marker and sealed archive must
continue to reject every reuse.

```bash
./gradlew --no-daemon --no-parallel --console=plain \
  :forge:1.20.1:verifyRegistryFoundationServerProbe
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
python3 -B scripts/e2e/forge_server_forest_lantern_evidence_v16.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v16
```

The runner did not create the tracked archive. The accepted publication copied
only the `evidence/forest-lantern` payload with modification times preserved,
then sealed and verified it exactly once:

```bash
server_capture="scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v16/evidence/forest-lantern"
server_archive="docs/evidence/forge-1.20.1/forest-lantern-server-v16"
/bin/mkdir "$server_archive"
/bin/cp -pR "$server_capture"/. "$server_archive"/
python3 -B scripts/e2e/forge_server_forest_lantern_evidence_v16.py \
  --create-archive-manifest "$server_archive"
python3 -B scripts/e2e/forge_server_forest_lantern_evidence_v16.py \
  --archive "$server_archive"
```

The exact archive directory and launch-attempt marker now block another
provision, check, or run. The v15 historical safety task, v16 active safety
task, strict archive verifier, and integrated Gradle archive gate all pass.
Forge packaged-client Forest Lantern evidence is the next incomplete milestone.

## Historical Forge 1.20.1 dedicated-server metal-block-registry probe (v13)

The accepted historical server-only proof is the Forge 1.20.1
`metal-block-registry` scenario. It runs Etherology's checked-out production
source-set output and the isolated server probe in a fresh repository-owned
profile; it never loads the Forge client harness and never consults, adopts,
resets, or deletes an external Minecraft profile. Its exact one-shot runtime is:

```text
scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v13/
  game/
  evidence/metal-block-registry/
```

The tracked profile pins Minecraft 1.20.1, Forge 47.4.9, Java 17, the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task, and the complete
required and forbidden mod-ID inventories. Its size is 1196 bytes and its
SHA-256 is
`c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`.
`provision` succeeds only for a brand-new target. The accepted v13 runtime is
immutable: the next native run must increment the profile ID and use another
fresh directory rather than reuse, clean, or replace v13.

The following commands record the v13 workflow. Build and validation do not
launch Minecraft. Because v13 has already been accepted, `provision`, `check`,
and `run` may be used for another native capture only after advancing the
tracked profile, contract, and verifier to a new ID and fresh runtime:

```bash
./gradlew --no-daemon --no-parallel \
  :forge:1.20.1:verifyRegistryFoundationServerProbe --console=plain
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
```

The runner uses a JDK 21-or-newer Gradle host, selects Java 17 for the real
dedicated server, and wraps Gradle in macOS `caffeinate`. It bounds the process
and server logs independently, contains the process group, rejects crash,
forbidden client-startup, and unexpected client-class markers, requires a saved
world and normal shutdown, and publishes `done.marker` only after the report,
copied server log, and launcher result pass. External game profiles consulted:
zero.

The accepted fresh v13 run emitted a schema-8 report and passed all 188 ordered
assertions. `SharedMetalBlocks` and `SharedMetalBlockItems` supplied these exact
block and item IDs:

```text
etherology:azel_block, etherology:ebony_block, etherology:ethril_block
```

Every block resolved to `net.minecraft.block.Block`, every corresponding item
resolved to `net.minecraft.item.BlockItem`, and each item mapped back to the
exact block with maximum count 64. The probe checked exact hardness, blast
resistance, map color, metal sound, tool requirement, luminance, opacity, and
full-cube properties. All three entries were pickaxe-mineable and required an
iron-tier tool; ethril and ebony, but not azel, were beacon bases. It checked
exact `id` and `Count` NBT round trips, then directly placed azel, ebony, and
ethril at `8,200,8`, `9,200,8`, and `10,200,8`. Registry, properties, selected
tags, stack NBT, and exact placed block IDs remained stable after a real
`reload`. The cumulative assertions also rechecked the v11 material-item, v10
particle, v7 enchantment, game-event/tag, loot-condition, reloadable loot-table,
and Ether-source contracts. The exact lifecycle was
`tags_updated_initial > server_started > reload_requested > tags_updated_reload > reload_command_returned > stop_requested > server_stopping > server_stopped`.
The server saved its world, completed normal `stop(false)`, exited with code
zero, and its copied log passed the strict marker scan. After
`ServerStoppedEvent` and atomic report publication, the probe-only terminator
joins the stopped-event server thread before `System.exit` to handle the proven
Loom-userdev non-daemon thread leak. This path is absent from the production
mod.

Validate the completed live runtime or immutable five-file archive with the
v13 verifier:

```bash
python3 -B scripts/e2e/forge_server_metal_block_evidence_v13.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v13
python3 -B scripts/e2e/forge_server_metal_block_evidence_v13.py \
  --archive docs/evidence/forge-1.20.1/metal-block-registry-server-v13
```

The archive records report SHA-256
`b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`,
server-log SHA-256
`f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`,
and archive-manifest SHA-256
`0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`.
`validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity` owns the
archive-only check, while `validateForgeMetalBlockRegistryMilestone` combines
the frozen native proof with exact current Common/Fabric/Forge registry,
bytecode, bootstrap, resource, artifact, and isolation gates.
Those are separate proof boundaries: the immutable v13 archive validates
capture-time Loom-userdev observations and payload integrity, but does not
compare or cryptographically bind later sources or rebuilt JARs.

This scenario is headless and creates no screenshots. Its exact bounded scope
is registry/classes/mappings/properties, selected tags, in-process stack NBT,
direct server-world placement through reload, and normal save/stop. It does not
prove restart persistence, player `/give` or player placement, mining/drop
behavior, beacon activation, recipe execution, creative-tab interaction,
client rendering, the complete port, or release readiness.

The v12 profile was consumed by a failed diagnostic launch: the generated
`mineable/pickaxe` and `needs_iron_tool` files packaged by this bounded Forge
slice still treated unported Etherology IDs as required, so Forge reported
tag-load errors. Those unported IDs are now optional while the three accepted
metal-block IDs remain required. `needs_stone_tool` is unchanged and outside
this Forge resource slice. No v12 archive was accepted; the successful proof
was captured in the separate fresh v13 runtime.

## Historical Forge 1.20.1 dedicated-server material-item-registry probe (v11)

The immutable v11 predecessor passed all 163 schema-7 assertions for the exact
14 `SharedMaterialItems` IDs, vanilla runtime classes, maximum counts, and
in-process `ItemStack` NBT round trips through a real reload. The current v13
record re-proves those assertions cumulatively. Validate the frozen v11 archive
only with its pinned verifier:

```bash
python3 -B scripts/e2e/forge_server_material_item_evidence_v11.py \
  --archive docs/evidence/forge-1.20.1/material-item-registry-server-v11
```

The v11 archive binds profile-manifest SHA-256
`63ee2c8707f276cc87df2e0b162b2f3174e1fe1b3d689b26135298154ed1b171`,
report SHA-256
`f3cc85b8514704f6c789e5abbdb835ede8aea062f5bae5b7ade0ab20da26bd4f`,
server-log SHA-256
`ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`,
and archive-manifest SHA-256
`9f5ff60298d9066e92c3f3e8ff6e5ab97fba5b35369f6ed09e1359bed7afe347`.
It proves only its capture-time material-item contract and remains immutable.

## Historical Forge 1.20.1 dedicated-server particle-registry probe (v10)

The immutable v10 predecessor passed all 138 schema-6 assertions for the exact
22 `SharedParticleTypes` IDs, eight payload families, concrete types, spawn
flags, codecs, factories, command/packet/codec round trips, and seal metadata.
It used the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v10` profile and a real `reload`; v13
re-proves all of its assertions cumulatively. Its five-file archive remains
frozen at `docs/evidence/forge-1.20.1/particle-registry-server-v10` and must not
be recaptured with the current v13 runner.

Validate only the completed historical runtime or immutable archive with the
pinned verifier:

```bash
python3 -B scripts/e2e/forge_server_particle_evidence_v10.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v10
python3 -B scripts/e2e/forge_server_particle_evidence_v10.py \
  --archive docs/evidence/forge-1.20.1/particle-registry-server-v10
```

The v10 archive binds profile-manifest SHA-256
`5b3def0df2aacfea5db04b92975925c25223f117941ceb576cc6b3e6616f14e4`,
report SHA-256
`ab829d182e648385f6052fea469bef3a18a6a972f0baa98be6b83569897f3d75`,
server-log SHA-256
`44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`,
and archive-manifest SHA-256
`29fddf549c4b8728911fe1d048816d0353256ef7a7e533b62d0461249c485ed1`.
This historical headless proof creates no screenshots and does not install or
exercise Forge client particle factories/renderers, emitted visuals, or
particle-emitting gameplay mechanics.

## Historical Forge 1.20.1 dedicated-server enchantment-registry probe (v7)

The immutable v7 predecessor passed all 95 schema-5 assertions for exact Peal
and Reflection registration, class/properties, singular
`minecraft:non_treasure` membership, reload stability, and the earlier
cumulative contracts. Its archive remains at
`docs/evidence/forge-1.20.1/enchantment-registry-server-v7`; it must not be
provisioned, reused, cleaned, or replaced.

```bash
python3 -B scripts/e2e/forge_server_enchantment_evidence_v7.py \
  --archive docs/evidence/forge-1.20.1/enchantment-registry-server-v7
```

The archive records profile-manifest SHA-256
`36b0f67d7ef55cd8e34aac92dd4e5866e17d5be4ffa91987793c913dd60f5773`,
report SHA-256
`1f5209c53fab524db662e7e7ef8ba044ba773fc9800dc3d3086e840093dc5aef`,
server-log SHA-256
`b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`,
and archive-manifest SHA-256
`377acc9241417a169ac2f9dbe1f555d5918509a486daf40d89533de4d414feec`.
Its accepted state is re-proved by v13, but the historical bytes remain frozen.
It does not prove enchantment applicability, Peal shockwaves, projectile
reflection, client rendering, screenshots, full combat parity, or release
readiness.

## Historical Forge 1.20.1 dedicated-server Ether-source reload probe (v6)

The v6 server-only harness proved the Common-owned Ether-source listener
and default data in a separate repository-owned profile. It never loads the
Forge client harness and never consults, adopts, resets, or deletes an external
Minecraft profile. Its exact one-shot runtime is:

```text
scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v6/
  game/
  evidence/ether-source-reload/
```

The frozen v6 profile pins Minecraft 1.20.1, Forge 47.4.9, Java 17, the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task, and the complete required
and forbidden mod-ID inventories. The mutable runner has advanced to v13, so v6
must be inspected only through its immutable snapshot and verifier. It must
never be provisioned, reused, cleaned, or replaced.

At capture time, the v6 profile used the same named build verification and
runner lifecycle shown above. Those mutable runner commands now target v13 and
must not be used as instructions to recapture v6.

The runner uses a JDK 21-or-newer Gradle host, selects Java 17 for the real
dedicated server, and wraps Gradle in macOS `caffeinate`. It bounds the process
and server logs independently, contains the process group, rejects crash and
client markers, requires a saved world and normal shutdown, and publishes
`done.marker` only after the report, copied server log, and launcher result
pass. External game profiles consulted: zero.

`EtherSourceLoader` and the default `ether_sources` resources have one Common
implementation/resource owner. The accepted fresh v6 run issued the real
`reload` command and its schema-4 report passed all 72 ordered assertions. The
initial map contained exactly 23 entries, including corrected
`etherology:primoshard_rella = 4` and `minecraft:redstone = 2`. The enabled
probe pack overrode redstone and added diamond, producing exactly 24 entries
with `minecraft:redstone = 9.5` and `minecraft:diamond = 13`. The sole game
event and exact listening-tag membership remained stable. The sole loot
condition and its evaluated behavior also remained stable while the probe
`LootTable` instance was replaced as expected during reload. The server saved
the world, completed normal `stop(false)`, exited with code zero, and its copied
log contains no `ERROR` or `FATAL` marker.

The probe records the reload lifecycle before requesting the normal server
stop. After `ServerStoppedEvent` and atomic report publication, a probe-only
terminator joins the stopped-event server thread before `System.exit` to handle
the proven Loom-userdev non-daemon thread leak. This path is absent from the
production mod. The scenario is headless, so it neither creates nor requires
screenshots.

Validate only the immutable five-file archive with the v6 verifier:

```bash
python3 -B scripts/e2e/forge_server_reload_evidence_v6.py \
  --archive docs/evidence/forge-1.20.1/ether-source-reload-server-v6
```

The archive records profile-manifest SHA-256
`2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0`,
server-log SHA-256
`0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`,
and archive-manifest SHA-256
`6d552536f74c018ce56e238fcb5a3aacd8fa363c76293863514adf9d7bafc2e0`.
`validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity` owns this
archive check, and `validateForgeEtherSourceReloadMilestone` combines it with
the exact current Common/Fabric/Forge artifact and listener-ownership gates.

This bounded run does not prove furnace or machine consumption, the wider
Ether network, the full authoritative registry, native sound playback, Forge
custom sculk-frequency behavior, Attrahite drops, or release readiness.

## Historical Forge 1.20.1 dedicated-server registry-foundation probe (v4)

The accepted game-event and loot-condition foundation has a separate server-only harness and a separate
repository-owned profile. It never loads the Forge client harness and never
consults, adopts, resets, or deletes an external Minecraft profile. Its exact
one-shot runtime is:

```text
scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v4/
  game/
  evidence/registry-foundation/
```

The frozen profile pins Minecraft 1.20.1, Forge 47.4.9, Java 17, the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task, and the complete required
and forbidden mod-ID inventories. The mutable controller and profile have
advanced to v13. They cannot recapture v4, and v4 must not be provisioned,
reused, cleaned, or replaced. Only its immutable archive verifier remains an
active instruction:

```bash
python3 -B scripts/e2e/forge_server_evidence.py \
  --archive docs/evidence/forge-1.20.1/registry-foundation-server-v4
```

The runner uses a JDK 21-or-newer Gradle host, selects Java 17 for the real
dedicated server, and wraps Gradle in macOS `caffeinate`. It bounds the process
and server logs independently, contains the process group, rejects crash and
client markers, requires a saved world and normal shutdown, and publishes
`done.marker` only after the report, copied server log, and launcher result pass.
External game profiles consulted: zero.

The `registry-foundation` probe checks exactly one Etherology game event:
`etherology:etherology_resonance`, internal ID `etherology_resonance`, range 16.
It observes one `SERVER_DATA_LOAD` static tag update and exact membership in
only `minecraft:vibrations` and `minecraft:warden_can_listen`. It also records
exactly one Etherology loot-condition type,
`etherology:random_chance_with_fortune`, backed by
`ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer`.
The synthetic `etherology_e2e_server_probe:registry_foundation` table returned
`[minecraft:gold_ingot, minecraft:stone]` for an empty tool and
`[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` for Fortune I.
Its pools cover chance `1`, chance `0` plus Fortune multiplier `1`, and the
mixed chance `0.99` plus multiplier `0.01` case.

The probe also records the full sorted loaded-mod inventory, requires
Etherology and the server probe, and requires an empty intersection with all
seven forbidden IDs. The accepted fresh repository-owned `v4` Loom-userdev run
passed all 39 ordered assertions, saved the world, traversed
`tags_updated`, `server_started`, `server_stopping`, and `server_stopped`, used
normal `stop(false)`, and exited with code zero. The copied server log contains
no `ERROR` or `FATAL` marker.

Loom userdev leaves a proven non-daemon thread alive after normal Forge server
shutdown on this path. Only the isolated probe schedules a terminator after it
observes `ServerStoppedEvent` and atomically publishes the report. The daemon
terminator joins the actual stopped-event server thread before `System.exit`;
timeout or interruption exits with status one. The runner still independently
validates the lifecycle, report, logs, save, and zero exit. This workaround is
not present in the production mod. The scenario is a headless
registry/tag/loot-condition proof, so it neither creates nor requires
screenshots.

The accepted five-file archive is
`docs/evidence/forge-1.20.1/registry-foundation-server-v4`. The archive-only
command above validates it without consulting the live runtime or current build
outputs.

That validation reports 39 assertions and the copied server-log SHA-256
`085332dc956ea75327d820fc398e122779185c173b6f8002d9962862c9feaea2`.
Archive validation proves capture-time payload integrity only; it does not
compare current sources or rebuilt artifacts.
`validateForgeRegistryFoundationServerEvidenceArchiveIntegrity` owns that
archive check and runs the separate 63 Python runner/verifier safety tests.
`validateForgeGameEventRegistryMilestone` and
`validateForgeLootConditionRegistryMilestone` separately prove the exact
current Common/Fabric/Forge artifact structure. The combined
`validateForgeRegistryFoundationMilestone` requires those boundaries, the
probe isolation checks, and v4 archive integrity.

The canonical Attrahite resource remains Fabric-only because its items are not
ported. The synthetic table proves the shared condition and serializer, not
Attrahite gameplay or drop parity. This bounded run also does not satisfy the
full authoritative registry/catalog placement-and-save smoke, native sound
playback, or Forge custom sculk-frequency behavior. Fabric's supported
`SculkSensorFrequencyRegistry` frequency 10 is statically checked, while Forge
47 has no supported equivalent and remains deferred. The current Fabric `v23`
archive adds a bounded visual proof for the three exact metal-block IDs: exact
server placement and client mirroring, fixed-camera rendering, two native
1920x1080 screenshots after 120 stable renders each, forced save, and normal
shutdown. It does not prove player placement, mining, drops, beacons, recipes,
creative tabs, persistence across restart, multiplayer, or release readiness.
The Fabric `v22` Phase 0 archive remains the historical packaged-startup proof;
its four-machine screenshots do not show the metal blocks. The immutable Fabric
`v21` and `v20` archives likewise remain historical. The immutable v2
game-event-only archive remains historical evidence;
v4 superseded it as the registry-foundation proof, and the historical v4
verifier intentionally does not accept v2 as its active archive. The v6
Ether-source reload and v7 enchantment-registry archives remain immutable
historical evidence; v10 is the historical particle-registry predecessor, and
v11 is the historical material-item predecessor, and v13 is the current
cumulative metal-block-registry proof.

The Forge safety tests use temporary directories and mocks only. They do not
download, provision, build, or launch Minecraft:

```bash
python3 -B -m unittest scripts/e2e/test_forge_client.py \
  scripts/e2e/test_forge_evidence.py \
  scripts/e2e/test_forge_channel_evidence.py \
  scripts/e2e/test_forge_forest_lantern_evidence.py \
  scripts/e2e/test_forge_server.py \
  scripts/e2e/test_forge_server_evidence.py \
  scripts/e2e/test_forge_server_reload_evidence_v6.py \
  scripts/e2e/test_forge_server_enchantment_evidence_v7.py \
  scripts/e2e/test_forge_server_particle_evidence_v10.py \
  scripts/e2e/test_forge_server_material_item_evidence_v11.py \
  scripts/e2e/test_forge_server_metal_block_evidence_v12.py \
  scripts/e2e/test_forge_server_metal_block_evidence_v13.py
```
