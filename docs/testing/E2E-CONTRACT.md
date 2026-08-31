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

The Forge 1.20.1 harness uses the same one-controller rule in its isolated v12
profile. Its exact ordered scenario inventory is `ethereal-storage`, then
`ethereal-channel`, then `forest-lantern`; an absent property defaults to
`ethereal-storage`. An explicit id must match one of those three values without
whitespace normalization.

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
   replace a prior capture directory. A sealed archive permanently consumes
   the profile identity: provisioning and environment checks must reject it
   even if its ignored runtime directory is later removed.
4. Launch the offline dedicated server from the checked-out source-set output.
   The raw Gradle launch task must receive the exact cryptographic token from
   the runner's lock, match the exact profile marker, and find pristine live
   evidence directories; a direct or stale invocation must fail before game
   launch.
5. The production source-set output is the only Etherology target;
   no client harness or staged production JAR is part of this evidence mode.
6. Drive the scenario on server lifecycle events and commands, capture registry
   state at its declared checkpoints, and force-save the world.
7. Atomically publish the report, validate and copy the server log, publish the
   launcher result, then write `done.marker` last after normal shutdown.
8. Reject assertion, lifecycle, timeout, crash, fatal-log, forbidden mod/class,
   saved-world, publication-order, or profile-binding failures. Headless
   scenarios neither wait for render callbacks nor create screenshots.

The sealed-archive check is durable for the normal runner flow. The run token,
lock, marker, and pristine-directory checks are accidental-misuse interlocks,
not provenance authentication; same-account adversarial or concurrent
filesystem mutation and TOCTOU are outside this bounded threat model.

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
| `forest-lantern` | Exact block/item mapping, age 0–4 and horizontal-facing state inventory, thirteen packaged render resources with canonical SHA-256 digests, cutout layer, baked models, luminance, and client/server state mirrors; one direction-correct four-row matrix simultaneously renders and mirrors all twenty age×facing states on alternating peach-log backing, with all sixteen age 0–3 states explicitly reported as forced; unsupported real BlockItem placement rejection and four supported mature real BlockItem placements facing north/east/south/west; empty, full state matrix, cumulative placement-facing, and reopened native 1920×1080 captures after 120 consecutive exact render-ready frames each; forced save, full disconnect/reopen, exact matrix and placement persistence, and normal shutdown. This bounded visual contract does not prove natural/random growth, bonemeal spreading, jump breaking, mining speed, loot execution, recipe execution, creative-tab interaction, or multiplayer. |

## Loader-specific bounded scenarios

These scenarios record an accepted vertical during a transitional port. They do
not replace any standard scenario or establish complete loader readiness.

### Packaged-client bounded scenarios

| Scenario | Lane | Required proof |
|---|---|---|
| `ethereal-storage` | `forge-1.20.1` | Bounded Ethereal Storage vertical in a fresh integrated world: four-slot block entity and NBT reconstruction; per-Glint Ether transfer with exact-total conservation; Forge item-handler access on the unsided view and all six faces with simulated/live insertion, blocked extraction, and hidden display slot; viewer open/close and synchronized Gecko animation; native menu; closed/open/closed-again plus pre-restart and post-restart menu captures; forced save, full disconnect/restart, exact Ether distribution, ordered input inventory, display state, block-entity type, and menu reopen. This scenario does not satisfy `ether-network`, general `persistence`, multiplayer, or full-loader readiness. |
| `ethereal-channel` | `forge-1.20.1` | Bounded directed-channel vertical in a fresh integrated world and forced fixture chunk: exact channel/storage block and block-entity ids at non-overlapping fixture positions; redstone-fed powered repeaters provide exact strong power from the full solid arena floor while temporary support blocks are replaced by channels, then the first scheduled server tick resolves ACTIVATED with source-above `up=in` and target-east `east=out` without any manual neighbor refresh; an independent vanilla wall lever remains attached to a channel after natural propagation, reports `canPlaceAt=true`, produces the exact `north=in`, `east=out`, `west=empty`, cross topology, mirrors to the client, and survives save/restart; a strongly powered channel receives and retains exactly one Ether without forwarding; natural power removal transfers exactly one Ether on the fifth-tick cadence with total conservation and no reverse motion; a missing output evaporates exactly 0.2 Ether and exposes then naturally clears its evaporation flags after natural reactivation; channel NBT reconstruction and client sync; gated, transferred, and reopened 1920×1080 captures after 120 consecutive exact client/terrain-ready frames at the fixed first-person camera pose, with capture-time mirror, renderer, and camera assertions; forced save, full disconnect/restart, exact server state, and exact block-entity types. Storage Ether distribution is a server oracle because the storage foundation has no client update packet. This scenario does not satisfy the complete `ether-network`, general `persistence`, multiplayer, or full-loader readiness contracts. |
| `forest-lantern` | `forge-1.20.1` | Bounded Forest Lantern packaged-client vertical in a fresh integrated world: exact block/item registry mapping, twenty age/facing states with non-negative raw ids, thirteen effective resources with canonical SHA-256 digests, baked models, Forge cutout layer, and luminance 8; sixteen forced age 0–3 × north/east/south/west states against deterministic vanilla log supports explicitly record `canPlaceAt=false` because the peach-log/Golden-Forest graph is deferred; an iron-bars placement is rejected and four supported mature age-4 placements use the real vanilla `BlockItem` path with exact north/east/south/west states and stack consumption, completing an exact twenty-state native fixture matrix; empty, stages, four cumulative-facing, and reopened 1920×1080 captures each require 120 consecutive exact server/client mirror, fixture-position render-readiness, and camera frames; the complete matrix is required before save and after a full disconnect/reopen with exact persistence. This scenario does not claim natural growth, bonemeal spreading, peach-log integration, jump breaking, mining speed, loot or recipe execution, creative-tab interaction, multiplayer, or full-loader readiness. |

### Headless dedicated-server bounded scenarios

| Scenario | Lane | Required proof |
|---|---|---|
| `ether-source-reload` | `forge-1.20.1` | Bounded dedicated-server data-reload vertical in a fresh repository-owned Loom-userdev profile: Common is the sole listener/default-data owner; exact initial 23-entry map with corrected `etherology:primoshard_rella = 4` and `minecraft:redstone = 2`; a real `reload` command with an enabled probe pack; exact reloaded 24-entry map with `minecraft:redstone = 9.5` and added `minecraft:diamond = 13`; stable game-event registry and tags; stable loot-condition registry and evaluated behavior while the probe `LootTable` instance is replaced; world save, normal stop, exit code zero, and no `ERROR` or `FATAL` marker. This headless scenario requires no screenshots and does not satisfy furnace/machine consumption, the wider Ether network, the full authoritative registry, sound playback, Forge custom sculk frequency, Attrahite drops, or release readiness. |
| `enchantment-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: Common owns exactly `etherology:peal` and `etherology:reflection` through `ru.feytox.etherology.registry.misc.PealEnchantment` and `ru.feytox.etherology.registry.misc.ReflectionEnchantment`; Peal has maximum level 3, minimum powers `1, 12, 23`, and maximum powers `21, 32, 43`; Reflection has maximum level 1, minimum power `1`, and maximum power `21`; both are the only Etherology entries in the singular `minecraft:non_treasure` tag; exact registry identities, properties, and tag membership remain stable at server start and through a real `reload`; the previously accepted game-event, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and does not prove enchantment applicability, Peal shockwaves, projectile reflection, client visuals, complete combat parity, the full authoritative registry, or release readiness. |
| `particle-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: `SharedParticleTypes` owns the exact 22 canonical IDs; exact type classes, eight payload families, `shouldAlwaysSpawn = false`, codecs, parameter factories, sample command strings, packet/codec round trips, and seal order/colors/textures are captured without error and remain stable at server start and through a real `reload`; the item parser accepts namespaced IDs; all earlier enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and does not install or exercise Forge client particle factories/renderers, emitted visuals, gameplay consumers, the full authoritative registry, or release readiness. |
| `material-item-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: `SharedMaterialItems` owns exactly `etheroscope`, `thuja_oil`, `azel_ingot`, `azel_nugget`, `ethril_ingot`, `ethril_nugget`, `ebony_ingot`, `ebony_nugget`, `enriched_attrahite`, `raw_azel`, `attrahite_brick`, `binder`, `ebony`, and `resonating_wand`; all resolve to vanilla `Item`, `enriched_attrahite` has maximum count 16, and the other 13 have maximum count 64; exact `ItemStack` NBT ID/count/key round trips and deterministic save representations remain stable at server start and through a real `reload`; all historical particle, enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots and proves registry properties and in-process `ItemStack` NBT round-trip/reload evidence only. It does not execute player `/give`, a second JVM or restart, Forge fuel registration, creative-tab placement, recipes, client gameplay, the full authoritative registry, or release readiness. |
| `metal-block-registry` | `forge-1.20.1` | Historical cumulative dedicated-server registry/reload predecessor in a fresh repository-owned Loom-userdev profile: `SharedMetalBlocks` and `SharedMetalBlockItems` own exactly `azel_block`, `ethril_block`, and `ebony_block` as vanilla `Block` instances and mapped `BlockItem`s; exact runtime classes/mappings, vanilla-copy properties, selected pickaxe/iron-tool/beacon tags, and maximum-count stack NBT remain stable at server start and through a real `reload`; the three blocks are placed directly at bounded server-world positions and the exact placed IDs remain stable through reload; all historical material-item, particle, enchantment, game-event/tag, loot-condition, and Ether-source assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots. It does not prove a second JVM or restart persistence, player `/give` or player placement, mining/drop behavior, beacon activation, recipe execution, creative-tab interaction, client rendering, the full authoritative registry, or release readiness. |
| `food-item-registry` | `forge-1.20.1` | Accepted historical cumulative dedicated-server registry/reload and native-consumption proof in a fresh repository-owned Loom-userdev profile: `SharedFoodItems` owns exactly the plain `etherology:forest_lantern_crumb` vanilla `Item`; its food component has hunger 3, saturation modifier 2.0, no always-edible behavior, no effects, and no recipe remainder; exact registry, class, food, stack, and save-representation state remains stable through a real `reload`; real `ServerPlayerEntity` instances `EtherFoodStart` and `EtherFoodReload` prove hunger `10 → 13`, saturation `0 → 12`, stack `2 → 1`, and retention of the same `ItemStack` instance after consumption; all v13 and earlier assertions remain cumulative; world save, normal stop, exit code zero, and no fatal or forbidden client-startup marker. This headless scenario requires no screenshots. The exact model, texture, and English/Russian names are checked statically; its then-deferred recipes and advancements are superseded by the prepared Forest Lantern contract. It does not prove a second JVM or restart persistence, multiplayer, client visuals, recipe execution, the full authoritative registry, or release readiness. |
| `forest-lantern` | `forge-1.20.1` | Prepared cumulative dedicated-server schema-10 proof in a fresh repository-owned Loom-userdev profile: exact shared block/item identity and mapping, mature north default, all twenty age×facing states with unique non-negative server network IDs and exact outline shapes, properties, hoe tag and deliberately empty peach-log tag; exact age-gated loot, four recipes and four recipe advancements loaded initially and after a real `reload`, including real recipe matching/crafting; real supported mature `BlockItem` placements in all four facings followed by support removal; a real grounded `ServerPlayerEntity` with shears proves mining speed `15.0` and the expected effective breaking delta across every one of the twenty states; separately seeded real player jumps prove the 40% callback's retain, single-callback guard, mature break and exact one-item drop paths using fresh players before and after reload; registry, states, tags, loaded data, and mechanics remain exact through reload; world save, normal stop, exit code zero, and no fatal, client-startup, or forbidden-class marker. This headless scenario requires no screenshots. Ages 0–3 cannot naturally attach while the peach-log/Golden-Forest graph is deferred, so natural growth and bonemeal spreading remain outside this bounded proof; it also does not prove a second JVM, multiplayer, client visuals, creative-tab interaction, the full authoritative registry, or release readiness. |

The current accepted cumulative record is `food-item-registry` report schema 9
with 219 of 219 passing assertions in
`docs/evidence/forge-1.20.1/food-item-registry-server-v14`. Its tracked profile
manifest is 1192 bytes with SHA-256
`442d11e6a5072c8ec418bced406529dc15caa6d4f4d4c5c68edc8a79ce2e493d`.
The archive binds archive-manifest SHA-256
`eacab05996569a78a55ed21117d2a7e0768d87c258c93757cdc4ab4205881927`,
report SHA-256
`3ccd86d4ef6f5b31fd37b686254bdf427e351019c778d2d8e3f03958de0e1f6c`,
launcher-result SHA-256
`4ec610688bf030ea722772c40d871ec1b954fcbc0b15f90cbad41acec6278ad0`,
completion-marker SHA-256
`37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`,
and server-log SHA-256
`e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db`.
The exact model and texture SHA-256 values are
`6ba61590386580a2f70526313d501eec44cd88ff9d86cd1d13d9092b41a42fbe`
and `44f9d92ccf36c3555d21ace9eea0268e43eb4a8e95f1e81b74f22977d4928d65`;
the accepted translations are `Mushroom Crumb` and `Грибной мякиш`.

The next prepared cumulative record is `forest-lantern` report schema 10 with
266 exact ordered assertions. Its unused v15 profile manifest is 1,184 bytes
with SHA-256
`b0ddfd9ac8ac9073d055a492bd71250995b42c69a6c54a30eef0f379319cf58c`.
No v15 native acceptance or archive is claimed until the fresh one-shot run and
strict archive publication complete.

The v6 `ether-source-reload`, v7 `enchantment-registry`, v10
`particle-registry`, v11 `material-item-registry`, and v13
`metal-block-registry` archives remain immutable historical evidence. All
accepted states are included in and superseded by the cumulative v14 runtime
proof. The v12 metal-block profile was consumed by a failed diagnostic tag-load
run and has no accepted archive. In the two tag files packaged by this bounded
Forge slice—`mineable/pickaxe` and `needs_iron_tool`—still-unported IDs were
then made optional before the fresh v13 capture; `needs_stone_tool` remains
unchanged and outside the slice. This Loom-userdev record captures observations
from an execution of the then-checked-out source set; it binds neither exact
source bytes nor a packaged Forge JAR. Frozen archives prove capture-time
integrity; current-source or rebuilt-artifact identity still requires a new
isolated native run.

The strict current verifier is:

```bash
python3 -B scripts/e2e/forge_server_food_item_evidence_v14.py \
  --archive docs/evidence/forge-1.20.1/food-item-registry-server-v14
```

All 87 Python runner and verifier safety tests pass. Separately, the executable
`:forge:1.20.1:serverProbeSafetyInterlockTest` task passes 20 non-Minecraft
Gradle interlock fixture cases and is wired into
`forgeFoodItemRegistryServerSafetyTest`. These are distinct suites, not one
107-test count. The integrated positive and forward-gate check is:

```bash
./gradlew --no-daemon --no-parallel --console=plain \
  :forge:1.20.1:validateForgeFoodItemRegistryMilestone \
  :forge:1.20.1:verifyForgePortGateClosed
```

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
