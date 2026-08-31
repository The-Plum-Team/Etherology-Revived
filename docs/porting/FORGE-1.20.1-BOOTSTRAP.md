# Forge 1.20.1 bootstrap, storage, channel, sound, and registry-foundation status

The Forge lane now has a native JavaFML entry point, a loader-neutral lifecycle handshake, and
accepted bounded item, storage, channel, sound-registry, game-event, and loot-condition
registry/server milestones.
Its ethereal-storage
vertical persists a 64-unit internal Ether buffer and four inventory slots, owns a server menu, exposes
vanilla sided insertion and native Forge item-handler access, transfers Ether into canonical Glint
state, registers a Dist-scoped Forge client screen and Gecko renderer, and has passed a packaged
save/restart/reopen scenario in an isolated macOS client. The ethereal-channel vertical registers
the shared channel and block entity, applies the shared storage/pipe transfer contract,
preserves direction and Ether state, supports redstone gating and native Forge wall levers, and
has passed its own packaged save/restart scenario in a fresh isolated macOS client. These runs
accept only bounded storage parity and channel-foundation behavior. The subsequent static sound
milestone gives the 14 canonical sound-event IDs one Common declaration owner and closes the exact
21 packaged mono 44.1 kHz OGG files, `sounds.json` entries and attenuation values, and English
subtitles. It does not prove native playback. The game-event milestone gives
`etherology:etherology_resonance` one Common deferred owner, preserves internal ID
`etherology_resonance` and range 16, packages exact vanilla `vibrations` and
`warden_can_listen` tag membership on both loaders, and adds a real Forge dedicated-server
registry/tag data-load proof. The loot-condition milestone gives
`etherology:random_chance_with_fortune` one Common deferred owner and proves its canonical
serializer with a synthetic server loot table. Channel cases, particles, the remaining loot and
recipes, the wider machine/network graph, most of the authoritative registry spine, and a playable
Etherology port remain incomplete.
The lane cannot produce or publish a release artifact.

The JavaFML entry point first exposes its mod event bus through Architectury's `EventBuses`, then
`EtherologyBootstrap` attaches the shared block, item, block-entity, screen-handler, sound-event,
game-event, and loot-condition registries during `@Mod` construction, before Forge registry events
run. It delegates
handshake idempotence and failure state to `BootstrapLifecycle`. `PlatformRegistrar` supplies the
loader boundary. The native `ForgePlatformRegistrar` listens for `FMLCommonSetupEvent` and uses
`enqueueWork` so the handshake runs on Forge's setup work queue instead of its parallel
event-dispatch thread.

Completing this handshake means only that JavaFML reached the expected lifecycle phase. It does
not mean that the remaining items, blocks, entities, components, networking, world generation, or
client systems have been registered.

## Verification tasks

- `validateForgeBootstrapInputs` is expected to pass. It checks the common lifecycle classes,
  native metadata, `@Mod` entry point, Architectury event-bus ordering, and
  `ForgePlatformRegistrar` installation.
- `validateForgeEtherItemMilestone` is expected to pass. It checks the shared deferred registry,
  bootstrap attachment, model, texture, and English translation for `etherology:ether`.
- `validateForgeStorageFoundationMilestone` is expected to pass. It checks the distinct shared
  block and block-entity registries, block item, bounded foundation types, bootstrap attachment,
  and packaged block resources.
- `validateForgePersistentStorageMenuCoreMilestone` is expected to pass. It checks persistent
  Ether and inventory paths, the typed 3+1 menu topology, server opening and input drops, vanilla
  sided automation, the deferred screen-handler registry, and Dist-scoped enqueued client
  registration. It depends on real Common and Forge test/compile tasks, reads the Common JAR and
  compiled Forge classes, and does not accept source comments as Forge client or capability proof.
- `validateForgeStorageParityMilestone` is expected to pass. It adds canonical per-Glint Ether
  arithmetic, invalidatable Forge item-handler capability interop, synchronized Gecko open/close
  animation, and the required model, animation, and texture resources to the positive storage
  gate.
- `validateForgeChannelImplementationMilestone` is expected to pass. It checks the shared Ether
  storage/pipe contracts, registered channel block and block entity, persistent Ether and
  direction state, compiled fifth-tick transfer and evaporation behavior, storage endpoints,
  native Forge lever support, and the deterministic harness and visual-verifier regressions.
- `validateForgeChannelEvidenceArchiveIntegrity` is expected to pass. It validates the immutable
  six-file Channel archive and its capture-time provenance without comparing a later whole JAR.
- `validateForgeChannelNetworkMilestone` is expected to pass. It adds the fresh packaged
  `ethereal-channel` run, exact save/restart state, three native framebuffers, and frozen artifact
  provenance to the positive bounded channel-foundation gate.
- `validateForgeSoundRegistryMilestone` is expected to pass. It requires the accepted Channel
  implementation and archive, runs the Common, Fabric, and Forge tests, and checks the Common,
  Fabric-transformed Common, Forge-transformed Common, remapped Fabric production, and Forge
  shadow artifacts. It accepts the exact 14 sound IDs, 21 packaged OGGs, sound manifest and
  attenuation entries, and English subtitle closure without claiming playback.
- `validateForgeGameEventRegistryMilestone` is expected to pass. It requires the sound milestone
  and checks exact declaration ownership and multiplicity, constructor/range data flow, loader
  bootstrap paths, Fabric's sole supported frequency hook, prohibited direct alternatives, and
  both packaged vanilla tags across Common, transformed Common, Fabric development/remapped
  production, and Forge shadow artifacts.
- `validateForgeLootConditionRegistryMilestone` is expected to pass. It requires the game-event
  milestone and proves that `SharedLootConditions` is the sole deferred owner of
  `etherology:random_chance_with_fortune`, backed by
  `RandomChanceWithFortuneConditionSerializer`, across the Common and loader artifact boundaries.
- `verifyRegistryFoundationServerProbe` is expected to pass without launching Minecraft. It builds and
  tests the server-only probe, validates its Java 17 Loom configuration and exact `main` plus
  probe-mod binding, requires the dev-launch-injector bootstrap, and proves that probe classes do
  not enter the production artifact. The actual launch is owned by
  `scripts/e2e/forge_server.py run`, which invokes
  `:forge:1.20.1:runRegistryFoundationServerProbe` inside the isolated profile.
- `validateForgeRegistryFoundationServerEvidenceArchiveIntegrity` is expected to pass. It runs
  the 63 runner/verifier safety tests and validates the immutable five-file v4 archive independently
  of the ignored live runtime.
- `validateForgeRegistryFoundationMilestone` is the combined positive gate. It requires the
  sound, game-event, and loot-condition artifact proofs, current probe isolation checks, and frozen
  dedicated-server evidence before the broader authoritative registry gate may run.
- `validateForgeChannelCurrentArtifactDiagnostic` is deliberately not an acceptance dependency.
  It now fails because the later sound milestone changed the whole production JAR relative to the
  Channel capture. That expected byte mismatch is not a Channel regression and does not establish
  current equality.
- `verifyForgePortGateClosed` is expected to pass. It is a diagnostic task, not an artifact gate:
  after requiring every accepted positive gate, it reports the first incomplete forward stage.
  That stage is now the broader authoritative registry spine; the bounded sound, game-event, and
  loot-condition steps are no longer in its missing-condition list.
- `validateForgeReleaseReadinessMilestone` is a permanent final backstop after every bounded
  forward gate. It fails unconditionally until the full gameplay graph and complete packaged
  native Forge client, dedicated-server, persistence, and E2E matrix are implemented and the task
  itself is replaced by concrete acceptance checks. The bounded Storage and Channel runs, static
  sound gate, and registry-foundation dedicated-server proof are not sufficient. Class or method-name
  stubs cannot open this gate.
- `validateForgePortInputs`, `remapJar`, and every future `publish*` task depend on every accepted
  positive gate plus the remaining authoritative-registry, gameplay, and final-readiness
  milestones. They remain closed until full native release readiness is explicitly accepted.

The diagnostic verification walks the predeclared forward milestones in order. A completed stage
must gain positive proof proportionate to its contract, and every runtime mechanic still requires
native proof. The unconditional final readiness stage remains in the artifact path. Neither the
diagnostic task nor a removed condition may replace `validateForgePortInputs` merely to produce an
artifact.

## Accepted Ether item milestone

The first accepted gameplay vertical is the existing `etherology:ether` item. Its positive gate
requires all of the following:

1. `ru/feytox/etherology/registry/item/SharedItems.class` in the common JAR.
2. `SharedDeferredRegister` backing, which owns one Architectury `DeferredRegister` rather than a
   copied direct Fabric registration path.
3. The `ether` item registration in that registry.
4. A direct `SharedItems` installation reference from `EtherologyBootstrap`.
5. The Ether item model, texture, and English translation used by the Forge resource source set.

The shared registry intentionally has a different fully qualified class name from the canonical
Fabric `EItems`. This prevents the transformed common JAR from replacing Fabric's existing
registry class while the two source graphs coexist during the port.

`SharedItems.register()` is attached in `EtherologyBootstrap.initialize(...)` during `@Mod`
construction. It is not delayed into the enqueued loader handshake, which runs after Forge
registry events.

## Accepted ethereal-storage registration foundation

The second accepted foundation uses distinct loader-neutral `SharedBlocks` and
`SharedBlockEntities` registries with Architectury `DeferredRegister`. Together with `SharedItems`,
they register `ethereal_storage`, its `BlockItem`, and `ethereal_storage_block_entity` before Forge
registry events. `EtherealStorageFoundationBlock` creates an
`EtherealStorageFoundationBlockEntity`, so a placed storage has the registered type instead of
being a block-only marker.

`SharedDeferredRegister` centralizes attach-once and first-failure lifecycle behavior. All
foundation class names differ from canonical Fabric's `EBlocks`, `EtherealStorageBlock`, and
`EtherealStorageBlockEntity`, preventing shadow replacement while the source graphs coexist. The
Forge source set packages the existing blockstate, models, texture, translation, and loot table.
The recipe is deliberately excluded from the accepted Forge data set because `etheroscope` and its
other supporting gameplay registrations are not yet present in the shared Forge graph.

## Accepted persistent storage/menu core

The distinct shared block entity owns `storage_ether` and exactly four item slots. Slots zero
through two accept the non-stackable bounded `etherology:glint_shard` input; slot three is rebuilt
from normalized internal Ether, cannot be inserted into or taken, and is never dropped. Serialized
slot-three data is not authoritative. File-provided non-finite or out-of-range Ether is normalized
to `[0, 64]` before rebuilding the display. `Inventory.clear()` clears the three real inputs but
retains the internal Ether buffer and immediately rebuilds slot three, so the display never
contradicts the chosen retained-Ether semantics.

`EtherealStorageFoundationScreenHandler` owns quick-move boundaries and viewer open/close calls.
The block opens it only on the logical server and scatters the three real inputs only on the
logical server. `SidedInventory` exposes those three typed inputs to vanilla hopper insertion and
disallows extraction. `SharedScreenHandlers` registers `ethereal_storage_screen_handler` through
the shared deferred lifecycle. `ForgeClientEvents` is a `Dist.CLIENT` mod-bus subscriber and binds
the native screen from enqueued `FMLClientSetupEvent` work, leaving the JavaFML entrypoint free of
client references.

The common tests execute normalization, display-count, clear/display, viewer, Glint-capacity, and
transfer transitions. ASM tests inspect compiled control flow for NBT order, typed slots,
quick-move structure, sided restrictions, server guards, ticking, and Gecko controller ownership.
Forge tests cover item-handler views and invalidation, client registration, resource packaging,
and distribution safety. The positive gates run those Common and Forge test/compile paths before
inspecting compiled loader artifacts.

## Accepted bounded ethereal-storage parity milestone

The positive storage-parity gate now accepts all three previously declared conditions:

1. `etherology:components/stored_ether` is the canonical per-Glint state, each Glint is bounded to
   128 Ether, and storage moves at most one Ether on every fifth server tick while preserving the
   combined total. The derived display slot represents internal Ether only.
2. `EtherealStorageItemHandlerProvider` exposes exactly the three input slots through cached
   unsided and six-faced `ForgeCapabilities.ITEM_HANDLER` views. It allows insertion, rejects
   extraction, hides the display slot, and invalidates every `LazyOptional` with its block entity.
3. The shared block entity owns one Gecko controller and synchronized open/close triggers. The
   Forge client registers the canonical model, renderer, render layer, and item model predicate.

The packaged `ethereal-storage` scenario then placed the block in a fresh integrated world,
exercised the item handler and Glint transfer, captured closed/open/closed-again and menu frames,
forced a save, disconnected, restarted the same isolated world, compared exact persistent state,
and reopened the menu. It passed 34 of 34 assertions in 438 client ticks. The full artifact hashes,
five native framebuffers, report, and deterministic visual ratios are frozen in the
[`Forge 1.20.1 runtime evidence`](../evidence/forge-1.20.1/README.md).

## Accepted bounded ethereal-channel foundation milestone

The shared foundation now registers `ethereal_channel` and
`ethereal_channel_block_entity` alongside the accepted storage endpoint. The channel persists its
Ether, output direction, connection state, activation state, and evaporation flags. Shared pipe
behavior retains one Ether while redstone-gated, transfers it in the output direction on the
fifth-tick cadence after natural power removal, and evaporates exactly `0.2` Ether on that cadence
when no output exists. A narrow Forge-owned mixin preserves vanilla wall-lever support on the
channel without importing the broad Fabric mixin configuration.

The packaged `ethereal-channel` scenario placed exact registered channel and storage endpoints in
a fresh integrated world, proved the gate-to-transfer-to-evaporation sequence, verified exact
client mirrors and render readiness for 120 consecutive frames at the fixed camera before every
capture, force-saved, disconnected, restarted the world, and compared the exact persistent state.
It passed 42 of 42 assertions in 3,434 client ticks with three native `1920x1080` framebuffers. The
full artifact hashes, report, screenshots, and deterministic visual ratios are frozen in the
[`Forge 1.20.1 runtime evidence`](../evidence/forge-1.20.1/README.md).

This positive milestone covers only the shared channel foundation, bounded directed behavior with
storage endpoints, and native Forge lever support. It does not cover channel-case interaction and
registration, channel particles and the client ticker, channel loot and recipe data, or the wider
machine/network graph.

## Accepted bounded SharedSounds registry milestone

`SharedSounds` is the single Common declaration owner for these exact 14 IDs:
`electricity_sound`, `matrix_idle_sound`, `deflect`, `bubbles`, `pouf`, `ratchet`,
`brewing_dissolution`, `thunder_zap`, `tuning_mace`, `tuning_fork_activate`,
`tuning_fork_tuning`, `tuning_fork_resonance`, `broadsword`, and `warp_counter`.
Forge attaches that deferred registry during `@Mod` construction, before lifecycle events, without
resolving its suppliers during declaration.

Fabric attaches `SharedSounds` from its canonical initializer without invoking
`EtherologyBootstrap`; all canonical sound consumers resolve the shared suppliers only at playback
sites. Forge attaches the same owner through its Common bootstrap. The eager `EtherSounds` owner
has been removed, while the Common JAR, both loader-transformed Common JARs, remapped Fabric
production JAR, and Forge shadow JAR preserve exactly one `SharedSounds` owner. Thus both active
loaders use one declaration owner without class shadowing or a dormant public supplier contract.

The resource contract contains exactly 21 packaged mono 44.1 kHz OGG files locked to canonical
SHA-256 digests. Its exact per-event `sounds.json` mapping includes the canonical ordered audio
references and attenuation entries, and every referenced English subtitle key resolves. The Forge
shadow entries must remain byte-identical to their canonical sources.
`validateForgeSoundRegistryMilestone` seals those declarations and resources across the Common,
Fabric-transform, Forge-transform, remapped Fabric-production, and Forge-shadow boundaries. No
native sound-playback E2E was run for this static milestone; each later consumer mechanic must
prove that it plays the expected event.

## Accepted bounded registry foundation and server milestone

`SharedGameEvents` is the sole Common deferred owner of
`etherology:etherology_resonance`. It constructs exactly one game event whose internal ID is
`etherology_resonance` and whose range is 16, then exposes it through a `RegistrySupplier` that is
resolved only at use sites. Forge attaches this owner from `EtherologyBootstrap` before registry
events. Fabric attaches the same owner from its canonical initializer, then uses the supported
`SculkSensorFrequencyRegistry` API to assign frequency 10. Forge 47 has no supported equivalent,
so custom Forge sculk-frequency behavior remains explicitly deferred instead of mutating vanilla
frequency state.

Both loader artifacts package exact additive membership for the resonance event in the vanilla
`minecraft:vibrations` and `minecraft:warden_can_listen` game-event tags. The Common JAR, both
loader-transformed Common JARs, Fabric development and remapped production JARs, and Forge shadow
JAR retain exactly one shared owner and exclude the removed eager `EventsRegistry` owner.
`validateForgeGameEventRegistryMilestone` also rejects alternate direct registrations, duplicate
constructor ownership, early supplier resolution, direct Fabric registration outside the supported
frequency hook, and direct mutation of vanilla frequency storage.

`SharedLootConditions` is the sole Common deferred owner of
`etherology:random_chance_with_fortune`. Both loaders bind that ID to
`ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer`, with exact ownership
checked across the Common, transformed Common, Fabric, and Forge production boundaries. The
canonical Attrahite loot-table resource remains Fabric-only because the items it references have
not been ported. A probe-owned synthetic table tests the condition without packaging that resource
for Forge.

These static artifact proofs are paired with a fresh repository-owned Loom-userdev server in the
`etherology-e2e-forge-server-1.20.1-v4` profile. The headless `registry-foundation` scenario ran
the exact `:forge:1.20.1:runRegistryFoundationServerProbe` task on a real Java 17 Forge 47.4.9
dedicated server and passed all 39 ordered assertions. Separately, all 63 Python runner/verifier
safety tests pass. The run proved the exact sole game event and two tags, the exact sole loot-condition type and
serializer, and stable state after server data load. The synthetic table's sorted outputs were
`[minecraft:gold_ingot, minecraft:stone]` with an empty tool and
`[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` with Fortune I. Its three pools cover
chance `1` with multiplier `0`, chance `0` with multiplier `1`, and the mixed chance `0.99` plus
multiplier `0.01` case.

The runner required a saved world, normal `stop(false)`, exit code zero, and no `ERROR` or `FATAL`
log marker. No screenshots are produced or claimed for this server-only proof. The synthetic table
proves the shared condition and serializer; it does not prove Attrahite gameplay or drop parity.
The immutable v2 game-event-only archive remains historical evidence, but v4 supersedes it as the
current registry-foundation proof.

The probe observes `ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That path joins the actual stopped-event server thread before `System.exit`
and works around a proven non-daemon Loom-userdev thread leak that would otherwise keep Gradle
alive; it does not enter the production artifact. The external runner bounds and validates the
process log, server log, report, saved world, lifecycle, crash state, completion-marker order, and
exit result.
The earlier Fabric `v20` packaged startup evidence predates this registry rebuild and therefore
does not claim equality with the current Fabric production JAR.

## Forward fail-closed broader authoritative registry milestone

The next forward gate remains the rest of the authoritative registry spine. The temporary block,
item, block-entity, and screen-handler catalogs must converge into one active declaration owner per
canonical ID without shadowing Fabric classes or resolving suppliers during declaration. Entity,
enchantment, recipe, effect, remaining game-event and loot consumers, particle, tree,
world-generation, and lifecycle-hook ownership also remains incomplete. The bounded
registry-foundation server
proof does not satisfy the full registry/catalog placement-and-save smoke. The unfinished portions
of the Ether graph stay behind that work.

`validateForgeReleaseReadinessMilestone` remains behind the forward registry and gameplay gates
and keeps the artifact path closed for every subsequent dedicated-server, client, persistence, and
E2E slice.

## Remaining implementation blockers

- The common JAR contains component state/access contracts, the loader handshake,
  `etherology:ether`, the accepted bounded ethereal-storage vertical, and the accepted bounded
  ethereal-channel foundation. `SharedSounds`, `SharedGameEvents`, and `SharedLootConditions` now
  own the accepted Common sound, resonance, and Fortune-scaled condition declarations, but the
  other temporary catalogs have not yet converged into the authoritative registry spine.
- The canonical initializer remains in the Fabric production source graph. Of 352 canonical main
  Java files, 22 directly import Fabric API, Biolith, Trinkets, or Fabric Shield Lib through 34
  import statements. Transitive ownership work remains beyond that lower bound.
- The canonical initializer directly reaches unported loader APIs for dynamic registries and
  reload lifecycle, networking, commands, block and wood type builders, brewing recipes, and biome
  modification. Fabric's resonance frequency 10 is registered through its supported hook; Forge
  47 has no supported equivalent, so that custom frequency remains deferred.
- Component contracts are shared, but only Fabric component adapters exist. Forge capability
  adapters for Ether, corruption, Teldecore, and visited state do not exist yet.
- A minimal Forge-owned mixin configuration now supplies the accepted channel lever support. The
  remaining retained mixins and any narrowly required access-transformer entries still need to be
  selected and proven with their owning slices; the broad Fabric access widener is not packaged.
- The client tree has 211 Java files; 64 directly import Fabric, REI/EMI, owo, Biolith, Trinkets, or
  Fabric Shield Lib APIs and still need common-versus-loader ownership decisions.

The next gameplay slice is the broader authoritative registry spine after the accepted SharedSounds,
SharedGameEvents, and SharedLootConditions foundations. A release remains invalid until that gate, the full-catalog
dedicated-server placement/save smoke, the deferred channel and sculk-frequency work, every
subsequent gameplay system, and the full native E2E matrix are ported and accepted.
