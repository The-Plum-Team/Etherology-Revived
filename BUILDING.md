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

Fabric 1.20.1 builds, generates data, and has passed its packaged Phase 0 E2E run
in a real macOS client and integrated world. Forge has a native JavaFML entry point,
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
v4 superseded it as the registry-foundation proof. The canonical Attrahite block/drop graph
remains Fabric-only because the full block and item set is not ported; this synthetic result
proves the condition and serializer, not Attrahite
gameplay or drop parity. No screenshots or native sound-playback evidence are claimed.

The packaged Fabric artifact after the metal-block rebuild was exercised in the
fresh repository-owned `etherology-e2e-fabric-1.20.1-v22` profile. Its packaged
Phase 0 smoke passed 42 of 42 ordered assertions, ran for 235 client ticks,
captured two native 1920x1080 screenshots with a
`0.9796228780864198` changed-pixel ratio, entered and saved an integrated world,
and shut down normally. The immutable archive is `phase0-smoke-v22`; its
profile-manifest SHA-256 is
`289eb0c29066990f7ad967b4f141d08bd7823c0cb79bded85faa37907bd1328f`,
its production-JAR SHA-256 is
`5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd`,
and its harness-JAR SHA-256 is
`b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef`.
The report SHA-256 is
`a6a682db2ad60a5bb59df05a57cad101278def12989dc9cd092df85dc6b484bd`,
and the archive-manifest SHA-256 is
`1f0384073101cd9b6794b6322b941a2fbbfc59bd6210382202bea1b16df3df38`.

This bounded client smoke proves capture-time packaged-artifact startup and
rendering, integrated-world entry, the existing four-machine fixture mirror,
save, and normal shutdown. The world fixture is composed of machines; neither
native screenshot shows or directly interacts with the three metal blocks. The
run does not prove the material items or food, mining/drop behavior, beacon
activation, recipes, creative-tab behavior, or other unexercised gameplay.
Those exact current declarations and resources remain the responsibility of
the static artifact gates until their own mechanic-level E2E scenarios exist.
Fabric `v21` and `v20` are immutable historical archives.

The v22 archive seals its capture-time profile, artifact, report, and screenshot
identity. Archive-only validation does not compare or cryptographically bind
later sources or rebuilt artifacts; another rebuild requires a fresh v23-or-newer
profile before equivalent native evidence can be claimed.

The current cumulative server proof is the fresh repository-owned
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
The release artifact remains deliberately blocked on the rest of the
authoritative registry spine and the remaining gameplay and native-readiness
work.

## Requirements

- A JDK 21 or newer to run Gradle 9.6.1. Gradle compiles the Minecraft modules for Java 17.
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
  validateSupportCatalog
```

Compile and test every accepted bounded Forge milestone without claiming a release artifact:

```shell
bash ./gradlew --no-daemon --no-parallel \
  :forge:1.20.1:validateForgeMetalBlockRegistryMilestone \
  :forge:1.20.1:validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The combined positive metal-block task includes the accepted sound,
game-event, loot-condition, registry-foundation, Ether-source, enchantment,
and particle tasks; static registry/resource/probe checks; every frozen
predecessor archive; and the current v13 metal-block archive. It exercises
Common, Fabric, and Forge tests, both transformed Common artifacts, the Fabric
development and remapped production JARs, and the Forge shadow JAR. It proves
the inherited exact 14 material-item declarations and the three exact block
plus three exact block-item declarations, sole ownership, lazy supplier
lifecycle, loader bootstrap paths, absence of the former eager fields, and
exact packaged resources. Native v13 adds only the bounded server registry,
property/tag/NBT, direct world-placement-through-reload, and save/stop proof;
it does not infer player, mining, beacon, recipe, creative-tab, or client
behavior from that state.

`verifyRegistryFoundationServerProbe` builds and validates the isolated server-only probe, its Java 17 Loom
run configuration, and its separation from production artifacts. It deliberately does not launch
Minecraft. The under-test isolation task verifies the two explicit client-test artifacts; neither
is the blocked release artifact. The final task is diagnostic only: it reports the broader
authoritative registry spine as the first incomplete forward milestone.

## Forge dedicated-server metal-block-registry proof

The native metal-block-registry proof is a one-shot run in a new
repository-owned profile. `provision` refuses every existing target rather than
adopting, deleting, or resetting it. The accepted v13 runtime is immutable. The
commands below record the accepted v13 workflow; `validate` and both verifier
commands remain safe, but do not invoke `provision`, `check`, or `run` again
until every profile, contract, directory, and verifier literal has been
advanced to a fresh version.

```shell
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
python3 -B scripts/e2e/forge_server_metal_block_evidence_v13.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v13
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
