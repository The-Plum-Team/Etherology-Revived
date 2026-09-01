# Repository-owned original Fabric 1.21.1 baseline

`original_client.py` controls one versioned runtime and no other game directory:

```text
scripts/baseline/.state/runtimes/
  etherology-original-fabric-1.21.1-published-0.1.7-v5/
```

The tracked contract is
`original-fabric-1.21.1-published-0.1.7-v5.json`. It pins Minecraft `1.21.1`,
Fabric Loader `0.17.3`, Java `21`, the reference bundle's outer hash, and the
exact eight top-level published JAR members. It also pins a separately built,
client-only capture harness as a ninth root JAR. The controller recursively
reads Fabric metadata, rejects any unlisted JAR, and rejects Quick Skin (`quickskin`),
Customizable Player Models (`cpm`), Ears (`ears`), and Architectury
(`architectury`), including jar-in-jar copies.

The v5 manifest is `10,321` bytes with SHA-256
`ff1a5d17607878fdb77f6c3daa3da69185ae6243e853ac26bff446ad2478593b`.

Runtime authority is byte-exact as well. The manifest records the official
Minecraft version JSON, asset index, client JAR, a tracked official Fabric
loader-profile response, and all eight Fabric library JARs by URL, size, SHA-1,
and SHA-256 where the upstream format exposes both hashes. The Mojang metadata
and asset index are the fresh-install responses observed in 2026, not a claim
about release-day 2024 bytes. Fabric Meta stamps every profile response with
the request time, so the exact response captured on 2026-08-30 is committed as
a separately hash-pinned fixture; the endpoint URL remains provenance, not a
provisioning input. Any change to a tracked or downloaded authority fails
closed.

The controller has no profile-path option and no adopt, reset, or delete action.
It does not read launcher accounts, Modrinth metadata, Modrinth profiles, or any
other user profile. Every marker, lock, installer, launcher file, game file, log,
process record, and eventual evidence artifact that it writes is below the one
runtime directory. A foreign marker, unexpected inventory, unsafe path, or
symlink fails closed.

## Two provenance strata

The two upstream references answer different questions and must never be mixed
into one evidence identity.

### `published-0.1.7`: runtime reference

- Bundle:
  `scripts/baseline/.state/Etherology E2E 1.21.1.mrpack`
- Bundle size: `7,838,299` bytes
- Bundle SHA-256:
  `764bb85e398c3dca43f68a0d2c1eeecd0f3523da7a48adf48bcc5a967af4b91b`
- Etherology JAR: `Etherology-1.21-0.1.7.jar`
- Etherology JAR SHA-256:
  `38de3c1aad47fc715c2226266dec4c70c02d16370034a4e0350508131ac15c43`
- Source-commit binding: unavailable; the published JAR contains no Git or SCM
  identity.

This stratum is the authority for original-client behaviour and screenshots.
The other seven pinned JARs are Fabric API, FabricShieldLib, Biolith, Cardinal
Components API, GeckoLib, owo-lib, and Trinkets. Their names, sizes, root mod
IDs, and SHA-256 hashes are authored once in the tracked manifest.

The separately packaged capture harness is not part of the published reference
and does not replace it:

- Harness JAR:
  `Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.0.jar`
- Harness size: `210,330` bytes
- Harness SHA-256:
  `e149747e9ab5c8a9e0c8fa19d291e0916370e77d45bdd886c4ea88f697e6746d`
- Harness mod id: `etherology_original_baseline_harness`
- Environment: client only
- Exact dependency: `etherology=1.21-0.1.7`

The harness has no compile dependency or implementation link to Etherology
classes. Its own artifact validator enforces that separation.

### `source-0.1.8`: source and build reference

- Upstream branch: `1.21`
- Commit: `70c0fcafb54f91ef715051dc1f6f7c77914e5805`
- Tree: `096d1c5b8f9033c5b96a9504508c306bce3834fc`
- Declared version: `1.21-0.1.8`
- Clean-export remapped JAR SHA-256:
  `d069a1dbc37d68d24174f700a03b68c6fbda98139052d02e59a7351ac0140f39`
- Clean-export sources JAR SHA-256:
  `b6b0ffc85d6d9d95d2f8daa8747747aaf2be765e6631f3b2e87f7093033271a3`

The clean export built successfully with Java 21 and Fabric Loom `1.8.13` on a
retry after one transient TLS EOF while downloading an LWJGL native. This is
build proof only. It is not runtime proof and does not cryptographically or
behaviourally identify the published `0.1.7` JAR.

Evidence records must use exactly one reference id. Source inspection may guide
a mechanic hypothesis, but it cannot be cited as proof of what
`published-0.1.7` did in game. Conversely, a `published-0.1.7` screenshot cannot
prove the `source-0.1.8` implementation.

## Controller lifecycle

Build the separately pinned harness first. This compiles/tests/remaps only the
dedicated baseline project; it does not launch Minecraft:

```bash
./gradlew -p baseline-harness/fabric/1.21.1 --no-daemon clean buildHarness
```

The build uses Java 21 and pinned Architectury Loom `1.17.480`. Static
controller validation is then read-only:

```bash
python3 -B scripts/baseline/original_client.py validate
```

The remaining lifecycle is deliberately split into distinct gates:

```bash
python3 -B scripts/baseline/original_client.py provision
python3 -B scripts/baseline/original_client.py stage
python3 -B scripts/baseline/original_client.py check
python3 -B scripts/baseline/original_client.py run --scenario slitherite-block-registry
```

- `provision` is the controller's explicit dependency-download phase. It
  validates the tracked Fabric response, complete pinned Python launcher/HTTP
  stack, and Java 21 before creating a runtime root. It then
  installs vanilla Minecraft in a killable owned worker with a hard one-hour
  parent deadline, replaces the generated version JSON with the pinned
  no-bundled-runtime projection, copies the validated Fabric response into the
  owned installer directory, and installs its eight pinned libraries. A byte may
  come from the ignored repository transport cache at
  `.state/pinned-fabric-libraries` only when its regular-file type, containment,
  size, SHA-1, and SHA-256 all match the manifest; otherwise a missing cache entry
  is downloaded from its exact pinned URL. Linked or tampered cache content fails
  closed. This cache contains no launcher or game profile and does not make any
  profile an installation source.
  No Fabric installer or Mojang Java runtime executes or remains. Before atomic
  publication, it verifies 63 selected macOS vanilla libraries, eight Fabric
  libraries, the client JAR, all 16 native-classifier JAR selections, every
  asset object, both source metadata files and their projections, and the
  complete immutable launcher-input inventory. Terminal signals are deferred
  across worker creation and cleanup so the worker cannot be orphaned.
- `stage` first requires a pristine, exact owned game/HOME/TMP profile. It then
  extracts only the eight hash-pinned published JAR members, copies the
  separately hash-pinned harness as the ninth JAR, and writes an artifact-set
  lock. It refuses unexpected files.
- `check` verifies the marker, installed runtime, bundle, staged inventory,
  harness, fresh evidence directories, absent scenario world, Java, and exact
  scenario-bearing command—including the sole game directory, native directory,
  asset index, and `960x540` logical resolution pair—without launching Minecraft.
- `run` requires one exact tracked scenario and permanently consumes that
  profile's one launch attempt before `Popen`, even if launch subsequently
  fails. The exclusive seal binds the complete argv, exact pristine profile
  snapshot, minimal repository-owned HOME/TMPDIR environment, complete selected
  JDK image, macOS `caffeinate` executable, hash-pinned launcher generator and
  five-member HTTP dependency chain, both version metadata files, exact ordered
  72-entry classpath, 16 native-classifier selections, asset index/objects,
  artifact lock, and complete immutable launcher-input inventory. Native
  extraction occurs only after the seal in a fresh repository-owned temporary
  directory outside that inventory. The controller streams output through a
  live 64 MiB cap and controls only the process group created by that same
  invocation; HUP, INT, QUIT, and TERM are deferred across spawn/assignment and
  translated into bounded process-group cleanup.
  After a zero exit it rehashes all launch and staged inputs, validates the
  bounded extracted-native inventory, then verifies exact ordered assertions, a
  decoded nonblank PNG, exact origin-region save proof, report-hash
  marker/publication order, empty crash reports, and a clean game-log shutdown.

The native client may still contact normal Minecraft services during `run`.
Minecraft 1.21.1 can write a downloaded player texture below
`launcher/assets/skins`, so that subtree must be absent before launch and is the
one explicit exception to launcher immutability. It is excluded from the input
hash, then bounded to 256 regular hash-shaped files/64 MiB, checked for links,
hashed after exit, and persisted in the successful-run verification record. A
downloaded first-person player texture can influence framebuffer pixels, so the
record makes that ambient visual input inspectable; it is not treated as proof
of an Etherology mechanic. Use an OS-level network policy if a fully offline
process is required.

The first `provision` attempt on 2026-08-31 failed closed before publication
because Fabric Meta returned newly timestamped bytes instead of the captured
response. A second failed closed when macOS rejected a Requests proxy lookup in
a forked multithreaded Python child. Both staging directories were removed and
no runtime was published. After tracking the response and changing the killable
worker to a clean spawned interpreter, `provision`, `stage`, and `check` all
passed for the new owned profile. Its one `phase0-smoke` run then passed all 30
assertions, captured the native 1920x1080 framebuffer after 120 ready renders,
saved the world, and exited cleanly. The frozen evidence is under
`docs/evidence/original-1.21.1/phase0-smoke-v1`. `minecraft-launcher-lib==8.0`,
Requests `2.34.2`, urllib3 `2.7.0`,
certifi `2026.6.17`, idna `3.18`, and charset-normalizer `3.4.9` must be available
in the invoking Python environment even for `validate`. Before any launcher or
HTTP package code is imported, the controller validates each exact wheel
`RECORD`, every hashed regular file, standard import resolution, and the absence
of unpinned optional HTTP implementations. Immediately after the controlled
import, it validates every loaded module's origin and loader and rechecks the
optional-module exclusion. It rejects extra executable package files and
redirects bytecode lookup to an absent repository-owned cache prefix with
bytecode writes disabled. The controller does not install Python packages or
write a shared package cache.

Launcher-lib 8.0 leaves Mojang's `${clientid}` and `${auth_xuid}` arguments
unresolved for this version JSON. Command generation requires exactly one of
each and replaces them with fixed offline literals before the complete argv is
validated or sealed.

## Active `slitherite-block-registry` v5 contract

The unconsumed v5 profile is dedicated to the published-0.1.7 Slitherite
family. It validates 17 ordered block/item registry pairs, exact intermediary
runtime classes and default states, 1,262 state network IDs, 79 Etherology
visual assets plus one vanilla sentinel, grouped block/item tags, 17 self-drop
loot tables (including one item from every double slab), 29 family recipes and
their 29 advancements, and five related cross-system recipes that are recorded
but not claimed as Slitherite-owned. All 17 gallery members are placed through
their real `BlockItem` path.

The native behavior sequence requires a deterministic button pulse and reset,
an ignored item entity plus an activating living entity on the pressure plate,
a forced save, a full disconnect/reopen, and exact structural/data equality
after reopen. Each initial/reopened capture requires 20 fresh paired local
server/client light samples (sky 15 and block 14), terrain/fixture readiness,
and 120 consecutive completed renders. Global lighting-provider pending state
is diagnostic only; timeout reports retain the last local samples and counters.
The strict controller independently rejects dark images by mean-luminance and
dark-pixel bounds and compares both the reopened structure and framebuffer
pixels.

Expected evidence, once the one authorized run is eventually performed, is:

```text
scripts/baseline/.state/runtimes/
  etherology-original-fabric-1.21.1-published-0.1.7-v5/
    evidence/slitherite-block-registry/
      reports/{report.json,done.marker}
      screenshots/{slitherite-block-registry-initial.png,
                   slitherite-block-registry-reopened.png}
```

At preparation time v5 has no runtime, launch-attempt seal, evidence, or
archive. Provisioning and launching it are deliberately outside this change.
The v1-v4 manifests and their historical evidence remain immutable.

## Historical `phase0-smoke` contract

The implemented first scenario creates a brand-new repository-owned integrated
world named `etherology-original-phase0-smoke-world` with seed
`19514442935972151`. It checks the published resources and registries, places
the Brewing Cauldron, Empowerment Table, Ethereal Storage, and Armillary Sphere,
requires the exact registered block-entity type at every server and client
position, records the live server name/seed/dimension, and fixes the player to
an exact first-person camera pose. Terrain and all four positions must remain
rendering-ready for 120 consecutive completed render callbacks; any failed
predicate resets the counter. The harness adapts the logical window size to the
live macOS backing scale and writes one unedited `1920x1080` Minecraft
framebuffer capture. It then force-saves, atomically publishes `report.json`,
publishes `done.marker` with the report SHA-256, and schedules a clean client
stop.

All outputs stay below:

```text
scripts/baseline/.state/runtimes/
  etherology-original-fabric-1.21.1-published-0.1.7-v1/
    evidence/phase0-smoke/
      reports/{report.json,done.marker}
      screenshots/phase0-smoke.png
```

The harness and controller refuse pre-existing evidence or a pre-existing
scenario world. The legacy gallery datapack remains an unprovisioned historical
fixture and is not used by `phase0-smoke`.

Run the pure controller tests with:

```bash
python3 -B -m unittest discover -s scripts/baseline/tests -v
```
