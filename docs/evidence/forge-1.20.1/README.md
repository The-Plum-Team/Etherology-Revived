# Forge 1.20.1 runtime evidence

This directory contains frozen evidence captured from a real Minecraft Forge
client on the baseline Mac. The run used a new repository-owned profile under
`scripts/e2e/.state/`; it did not read, modify, or derive data from an external
launcher profile.

## Ethereal Storage (v7)

- Profile: `etherology-e2e-forge-1.20.1-v7`
- Runtime directory: `scripts/e2e/.state/runtimes/etherology-e2e-forge-1.20.1-v7`
- Tracked profile manifest SHA-256:
  `56244f01a169f6189b8f89f6c32ba7bd1d4edc7c573481d47f96f56f6018ccb7`
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Forge installer SHA-256:
  `58fc5db6e3dc47745475375be6fa275e68320563c05d29b4203e0d2ca57a50c4`
- Vanilla client SHA-256:
  `56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f`
- Architectury Forge 9.2.14 SHA-256:
  `47d5eca3d83aae1ac1d4a70116727715bd7ef4c077d228fee873065cbca94687`
- GeckoLib Forge 4.7.4 SHA-256:
  `6ccdc4001520a098f0af16cebcabc2edde655a64ca39f7acd1e4c90e31bc5164`
- Production JAR SHA-256:
  `c634d89d61b0ea6ccd8d9e523e6c8928446b8cf7c98d5b80d0691c1847063006`
- Harness JAR SHA-256:
  `4a28be83711c5c68dc1749197f0f1a00ccb7029b89496a408cd3e7913d9e288b`
- Report status: `passed`
- Assertions: `34` passed, `0` failed
- Client ticks: `438`
- Centered-storage ROI, closed to open: `0.253670`
- Centered-storage ROI, closed return: `0.001168`

The disposable runtime materialized the Forge child-version client JAR from the
exact pinned vanilla bytes. The parent and child JARs are both 23,028,853 bytes
and have the vanilla client digest recorded above.

The real integrated-world fixture verifies the four-slot storage block entity,
NBT reconstruction, simulated and live Glint insertion through Forge
`ITEM_HANDLER` on the unsided view and all six faces, blocked extraction, and a
hidden display slot. Starting with 64 internal Ether and 64 Ether in one Glint,
the run observes transfer while preserving 128 total Ether. It then waits for a
quiescent state of 0 internal Ether and 128 Glint Ether, force-saves, disconnects,
restarts the integrated world, compares the exact Ether distribution, ordered
input inventory, display state, and block-entity type, and reopens the native
menu. Viewer open and close calls also drive the captured Gecko animation.

## Capture contract

All five PNGs are composed Minecraft framebuffers at `1920x1080`; none is an
operating-system screenshot or crop. The world captures wait for 16 completed
closed renders, 48 completed open renders, and 60 completed closed-again renders.
Both menu captures wait for two completed screen renders. The live verifier
compares the centered storage region (`45%..55%` horizontally and
`36%..62%` vertically), requiring a material closed-to-open change and a return
near the original closed state. It also checks PNG CRCs and dimensions, rejects
blank frames, validates the exact screenshot inventory and artifact lock, scans
logs and crash reports, requires a normal shutdown, and confirms that
`done.marker` was published last.

Frozen file digests:

- `ethereal-storage-v7/archive-manifest.json`:
  `e5cb1dfd8dc988d949de13668af420eec81d4fff6b0734e837ddfd699598a096`
- `ethereal-storage-v7/reports/report.json`:
  `5fe0e9d124206d1be07e9c2db3a4dc488b84f220267ef412ebd868ad23b91048`
- `ethereal-storage-v7/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `ethereal-storage-v7/screenshots/ethereal-storage-closed.png`:
  `6794bbf178f1bbc24d6ee0c95f99efb9df439fd782acea80267fd0bc54a7ce5b`
- `ethereal-storage-v7/screenshots/ethereal-storage-open.png`:
  `956cac7e9d584d57630fb4db062cb49c0944fe01215a56953665dff59dc5ac73`
- `ethereal-storage-v7/screenshots/ethereal-storage-closed-again.png`:
  `626d41d543ac02e794083a1bf61cdb36677a02ce66254d3d25a99bd0f9e21d8c`
- `ethereal-storage-v7/screenshots/ethereal-storage-menu.png`:
  `878a3d8fa8d81fd0d99c9da4132db28b9a720fca42bedf86459ab2a45ba55336`
- `ethereal-storage-v7/screenshots/ethereal-storage-reopened.png`:
  `550b1e762ee99e9b162313296e2adbbe16712363c9459fc01ef926f44591b334`

The complete assertion inventory and artifact provenance are in
[`report.json`](ethereal-storage-v7/reports/report.json). The eight-file archive
can be checked without the ignored live runtime:

```text
Validated archived ethereal-storage for etherology-e2e-forge-1.20.1-v7: 34 assertions, 5 screenshots, closed-to-open 0.253670, closed-return 0.001168
Production SHA-256: c634d89d61b0ea6ccd8d9e523e6c8928446b8cf7c98d5b80d0691c1847063006
Harness SHA-256: 4a28be83711c5c68dc1749197f0f1a00ccb7029b89496a408cd3e7913d9e288b
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

Run `python3 -B scripts/e2e/forge_evidence.py --archive
docs/evidence/forge-1.20.1/ethereal-storage-v7` from the repository root to
repeat that archival check. It proves the tracked report, marker, images, and
capture-time artifact provenance remain internally intact. It deliberately does
not claim that later source changes or rebuilt JARs still match this capture; a
new isolated profile and native run are required for that claim.

This is proof for the bounded Forge Ethereal Storage vertical only. It does not
open the Forge release gate or establish parity for channels, the wider Ether
network, the remaining gameplay graph, dedicated-server behavior, or the full
E2E matrix.
