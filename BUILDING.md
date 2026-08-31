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
particle-registry, and behavior-free material-item milestones. Storage and Channel have separate
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
gameplay or drop parity. No screenshots or native sound-playback evidence are claimed. The earlier
Fabric `v20` packaged client archive remains historical.

The current Fabric artifact was also exercised in the fresh repository-owned
`etherology-e2e-fabric-1.20.1-v21` profile after the shared material-item
rebuild. Its packaged Phase 0 smoke passed 42 of 42 ordered assertions, ran for
211 client ticks, captured two native 1920x1080 screenshots with a
`0.9796272183641975` changed-pixel ratio, entered and saved an integrated world,
and shut down normally. The immutable archive is `phase0-smoke-v21`; its
profile-manifest SHA-256 is
`d6fa9ac08407128f34473add51c2f75da703c34c73ac985c7af11b024449d722`
and its production-JAR SHA-256 is
`287d0b67e09fadd116905a5588615fae38daf43e22af8cdf0e37546595c38d75`.
This bounded client smoke does not directly exercise the 14 material IDs or
their fuel, creative-tab, recipe, or other gameplay consumers; exact current
cross-loader ownership, IDs, properties, and resources are covered by the
static artifact gates. The migrated Fabric consumers compile and datagen
completes, but their individual mappings and behavior remain outside this
bounded native scenario.

The current cumulative server proof is the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v11` `material-item-registry` run. Its
schema-7 report passed 163 of 163 ordered assertions on a real Java 17 Forge
47.4.9 dedicated server. It resolved the 14 exact IDs, their vanilla `Item`
runtime class and maximum counts, and exact `ItemStack` NBT ID/count/key round
trips after server-data load. Registry properties and NBT observations were
unchanged at server start and after a real `reload`. The cumulative contract
also re-proved the particle, enchantment, game-event/tag, loot-condition, and
exact initial/reloaded Ether-source states. The server saved the world, stopped
normally, exited zero, and logged no fatal, forbidden client-startup, or
unexpected client-class marker. This headless scenario produced no
screenshots. Its immutable archive is `material-item-registry-server-v11`,
with profile-manifest SHA-256
`63ee2c8707f276cc87df2e0b162b2f3174e1fe1b3d689b26135298154ed1b171`,
report SHA-256
`f3cc85b8514704f6c789e5abbdb835ede8aea062f5bae5b7ade0ab20da26bd4f`,
server-log SHA-256
`ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`,
and archive-manifest SHA-256
`9f5ff60298d9066e92c3f3e8ff6e5ab97fba5b35369f6ed09e1359bed7afe347`.
This proves registry properties and in-process `ItemStack` NBT
round-trip/reload stability only. It does not prove player `/give`, a second
JVM or server restart, Forge fuel registration, creative-tab placement,
recipes, client gameplay, or release readiness. The v6 Ether-source, v7
enchantment, and v10 particle archives remain immutable historical evidence.
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
  :forge:1.20.1:validateForgeMaterialItemRegistryMilestone \
  :forge:1.20.1:validateForgeMaterialItemRegistryServerEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The combined positive material-item task includes the accepted sound,
game-event, loot-condition, registry-foundation, Ether-source, enchantment,
and particle tasks; static registry/resource/probe checks; every frozen
predecessor archive; and the current v11 material-item archive. It exercises
Common, Fabric, and Forge tests, both transformed Common artifacts, the Fabric
development and remapped production JARs, and the Forge shadow JAR. It proves
the exact 14 declarations, sole ownership, lazy supplier lifecycle, loader
bootstrap paths, absence of the former eager fields, and byte-exact packaged
models, textures, and English names. Native v11 adds only server registry
properties and `ItemStack` NBT round-trip/reload evidence; it does not infer
fuel, creative-tab, recipe, or client gameplay from that state.

`verifyRegistryFoundationServerProbe` builds and validates the isolated server-only probe, its Java 17 Loom
run configuration, and its separation from production artifacts. It deliberately does not launch
Minecraft. The under-test isolation task verifies the two explicit client-test artifacts; neither
is the blocked release artifact. The final task is diagnostic only: it reports the broader
authoritative registry spine as the first incomplete forward milestone.

## Forge dedicated-server material-item-registry proof

The native material-item-registry proof is a one-shot run in a new
repository-owned profile. `provision` refuses every existing target rather than
adopting, deleting, or resetting it. The accepted v11 runtime is immutable. The
commands below record the accepted v11 workflow; `validate` and both verifier
commands remain safe, but do not invoke `provision`, `check`, or `run` again
until every profile, contract, directory, and verifier literal has been
advanced to a fresh version.

```shell
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
python3 -B scripts/e2e/forge_server_material_item_evidence_v11.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v11
python3 -B scripts/e2e/forge_server_material_item_evidence_v11.py \
  --archive docs/evidence/forge-1.20.1/material-item-registry-server-v11
```

The runner invokes the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task under `caffeinate`, with a
JDK 21-or-newer Gradle host and Java 17 dedicated server. It independently
bounds the process and server logs, validates all 163 ordered assertions and the
full mod inventory, requires a saved world, normal Forge lifecycle, no crash
report, no fatal/client marker, and exit code zero, and publishes its done
marker last. Report schema 7 records the 14 exact material-item IDs, vanilla
runtime class, maximum counts, `ItemStack` NBT ID/count/keys, exact in-process
round trips, real reload stability, and the cumulative prior registry and
Ether-source states. The probe records
`ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That narrow workaround joins the actual stopped-event
server thread before `System.exit` and prevents a proven Loom-userdev
non-daemon thread leak from hanging Gradle; it is not production mod behavior.
This is a headless registry-properties and `ItemStack` NBT
round-trip/reload proof, so screenshots are neither produced nor claimed. It
does not execute player `/give`, a second JVM or server restart, Forge fuel
registration, creative-tab placement, recipes, or client gameplay. It also
does not prove the full authoritative registry or release readiness.

### Historical v10 particle-registry proof

The immutable `particle-registry-server-v10` archive remains the accepted
historical proof for the exact 22 shared particle declarations and their
server-side type/factory/codec/wire/seal contracts. Its schema-6 report contains
138 passing assertions, all of which are re-proved cumulatively by v11.
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
assertions, which are re-proved cumulatively by v11. Validate it only through
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
