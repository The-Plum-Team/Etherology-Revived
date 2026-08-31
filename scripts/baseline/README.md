# Isolated original client launcher

`original_client.py` provisions and launches a dedicated Etherology reference
game directory named `Etherology E2E 1.21.1`. It uses a deterministic offline
test identity, Modrinth's pinned Java 21 runtime, Fabric Loader `0.17.3`, and a
`1280x720` window.

The isolated directory is:

```text
~/Library/Application Support/ModrinthApp/profiles/Etherology E2E 1.21.1/
```

The user's `1.21.1 Fabric` profile is a read-only byte source during the first
`provision` only. The script copies eight allowlisted JARs after checking their
SHA-256 hashes and Fabric mod IDs. It never copies that profile's settings,
configuration, saves, logs, screenshots, or unrelated mods, and later checks
and launches use only the isolated directory.

The exact top-level inventory is Etherology `0.1.7`, Fabric API,
FabricShieldLib, Biolith, Cardinal Components API, GeckoLib, owo-lib, and
Trinkets. Required jar-in-jar libraries remain inside those exact hash-pinned
bytes: Fabric-ASM, MidnightLib, MixinSquared, owo-sentinel, the Fabric API and
CCA modules, and owo's Endec/Jankson support. No additional runtime JAR is copied.
Every integrity check recursively asserts that Quick Skin (`quickskin`),
Customizable Player Models (`cpm`), Ears (`ears`), and Architectury
(`architectury`) are absent.

The launcher never reads Modrinth's account database or keychain, and it does
not print or persist the generated Minecraft command. The shared, read-only
metadata root is pinned to `fabric-loader-0.17.3-1.21.1`; Loader `0.16.9` cannot
satisfy GeckoLib `4.9.2`'s `fabricloader >=0.17` requirement.

## Usage

Create the isolated directory without launching Minecraft:

```bash
python3 -B scripts/baseline/original_client.py provision
```

Install the pinned NBT tooling into the ignored launcher state, then provision
the validated gallery world without launching Minecraft:

```bash
python3 -m pip install --target scripts/baseline/.state/python \
  -r scripts/baseline/world-requirements.txt
python3 -B scripts/baseline/original_client.py world
```

The world action verifies the full source-world fingerprint, omits its
transient server `session.lock`, and atomically publishes
`saves/Etherology Baseline Gallery`. Both `level.dat` and `level.dat_old` are
written through `nbtlib` with the display name, creative mode, commands,
non-hardcore state, and a zero-radius spawn overlooking the five bays. The
gallery datapack must remain enabled and match its pinned fingerprint.

Running `world` again is idempotent while the save is pristine. After Minecraft
has legitimately saved gameplay changes, the strict action reports drift and
refuses to replace the world. `check` and `start` still validate the world's
identity, controlled NBT settings, and immutable datapack without rejecting
ordinary region or player-data changes.

Run the non-launching integrity and command-generation check:

```bash
python3 -B scripts/baseline/original_client.py check
```

Prepare a deterministic official-format import archive without opening an app:

```bash
python3 -B scripts/baseline/original_client.py pack
```

The ignored output is
`scripts/baseline/.state/Etherology E2E 1.21.1.mrpack`. It contains the exact
allowlisted files under `overrides/` and a format-version-1
`modrinth.index.json`; it has no network URLs, account data, or tokens.

Only after provisioning the profile and world and passing `check`, manage the
visible client with:

```bash
python3 -B scripts/baseline/original_client.py start
python3 -B scripts/baseline/original_client.py status
python3 -B scripts/baseline/original_client.py stop
```

The Python environment must contain exactly `minecraft-launcher-lib==8.0`.
`start` wraps the client in `caffeinate -dimsu`, refuses to reuse an active
game directory, and records only non-secret PID metadata and console logs under
the ignored `scripts/baseline/.state/` directory. Its generated command uses
`quickPlaySingleplayer` to enter `Etherology Baseline Gallery` directly.

The isolated `profile.json` is a launcher-owned, credential-free provenance
record. Modrinth App `0.17.3` keeps its visible instance registry in its app
database, so creating this folder alone does not register an instance in the
GUI. On the baseline Mac, opening the generated `.mrpack` through Modrinth's
supported file-import flow registered `Etherology E2E 1.21.1`; this workflow
never edits the app database directly. On another Mac, import the generated
archive through that same supported UI. Never point the app at or launch the
user's source profile.

Minecraft's own screenshots land in:

```text
~/Library/Application Support/ModrinthApp/profiles/Etherology E2E 1.21.1/screenshots/
```

Copy accepted captures into the appropriate mechanic directory below
`docs/baseline/original-1.21.1/`. Keep the Mac connected to power during a long
capture run; `caffeinate` prevents idle sleep but does not extend battery life.
