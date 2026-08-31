# End-to-end test contract

Etherology uses two explicit E2E evidence modes. Packaged-client scenarios launch
Etherology's exact staged production JAR and required runtime libraries in a real
client; their automation is a separate client-only JAR that must never enter a
published mod. Headless Forge registry scenarios launch checked-out production
source-set output through the pinned Loom-userdev dedicated-server task plus a
separate server-only probe that must never enter the production artifact. Every
scenario uses a fresh repository-owned runtime, and no architecture-reference
project code or JAR is built, launched, copied into a profile, or treated as an
E2E target.

## Target matrix

| Lane | Minecraft | Loader | Runtime Java |
|---|---|---|---:|
| `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.17.3 | 17 |
| `forge-1.20.1` | 1.20.1 | Forge 47.4.9 | 17 |

Both lanes target the same complete mechanic contract. Loader-specific setup is
allowed; loader-specific omissions cannot satisfy lane-wide readiness. The
transitional Forge lane may freeze a named bounded vertical while the rest of
its mechanic matrix remains explicitly release-blocking.

A bounded scenario may be frozen while a loader port is still incomplete, but it
accepts only that named vertical. It cannot satisfy the lane-wide scenario matrix
or remove a later release gate.

The Fabric runtime lock uses Trinkets' remapped Modrinth release JAR. Its Maven
artifact is valid for Loom development dependencies but is not staged directly in
the game profile because its `LivingEntityMixin` shadow remains in the named
namespace.

These are the current foundation lanes. Every later branch in the global support
catalog inherits the same Etherology mechanic contract with that branch's Fabric
and NeoForge runtime pins.

## Harness dispatch contract

The client harness reads the JVM property `etherology.e2e.scenario` once during
Fabric client initialization and constructs exactly one scenario controller. An
absent property selects `phase0-smoke`; explicit values must match a registered
scenario id exactly. Empty, whitespace-padded, and unknown values fail before any
tick or screen callback is registered.

The controller is the sole owner of Fabric end-client-tick and after-screen-init
subscriptions. It delegates those observations, plus the completed game-render
callback supplied by the client-only mixin, to the selected scenario. Individual
scenarios own their readiness state machine and any screen-specific after-render
subscription. Adding another scenario therefore extends the dispatcher without
adding another Fabric entrypoint or globally competing callbacks.

This dispatcher layer does not alter evidence. The selected scenario id remains
the report and evidence-directory identity, and `phase0-smoke` retains its existing
report schema, assertions, screenshot names, report-first publication, and
`done.marker`-last contract.

The Forge 1.20.1 harness uses the same one-controller rule in its isolated v11
profile. Its exact ordered scenario inventory is `ethereal-storage`, then
`ethereal-channel`; an absent property defaults to `ethereal-storage`. An explicit
id must match one of those two values without whitespace normalization.

## Packaged-client evidence lifecycle

1. Build and stage the production and E2E JARs.
2. Record and verify the SHA-256 of both JARs before launch. Loader-specific
   profiles must also bind their runtime marker and report to the exact tracked
   profile-manifest bytes used for capture.
3. Install a real client in a new disposable runtime directory. Install a dedicated
   server there as well when the scenario contract requires it.
4. Put the production JAR on every game process; put the harness only on clients.
5. Start an integrated world for single-client mechanics, or launch the offline
   dedicated server and connect the required clients for multiplayer mechanics.
6. Drive each scenario from client ticks, server-thread actions, and real readiness
   predicates.
7. Wait for the scenario-declared number of completed render callbacks, never
   fewer than two, before capturing the framebuffer.
8. Assert programmatic state, force-save persistent state, and write the scenario
   report.
9. Write `done.marker` last, shut down normally, terminate any remaining owned
   process groups, and freeze evidence.
10. Validate dimensions, hashes, blank-image probes, logs, crashes, step order,
    world lifecycle, and production-JAR identity before declaring the lane
    successful.

## Headless Loom-userdev evidence lifecycle

1. Build and test the production source sets and separate server probe, then
   prove that probe classes are absent from production artifacts.
2. Bind the exact tracked profile-manifest bytes, Minecraft/Forge/Java pins,
   named Loom task, required mod IDs, and forbidden client/foreign mod IDs.
3. Provision exactly one new repository-owned runtime. Never adopt, clean, or
   replace a prior capture directory.
4. Launch the offline dedicated server from the checked-out source-set output;
   no client harness or staged production JAR is part of this evidence mode.
5. Drive the scenario on server lifecycle events and commands, capture registry
   state at its declared checkpoints, and force-save the world.
6. Atomically publish the report, validate and copy the server log, publish the
   launcher result, then write `done.marker` last after normal shutdown.
7. Reject assertion, lifecycle, timeout, crash, fatal-log, forbidden mod/class,
   saved-world, publication-order, or profile-binding failures. Headless
   scenarios neither wait for render callbacks nor create screenshots.

## Standard scenarios

| Scenario | Required proof |
|---|---|
| `phase0-smoke` | Registry boot, non-negative block-state network IDs, creative inventory population, integrated-world fixture and save, no fatal log markers |
| `progression-oculus` | Oculus inspection and Empowerment Table recipe interaction |
| `seals-aspects` | Four seal identities, revelation rendering, detector output, sediment attraction |
| `golden-forest` | Biome resolves and its palette, peach vegetation, light plants, and pedestals render |
| `alchemy` | Cauldron heat/fill/stir/synthesis states and corruption feedback |
| `ether-network` | Fresh integrated-world Spinner, directional channels, Ethereal Storage, redstone gate, and Levitator fixture held in two forced ticking chunks; exact-one-unit Spinner pause before activation; real generation/transport/consumption; gravity-enabled stable-UUID vanilla armor stand on a supported force track across chunk reloads; bounded live server/client diagnostic history with lost-unit fail-fast; server/client mirroring; five block-entity NBT reconstructions; before/after native captures; forced-save retention |
| `staff-lenses` | Staff customization, radial selection, and Flow/Charge Redstone Lens effects |
| `spiritual-energy` | Depletion, devastation, corruption HUD, health loss, and recovery |
| `armillary` | Ordered aspect preview, drain, ingredient consumption, cancellation, and result |
| `storage-utilities` | Fresh integrated-world crate, shelf, spill-barrel, and tuning-fork fixture; real screen/state/inventory interactions; client mirroring; block-entity NBT reconstruction; before/after native captures; forced-save retention |
| `combat-equipment` | Two-handed suppression, knockback, shield reflection, and armor attributes |
| `persistence` | Player, item, chunk, machine, and research data survive save/restart |
| `multiplayer-sync` | Two clients observe identical seals, machines, casts, equipment, and player state |
| `metal-block-registry` | Exact azel, ethril, and ebony block IDs; nine packaged render resources; direct server-thread placement and integrated-client mirroring at three fixed positions; fixed first-person camera; empty-display and placed-block native 1920×1080 captures after 120 consecutive exact render-ready frames each; forced save and normal shutdown. This bounded visual contract does not prove player placement, creative-tab interaction, mining, drops, beacon behavior, recipes, restart persistence, or multiplayer. |

## Loader-specific bounded scenarios

These scenarios record an accepted vertical during a transitional port. They do
not replace any standard scenario or establish complete loader readiness.

### Packaged-client bounded scenarios

| Scenario | Lane | Required proof |
|---|---|---|
| `ethereal-storage` | `forge-1.20.1` | Bounded Ethereal Storage vertical in a fresh integrated world: four-slot block entity and NBT reconstruction; per-Glint Ether transfer with exact-total conservation; Forge item-handler access on the unsided view and all six faces with simulated/live insertion, blocked extraction, and hidden display slot; viewer open/close and synchronized Gecko animation; native menu; closed/open/closed-again plus pre-restart and post-restart menu captures; forced save, full disconnect/restart, exact Ether distribution, ordered input inventory, display state, block-entity type, and menu reopen. This scenario does not satisfy `ether-network`, general `persistence`, multiplayer, or full-loader readiness. |
| `ethereal-channel` | `forge-1.20.1` | Bounded directed-channel vertical in a fresh integrated world and forced fixture chunk: exact channel/storage block and block-entity ids at non-overlapping fixture positions; redstone-fed powered repeaters provide exact strong power from the full solid arena floor while temporary support blocks are replaced by channels, then the first scheduled server tick resolves ACTIVATED with source-above `up=in` and target-east `east=out` without any manual neighbor refresh; an independent vanilla wall lever remains attached to a channel after natural propagation, reports `canPlaceAt=true`, produces the exact `north=in`, `east=out`, `west=empty`, cross topology, mirrors to the client, and survives save/restart; a strongly powered channel receives and retains exactly one Ether without forwarding; natural power removal transfers exactly one Ether on the fifth-tick cadence with total conservation and no reverse motion; a missing output evaporates exactly 0.2 Ether and exposes then naturally clears its evaporation flags after natural reactivation; channel NBT reconstruction and client sync; gated, transferred, and reopened 1920×1080 captures after 120 consecutive exact client/terrain-ready frames at the fixed first-person camera pose, with capture-time mirror, renderer, and camera assertions; forced save, full disconnect/restart, exact server state, and exact block-entity types. Storage Ether distribution is a server oracle because the storage foundation has no client update packet. This scenario does not satisfy the complete `ether-network`, general `persistence`, multiplayer, or full-loader readiness contracts. |

### Headless dedicated-server bounded scenarios

| Scenario | Lane | Required proof |
|---|---|---|
| `ether-source-reload` | `forge-1.20.1` | Bounded dedicated-server data-reload vertical in a fresh repository-owned Loom-userdev profile: Common is the sole listener/default-data owner; exact initial 23-entry map with corrected `etherology:primoshard_rella = 4` and `minecraft:redstone = 2`; a real `reload` command with an enabled probe pack; exact reloaded 24-entry map with `minecraft:redstone = 9.5` and added `minecraft:diamond = 13`; stable game-event registry and tags; stable loot-condition registry and evaluated behavior while the probe `LootTable` instance is replaced; world save, normal stop, exit code zero, and no `ERROR` or `FATAL` marker. This headless scenario requires no screenshots and does not satisfy furnace/machine consumption, the wider Ether network, the full authoritative registry, sound playback, Forge custom sculk frequency, Attrahite drops, or release readiness. |
| `enchantment-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: Common owns exactly `etherology:peal` and `etherology:reflection` through `ru.feytox.etherology.registry.misc.PealEnchantment` and `ru.feytox.etherology.registry.misc.ReflectionEnchantment`; Peal has maximum level 3, minimum powers `1, 12, 23`, and maximum powers `21, 32, 43`; Reflection has maximum level 1, minimum power `1`, and maximum power `21`; both are the only Etherology entries in the singular `minecraft:non_treasure` tag; exact registry identities, properties, and tag membership remain stable at server start and through a real `reload`; the previously accepted game-event, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and does not prove enchantment applicability, Peal shockwaves, projectile reflection, client visuals, complete combat parity, the full authoritative registry, or release readiness. |
| `particle-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: `SharedParticleTypes` owns the exact 22 canonical IDs; exact type classes, eight payload families, `shouldAlwaysSpawn = false`, codecs, parameter factories, sample command strings, packet/codec round trips, and seal order/colors/textures are captured without error and remain stable at server start and through a real `reload`; the item parser accepts namespaced IDs; all earlier enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and does not install or exercise Forge client particle factories/renderers, emitted visuals, gameplay consumers, the full authoritative registry, or release readiness. |
| `material-item-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: `SharedMaterialItems` owns exactly `etheroscope`, `thuja_oil`, `azel_ingot`, `azel_nugget`, `ethril_ingot`, `ethril_nugget`, `ebony_ingot`, `ebony_nugget`, `enriched_attrahite`, `raw_azel`, `attrahite_brick`, `binder`, `ebony`, and `resonating_wand`; all resolve to vanilla `Item`, `enriched_attrahite` has maximum count 16, and the other 13 have maximum count 64; exact `ItemStack` NBT ID/count/key round trips and deterministic save representations remain stable at server start and through a real `reload`; all historical particle, enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and proves registry properties and in-process `ItemStack` NBT round-trip/reload evidence only. It does not execute player `/give`, a second JVM or restart, Forge fuel registration, creative-tab placement, recipes, client gameplay, the full authoritative registry, or release readiness. |
| `metal-block-registry` | `forge-1.20.1` | Current cumulative dedicated-server registry/reload proof in a fresh repository-owned Loom-userdev profile: `SharedMetalBlocks` and `SharedMetalBlockItems` own exactly `azel_block`, `ethril_block`, and `ebony_block` as vanilla `Block` instances and mapped `BlockItem`s; exact runtime classes/mappings, vanilla-copy properties, selected pickaxe/iron-tool/beacon tags, and maximum-count stack NBT remain stable at server start and through a real `reload`; the three blocks are placed directly at bounded server-world positions and the exact placed IDs remain stable through reload; all historical material-item, particle, enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots. It does not prove a second JVM or restart persistence, player `/give` or player placement, mining/drop behavior, beacon activation, recipe execution, creative-tab interaction, client rendering, the full authoritative registry, or release readiness. |

The current accepted cumulative record is `metal-block-registry` report
schema 8 with 188 of 188 passing assertions in
`docs/evidence/forge-1.20.1/metal-block-registry-server-v13`. It binds
profile-manifest SHA-256
`c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`,
report SHA-256
`b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`,
server-log SHA-256
`f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`,
and archive-manifest SHA-256
`0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`.
The v6 `ether-source-reload`, v7 `enchantment-registry`, and v10
`particle-registry` archives remain immutable historical evidence. The v11
`material-item-registry` archive is the immediate historical predecessor; all
accepted states are included in and superseded by the cumulative v13 runtime
proof. The v12 metal-block profile was consumed by a failed diagnostic tag-load
run and has no accepted archive. In the two tag files packaged by this bounded
Forge slice—`mineable/pickaxe` and `needs_iron_tool`—still-unported IDs were
then made optional before the fresh v13 capture; `needs_stone_tool` remains
unchanged and outside the slice. This Loom-userdev record captures observations
from an execution of the then-checked-out source set; it binds neither exact
source bytes nor a packaged Forge JAR. Frozen archives prove capture-time
integrity; current-source or rebuilt-artifact identity still requires a new
isolated native run.

## Screenshot contract

- Captures are composed Minecraft framebuffers, not operating-system crops.
- The packaged E2E suite captures at 1920×1080.
- On the baseline Mac, a 960×540 logical GLFW window produces that 1920×1080
  framebuffer at the display's 2× Retina scale.
- Each capture belongs to a scenario, role, and step through `report.json`; filenames
  are descriptive but not authoritative.
- A capture is required exactly when the scenario contract declares one.
- A headless dedicated-server scenario has no screenshot requirement when its
  bounded contract explicitly says so.
- At least one before/after pair per interactive mechanic must exceed a declared
  changed-pixel threshold.
- Curated original/Fabric/Forge comparisons are copied from disposable E2E output
  into the mechanic directories under `docs/baseline` and `docs/evidence`.

## Failure policy

The gate fails for any missing or unexpected step, assertion failure, unapproved
crash report, fatal mixin/access-widener/linkage/mod-loading log marker, or
evidence over the configured bound. A packaged-client scenario also fails for a
missing or blank screenshot, wrong dimensions, or a production JAR whose digest
differs from the staged release manifest. A headless Loom-userdev scenario
instead fails when its exact profile, source-set task, probe isolation, or
publication contract does not match.
