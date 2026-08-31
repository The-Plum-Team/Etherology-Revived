# Forge 1.20.1 bootstrap and persistent-storage-core status

The Forge lane now has a native JavaFML entry point, a loader-neutral lifecycle handshake, and one
complete loader-neutral item registration vertical. Its bounded ethereal-storage vertical now
persists a 64-unit internal Ether buffer and four inventory slots, owns a server menu, exposes
vanilla sided insertion, and registers a Dist-scoped Forge client screen. This is a persistent
storage/menu core, not complete storage parity or a playable Etherology port. It cannot produce or
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
- `verifyForgePortGateClosed` is expected to pass. It is a diagnostic task, not an artifact gate:
  after requiring every accepted positive gate, it reports the first incomplete forward stage.
  That stage is storage parity today and automatically becomes channel/network after storage
  parity passes.
- `validateForgeStorageParityMilestone` is expected to fail until per-glint Ether arithmetic,
  invalidatable Forge item-handler capability interop, and synchronized Gecko open/close animation
  are implemented.
- `validateForgeChannelNetworkMilestone` is the explicit next forward gate. It depends on storage
  parity, so it cannot run past today's first failure; once storage parity passes, it requires the
  shared Ether transfer contract and registered persistent directed ethereal channel.
- `validateForgeReleaseReadinessMilestone` is a permanent final backstop after channel/network. It
  fails unconditionally until the full gameplay graph and packaged native Forge client,
  dedicated-server, persistence, and E2E proof are implemented and the task itself is replaced by
  concrete acceptance checks. Class or method-name stubs cannot open this gate.
- `validateForgePortInputs`, `remapJar`, and every future `publish*` task depend on every accepted
  gate plus all three forward milestones and are therefore expected to fail during storage parity,
  remain closed during channel/network work, and remain closed afterward until full native release
  readiness is explicitly accepted.

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
Forge source set packages the existing blockstate, models, texture, translation, loot table, and
recipe. The recipe remains unusable because `etheroscope` is not yet registered in the shared
Forge graph.

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

The common tests execute pure normalization, display-count, clear/display, and multi-viewer
transitions. ASM tests inspect compiled control flow for the NBT read/write order, typed slots,
quick-move structure, sided restrictions, and logical-server guards. The positive gate runs these
Common tests and the Forge test/compile path before inspecting compiled loader artifacts. A real
NBT save/restart/reopen round trip was not run in this slice. These deterministic checks do not
claim native loader or packaged E2E proof; an isolated Forge runtime scenario remains required.

## Forward fail-closed storage-parity milestone

Artifacts remain blocked on all three conditions below:

1. The bounded glint item has no canonical `stored_ether` NBT, capacity arithmetic, or transfer
   behavior. The three input slots therefore store items but do not charge or drain them.
2. Vanilla `SidedInventory` insertion is present, but Forge `ForgeCapabilities.ITEM_HANDLER`,
   `LazyOptional`, and invalidation lifecycle are absent. The forward gate reserves the concrete
   Forge owner `EtherealStorageItemHandlerProvider` so unrelated item capabilities cannot satisfy
   this condition.
3. Basic viewer counting and server chest sounds are present, but the Gecko renderer/controller and
   tracking-recipient open/close animation packets are absent.

The first gate must remain on these storage behaviors. The already-declared
`validateForgeChannelNetworkMilestone` keeps the artifact path closed when storage parity later
passes, until the ethereal channel/network vertical is itself complete. The unconditional
`validateForgeReleaseReadinessMilestone` then keeps it closed for every remaining gameplay and
native-runtime slice.

## Remaining implementation blockers

- The common JAR contains component state/access contracts, the loader handshake,
  `etherology:ether`, and the bounded persistent ethereal-storage/menu core.
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

The next native slice must close the three storage-parity gaps before implementing the already
gated channel/network vertical. A release remains invalid until subsequent gameplay systems and
their native tests are ported as well.
