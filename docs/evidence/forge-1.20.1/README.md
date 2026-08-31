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

## Ethereal Storage capture contract

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

## Ethereal Channel (v11)

- Profile: `etherology-e2e-forge-1.20.1-v11`
- Runtime directory: `scripts/e2e/.state/runtimes/etherology-e2e-forge-1.20.1-v11`
- Tracked profile manifest SHA-256:
  `af21ba7cbf1ba71f06a1dc2594daa5aa4a790ee89df3ed560760ceb1b6aa8e6f`
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Production JAR SHA-256:
  `45e94cf67b0be441aae529df08ede9602e3d6d67f07e3570d1facd067b0cfa12`
- Harness JAR SHA-256:
  `e229554424abf1896436d87298d4788786e784fd987f96fe731b1c42a54f5284`
- Report status: `passed`
- Assertions: `42` passed, `0` failed
- Client ticks: `3434`
- Gated-to-transferred change: `0.013136`
- Transferred-to-reopened structure: `0.004009`

The fresh integrated-world fixture verifies the shared ethereal-channel
foundation with registered channel and storage endpoints, persistent Ether and
direction state, redstone activation, and native Forge support for a vanilla
wall lever attached to the channel. A powered channel first receives and holds
exactly one Ether. Removing the gate transfers that Ether to storage on the
fifth-tick cadence without reverse motion. An independent channel with a
missing output then evaporates exactly `0.2` Ether on its fifth-tick cadence and
naturally clears its evaporation state after reactivation. The run force-saves,
disconnects, restarts the integrated world, and compares the exact Ether,
activation, direction, lever, and block-entity state.

## Ethereal Channel capture contract

All three PNGs are composed Minecraft framebuffers at `1920x1080`. Before each
capture, the client sustained 120 consecutive frames with the exact expected
block and block-entity mirror, a complete and ready terrain render, and the
fixed first-person camera pose. The harness rechecked and latched those three
conditions immediately before recording each screenshot. The deterministic
verifier requires the gated-to-transferred change recorded above, limits
unrelated scene movement, and requires the reopened image to retain the
transferred structure. It also validates PNG dimensions, archive inventory,
artifact provenance, assertion evidence, normal shutdown, and publication of
`done.marker` only after the report and captures were complete.

Frozen file digests:

- `ethereal-channel-v11/archive-manifest.json`:
  `b04886bf18b8f74a8bbfed0d0d48e2ae7f0b32321aa820952280673ec3fd70be`
- `ethereal-channel-v11/reports/report.json`:
  `80c31a4ca1e0e3c1000de48f83b374cf3ae17dcf43e76d0d1fac0cbaa9204203`
- `ethereal-channel-v11/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `ethereal-channel-v11/screenshots/ethereal-channel-gated.png`:
  `ffcd33ea596992a39afb86d1011467cf95207d0e02f43968f438bcffe7f38006`
- `ethereal-channel-v11/screenshots/ethereal-channel-transferred.png`:
  `e33947206f968a5f8005e6e4163c588f9464e70484d485bd68f6d7e912fdb901`
- `ethereal-channel-v11/screenshots/ethereal-channel-reopened.png`:
  `5860d53c1f9c56b12d91469c043faae98ab8bc2876b52f696adcaa5fc00cfa01`

The complete assertion inventory and artifact provenance are in
[`report.json`](ethereal-channel-v11/reports/report.json). The six-file archive
can be checked without the ignored live runtime:

```text
Validated archived ethereal-channel for etherology-e2e-forge-1.20.1-v11: 42 assertions, 3 screenshots, transfer-change 0.013136, reopened-structure 0.004009
Production SHA-256: 45e94cf67b0be441aae529df08ede9602e3d6d67f07e3570d1facd067b0cfa12
Harness SHA-256: e229554424abf1896436d87298d4788786e784fd987f96fe731b1c42a54f5284
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

Run `python3 -B scripts/e2e/forge_channel_evidence.py --archive
docs/evidence/forge-1.20.1/ethereal-channel-v11` from the repository root to
repeat that archival check. The frozen archive proves only its own integrity;
later source changes and rebuilt JARs require another fresh isolated profile and
native run before they can claim equivalence.

`validateForgeChannelEvidenceArchiveIntegrity` is the positive Gradle gate for
that immutable historical record. `validateForgeChannelCurrentArtifactDiagnostic`
is deliberately separate: it compares the current whole production and harness
JARs with their capture-time digests. It now fails because later registry
milestones changed the production JAR after this capture. That
expected whole-JAR mismatch neither invalidates the archive nor demonstrates a
Channel regression, and it must not be described as current-artifact equality.
Only a new isolated native run can establish equality for later artifacts.

This accepts only the shared ethereal-channel foundation and its bounded
network behavior with storage endpoints and native Forge lever support. Channel
case interaction and registration, channel particles and the client ticker,
channel loot and recipe data, and the wider machine/network graph remain
deferred. The bounded SharedSounds registry/resource milestone has since passed,
but no native sound-playback E2E was run and this directory is not playback
evidence. The broader authoritative registry spine is the next forward gate.
The Forge release gate remains closed.

## Shared game-event dedicated server (v2)

- Profile: `etherology-e2e-forge-server-1.20.1-v2`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v2`
- Tracked profile manifest SHA-256:
  `9dc0c3c3162ebe25d9d65f38296ccf0c506d871e31d1627377b3d5d95a83fa31`
- Tracked profile manifest size: `1183` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: Loom userdev dedicated server
- Named task: `:forge:1.20.1:runGameEventServerProbe`
- Report status: `passed`
- Assertions: `31` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `89988125b90a78c9f996487c345d1efc70302340c1e08cb449b8a826aef24394`

This fresh, repository-owned Java 17 server loaded the exact sorted mod-ID
inventory `architectury`, `etherology`, `etherology_e2e_server_probe`, `forge`,
`geckolib`, `generated_a85b72e`, and `minecraft`. The generated ID belongs to
the Loom userdev launch composition. The full inventory contained none of the
seven forbidden client or foreign-mod IDs, and the recorded forbidden
intersection is empty.

The server observed exactly one Etherology game event,
`etherology:etherology_resonance`, in the vanilla game-event registry. It
verified the internal ID `etherology_resonance`, range 16, and the same runtime
instance after server start. One `SERVER_DATA_LOAD` static tag update bound the
event to exactly `minecraft:vibrations` and
`minecraft:warden_can_listen`. The server then requested `stop(false)`, saved
all three dimensions, and traversed `tags_updated`, `server_started`,
`server_stopping`, and `server_stopped` before publishing the report.

The Loom userdev launcher left non-daemon transformation workers alive after a
normal v1 server stop. The v2 probe therefore schedules a probe-only daemon
terminator after publishing its report. It joins the actual stopped-event
server thread for at most 30 seconds and permits exit code zero only after that
thread ends; timeout, interruption, publication failure, or a failed report
uses exit code one. This workaround is isolated from the production artifact.
The external runner independently requires the passing report, normal save and
log markers, exact successful termination token, saved `level.dat`, no crash
report, zero process exit, and publication of `done.marker` last.

The scenario is a headless registry and data-pack proof. It produces no
screenshots and does not claim a visual mechanic. Frozen file digests:

- `game-event-registry-server-v2/archive-manifest.json`:
  `a5396bdae7bcf462c60688955eea970ee541f7e7a70dd5425c5f707b386c7dcd`
- `game-event-registry-server-v2/reports/report.json`:
  `ffce96f7f4adb51adfc434d1ae282b622f60088a085df021d9c8310106486739`
- `game-event-registry-server-v2/reports/launcher-result.json`:
  `edbdcfec5fab673a2687870dd1fb8febfd9d4fda2983ed41374b183bb44b51a9`
- `game-event-registry-server-v2/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `game-event-registry-server-v2/logs/latest.log`:
  `89988125b90a78c9f996487c345d1efc70302340c1e08cb449b8a826aef24394`

Validate the five-file archive without the ignored live runtime:

```text
Validated archived game-event-registry for etherology-e2e-forge-server-1.20.1-v2: 31 assertions
Server log SHA-256: 89988125b90a78c9f996487c345d1efc70302340c1e08cb449b8a826aef24394
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

Run `python3 -B scripts/e2e/forge_server_evidence.py --archive
docs/evidence/forge-1.20.1/game-event-registry-server-v2` from the repository
root to repeat that check. `validateForgeGameEventServerEvidenceArchiveIntegrity`
and the combined `validateForgeGameEventMilestone` make this immutable record a
positive gate. `validateForgeGameEventRegistryMilestone` separately inspects
the current Common, Fabric, and Forge artifacts; the archive itself does not
claim current-source or rebuilt-artifact identity.

This accepts only the shared resonance declaration, its two listening tags,
and dedicated-server registry/data-load lifecycle. Fabric's supported custom
sculk frequency 10 is covered statically, while Forge 47 has no supported
equivalent and remains deferred. The proof does not cover sound playback, the
full registry-ID manifest, every catalog entry's placement/save behavior, or
the remaining gameplay and native E2E matrix. The Forge release gate remains
closed on the broader authoritative registry spine.
