# Ether network and machines

Record channels, forks, sockets, storage, Glints, Ethereal Furnace, Spinner,
Metronome, Levitator, generators, and transfer direction/state.

Use deterministic layouts with labeled sources and sinks. Record stored Ether,
transfer rate, tick count, block-entity state, visible animation, and results
after unload/reload. The visual gallery intentionally leaves machines
unconfigured.

The port passes this area when topology, capacity, processing, synchronization,
and persistence are equivalent on Fabric and Forge.

## Static reference capture

![Ether network and machines gallery bay](original-0.1.7-static-gallery.png)

- Reference JAR: `Etherology-1.21-0.1.7.jar`
- Captured: `2026-08-31 00:29:38` Europe/Madrid
- Fixture viewpoint: spectator at `17.5 101 18.5`
- Framebuffer: `2560x1440`
- SHA-256: `688885fec738f737310c115270bd4c2e955144066a86df14f56c06d1e974f9d3`

This capture records the unconfigured machine and channel presentation. It is
not evidence of transfer, processing, synchronization, or persistence.

## Fabric 1.20.1 interaction record

The isolated `etherology-e2e-fabric-1.20.1-v19` client completed the
`ether-network` scenario with 46 passing assertions and no failures. The real
integrated-world fixture verifies:

- four counted Spinner generations and directional channel transfer;
- the first Ether unit becoming 100 ticks of Levitator fuel;
- a visible armor stand moving `0.7676404465781816` blocks on the force track;
- the redstone gate closing before later Ether is retained;
- final Ether distribution of one unit each in the Levitator, output channel,
  and Ethereal Storage;
- five block-entity NBT reconstructions, client mirroring, and a forced save.

![Fabric 1.20.1 Ether network before activation](../../../../evidence/fabric-1.20.1/ether-network-v19/screenshots/ether-network-before.png)

![Fabric 1.20.1 Ether network after transfer](../../../../evidence/fabric-1.20.1/ether-network-v19/screenshots/ether-network-after.png)

The complete assertion inventory, bounded state history, and artifact
provenance are stored in
[`report.json`](../../../../evidence/fabric-1.20.1/ether-network-v19/reports/report.json).
The deterministic verifier measured a changed-pixel ratio of `0.432843` between
the two native 1920x1080 Minecraft framebuffers.

## Forge 1.20.1 Ethereal Storage record

The isolated `etherology-e2e-forge-1.20.1-v7` client completed the bounded
`ethereal-storage` scenario with 34 passing assertions and no failures. Its real
integrated-world fixture verifies:

- one four-slot Ethereal Storage block entity reconstructed from NBT;
- Forge `ITEM_HANDLER` access on the unsided view and all six faces, including
  simulated and live Glint insertion, blocked extraction, and the hidden display
  slot;
- transfer from 64 internal Ether plus a 64-Ether Glint while preserving 128
  total Ether;
- viewer open/close calls and the synchronized Gecko open/close animation;
- the native menu before save and after a complete integrated-world restart;
- exact Ether distribution, ordered input inventory, display state, and
  block-entity type retained across forced save, disconnect, and restart.

![Forge 1.20.1 Ethereal Storage closed](../../../../evidence/forge-1.20.1/ethereal-storage-v7/screenshots/ethereal-storage-closed.png)

![Forge 1.20.1 Ethereal Storage open](../../../../evidence/forge-1.20.1/ethereal-storage-v7/screenshots/ethereal-storage-open.png)

![Forge 1.20.1 Ethereal Storage menu before restart](../../../../evidence/forge-1.20.1/ethereal-storage-v7/screenshots/ethereal-storage-menu.png)

![Forge 1.20.1 Ethereal Storage menu after restart](../../../../evidence/forge-1.20.1/ethereal-storage-v7/screenshots/ethereal-storage-reopened.png)

The complete assertion inventory and artifact provenance are stored in
[`report.json`](../../../../evidence/forge-1.20.1/ethereal-storage-v7/reports/report.json).
The deterministic verifier measured `0.253670` changed pixels in the centered
storage region from closed to open and only `0.001168` from the original closed
frame to the closed-again frame. These captures and assertions accept only the
bounded storage vertical; Forge channel transfer and the wider Ether network
remain separate, release-blocking work.
