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
shared-sound, game-event, loot-condition, Ether-source reload, and enchantment-registry
milestones. Storage and Channel have separate packaged save/restart evidence from fresh isolated
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

The historical registry-foundation artifact checks are paired with a fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v4` Loom-userdev run on a real Java 17, Forge 47.4.9
dedicated server. The headless `registry-foundation` scenario passed 39 of 39 ordered assertions
and is frozen in `registry-foundation-server-v4`. Separately, all 63 Python safety tests pass. Its
synthetic table returned
`[minecraft:gold_ingot, minecraft:stone]` for an empty tool and
`[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` for Fortune I, including the mixed
`0.99 + 0.01` condition. The server saved the world, completed normal `stop(false)`, exited zero,
and logged no `ERROR` or `FATAL` marker. The historical v2 game-event archive remains frozen, but
v4 superseded it as the registry-foundation proof. The canonical Attrahite resource remains Fabric-only because
its items are not ported; this synthetic result proves the condition and serializer, not Attrahite
gameplay or drop parity. No screenshots or native sound-playback evidence are claimed. The earlier
Fabric `v20` packaged client evidence predates this registry rebuild and does not prove current
Fabric-artifact equality.

The current cumulative server proof is the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v7` `enchantment-registry` run. Its schema-5
report passed 95 of 95 assertions on a real Java 17 Forge 47.4.9 dedicated
server. It observed only `etherology:peal` and `etherology:reflection` in the
Etherology enchantment namespace, their exact concrete classes, levels and
powers, and exact membership in `minecraft:non_treasure`. Registry identity,
properties, and tag membership were unchanged at server start and after a real
`reload`. The cumulative contract also re-proved the game-event, loot-condition,
and exact initial/reloaded Ether-source states from the historical v6 run. The
server saved the world, stopped normally, exited zero, and logged no `ERROR`,
`FATAL`, or client-startup marker. This headless scenario produced no
screenshots. Its immutable archive is `enchantment-registry-server-v7`, with
profile-manifest SHA-256
`36b0f67d7ef55cd8e34aac92dd4e5866e17d5be4ffa91987793c913dd60f5773`,
report SHA-256
`1f5209c53fab524db662e7e7ef8ba044ba773fc9800dc3d3086e840093dc5aef`,
server-log SHA-256
`b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`,
and archive-manifest SHA-256
`377acc9241417a169ac2f9dbe1f555d5918509a486daf40d89533de4d414feec`.
It does not prove enchantment applicability, Peal shockwave behavior,
projectile reflection, client visuals, full combat parity, the full registry,
or release readiness. The v6 Ether-source archive remains immutable historical
evidence. The release artifact remains deliberately blocked on the rest of the
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
  :forge:1.20.1:validateForgeEnchantmentRegistryMilestone \
  :forge:1.20.1:validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The combined positive enchantment-registry task includes the accepted sound,
game-event, loot-condition, registry-foundation, and Ether-source tasks; static
registry/resource/probe checks; the historical v4 and v6 archives; and the
current v7 enchantment-registry archive. It exercises Common, Fabric, and Forge
tests, both transformed Common artifacts, the Fabric development and remapped
production JARs, and the Forge shadow JAR. It proves exact declaration
multiplicity and ownership, loader bootstrap paths, absence of eager duplicate
registration, and byte-exact packaged tags. The inherited sound gate still does
not run or claim native playback, and the enchantment gate does not infer
applicability or combat behavior from registry state.

`verifyRegistryFoundationServerProbe` builds and validates the isolated server-only probe, its Java 17 Loom
run configuration, and its separation from production artifacts. It deliberately does not launch
Minecraft. The under-test isolation task verifies the two explicit client-test artifacts; neither
is the blocked release artifact. The final task is diagnostic only: it reports the broader
authoritative registry spine as the first incomplete forward milestone.

## Forge dedicated-server enchantment-registry proof

The native enchantment-registry proof is a one-shot run in a new
repository-owned profile. `provision` refuses every existing target rather than
adopting, deleting, or resetting it. The accepted v7 runtime is immutable. The
commands below record the accepted v7 workflow; `validate` and both verifier
commands remain safe, but do not invoke `provision`, `check`, or `run` again
until every profile, contract, directory, and verifier literal has been
advanced to a fresh version.

```shell
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
python3 -B scripts/e2e/forge_server_enchantment_evidence_v7.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v7
python3 -B scripts/e2e/forge_server_enchantment_evidence_v7.py \
  --archive docs/evidence/forge-1.20.1/enchantment-registry-server-v7
```

The runner invokes the exact
`:forge:1.20.1:runRegistryFoundationServerProbe` task under `caffeinate`, with a
JDK 21-or-newer Gradle host and Java 17 dedicated server. It independently
bounds the process and server logs, validates all 95 ordered assertions and the
full mod inventory, requires a saved world, normal Forge lifecycle, no crash
report, no fatal/client marker, and exit code zero, and publishes its done
marker last. Report schema 5 records the exact enchantment registry, classes,
level/power properties, singular non-treasure tag, real reload stability, and
the cumulative prior registry/Ether-source states. The probe records
`ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That narrow workaround joins the actual stopped-event
server thread before `System.exit` and prevents a proven Loom-userdev
non-daemon thread leak from hanging Gradle; it is not production mod behavior.
This is a headless registry/tag/loot-condition/Ether-source/enchantment proof,
so screenshots are neither produced nor claimed. It does not prove
enchantment applicability, Peal shockwaves, projectile reflection, client
visuals, full combat, the full authoritative registry, or release readiness.

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
