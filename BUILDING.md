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
metadata, accepted bounded Ethereal Storage and Ethereal Channel verticals, and an accepted
shared-sound registry/resource milestone. Storage and Channel have separate packaged
save/restart evidence from fresh isolated macOS clients. `SharedSounds` owns the exact 14
Common sound IDs, while the packaged resources close their 21 mono 44.1 kHz OGG files,
`sounds.json` entries, attenuation values, and English subtitles. No native sound-playback E2E
is claimed by that static registry milestone. The release artifact remains deliberately blocked
on the rest of the authoritative registry spine and the remaining gameplay and native-readiness
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
  :forge:1.20.1:validateForgeSoundRegistryMilestone \
  :forge:1.20.1:validateForgeChannelEvidenceArchiveIntegrity \
  :forge:1.20.1:verifyE2eUnderTestIsolation \
  :forge:1.20.1:verifyForgePortGateClosed
```

The positive sound task consumes the accepted Channel implementation and archive-integrity gate,
Common, Fabric, and Forge tests, both transformed Common artifacts, the remapped Fabric production
JAR, and the Forge shadow JAR. It
proves that `SharedSounds` is the sole declaration owner on both loaders: Fabric attaches it from
its canonical initializer, while Forge attaches it before lifecycle registry events. The legacy
eager `EtherSounds` owner is absent, and the packaged sound bytes are locked to their canonical
sources. The task does not run or claim native playback. The under-test isolation task verifies the two
explicitly test-only packaged artifacts; neither is the blocked release artifact. The final task
is diagnostic only: it now reports the broader authoritative registry spine as the first
incomplete forward milestone.

`validateForgeChannelEvidenceArchiveIntegrity` verifies only the immutable channel archive and
its capture-time provenance. `validateForgeChannelCurrentArtifactDiagnostic` is deliberately
separate and now fails because the later sound work changed the whole production JAR. That digest
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
