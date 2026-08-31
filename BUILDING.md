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
shared-sound and game-event registry milestones. Storage and Channel have separate packaged
save/restart evidence from fresh isolated macOS clients. `SharedSounds` owns the exact 14
Common sound IDs, while the packaged resources close their 21 mono 44.1 kHz OGG files,
`sounds.json` entries, attenuation values, and English subtitles. No native sound-playback E2E
is claimed by that static registry milestone.

`SharedGameEvents` is the sole Common deferred owner of
`etherology:etherology_resonance`, with internal ID `etherology_resonance` and range 16. Both
loaders package the event in the vanilla `vibrations` and `warden_can_listen` tags. Fabric uses
the supported `SculkSensorFrequencyRegistry` hook for frequency 10. Forge 47 has no supported
equivalent, so the Forge custom sculk frequency remains deferred. Exact cross-loader artifact
checks are paired with a real Java 17 dedicated-server run in the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v2` Loom-userdev profile. That headless run passed 31 of 31
registry, tag, mod-inventory, isolation, and lifecycle assertions, including normal
`stop(false)`. The runner additionally required a saved world and process exit code zero.
The earlier Fabric `v20` packaged startup/integrated-world smoke has two native framebuffers,
save, and normal shutdown, but it predates the game-event rebuild and does not prove current
Fabric-artifact equality. The release artifact remains deliberately blocked on the rest of the
authoritative registry spine and the remaining gameplay and native-readiness work.

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
  :forge:1.20.1:validateForgeGameEventMilestone \
  :forge:1.20.1:validateForgeGameEventServerEvidenceArchiveIntegrity \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The combined positive game-event task includes the accepted sound task, current server-probe
validation, the frozen dedicated-server archive, Common, Fabric, and Forge tests,
both transformed Common artifacts, the Fabric development and remapped production JARs, and the
Forge shadow JAR. It proves exact declaration multiplicity and ownership, constructor/range data
flow, loader bootstrap paths, Fabric's sole supported frequency hook, absence of unsupported direct
registration alternatives, and byte-exact packaged tags. The inherited sound gate proves that
`SharedSounds` is the sole declaration owner on both loaders and locks the packaged sound bytes to
their canonical sources; it still does not run or claim native playback.

`verifyGameEventServerProbe` builds and validates the isolated server-only probe, its Java 17 Loom
run configuration, and its separation from production artifacts. It deliberately does not launch
Minecraft. The under-test isolation task verifies the two explicit client-test artifacts; neither
is the blocked release artifact. The final task is diagnostic only: it reports the broader
authoritative registry spine as the first incomplete forward milestone.

## Forge dedicated-server registry proof

The native registry proof is a one-shot run in a new repository-owned profile. `provision` refuses
every existing target rather than adopting, deleting, or resetting it. For a newly bumped profile
revision, validate, provision once, check the exact pristine layout, run the named Loom task through
the bounded runner, and then validate the live evidence:

```shell
python3 -B scripts/e2e/forge_server.py validate
python3 -B scripts/e2e/forge_server.py provision
python3 -B scripts/e2e/forge_server.py check
python3 -B scripts/e2e/forge_server.py run
python3 -B scripts/e2e/forge_server_evidence.py \
  --runtime scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v2
```

The runner invokes the exact `:forge:1.20.1:runGameEventServerProbe` task under `caffeinate`, with
a JDK 21-or-newer Gradle host and Java 17 dedicated server. It independently bounds the process and
server logs, validates all 31 ordered assertions and the full mod inventory, requires a saved world,
normal Forge lifecycle, no crash report, and exit code zero, and publishes its done marker last. The
probe records `ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That narrow workaround joins the actual stopped-event server thread before
`System.exit` and prevents a proven Loom-userdev non-daemon thread leak from hanging Gradle; it is
not production mod behavior.
This is a headless registry/tag proof, so screenshots are neither produced nor claimed.

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
