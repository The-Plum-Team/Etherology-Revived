# Forge 1.20.1 bootstrap and bounded-storage status

The Forge lane now has a native JavaFML entry point, a loader-neutral lifecycle handshake, and one
complete loader-neutral item registration vertical. Its bounded ethereal-storage vertical now
persists a 64-unit internal Ether buffer and four inventory slots, owns a server menu, exposes
vanilla sided insertion and native Forge item-handler access, transfers Ether into canonical Glint
state, registers a Dist-scoped Forge client screen and Gecko renderer, and has passed a packaged
save/restart/reopen scenario in an isolated macOS client. This accepts the bounded storage-parity
milestone only, not the channel/network graph or a playable Etherology port. It cannot produce or
publish a release artifact.

The JavaFML entry point first exposes its mod event bus through Architectury's `EventBuses`, then
`EtherologyBootstrap` attaches the shared block, item, block-entity, and screen-handler registries
during `@Mod` construction, before Forge registry events run. It delegates handshake idempotence
and failure state to `BootstrapLifecycle`. `PlatformRegistrar` supplies the loader boundary. The
native `ForgePlatformRegistrar` listens for `FMLCommonSetupEvent` and uses `enqueueWork` so the
handshake runs on Forge's setup work queue instead of its parallel event-dispatch thread.

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
- `verifyForgePortGateClosed` is expected to pass. It is a diagnostic task, not an artifact gate:
  after requiring every accepted positive gate, it reports the first incomplete forward stage.
  That stage is now ethereal channel/network.
- `validateForgeChannelNetworkMilestone` is the explicit next forward gate. It depends on storage
  parity and is expected to fail until the shared Ether transfer contract and registered persistent
  directed ethereal channel are implemented and accepted.
- `validateForgeReleaseReadinessMilestone` is a permanent final backstop after channel/network. It
  fails unconditionally until the full gameplay graph and complete packaged native Forge client,
  dedicated-server, persistence, and E2E matrix are implemented and the task itself is replaced by
  concrete acceptance checks. The bounded storage run is not sufficient. Class or method-name
  stubs cannot open this gate.
- `validateForgePortInputs`, `remapJar`, and every future `publish*` task depend on every accepted
  positive gate plus the channel/network and final-readiness milestones. They remain closed during
  channel/network work and afterward until full native release readiness is explicitly accepted.

The diagnostic verification walks the predeclared forward milestones in order. A completed stage
must gain its positive and native proof, while the unconditional final readiness stage remains in
the artifact path. Neither the diagnostic task nor a removed condition may replace
`validateForgePortInputs` merely to produce an artifact.

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

## Forward fail-closed channel/network milestone

The first incomplete gate is now `validateForgeChannelNetworkMilestone`. It requires the shared
Ether storage transfer contract, a registered `ethereal_channel` block and block entity, persistent
`stored_ether`, output-direction state, and directed transfer behavior. Its eventual acceptance
must include the matching native runtime proof; storage evidence cannot stand in for channel
transport.

`validateForgeReleaseReadinessMilestone` remains behind that gate and keeps the artifact path
closed for every subsequent gameplay, dedicated-server, client, persistence, and E2E slice.

## Remaining implementation blockers

- The common JAR contains component state/access contracts, the loader handshake,
  `etherology:ether`, and the accepted bounded ethereal-storage vertical.
- The canonical initializer remains in the Fabric production source graph. Of 352 canonical main
  Java files, 22 directly import Fabric API, Biolith, Trinkets, or Fabric Shield Lib through 34
  import statements. Transitive ownership work remains beyond that lower bound.
- The canonical initializer directly reaches unported loader APIs for dynamic registries and
  reload lifecycle, networking, commands, block and wood type builders, brewing recipes, sculk
  event frequencies, and biome modification.
- Component contracts are shared, but only Fabric component adapters exist. Forge capability
  adapters for Ether, corruption, Teldecore, and visited state do not exist yet.
- Forge-owned mixin configuration and an access-transformer replacement for the Fabric access
  widener do not exist yet.
- The client tree has 211 Java files; 64 directly import Fabric, REI/EMI, owo, Biolith, Trinkets, or
  Fabric Shield Lib APIs and still need common-versus-loader ownership decisions.

The next native slice is the already gated ethereal channel/network vertical. A release remains
invalid until that slice and every subsequent gameplay system, dedicated-server check, and native
E2E requirement are ported and accepted as well.
