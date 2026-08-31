# Isolated Fabric 1.20.1 E2E client

This harness owns one purpose-built Etherology runtime and never resolves,
copies, registers, inspects, or launches an existing game profile. Its only
game directory is below the ignored repository path:

```text
scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v19/game/
```

The parent runtime must contain the exact provenance marker written by this
script. If the configured directory already exists without that marker, has a
different marker, or is a symlink, every lifecycle action fails closed. There
is no profile-path argument and no adopt, reset, or delete action.

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

## Prepare without launching

Install the pinned launcher helper into ignored repository state:

```bash
python3 -m pip install --target scripts/e2e/.state/python \
  -r scripts/e2e/requirements.txt
```

Validate the tracked configuration without creating or launching a game:

```bash
python3 -B scripts/e2e/client.py validate
```

Provision the new isolated runtime:

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

## Pin the current production and harness builds

Build both separate artifacts, run the focused harness tests, verify isolation,
then stage them together:

```bash
./gradlew :fabric:1.20.1:buildE2eHarness \
  :fabric:1.20.1:verifyE2eHarnessIsolation --no-daemon --console=plain
python3 -B scripts/e2e/client.py stage
python3 -B scripts/e2e/client.py check
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

The implemented scenarios are `phase0-smoke`, `storage-utilities`, and
`ether-network`. The storage scenario creates a fresh integrated world and
exercises crate, shelf, spill-barrel, and tuning-fork interactions and
persistence. The ether scenario creates a separate fresh world and exercises a
Spinner, directional channels, Ethereal Storage, a redstone gate, and a
Levitator force/retention path. An explicit empty, whitespace-padded, or unknown
value aborts harness initialization before any client callbacks are registered;
it is never silently normalized or replaced with another scenario.
The stable Fabric entrypoint remains
`dev.theplumteam.etherology.e2e.fabric.PhaseZeroHarness`, so the isolated profile
identity and staged-artifact contract do not change.

## Client lifecycle

Only after `check` succeeds:

```bash
python3 -B scripts/e2e/client.py start --scenario phase0-smoke
python3 -B scripts/e2e/client.py status
python3 -B scripts/e2e/client.py stop
```

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

Minecraft screenshots are isolated at:

```text
scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v19/game/screenshots/
```

Those native files are raw captures. `provision` also creates a fail-closed
scenario evidence tree at:

```text
scripts/e2e/.state/runtimes/etherology-e2e-fabric-1.20.1-v19/evidence/
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
python3 -B -m unittest scripts/e2e/test_client.py scripts/e2e/test_evidence.py
```

After the selected scenario shuts down, validate its frozen report, artifacts,
screenshots, world, and logs before copying evidence into `docs/evidence`:

```bash
python3 -B scripts/e2e/evidence.py --scenario phase0-smoke
```
