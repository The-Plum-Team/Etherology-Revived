# Packaged end-to-end test contract

Etherology's E2E gate uses a production-plus-harness architecture. A test run
launches only Etherology's exact staged production JAR and its required runtime
libraries in a real client. Scenarios use a real integrated server or disposable
dedicated server according to the behavior under test. The automation code is
delivered as a separate client-only JAR and must never be included in a published
mod. No architecture-reference project code or JAR is built, launched, copied into
a profile, or treated as an E2E target.

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

## Evidence lifecycle

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

## Loader-specific bounded scenarios

These scenarios record an accepted vertical during a transitional port. They do
not replace any standard scenario or establish complete loader readiness.

| Scenario | Lane | Required proof |
|---|---|---|
| `ethereal-storage` | `forge-1.20.1` | Bounded Ethereal Storage vertical in a fresh integrated world: four-slot block entity and NBT reconstruction; per-Glint Ether transfer with exact-total conservation; Forge item-handler access on the unsided view and all six faces with simulated/live insertion, blocked extraction, and hidden display slot; viewer open/close and synchronized Gecko animation; native menu; closed/open/closed-again plus pre-restart and post-restart menu captures; forced save, full disconnect/restart, exact Ether distribution, ordered input inventory, display state, block-entity type, and menu reopen. This scenario does not satisfy `ether-network`, general `persistence`, multiplayer, or full-loader readiness. |
| `ethereal-channel` | `forge-1.20.1` | Bounded directed-channel vertical in a fresh integrated world and forced fixture chunk: exact channel/storage block and block-entity ids at non-overlapping fixture positions; redstone-fed powered repeaters provide exact strong power from the full solid arena floor while temporary support blocks are replaced by channels, then the first scheduled server tick resolves ACTIVATED with source-above `up=in` and target-east `east=out` without any manual neighbor refresh; an independent vanilla wall lever remains attached to a channel after natural propagation, reports `canPlaceAt=true`, produces the exact `north=in`, `east=out`, `west=empty`, cross topology, mirrors to the client, and survives save/restart; a strongly powered channel receives and retains exactly one Ether without forwarding; natural power removal transfers exactly one Ether on the fifth-tick cadence with total conservation and no reverse motion; a missing output evaporates exactly 0.2 Ether and exposes then naturally clears its evaporation flags after natural reactivation; channel NBT reconstruction and client sync; gated, transferred, and reopened 1920×1080 captures after 120 consecutive exact client/terrain-ready frames at the fixed first-person camera pose, with capture-time mirror, renderer, and camera assertions; forced save, full disconnect/restart, exact server state, and exact block-entity types. Storage Ether distribution is a server oracle because the storage foundation has no client update packet. This scenario does not satisfy the complete `ether-network`, general `persistence`, multiplayer, or full-loader readiness contracts. |
| `ether-source-reload` | `forge-1.20.1` | Bounded dedicated-server data-reload vertical in a fresh repository-owned Loom-userdev profile: Common is the sole listener/default-data owner; exact initial 23-entry map with corrected `etherology:primoshard_rella = 4` and `minecraft:redstone = 2`; a real `reload` command with an enabled probe pack; exact reloaded 24-entry map with `minecraft:redstone = 9.5` and added `minecraft:diamond = 13`; stable game-event registry and tags; stable loot-condition registry and evaluated behavior while the probe `LootTable` instance is replaced; world save, normal stop, exit code zero, and no `ERROR` or `FATAL` marker. This headless scenario requires no screenshots and does not satisfy furnace/machine consumption, the wider Ether network, the full authoritative registry, sound playback, Forge custom sculk frequency, Attrahite drops, or release readiness. |

The accepted `ether-source-reload` record is report schema 4 with 72 of 72
passing assertions in
`docs/evidence/forge-1.20.1/ether-source-reload-server-v6`. It binds profile
manifest SHA-256
`2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0`,
server-log SHA-256
`0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`,
and archive-manifest SHA-256
`6d552536f74c018ce56e238fcb5a3aacd8fa363c76293863514adf9d7bafc2e0`.
The archive proves capture-time integrity; current-source or rebuilt-artifact
identity still requires a new isolated native run.

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

The gate fails for any missing or unexpected step, assertion failure, missing or
blank screenshot, wrong dimensions, unapproved crash report, fatal mixin/access
widener/linkage/mod-loading log marker, evidence over the configured bound, or a
production JAR whose digest differs from the staged release manifest.
