# Etherology 1.21.1 reference baseline

This directory records the behaviour and presentation of upstream Etherology before
the 1.20.1 port. Each mechanic directory contains its own observations and the
screenshots captured from a real Fabric 1.21.1 client on macOS.

## Reference build

- Upstream repository: <https://github.com/feytox/Etherology>
- Source branch: `1.21`
- Source commit: `70c0fcafb54f91ef715051dc1f6f7c77914e5805`
- Installed reference JAR: `Etherology-1.21-0.1.7.jar`
- Minecraft: `1.21.1`
- Loader: Fabric Loader `0.17.3`
- Isolated game directory: `Etherology E2E 1.21.1`

The original `1.21.1 Fabric` Modrinth directory is only a read-only source for
the exact reference JAR bytes. Baseline runs use an independent directory with
its own `profile.json`, options, configuration, saves, logs, and screenshots.
That directory was registered as `Etherology E2E 1.21.1` through Modrinth's
supported local `.mrpack` import flow; no app database was edited directly.
Its eight top-level JARs are Etherology, Fabric API, FabricShieldLib, Biolith,
Cardinal Components API, GeckoLib, owo-lib, and Trinkets. Quick Skin,
Customizable Player Models, Ears, and Architectury are explicitly excluded.

GeckoLib `4.9.2` requires `fabricloader >=0.17`, so the installed Loader
`0.16.9` metadata from the source instance is not a runnable baseline.

## Baseline world

The isolated save `Etherology Baseline Gallery` is a fingerprinted copy of the
validated server-staged world. Its gallery datapack and five constructed bays
are preserved, while the transient server lock is omitted. The copied NBT uses
creative mode, commands enabled, and a fixed zero-radius spawn overlooking the
gallery. The reference launcher passes that save name through Minecraft Quick
Play so a baseline run enters it directly.

The source branch advertises `1.21-0.1.8`, while the locally installed reference
build is `1.21-0.1.7`. Observations therefore identify the exact JAR when behaviour
could differ from current source.

## Evidence rules

- Screenshots are unedited Minecraft captures unless a file explicitly says otherwise.
- Every mechanic note identifies the setup, action, visible result, and porting
  acceptance criterion.
- Runtime logs and the exact launch metadata are retained so the reference can
  be reproduced without using the source profile.
- A missing screenshot is tracked as an incomplete baseline, not interpreted as a
  missing feature.

## Static gallery overview

![Reference gallery overview](gallery-overview.png)

- Captured: `2026-08-31 00:32:43` Europe/Madrid
- Viewpoint: spectator at `29.5 104 35.5`, facing `29.5 98 4.5`
- Framebuffer: `2560x1440`
- SHA-256: `13b316bcdb35ed7f67b318fb5ca76e96e296fd3f63b821cf2003096998d5e123`
- Source: unedited Minecraft F2 capture from the isolated
  `Etherology E2E 1.21.1` profile

The overview and bay captures establish the reference build's static models,
textures, labels, and fixture layout. They do not replace the interaction,
persistence, generation, or numerical checks required by each mechanic note.
