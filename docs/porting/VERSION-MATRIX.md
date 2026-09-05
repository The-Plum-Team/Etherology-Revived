# Etherology version matrix

Etherology uses one independently buildable release branch per exact Minecraft version. The global
roadmap lives in [`release/support-catalog.json`](../../release/support-catalog.json); each release
branch keeps only its own two buildable loader lanes in `release/release-matrix.json`.

The support catalog records the exact Java target, remap policy, loader version, platform API
version, and Architectury API version for every planned branch. For Forge and NeoForge, the loader
and platform API are the same pinned platform coordinate, so their two version fields intentionally
match.

## Branch eras

| Minecraft versions | Branch name | Loader pair | Java | Production namespace |
| --- | --- | --- | ---: | --- |
| `1.20.1` | `forge-and-fabric-1.20.1` | Fabric + Forge | 17 | Remapped |
| `1.21.1` through `1.21.11` | `fabric-and-neoforge-<version>` | Fabric + NeoForge | 21 | Remapped |
| `26.1`, `26.1.1`, `26.1.2`, `26.2` | `fabric-and-neoforge-<version>` | Fabric + NeoForge | 25 | Official/no-remap |

The current release matrix materializes Minecraft 1.20.1 only. Its exact anchors are Fabric Loader
`0.17.3`, Fabric API `0.92.6+1.20.1`, Forge `1.20.1-47.4.9`, and Architectury API `9.2.14`.
Catalog entries for later versions are a porting roadmap, not a claim that those artifacts already
build or run.

## Source architecture

Shared behavior belongs in `common/src/main`. Fabric, Forge, and NeoForge entry points and metadata
belong in their loader module. A release branch may introduce a narrow `legacy<version>` overlay
only for files that cannot share the canonical API. Do not copy complete source snapshots between
versions; the release matrix must declare every active overlay, and generated Stonecutter output
must remain untracked.

Creating a version branch consists of:

1. Branching with the exact catalog name.
2. Replacing the branch-local release matrix with exactly that version's two catalogued loaders.
3. Adding the matching version-suffixed Gradle properties and loader module.
4. Porting canonical code first, then adding only the compatibility overlays compilation proves are
   necessary.
5. Resolving resource-pack and server-data-pack formats directly against that Minecraft version.
6. Running the catalog validator, compilation, and packaged runtime checks before claiming support.

## Testing policy

Retain the accepted original `published-0.1.7` Fabric 1.21.1 and 1.20.1 foundational native
evidence. While materializing later release branches, run build, static, and mechanic tests;
batch their per-version screenshot E2E into a final matrix after all release branches exist,
using a reliable graphical macOS/self-hosted runner. Local visual reruns during porting are
reserved for renderer/API boundaries or failures. Ordinary headless Linux CI does not validate
native screenshots, and a materialized branch is not visual or release acceptance.

## Pack-format warning

Pack-format values are deliberately absent from the support catalog. The architecture audit found
anomalous, apparently stale pack-format entries for Minecraft 1.21.11 and 26.1 through 26.1.2.
Those values were not copied. Each release branch must establish and test its own client-resource
and server-data pack formats, then keep them only in its branch-local release matrix.

## Validation

Run the deterministic validator directly:

```shell
PYTHONDONTWRITEBYTECODE=1 python3 scripts/release/validate_support_catalog.py
```

Run its unit tests:

```shell
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/release/test_validate_support_catalog.py
```

The Gradle verification-only entry point is:

```shell
bash ./gradlew --no-daemon --no-parallel validateSupportCatalog
```
