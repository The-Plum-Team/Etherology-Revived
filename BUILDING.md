# Building Etherology Revived

The port uses a Stonecutter-managed common module and one loader module for Fabric and Forge. The
checked-in release matrix is the source of truth for Minecraft, loader, and Java versions.

## Current 1.20.1 branch

The active matrix declares exactly two Minecraft 1.20.1 release lanes:

| Gradle project | Loader | Java | Canonical source root |
| --- | --- | ---: | --- |
| `:common:1.20.1` | Shared code | 17 | `common/src/main` |
| `:fabric:1.20.1` | Fabric | 17 | `fabric/src/main` |
| `:forge:1.20.1` | Forge | 17 | `forge/src/main` |

The two loader rows are the release lanes; the common project supports both. The
Fabric lane currently compiles the ported canonical implementation under the root
`src/` tree together with its Fabric platform sources and the Stonecutter-generated
common sources. This is an explicit transition state while loader-neutral code is
moved into `common` by complete vertical slices.

The shared-item implementation includes the four canonical Primoshards and the five vanilla Ebony tools
in Common. `SharedPrimoShardItems` owns the exact Keta/Rella/Clos/Via mapping and tooltip behavior;
`SharedToolItems` now owns the Warp Counter plus the Ebony axe, pickaxe, hoe, shovel, and sword.
The moved `EtherToolMaterials` retains both material definitions and lazy, memoized ingot repair.
`validateForgeEbonyToolsStaticMilestone` includes the Primoshard, alchemy, pedestal, aspect, and
lens static gates and the earlier accepted foundations. It does not claim new native gameplay
or visual acceptance. The five crafting recipes and five tool tags are included.

The seven pattern tablets now have one `SharedPatternTabletItems` declaration owner, with the
canonical `PatternTabletItem`, `StaffStyles`, and vanilla smithing-tooltip accessor in Common.
Both loaders install the same nine-chest loot additions and apprentice-toolsmith trade; the
trade waits for the Traditional tablet's registration through `RegistrySupplier.listen`.
`etherology.common.mixins.json` is consumed by both loaders, while the accessor is removed from
Fabric's loader-specific config. Loom remaps its annotations directly, without a legacy refmap.
`validateForgePatternTabletStaticMilestone` includes the earlier shared-item gates and verifies
ownership, aliases, acquisition wiring, remapping, and exact packaged resources. Applying a
tablet at the Inventor Table still depends on the unported Forge staff/table graph. No new
native tooltip, chest-generation, trade, or visual acceptance is claimed by this static slice.

The four Ebony armor pieces now use `SharedArmorItems`, with the original armor class and material
in Common. Their slot-specific speed modifiers, UUIDs, iron-based statistics, and lazy ingot repair
are unchanged. Forge includes the four crafting recipes and both nugget recycling recipes, whose
nine gear inputs are now shared. The cumulative entry point is
`validateForgeEbonyArmorStaticMilestone`; it checks all six build artifacts, 44 inventory models,
six textures, and the crafting/recycling resources. Native armor, repair, movement, trimming, and
image E2E remain unverified for this slice.

The last fully green CI checkpoint before the tablet and armor changes is
[`61b6cb75`](https://github.com/The-Plum-Team/Etherology-Revived/actions/runs/33985498669):
607 Java tests (238 Common, 175 Fabric, 194 Forge), builds, datagen, harness isolation, and the
cumulative gates through Primoshards and Ebony tools. This checkpoint does not verify the newer slices.

Fabric 1.20.1 builds, generates data, and has passed its packaged Phase 0 and
dedicated metal-block-registry E2E runs in real macOS clients and integrated
worlds. Forge has a native JavaFML entry point,
metadata, accepted bounded Ethereal Storage and Ethereal Channel verticals, and accepted
shared-sound, game-event, loot-condition, Ether-source reload, enchantment-registry,
particle-registry, behavior-free material-item, and behavior-free metal-block milestones. Storage and Channel have separate
packaged save/restart evidence from fresh isolated
macOS clients. `SharedSounds` owns the exact 14
Common sound IDs, while the packaged resources close their 21 mono 44.1 kHz OGG files,
`sounds.json` entries, attenuation values, and English subtitles. No native sound-playback E2E
is claimed by that static registry milestone.

`SharedGameEvents` is the sole Common deferred owner of
`etherology:etherology_resonance`, with internal ID `etherology_resonance` and range 16. Both
loaders package the event in the vanilla `vibrations` and `warden_can_listen` tags. Fabric uses
the supported `SculkSensorFrequencyRegistry` hook for frequency 10. Forge 47 has no supported
equivalent, so the Forge custom sculk frequency remains deferred.
`SharedLootConditions` is the sole Common deferred owner of
`etherology:random_chance_with_fortune`, using
`RandomChanceWithFortuneConditionSerializer` on both loaders.

Common is also the sole implementation and resource owner of the
`EtherSourceLoader` server-data listener and its default `ether_sources` data.
Both loaders install that idempotent Common listener; Forge does not carry a
copied loader-specific implementation or default-data shadow.

`SharedEnchantments` is the sole Common deferred owner of exactly
`etherology:peal` and `etherology:reflection`. Both loader artifacts package
those two entries in the singular `minecraft:non_treasure` tag. Fabric's
retained `EtherEnchantments` class owns gameplay policy only, not registration.

`SharedParticleTypes` owns the exact 22 Common particle declarations and their
server payload/codec/wire contracts. `SharedMaterialItems` owns the following
14 behavior-free item declarations on both loaders:

```text
etheroscope, thuja_oil, azel_ingot, azel_nugget, ethril_ingot,
ethril_nugget, ebony_ingot, ebony_nugget, enriched_attrahite, raw_azel,
attrahite_brick, binder, ebony, resonating_wand
```

All 14 have the vanilla `Item` runtime class. `enriched_attrahite` has maximum
count 16; every other item has maximum count 64. The static material-item gate
checks exact Common ownership and IDs, lazy supplier use, one attachment per
loader, removal of the former eager fields, and byte-exact models, textures,
and English names in the relevant Common, transformed, Fabric, and Forge
artifacts.

`SharedMetalBlocks` owns exactly `azel_block`, `ethril_block`, and
`ebony_block`; `SharedMetalBlockItems` owns the three corresponding placeable
`BlockItem` declarations. The static gate checks exact Common ownership,
deferred registration/attachment order, block-to-item mappings, vanilla-copy
settings, packaged models/textures/names, self-drop tables, compression and
decompression recipes, and selected mining/beacon tags across both loaders.
In the two tag files packaged by this bounded Forge slice—`mineable/pickaxe`
and `needs_iron_tool`—still-unported IDs are optional while the three accepted
metal-block IDs remain required. `needs_stone_tool` is unchanged and outside
this Forge resource slice.

The historical registry-foundation artifact checks are paired with a fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v4` Loom-userdev run on a real Java 17, Forge 47.4.9
dedicated server. The headless `registry-foundation` scenario passed 39 of 39 ordered assertions
and is frozen in `registry-foundation-server-v4`. Separately, all 63 Python safety tests pass. Its
synthetic table returned
`[minecraft:gold_ingot, minecraft:stone]` for an empty tool and
`[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` for Fortune I, including the mixed
`0.99 + 0.01` condition. The server saved the world, completed normal `stop(false)`, exited zero,
and logged no `ERROR` or `FATAL` marker. The historical v2 game-event archive remains frozen, but
v4 superseded it as the registry-foundation proof. At that checkpoint the canonical Attrahite
block/drop graph was Fabric-only; this synthetic result proves the condition and serializer,
not Attrahite gameplay or drop parity. The later accepted Forge server v19 and client v17 archives
provide the separate bounded Attrahite proof. No screenshots or native sound-playback evidence
are claimed by v4.

The historical Fabric Forest Lantern packaged-client proof is the consumed
`etherology-e2e-fabric-1.20.1-v24` Forest Lantern profile. It passed all 68
assertions and produced seven unedited 1920x1080 captures spanning the empty,
all-stage, cumulative-facing, and reopened fixtures. The strict archive and
`:fabric:1.20.1:validateFabricForestLanternV24Milestone` both pass. Any later
Fabric lifecycle action requires a fresh identity beyond all consumed profiles. The latest
accepted Fabric proof is Slitherite v31: 185 assertions, two native captures, and a complete
save/disconnect/reopen lifecycle. Its frozen archive and
`validateFabricSlitheriteV31Milestone` are separate from later source and artifact changes.

The historical packaged Fabric artifact after the metal-block rebuild was exercised by the
dedicated `metal-block-registry` scenario in the fresh repository-owned
`etherology-e2e-fabric-1.20.1-v23` profile. Its schema-2 report passed all 25
ordered assertions. The harness captured the exact empty three-pedestal fixture,
placed `azel_block`, `ethril_block`, and `ebony_block` directly on the integrated
server thread, observed the exact client mirror, and captured the populated
fixture from the same fixed camera. Each unedited native 1920x1080 framebuffer
followed 120 consecutive stable renders, and the changed-pixel ratio was
`0.087510`. The world force-saved and the client shut down normally.

The immutable archive is `metal-block-registry-v23`; its profile-manifest
SHA-256 is
`36e7ccb7556aaaf0edb01b066de6d5263f3dde3545ac016e84cf07f795403f84`,
its production-JAR SHA-256 is
`5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd`,
and its harness-JAR SHA-256 is
`0cc892f41399eec903af57c3270f19db027b0e7611e392a0fc817876e373b111`.
The report SHA-256 is
`938f0f73c1104d82d9ede1dd3852ee31871f9b9479017811957102209ff54e73`,
and the archive-manifest SHA-256 is
`69717273eac7b543378aa1a804573e27805e33b771601abba7c49923a5a42f44`.

This bounded visual proof covers the three exact block registry/default-state
network IDs, nine render resources, packaged-root provenance, integrated-world
and chunk readiness, exact before/after server-client fixture states, stable
rendering, native captures, forced save, and isolated save directory. It uses
direct server-side fixture placement, so it does not prove `BlockItem` inventory
or player placement, mining or drops, tool-tier enforcement, beacon activation,
recipes, creative tabs, restart persistence, multiplayer, or release readiness.

The consumed v23 profile and runtime are immutable. Read-only profile and
archive validation remain safe. The later v24 identity is also consumed, so any
next lifecycle action or native launch requires every active literal to advance
to a fresh unused identity beyond v31.
The v22 Phase 0 archive remains the immutable historical packaged-startup proof:
it passed 42 of 42 assertions, ran for 235 ticks, and captured two native
1920x1080 screenshots with changed-pixel ratio `0.9796228780864198`, but its
four-machine fixture did not show or interact with the metal blocks. Fabric
`v21` and `v20` likewise remain immutable historical archives. Archive-only
validation does not compare or cryptographically bind later sources or rebuilt
artifacts.

The historical metal-block server proof is the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v13` `metal-block-registry` run. Its
schema-8 report passed 188 of 188 ordered assertions on a real Java 17 Forge
47.4.9 dedicated server. It resolved the three exact shared blocks and three
corresponding `BlockItem`s, verified their runtime classes/mappings,
properties, selected tags, and exact maximum-count stack NBT, and placed them
directly at three bounded world positions. Registry, property, tag, NBT, and
placed-block observations were unchanged after a real `reload`. The cumulative
contract also re-proved the v11 material-item and earlier registry/reload
states. The world saved, the server stopped normally, the launcher exited
zero, and the copied log passed the verifier. This headless scenario produced
no screenshots. Its immutable archive is `metal-block-registry-server-v13`,
with profile-manifest SHA-256
`c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`,
report SHA-256
`b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`,
server-log SHA-256
`f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`,
and archive-manifest SHA-256
`0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`.
This proves only the bounded registry/classes/mappings/properties/selected-tag,
in-process NBT, direct world-placement-through-reload, and normal save/stop
contract. It does not prove restart persistence, player `/give` or placement,
mining/drop behavior, beacon activation, recipe execution, creative tabs,
client rendering, or release readiness. The v6 Ether-source, v7 enchantment,
v10 particle, and v11 material-item archives remain immutable historical
evidence. The consumed v12 diagnostic profile is not accepted evidence.
The immutable v13 archive proves capture-time Loom-userdev observations and
payload integrity only; it does not compare or cryptographically bind later
sources or rebuilt JARs. Current static artifact checks are a separate boundary.

The historical Forge Forest Lantern server proof is the consumed
`etherology-e2e-forge-server-1.20.1-v16` Forest Lantern run. Its schema-10
report passed 266 of 266 assertions on Java 17 and Forge 47.4.9 and is frozen in
`forest-lantern-server-v16`. The separate consumed Forge packaged-client v13
run passed 69 of 69 assertions in 575 ticks, captured seven unedited 1920x1080
framebuffers, and persisted the exact twenty-state fixture through a full
disconnect/reopen. Its archive is `forest-lantern-v13`. The prior Forge server
v15 oracle and Forge client v12 harness diagnostic are consumed but unaccepted
and must never be relaunched. Later accepted proofs are the Attrahite dedicated-server v19
archive and the Slitherite packaged-client v19 archive. Any new client run must use an identity
beyond all consumed client profiles; the dedicated-server v21 attempt remains pending.

The bounded Forest Lantern positive gate is
`:forge:1.20.1:validateForgeForestLanternMilestone`; it combines the current
static/artifact contract with both accepted Forge Forest Lantern archives.
The release artifact remains deliberately blocked on the rest of the
authoritative registry spine and the remaining gameplay and native-readiness
work.

## Requirements

- A JDK 21 or newer for the Gradle build and Stonecutter plugin, plus a Java 17 Minecraft toolchain.
  Gradle compiles the Minecraft 1.20.1 modules for Java 17. GitHub installs both JDKs explicitly.
- Network access for the first dependency and toolchain resolution.
- Parallel Gradle execution disabled. The project property and commands below enforce this because
  Loom/Stonecutter configuration shares generated state between the loader nodes.

## Structural checks

List the complete Stonecutter project graph without compiling the mod:

```shell
bash ./gradlew --no-daemon --no-parallel projects
```

List root build and verification tasks:

```shell
bash ./gradlew --no-daemon --no-parallel tasks --group build
bash ./gradlew --no-daemon --no-parallel tasks --group verification
```

Build and test the current validated Fabric lane with:

```shell
bash ./gradlew --no-daemon --no-parallel \
  :common:1.20.1:test \
  :fabric:1.20.1:test \
  :fabric:1.20.1:build \
  :fabric:1.20.1:buildE2eHarness \
  :fabric:1.20.1:verifyE2eHarnessIsolation \
  :fabric:1.20.1:fabricForestLanternEvidenceSafetyTest \
  :fabric:1.20.1:validateFabricForestLanternEvidenceArchiveIntegrity \
  :fabric:1.20.1:validateFabricForestLanternV24Milestone \
  validateSupportCatalog
```

Compile and test the implemented shared-item foundations without claiming a release artifact:

```shell
bash ./gradlew --no-daemon --no-parallel \
  :forge:1.20.1:validateForgeEbonyArmorStaticMilestone \
  :forge:1.20.1:validateForgeForestLanternMilestone \
  :forge:1.20.1:validateForgeForestLanternServerEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeForestLanternClientEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The combined positive Forest Lantern task includes the accepted sound,
game-event, loot-condition, registry-foundation, Ether-source, enchantment,
particle, material-item, metal-block, and food-item tasks; static
registry/resource/probe checks; every frozen predecessor archive; and the
historical v16 server plus v13 client Forest Lantern archives. It exercises
Common, Fabric, and Forge tests, both transformed Common artifacts, the Fabric
development and remapped production JARs, and the Forge shadow JAR. It proves
the inherited exact 14 material-item declarations and the three exact block
plus three exact block-item declarations, sole ownership, lazy supplier
lifecycle, loader bootstrap paths, absence of the former eager fields, and
exact packaged resources. Native Forest Lantern evidence adds the bounded
server mechanic/reload/save proof and packaged-client rendering/reopen proof;
it does not infer the remaining registry spine or full release readiness.

`verifyRegistryFoundationServerProbe` builds and validates the isolated server-only probe, its Java 17 Loom
run configuration, and its separation from production artifacts. It deliberately does not launch
Minecraft. The under-test isolation task verifies the two explicit client-test artifacts; neither
is the blocked release artifact. The final task is diagnostic only: it reports the broader
authoritative registry spine as the first incomplete forward milestone.

## Fabric metal-block-registry visual proof

The accepted v23 publication was one-shot. The first command below records the
completed sealing shape and must not be rerun; the second and the Gradle task are
repeatable archive-only checks that do not consult live runtime state or current
build outputs:

```shell
python3 -B scripts/e2e/fabric_metal_block_evidence.py \
  --create-archive-manifest docs/evidence/fabric-1.20.1/metal-block-registry-v23 \
  --capture-runtime scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v23 \
  --profile-manifest scripts/e2e/fabric-1.20.1-profile.json
python3 -B scripts/e2e/fabric_metal_block_evidence.py \
  --archive docs/evidence/fabric-1.20.1/metal-block-registry-v23
./gradlew :fabric:1.20.1:fabricMetalBlockRegistryEvidenceSafetyTest \
  --no-daemon --console=plain
./gradlew :fabric:1.20.1:validateFabricMetalBlockRegistryEvidenceArchiveIntegrity \
  --no-daemon --console=plain
```

The v23 profile is consumed historical evidence, and the later accepted v24
Forest Lantern profile is consumed as well. Before `provision`, `stage`,
`check`, `start`, live validation, or another manifest creation, advance every
active profile/runtime/snapshot/test/verifier/archive literal to a fresh unused
v25-or-newer identity.

## Historical Forge dedicated-server metal-block-registry proof

The native metal-block-registry proof is a one-shot run in a new
repository-owned profile. `provision` refuses every existing target rather than
adopting, deleting, or resetting it. The accepted v13 runtime is immutable. The
commands below are historical provenance except for archive-only validation.
The mutable generic runner now targets the consumed v16 Forest Lantern profile,
so do not invoke `provision`, `check`, or `run` from this v13 record.

```shell
python3 -B scripts/e2e/forge_server_metal_block_evidence_v13.py \
  --archive docs/evidence/forge-1.20.1/metal-block-registry-server-v13
```

The runner invokes the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task under `caffeinate`, with a
JDK 21-or-newer Gradle host and Java 17 dedicated server. It independently
bounds the process and server logs, validates all 188 ordered assertions and the
full mod inventory, requires a saved world, normal Forge lifecycle, no crash
report, no fatal/client marker, and exit code zero, and publishes its done
marker last. Report schema 8 records the three exact metal block and block-item
IDs, vanilla runtime classes and mappings, hardness/blast/map-color and other
block properties, selected mining/beacon tags, maximum-count `ItemStack` NBT,
direct placement at three bounded world positions, real reload stability, and
the cumulative prior registry and Ether-source states. The probe records
`ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That narrow workaround joins the actual stopped-event
server thread before `System.exit` and prevents a proven Loom-userdev
non-daemon thread leak from hanging Gradle; it is not production mod behavior.
This is a headless bounded registry/property/tag/NBT and direct
world-placement-through-reload proof, so screenshots are neither produced nor
claimed. It does not execute player `/give` or player placement, a second JVM
or server restart, mining/drop behavior, beacon activation, recipe execution,
creative-tab interaction, or client rendering. It also does not prove the full
authoritative registry or release readiness. The v12 profile was consumed by a
failed diagnostic launch whose tag-load errors exposed required unported
references in the two tag files packaged by this Forge slice. Those references
are now optional, while `needs_stone_tool` remains unchanged and outside the
slice; v12 has no accepted archive, and v13 used a fresh runtime after the fix.

### Historical v10 particle-registry proof

The immutable `particle-registry-server-v10` archive remains the accepted
historical proof for the exact 22 shared particle declarations and their
server-side type/factory/codec/wire/seal contracts. Its schema-6 report contains
138 passing assertions, all of which are re-proved cumulatively by v13.
Validate it only through the pinned historical verifier; never provision or
reuse the v10 runtime:

```shell
python3 -B scripts/e2e/forge_server_particle_evidence_v10.py \
  --archive docs/evidence/forge-1.20.1/particle-registry-server-v10
```

### Historical v7 enchantment-registry proof

The immutable `enchantment-registry-server-v7` archive remains the accepted
historical proof for exact Peal and Reflection registry properties and
non-treasure tag membership. Its schema-5 report contains 95 passing
assertions, which are re-proved cumulatively by v13. Validate it only through
its pinned verifier:

```shell
python3 -B scripts/e2e/forge_server_enchantment_evidence_v7.py \
  --archive docs/evidence/forge-1.20.1/enchantment-registry-server-v7
```

### Historical v6 Ether-source reload proof

The immutable `ether-source-reload-server-v6` archive remains the accepted
historical proof for Common listener/default-data ownership and the exact
23-entry to 24-entry real reload transition. Its schema-4 report contains 72
passing assertions. Validate it only through the pinned historical verifier;
never provision or reuse the v6 runtime:

```shell
python3 -B scripts/e2e/forge_server_reload_evidence_v6.py \
  --archive docs/evidence/forge-1.20.1/ether-source-reload-server-v6
```

### Historical v4 registry-foundation proof

The immutable `registry-foundation-server-v4` archive remains the accepted historical proof for
the game-event and loot-condition foundation. Its fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v4` profile passed all 39 ordered assertions, saved the world,
completed normal `stop(false)`, exited zero, and logged no `ERROR` or `FATAL` marker. The 63 Python
safety tests exercise its runner and archive verifier without launching Minecraft. Its synthetic
table proves the sole condition and serializer, including the mixed `0.99 + 0.01` case; it does
not prove the still-Fabric-only Attrahite table or drop parity. Validate the historical archive
without consulting a live runtime:

```shell
python3 -B scripts/e2e/forge_server_evidence.py \
  --archive docs/evidence/forge-1.20.1/registry-foundation-server-v4
```

That archive contains 39 passing assertions and copied server-log SHA-256
`085332dc956ea75327d820fc398e122779185c173b6f8002d9962862c9feaea2`.

`validateForgeChannelEvidenceArchiveIntegrity` verifies only the immutable channel archive and
its capture-time provenance. `validateForgeChannelCurrentArtifactDiagnostic` is deliberately
separate and now fails because later registry work changed the whole production JAR. That digest
mismatch is expected and does not imply a Channel regression or current-artifact equality. A new
isolated native run is required to establish equality for a rebuilt JAR. The bounded native
Storage and Channel records are frozen in
[`docs/evidence/forge-1.20.1/README.md`](docs/evidence/forge-1.20.1/README.md).

`buildAllLanes` is intentionally not yet a passing acceptance command: the Forge
`remapJar` task is fail-closed while that lane is incomplete. See
`docs/porting/FORGE-1.20.1-BOOTSTRAP.md` for its exact gate.

## Source ownership

- `common/src/main/java` and `common/src/main/resources` own loader-independent implementation as
  it is extracted by complete, tested vertical slices.
- `fabric/src/main/java` and `fabric/src/main/resources` own Fabric entry points and metadata.
- `forge/src/main/java` and `forge/src/main/resources` own Forge entry points and metadata.
- The root `src/main` and `src/client` trees temporarily remain canonical inputs to the Fabric lane;
  they are not Forge inputs.
- `common/src/test` owns future loader-independent tests.
- A version-specific compatibility overlay, if one becomes necessary, should be declared in
  `release/release-matrix.json` and use a narrow `common/src/legacy1_20_1` source root.

Stonecutter generates node-local sources under module `versions/` and `build/` directories. Do not
edit generated files; edit the canonical source roots and regenerate instead.
