# Forge 1.20.1 runtime evidence

This directory contains frozen evidence captured from real Minecraft Forge
clients and a dedicated server on the baseline Mac. Every run used a new
repository-owned profile under `scripts/e2e/.state/`; none read, modified, or
derived data from an external launcher profile.

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
deferred. The bounded SharedSounds, SharedGameEvents, SharedLootConditions, and
SharedEnchantments registry foundation has since passed. The exact shared
particle registry and server-side wire contract have also passed, but no native
sound-playback, enchantment-gameplay, or particle-rendering E2E was run. The
14 behavior-free material items and their bounded server registry/NBT contract
have passed as well. The three behavior-free metal blocks, their corresponding
block items, and their bounded server registry/property/tag/NBT/placement
contract have now passed. The plain `forest_lantern_crumb` food item and its
bounded registry/reload/native-consumption contract have now passed as well. The
broader authoritative registry spine is the next forward gate. The Forge
release gate remains closed.

## Current food-item-registry dedicated server (v14)

- Profile: `etherology-e2e-forge-server-1.20.1-v14`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v14`
- Scenario: `food-item-registry`
- Tracked profile manifest SHA-256:
  `442d11e6a5072c8ec418bced406529dc15caa6d4f4d4c5c68edc8a79ce2e493d`
- Tracked profile manifest size: `1192` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `9`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `219` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db`

`SharedFoodItems` is the sole Common deferred owner of exactly the plain
`etherology:forest_lantern_crumb` vanilla `Item`. Its food component has hunger
3 and saturation modifier 2.0, and it has no always-edible behavior, status
effects, or recipe remainder. The packaged model SHA-256 is
`6ba61590386580a2f70526313d501eec44cd88ff9d86cd1d13d9092b41a42fbe`;
the packaged texture SHA-256 is
`44f9d92ccf36c3555d21ace9eea0268e43eb4a8e95f1e81b74f22977d4928d65`.
The exact translations are `Mushroom Crumb` in English and `Грибной мякиш` in
Russian. The three original recipes and their three advancements reference the
still-unported `etherology:forest_lantern`, so they remain deliberately absent from
the Forge slice instead of loading invalid data or substituting another input.

The native server created two real `ServerPlayerEntity` instances,
`EtherFoodStart` and `EtherFoodReload`. It captured the exact registry ID,
runtime class, food properties, stack state, and deterministic save
representation before and after a real `reload`, with exact reload stability.
Real consumption changed hunger from 10 to 13, saturation from 0 to 12, and the
stack count from 2 to 1 while retaining the same `ItemStack` instance. The report
cumulatively re-proved the v13 metal-block, v11 material-item, v10 particle, v7
enchantment, game-event/tag, loot-condition, and Ether-source contracts. The
world saved, normal `stop(false)` completed, the launcher exited zero, and the
copied log passed the strict verifier scan.

Frozen file digests:

- `food-item-registry-server-v14/archive-manifest.json`:
  `eacab05996569a78a55ed21117d2a7e0768d87c258c93757cdc4ab4205881927`
- `food-item-registry-server-v14/reports/report.json`:
  `3ccd86d4ef6f5b31fd37b686254bdf427e351019c778d2d8e3f03958de0e1f6c`
- `food-item-registry-server-v14/reports/launcher-result.json`:
  `4ec610688bf030ea722772c40d871ec1b954fcbc0b15f90cbad41acec6278ad0`
- `food-item-registry-server-v14/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `food-item-registry-server-v14/logs/latest.log`:
  `e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_food_item_evidence_v14.py \
  --archive docs/evidence/forge-1.20.1/food-item-registry-server-v14
```

```text
Validated archived food-item-registry for etherology-e2e-forge-server-1.20.1-v14: 219 assertions
Server log SHA-256: e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

The complete Python runner/verifier safety suite contains 82 passing tests:

```bash
python3 -B -m unittest discover -s scripts/e2e -p 'test_*.py'
```

Separately, the executable Gradle interlock task passes 15 non-Minecraft
fixture cases and is wired into `forgeFoodItemRegistryServerSafetyTest`:

```bash
./gradlew --no-daemon --no-parallel --console=plain \
  :forge:1.20.1:serverProbeSafetyInterlockTest
```

The 15 Gradle fixture cases and 82 Python tests are distinct suites, not one
97-test count.

The integrated positive milestone and forward-gate diagnostic are:

```bash
./gradlew --no-daemon --no-parallel --console=plain \
  :forge:1.20.1:validateForgeFoodItemRegistryMilestone \
  :forge:1.20.1:verifyForgePortGateClosed
```

The v14 profile is durably consumed. Once its archive is sealed, the runner
rejects both reprovisioning and environment checks even if the ignored runtime
directory is removed. The raw Gradle launch task additionally requires the
exact cryptographic runner token and matching lock, exact tracked profile
marker, and pristine live evidence directories. Direct, stale, or replayed
launches fail before Minecraft starts in the normal runner flow. These are
accidental-misuse interlocks, not provenance authentication; same-account
adversarial or concurrent filesystem mutation and TOCTOU are outside the
bounded threat model.

This headless evidence creates no screenshots and supplies no client visual
proof. It does not prove a second JVM or restart persistence, multiplayer, the
six deferred recipe/advancement resources, creative-tab interaction, client
rendering/gameplay, the complete authoritative registry, a complete port, or
release readiness. Its immutable archive proves capture-time Loom-userdev
observations and payload integrity only; it does not compare or
cryptographically bind later sources or rebuilt JARs. Current static artifact
checks are a separate proof boundary.

## Historical metal-block-registry dedicated server (v13)

- Profile: `etherology-e2e-forge-server-1.20.1-v13`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v13`
- Scenario: `metal-block-registry`
- Tracked profile manifest SHA-256:
  `c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`
- Tracked profile manifest size: `1196` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `8`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `188` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`

`SharedMetalBlocks` is the sole Common deferred block owner and
`SharedMetalBlockItems` is the sole Common deferred item owner for exactly:

```text
etherology:azel_block, etherology:ebony_block, etherology:ethril_block
```

The static gate verifies the exact declarations, attachment order, absence of
the former eager `DecoBlocks` fields, correct block-to-item map enrollment, and
packaged models, textures, English names, self-drop tables, compression and
decompression recipes, and selected tags across the Common and loader
artifacts. In the two tag files packaged by this bounded Forge slice—
`mineable/pickaxe` and `needs_iron_tool`—still-unported IDs use
`required: false`, while the three accepted metal-block IDs remain required.
`needs_stone_tool` is unchanged and outside this Forge resource slice. This
optionality fix prevents the partial Forge catalog from failing data load
without pretending the unported blocks exist.

The native server resolved every block as `net.minecraft.block.Block` and
every corresponding item as `net.minecraft.item.BlockItem`, with exact
block-item and `Block.asItem()` mappings. Azel had hardness `5`, blast
resistance `6`, and lapis-blue map color; ethril had hardness `3`, blast
resistance `6`, and gold map color; ebony had hardness `5`, blast resistance
`6`, and orange map color. All three used the metal sound group, required the
correct tool, had zero luminance, were opaque full cubes, had maximum item
count 64, were pickaxe-mineable, and required an iron-tier tool. Only ethril
and ebony were members of the beacon-base tag. Maximum-count stacks serialized
exactly the `id` and `Count` keys and round-tripped to the same items/counts.

The probe directly placed azel, ebony, and ethril at `8,200,8`, `9,200,8`, and
`10,200,8`. Those exact placed IDs, along with registry identities, mappings,
properties, selected tags, and stack NBT, remained stable after a real
`reload`. The report cumulatively re-proved the v11 material-item, v10 particle,
v7 enchantment, game-event/tag, loot-condition, and Ether-source contracts. The
exact lifecycle was `tags_updated_initial`, `server_started`,
`reload_requested`, `tags_updated_reload`, `reload_command_returned`,
`stop_requested`, `server_stopping`, and `server_stopped`. The world saved,
normal `stop(false)` completed, the launcher exited zero, and the copied log
passed the strict verifier scan.

Frozen file digests:

- `metal-block-registry-server-v13/archive-manifest.json`:
  `0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`
- `metal-block-registry-server-v13/reports/report.json`:
  `b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`
- `metal-block-registry-server-v13/reports/launcher-result.json`:
  `ef3c7b162687acff8d919292b482585aa6859918a186c133f96028d6114be54f`
- `metal-block-registry-server-v13/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `metal-block-registry-server-v13/logs/latest.log`:
  `f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_metal_block_evidence_v13.py \
  --archive docs/evidence/forge-1.20.1/metal-block-registry-server-v13
```

```text
Validated archived metal-block-registry for etherology-e2e-forge-server-1.20.1-v13: 188 assertions
Server log SHA-256: f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity` validates the
immutable archive and pinned v13 runner/verifier contract.
`validateForgeMetalBlockRegistryMilestone` combines it with current static,
bytecode, resource, bootstrap, artifact, and isolation checks. The archive
proves capture-time integrity; current-source or rebuilt-artifact equality
requires another isolated native run.

This headless evidence proves only exact registry/classes/mappings/properties,
selected tags, in-process stack NBT, direct server-world placement through
reload, and normal save/stop. It creates no screenshots and does not prove a
second JVM or restart persistence, player `/give` or player placement,
mining/drop behavior, beacon activation, recipe execution, creative-tab
interaction, client rendering, the full authoritative registry, a complete
port, or release readiness.

The v12 profile was consumed by a failed diagnostic launch that exposed
required unported references in the packaged `mineable/pickaxe` and
`needs_iron_tool` files. It was not sealed or accepted as an archive.
`needs_stone_tool` remains unchanged and outside this Forge resource slice.
The optional-reference fix was followed by the separate fresh v13 run
documented above.

## Historical material-item-registry dedicated server (v11)

- Profile: `etherology-e2e-forge-server-1.20.1-v11`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v11`
- Scenario: `material-item-registry`
- Tracked profile manifest SHA-256:
  `63ee2c8707f276cc87df2e0b162b2f3174e1fe1b3d689b26135298154ed1b171`
- Tracked profile manifest size: `1200` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `7`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `163` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`

`SharedMaterialItems` is the sole Common deferred owner of exactly these 14
behavior-free item IDs:

```text
etherology:attrahite_brick, etherology:azel_ingot,
etherology:azel_nugget, etherology:binder, etherology:ebony,
etherology:ebony_ingot, etherology:ebony_nugget,
etherology:enriched_attrahite, etherology:etheroscope,
etherology:ethril_ingot, etherology:ethril_nugget, etherology:raw_azel,
etherology:resonating_wand, etherology:thuja_oil
```

The static gate proves one exact Common owner across the Common JAR, both
loader-transformed Common JARs, the Fabric development and remapped production
JARs, and the Forge shadow JAR. It checks exact supplier fields and registration
IDs, no eager `Registry.register` or supplier `get`, one attachment from each
loader, removal of the former eager `EItems` and `DecoBlockItems` fields, and
byte-exact packaged models and textures plus exact English names.

The native server resolved every entry as `net.minecraft.item.Item`. It
required maximum count 16 for `etherology:enriched_attrahite` and 64 for the
other 13. A maximum-count stack for each entry serialized to the exact
Etherology ID and count with exactly the `Count` and `id` NBT keys, then
round-tripped to the same item and count. Those registry properties and NBT
observations matched after initial server-data load, at `ServerStartedEvent`,
and after a real `reload`. The report cumulatively re-proved the accepted v10
particle, v7 enchantment, game-event/tag, loot-condition, and Ether-source
contracts. The exact lifecycle was `tags_updated_initial`, `server_started`,
`reload_requested`, `tags_updated_reload`, `reload_command_returned`,
`stop_requested`, `server_stopping`, and `server_stopped`. The world saved,
normal `stop(false)` completed, the launcher exited zero, and the copied log
contains no fatal or forbidden client-startup marker.

Frozen file digests:

- `material-item-registry-server-v11/archive-manifest.json`:
  `9f5ff60298d9066e92c3f3e8ff6e5ab97fba5b35369f6ed09e1359bed7afe347`
- `material-item-registry-server-v11/reports/report.json`:
  `f3cc85b8514704f6c789e5abbdb835ede8aea062f5bae5b7ade0ab20da26bd4f`
- `material-item-registry-server-v11/reports/launcher-result.json`:
  `152d4ea3d0c3405650b16b8a85b1be00daa34e8ca84c4b7a4c21d25b7f82e74a`
- `material-item-registry-server-v11/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `material-item-registry-server-v11/logs/latest.log`:
  `ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_material_item_evidence_v11.py \
  --archive docs/evidence/forge-1.20.1/material-item-registry-server-v11
```

```text
Validated archived material-item-registry for etherology-e2e-forge-server-1.20.1-v11: 163 assertions
Server log SHA-256: ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeMaterialItemRegistryServerEvidenceArchiveIntegrity` validates
the immutable archive and pinned v11 runner/verifier contract.
`validateForgeMaterialItemRegistryMilestone` combines it with current static,
bytecode, resource, bootstrap, artifact, and isolation checks. The archive
proves capture-time integrity; current-source or rebuilt-artifact equality
requires another isolated native run.

This headless evidence is limited to registry properties and in-process
`ItemStack` NBT round-trip/reload stability. It creates no screenshots, does
not give items to a player with `/give`, and does not launch a second JVM or
restart the server. It does not prove Forge fuel registration, creative-tab
placement, recipes, client rendering/gameplay, the full authoritative registry,
or release readiness.

## Historical particle-registry dedicated server (v10)

- Profile: `etherology-e2e-forge-server-1.20.1-v10`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v10`
- Scenario: `particle-registry`
- Tracked profile manifest SHA-256:
  `5b3def0df2aacfea5db04b92975925c25223f117941ceb576cc6b3e6616f14e4`
- Tracked profile manifest size: `1190` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `6`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `138` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`

`SharedParticleTypes` is the sole Common deferred owner of exactly these 22
particle-type IDs:

```text
light, steam, spark, electricity1, electricity2, item, rising, vital,
shockwave, glint_particle, energy_absorption, armillary_sphere, haze, alchemy,
ether_star, ether_dot, resonation, lightning_bolt, scalable_sweep,
redstone_flash, redstone_stream, seal
```

The native server resolved that exact Etherology registry membership and no
capture error. It checked the canonical `simple`, `moving`, `electricity`,
`item`, `light`, `scalable`, `seal`, and `spark` payload families, their
concrete particle types, `shouldAlwaysSpawn = false`, codecs, parameter
factories, sample effect/type identities, command strings, packet round trips,
codec round trips, and the exact seal order, colors, and texture identifiers.
The item command parser round-tripped `minecraft:diamond`; the port uses
`Identifier.fromCommandInput` so the namespace separator is consumed instead
of truncating the identifier before `:`.

The registry and wire observations matched after the initial server-data load,
at `ServerStartedEvent`, and after a real `reload`. The cumulative report also
reproved the accepted enchantment, game-event/tag, loot-condition, and
Ether-source contracts. The exact lifecycle was `tags_updated_initial`,
`server_started`, `reload_requested`, `tags_updated_reload`,
`reload_command_returned`, `stop_requested`, `server_stopping`, and
`server_stopped`. The world saved, normal `stop(false)` completed, the launcher
exited zero, and the copied log contains no `ERROR`, `FATAL`, or client-startup
marker.

Frozen file digests:

- `particle-registry-server-v10/archive-manifest.json`:
  `29fddf549c4b8728911fe1d048816d0353256ef7a7e533b62d0461249c485ed1`
- `particle-registry-server-v10/reports/report.json`:
  `ab829d182e648385f6052fea469bef3a18a6a972f0baa98be6b83569897f3d75`
- `particle-registry-server-v10/reports/launcher-result.json`:
  `5e5fa2e942b271eac607efd3dd6a8c5f4c8a7f22fd6912fa3952ef9a8801eec3`
- `particle-registry-server-v10/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `particle-registry-server-v10/logs/latest.log`:
  `44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_particle_evidence_v10.py \
  --archive docs/evidence/forge-1.20.1/particle-registry-server-v10
```

```text
Validated archived particle-registry for etherology-e2e-forge-server-1.20.1-v10: 138 assertions
Server log SHA-256: 44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeParticleRegistryServerEvidenceArchiveIntegrity` validates this
immutable archive and its pinned v10 runner/verifier contract.
`validateForgeParticleRegistryMilestone` combines it with the current Common,
Fabric, Forge, bytecode, artifact, bootstrap, and isolation checks. The archive
proves its capture-time observations and payload integrity; it does not by
itself prove identity with later source or rebuilt artifacts.

This is a headless registry and wire-format milestone. It neither installs nor
exercises Forge client particle factories or renderers, and it produces no
screenshots. Native visual behavior, particle-emitting mechanics, full client
parity, the remaining authoritative registry spine, and release readiness stay
behind later client and gameplay evidence.

## Historical enchantment-registry dedicated server (v7)

- Profile: `etherology-e2e-forge-server-1.20.1-v7`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v7`
- Scenario: `enchantment-registry`
- Tracked profile manifest SHA-256:
  `36b0f67d7ef55cd8e34aac92dd4e5866e17d5be4ffa91987793c913dd60f5773`
- Tracked profile manifest size: `1194` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `5`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `95` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`

Common is the sole declaration and implementation owner of the exact
`etherology:peal` and `etherology:reflection` enchantment registry entries.
The native server observed `PealEnchantment` with maximum level 3, minimum
powers `[1, 12, 23]`, and maximum powers `[21, 32, 43]`. It observed
`ReflectionEnchantment` with maximum level 1, minimum power `[1]`, and maximum
power `[21]`. Those were the exact and only Etherology enchantment IDs. Both,
and only those two Etherology enchantments, belonged to the singular vanilla
tag `minecraft:non_treasure`.

The registry IDs, concrete classes, level/power properties, and tag membership
were identical at the initial server-data load and `ServerStartedEvent`. The
run then enabled the isolated Ether-source probe pack and executed the real
`reload` command. Enchantment registry identity, properties, and tag membership
all remained stable after reload. The cumulative report also retains the
accepted game-event registry and listening tags, loot-condition registry and
evaluated behavior, and exact initial/reloaded Ether-source maps from the v6
contract.

The exact lifecycle was `tags_updated_initial`, `server_started`,
`reload_requested`, `tags_updated_reload`, `reload_command_returned`,
`stop_requested`, `server_stopping`, and `server_stopped`. The run saved the
world, completed normal `stop(false)`, and exited with code zero. Its copied
server log contains no `ERROR`, `FATAL`, or client-startup marker. This is a
headless dedicated-server proof, so screenshots are neither produced nor
required.

Frozen file digests:

- `enchantment-registry-server-v7/archive-manifest.json`:
  `377acc9241417a169ac2f9dbe1f555d5918509a486daf40d89533de4d414feec`
- `enchantment-registry-server-v7/reports/report.json`:
  `1f5209c53fab524db662e7e7ef8ba044ba773fc9800dc3d3086e840093dc5aef`
- `enchantment-registry-server-v7/reports/launcher-result.json`:
  `36999d3af873282ef9e6c17b3d74713239f1d6a95288edcce51874a242b1c827`
- `enchantment-registry-server-v7/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `enchantment-registry-server-v7/logs/latest.log`:
  `b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_enchantment_evidence_v7.py \
  --archive docs/evidence/forge-1.20.1/enchantment-registry-server-v7
```

```text
Validated archived enchantment-registry for etherology-e2e-forge-server-1.20.1-v7: 95 assertions
Server log SHA-256: b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity` validates this
immutable archive and its pinned v7 runner/verifier contract.
`validateForgeEnchantmentRegistryMilestone` combines it with current Common,
Fabric, Forge, resource, artifact, and isolation checks. The archive proves its
capture-time observations and payload integrity; it does not by itself prove
identity with later source or rebuilt artifacts.

This bounded proof does not establish enchanting-table or item applicability,
Peal shockwave behavior, projectile reflection, native client visuals or
screenshots, full combat parity, the remaining authoritative registry spine,
or release readiness. Those mechanics require their own fresh native-client or
gameplay evidence.

## Historical Ether-source reload dedicated server (v6)

- Profile: `etherology-e2e-forge-server-1.20.1-v6`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v6`
- Scenario: `ether-source-reload`
- Tracked profile manifest SHA-256:
  `2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0`
- Tracked profile manifest size: `1192` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `4`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `72` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`

Common is the sole implementation and resource owner of
`ru.feytox.etherology.data.ethersource.EtherSourceLoader` and the default
`ether_sources` data. The initial server-data load produced exactly 23 entries.
That map included the corrected `etherology:primoshard_rella = 4`, excluded the
legacy misspelling, and assigned `minecraft:redstone = 2`. The run then wrote
and enabled its isolated probe data pack and executed the real `reload` command.
The completed reload produced exactly 24 entries: the pack overrode
`minecraft:redstone` to `9.5`, added `minecraft:diamond = 13`, and retained the
corrected `etherology:primoshard_rella = 4` entry.

Across reload, the sole `etherology:etherology_resonance` game event and its
exact `minecraft:vibrations` and `minecraft:warden_can_listen` tag membership
remained stable. The sole
`etherology:random_chance_with_fortune` condition registry entry, serializer,
and evaluated empty-tool/Fortune-I behavior also remained stable. Minecraft
correctly replaced the probe `LootTable` instance during the reload; the report
asserts that replacement independently instead of treating object identity as
behavioral stability.

The exact lifecycle was `tags_updated_initial`, `server_started`,
`reload_requested`, `tags_updated_reload`, `reload_command_returned`,
`stop_requested`, `server_stopping`, and `server_stopped`. The run saved the
world, completed normal `stop(false)`, and exited with code zero. Its copied
server log contains no `ERROR` or `FATAL` marker. The isolated probe-only
terminator handles the known Loom-userdev non-daemon thread leak only after the
stopped-event server thread ends and the report is published; production
Etherology contains no such exit path. This is a headless dedicated-server
proof, so it produces no screenshots.

Frozen file digests:

- `ether-source-reload-server-v6/archive-manifest.json`:
  `6d552536f74c018ce56e238fcb5a3aacd8fa363c76293863514adf9d7bafc2e0`
- `ether-source-reload-server-v6/reports/report.json`:
  `bfdf0a50b8bc1629e3001acb7d3c28a2ed6f61a14d585ab7374c8f85c02e5987`
- `ether-source-reload-server-v6/reports/launcher-result.json`:
  `7986e97798def000a1397e010a2b63b0ad5dbc131d1ee93699ed98574ee0bd78`
- `ether-source-reload-server-v6/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `ether-source-reload-server-v6/logs/latest.log`:
  `0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_reload_evidence_v6.py \
  --archive docs/evidence/forge-1.20.1/ether-source-reload-server-v6
```

```text
Validated archived ether-source-reload for etherology-e2e-forge-server-1.20.1-v6: 72 assertions
Server log SHA-256: 0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity` validates this
immutable archive and its v6 runner/verifier contract.
`validateForgeEtherSourceReloadMilestone` combines that proof with the exact
current Common, Fabric, and Forge listener, default-resource, and artifact
checks. The archive itself proves capture-time observations and payload
integrity, not identity with later sources or rebuilt artifacts.

This bounded record does not prove furnace or machine consumption, the wider
Ether network, the full authoritative registry, native sound playback, Forge
custom sculk-frequency behavior, Attrahite drops, or release readiness. It is
retained as immutable historical proof; the cumulative v7 enchantment-registry
section remains its successor, and the cumulative v10 particle-registry section
is the current dedicated-server proof.

## Historical registry-foundation dedicated server (v4)

- Profile: `etherology-e2e-forge-server-1.20.1-v4`
- Runtime directory:
  `scripts/e2e/.state/runtimes/etherology-e2e-forge-server-1.20.1-v4`
- Scenario: `registry-foundation`
- Tracked profile manifest SHA-256:
  `c479fd833cae80e0a6446d40e2728d7ecaabd82e4d9657698cfb3a32fdd8bbb5`
- Tracked profile manifest size: `1192` bytes
- Minecraft: `1.20.1`
- Forge: `47.4.9`
- Runtime Java: `17`
- Distribution: `DEDICATED_SERVER`
- Execution: fresh repository-owned Loom-userdev dedicated server
- Named task: `:forge:1.20.1:runRegistryFoundationServerProbe`
- Report schema: `2`; archive-manifest schema: `1`
- Report status: `passed`
- Assertions: `39` passed, `0` failed
- Launcher exit code: `0`; timed out: `false`
- Copied server-log SHA-256:
  `085332dc956ea75327d820fc398e122779185c173b6f8002d9962862c9feaea2`

The server loaded the exact sorted mod-ID inventory `architectury`,
`etherology`, `etherology_e2e_server_probe`, `forge`, `geckolib`,
`generated_a788a0c`, and `minecraft`. It contained none of the seven forbidden
client or foreign-mod IDs. The probe found exactly one Etherology game event,
`etherology:etherology_resonance`, with internal ID
`etherology_resonance` and range 16. One `SERVER_DATA_LOAD` static tag update
bound it to exactly `minecraft:vibrations` and
`minecraft:warden_can_listen`; the registry and tag state remained identical at
`ServerStartedEvent`.

The probe also found exactly one Etherology loot-condition type,
`etherology:random_chance_with_fortune`, backed by
`ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer`.
The synthetic `etherology_e2e_server_probe:registry_foundation` table returned
the exact sorted outputs `[minecraft:gold_ingot, minecraft:stone]` for an empty
tool and `[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` for
Fortune I. Its three pools exercise chance `1`, chance `0` plus Fortune
multiplier `1`, and mixed chance `0.99` plus multiplier `0.01`. The condition,
serializer, table, and results remained identical at server start.

The run saved the world, requested normal `stop(false)`, traversed
`tags_updated`, `server_started`, `server_stopping`, and `server_stopped`, and
exited with code zero. Its copied server log contains no `ERROR` or `FATAL`
marker. The isolated probe-only terminator handles the known Loom-userdev
non-daemon thread leak only after the stopped-event server thread ends and the
report is published; production Etherology contains no such exit path. This is
a headless registry/data-pack proof, so it produces no screenshots.

Frozen file digests:

- `registry-foundation-server-v4/archive-manifest.json`:
  `38d7570cbc839c26154c276869d3e537771425c674a9db743b3521cad115b652`
- `registry-foundation-server-v4/reports/report.json`:
  `e75c303bdf614c2d9b471b779de6c807ae6502accea213802454576ca2209bfc`
- `registry-foundation-server-v4/reports/launcher-result.json`:
  `37666c4a8e91bf8edaa859b54814300af0a7c81876b55aa5092189df97bc49b0`
- `registry-foundation-server-v4/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `registry-foundation-server-v4/logs/latest.log`:
  `085332dc956ea75327d820fc398e122779185c173b6f8002d9962862c9feaea2`

Validate the five-file archive without the ignored live runtime:

```bash
python3 -B scripts/e2e/forge_server_evidence.py \
  --archive docs/evidence/forge-1.20.1/registry-foundation-server-v4
```

```text
Validated archived registry-foundation for etherology-e2e-forge-server-1.20.1-v4: 39 assertions
Server log SHA-256: 085332dc956ea75327d820fc398e122779185c173b6f8002d9962862c9feaea2
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

`validateForgeRegistryFoundationServerEvidenceArchiveIntegrity` runs the 63
Python runner/verifier safety tests and validates this immutable archive.
`validateForgeGameEventRegistryMilestone` and
`validateForgeLootConditionRegistryMilestone` separately inspect current
Common, Fabric, and Forge artifacts. `validateForgeRegistryFoundationMilestone`
requires those current-artifact gates, probe isolation checks, and archive
integrity. Each proof covers a distinct boundary; this archive proves its
capture-time observations and payload integrity, not identity with later builds.

The canonical Attrahite resource remains Fabric-only because its items are not
ported. The synthetic table proves the shared condition and serializer, not
Attrahite gameplay or drop parity. Native sound playback, Forge's unsupported
custom sculk frequency, the full authoritative registry spine, and the broader
gameplay/native E2E matrix remain deferred. The Fabric `v21` Phase 0 archive
exercises the packaged client artifact at the shared material-item checkpoint,
but its 42 baseline assertions do not directly test the 14 material IDs or
their gameplay consumers. It predates the metal-block rebuild and is not
current client rendering evidence for those blocks. Fabric `v20` remains an
immutable historical archive.

## Historical shared game-event dedicated server (v2)

This immutable game-event-only archive records the earlier accepted checkpoint.
It remains historical evidence, and v4 superseded it as the
registry-foundation proof. The v14 food-item archive is the current
cumulative dedicated-server proof. No current acceptance task or verifier
treats v2 as the active archive.

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

The capture-time verifier recorded:

```text
Validated archived game-event-registry for etherology-e2e-forge-server-1.20.1-v2: 31 assertions
Server log SHA-256: 89988125b90a78c9f996487c345d1efc70302340c1e08cb449b8a826aef24394
Archive integrity only: current sources and rebuilt artifacts were not compared.
```

The v2 files remain frozen for historical inspection. The historical
registry-foundation verifier is intentionally pinned to v4 and must not be
used to recertify v2 as current proof. The archive itself does not claim
current-source or rebuilt-artifact identity.

This accepts only the shared resonance declaration, its two listening tags,
and dedicated-server registry/data-load lifecycle. Fabric's supported custom
sculk frequency 10 is covered statically, while Forge 47 has no supported
equivalent and remains deferred. The proof does not cover sound playback, the
full registry-ID manifest, every catalog entry's placement/save behavior, or
the remaining gameplay and native E2E matrix. The Forge release gate remains
closed on the broader authoritative registry spine. The v4 section above is
the superseding registry-foundation proof; v6 is the historical Ether-source
reload proof, v7 is the historical enchantment-registry proof, and v10 is the
historical particle-registry proof. The v11 material-item archive is the
older material-item predecessor, v13 is the immediate historical metal-block
predecessor, and v14 is the current cumulative food-item-registry proof.
