# Forge 1.20.1 gameplay completion plan

This document is the implementation plan for turning the native Forge 1.20.1 bootstrap into a
complete Etherology gameplay port. It is intentionally stricter than a compilation checklist:
each slice must own a coherent mechanic, preserve the canonical registry identifiers and save
semantics, and finish with native runtime evidence before the release gate advances.

The shared Ether item and the bounded Ethereal Storage vertical are accepted foundations, not the
finished port. Storage now has canonical per-Glint Ether arithmetic, native Forge item-handler
capability lifecycle, synchronized Gecko animation, and packaged save/restart/reopen proof. The
ethereal channel/network gate is the first incomplete forward milestone. Broad content migration
must still follow the ownership and dependency order below.

## Audit snapshot

At the time of this audit, the canonical production graph contains 353 main Java files and 211
client Java files. The new common graph contains 15 Java files and the Forge graph contains two.
A direct loader/vendor scan finds 22 bound main owners and 64 bound client owners. After the full
REI, EMI, and Fabric datagen families are removed, 23 directly bound required-client owners
remain. The explicit-import reverse closure from the bound roots reaches 367 of the 564 canonical
files because the initializer, registry catalogs, and packet types are central dependencies.

This has two consequences:

1. The canonical source roots must not be added wholesale to the Forge source set.
2. Porting isolated classes without their registry, state, networking, and client lifecycle will
   repeatedly produce shells that compile but do not implement the mechanic.

Loader-neutral behavior belongs in the Stonecutter common lane. Fabric and Forge retain small
entrypoint, lifecycle, capability, tracking-recipient, and client-registration adapters. The
canonical Fabric graph remains authoritative until each bounded slice is accepted.

## Registry ownership rule

`SharedItems`, `SharedBlocks`, and `SharedBlockEntities` are temporary catalogs used to establish
the loader-neutral lifecycle without replacing canonical Fabric classes. They cannot remain a
second permanent catalog for the same identifiers. Before broad content migration, converge on
one authoritative declaration for every registry ID and attach that declaration once per loader.

The convergence must replace eager `Registry.register` calls and eager construction through
`RegistrableBlock` and `EBlock` with Architectury `DeferredRegister` and `RegistrySupplier`.
Suppliers must not be dereferenced while declarations are still being constructed. This is
especially important for block-entity builders and the generated arrays for seals, sedimentary
stones, and furniture blocks.

Each accepted registry slice must compare its ID manifest with Fabric, reject duplicate IDs, and
prove that no canonical Fabric class is shadowed by the transformed common JAR.

## Execution order

### Slice 0: build and authoritative registry spine

Source owners:

- Blocks: `ExtraBlocksRegistry`, `DecoBlocks`, `EBlocks`, `DevBlocks`, `EBlockFamilies`.
- Items: `EItems`, `ToolItems`, `ArmorItems`, `DecoBlockItems`, `EItemGroups`.
- Other registries: `EntityRegistry`, `EtherEnchantments`, `EtherSounds`, `RecipesRegistry`,
  `ScreenHandlersRegistry`, `EffectsRegistry`, `EventsRegistry`, `LootConditions`,
  `EtherParticleTypes`, `TreesRegistry`, and `WorldGenRegistry`.
- Eager helpers: `RegistrableBlock` and `EBlock`.

Implementation:

- Make Architectury deferred registries the single declaration path.
- Add common compile/annotation-processing support for Lombok before moving Lombok-owned classes.
- Add loader-matched GeckoLib common and Forge dependencies before moving Gecko-owned behavior.
- Add MixinExtras explicitly to Forge before enabling any retained MixinExtras injector.
- Keep creative tabs, fuel registration, reload listeners, lifecycle hooks, and trade hooks on
  Architectury APIs where those APIs preserve the required behavior.
- Use a Forge brewing registration adapter, Forge or vanilla wood/block-set registration, and a
  narrow platform hook for the custom sculk frequency.
- Register commands through Architectury's command event or Forge `RegisterCommandsEvent`.

Access and mixin blockers:

- Peach signs require either the current valid-block mutation in `BlockEntityTypeMixin` or custom
  sign and hanging-sign block-entity types.
- Peach flammability requires Forge block-state flammability hooks, a platform helper, or one
  narrow access transformer. The complete Fabric access widener must not be imported.
- Block-entity builders must receive resolved blocks only after the relevant suppliers exist.

Proof:

- Exact Fabric/Forge registry-ID manifest comparison.
- Duplicate-ID and premature-supplier-resolution unit tests.
- Dedicated-server datapack startup with no missing registry or codec errors.
- `/give`, placement, save, and reload smoke coverage for every ID in the accepted catalog.

This slice blocks broad content migration. The already-started storage mechanic may finish first
because its distinct shared declarations are intentionally bounded; those declarations must be
folded into the authoritative catalog during convergence.

### Slice 1: Forge state and capability backend

Source owners:

- `EtherologyComponents` and its four handles:
  - `CORRUPTION` on chunks.
  - `ETHER` on living entities.
  - `TELDECORE` on players.
  - `VISITED` on players.
- `CorruptionComponent`, `EtherComponent`, `TeldecoreComponent`, and `VisitedComponent`.
- The existing Fabric Cardinal Components adapters, which remain Fabric-only.

Forge implementation:

- Register native capabilities with `CapabilityManager`, `CapabilityToken`, and
  `RegisterCapabilitiesEvent`.
- Attach serializable providers through `AttachCapabilitiesEvent<Entity>` and
  `AttachCapabilitiesEvent<LevelChunk>`.
- Invalidate every `LazyOptional` at owner invalidation.
- Copy the intended player state through `PlayerEvent.Clone`. Teldecore and Visited must retain
  their Fabric `ALWAYS_COPY` behavior. Ether death behavior must be decided from the original
  behavior and tested rather than inferred.
- Tick living/player state from Forge tick events.
- Track loaded server chunks through chunk load/unload events and tick their corruption state at
  the end of the level or server tick. Forge has no native per-chunk capability tick event.
- Sync initial login, start-tracking, respawn, and every state mutation to client-side replicas.

Alternative:

Corruption can be refactored into level-owned `SavedData` with its own scheduler. That is a larger
semantic change. A loaded-chunk tracking set is closer to the current component behavior, but its
tests must prove that unloaded chunks stop ticking and are removed from the set.

Save compatibility risk:

Cardinal Components and Forge capabilities use different outer serialization containers. If a
world must move between loaders, define a stable Etherology-owned compound and explicit migration.
Equivalent values inside different loader containers are not cross-loader save compatibility.

Proof:

- NBT and copy unit tests for all four state types.
- Player death/respawn and relog checks.
- Chunk save, unload, reload, and no-unloaded-tick checks.
- Initial and mutation sync with a second client.
- Client HUD checks that read only the synchronized replica.

### Slice 2: shared packet transport

Source owners:

- `EtherologyNetwork`, `AbstractC2SPacket`, and `AbstractS2CPacket`.
- S2C packets: `StartBlockAnimS2C`, `StopBlockAnimS2C`, `SwitchBlockAnimS2C`,
  `RedstoneLensStreamS2C`, and `RemoveBlockEntityS2C`.
- C2S packets: `StaffMenuSelectionC2S`, `StaffTakeLensC2S`, `QuestCompleteC2S`, and the four
  Teldecore selected/page/tab/opened values owned by `EntityComponentC2SType`.
- Client owners: `EtherologyNetworkClient`, `S2CHandlers`, and `ClientEtherProxy`.

Implementation:

- Move payload codecs and handlers into shared records.
- Use Architectury `NetworkManager.registerReceiver`, `sendToPlayer`, `sendToPlayers`, and
  `sendToServer`.
- Queue every world or entity mutation through `PacketContext.queue`.
- Introduce a two-loader tracking-recipient boundary. Fabric uses `PlayerLookup`; Forge uses
  tracking entity/chunk recipients through `PacketDistributor` or an equivalent native query.
- Never substitute a dimension-wide broadcast for tracking recipients.
- Keep all C2S operations server-authoritative. Validate staff ownership and contents, enum
  ordinals, page values, identifier sets, and quest eligibility.

Proof:

- Round-trip and malformed-payload tests for all 12 logical channels.
- Wrong-side and scheduling tests.
- Malicious C2S rejection tests.
- Two-client tests for tracking range, sender inclusion, and exclusion variants.
- Native storage animation and Redstone Lens particle evidence when their owning slices land.

### Slice 3: complete ethereal storage — bounded milestone accepted

The deliberately bounded shared-foundation vertical in this slice is implemented and accepted.
Its declarations remain temporary owners that must be folded into the authoritative registry spine
during convergence; acceptance here does not establish parity for the channel/network graph or any
other Forge gameplay slice.

Source owners:

- Shared foundation: `EtherealStorageFoundationBlock` and
  `EtherealStorageFoundationBlockEntity`.
- Canonical behavior reference: `EtherealStorageBlock`, `EtherealStorageBlockEntity`, and
  `EtherealStorageScreenHandler`.
- Shared registries: `SharedBlocks`, `SharedBlockEntities`, `SharedItems`, the new
  `SharedScreenHandlers`, and `EtherologyBootstrap`.
- Supporting behavior: `ListBackedInventory`, `TypedSlot`, `ClosedSlot`, `GlintItem`,
  `EtherGlint`, `ComponentTypes.STORED_ETHER`, and the minimal Ether storage/counter contract.
- Forge client completion: `EtherealStorageFoundationScreen`,
  `EtherealStorageFoundationModel`, `EtherealStorageFoundationRenderer`, the canonical Gecko
  resources, and Gecko's built-in trigger synchronization. This vertical does not add a parallel
  Etherology animation packet.

Mechanic contract:

- Persist `storage_ether` and exactly four item slots.
- Slots zero through two accept the storage input item; slot three is server-maintained display
  state and cannot be directly modified by the player.
- `Inventory.clear()` clears those three real inputs while retaining `storage_ether` and rebuilding
  the derived display slot immediately.
- Persist per-Glint Ether under `etherology:components/stored_ether`, bounded to 128 per Glint.
  Charge at most one Ether every fifth server tick, fill input slots from zero through two, and
  keep the display slot derived from internal Ether only.
- Open the menu only from the logical server and let the block entity own the inventory.
- Implement correct quick-move, viewer lifecycle, dirty marking, and break-drop behavior.
- Expose vanilla sided inventory behavior plus cached Forge `ITEM_HANDLER` wrappers for the
  unsided view and all six faces. Expose only the three insertion slots, reject extraction, and
  invalidate every `LazyOptional` with the block entity.
- Register the menu with a shared deferred `ScreenHandlerType`. Register its Forge client screen
  from enqueued client setup and keep the class distribution-safe.
- Drive one shared Gecko controller from first-viewer open and final-viewer close, using Gecko's
  tracking synchronization for the open and close triggers.

Accepted bounded proof:

- Ether and four-slot NBT round trip.
- Typed slot insertion and display-slot rejection, compiled quick-move structure, and Forge
  item-handler automation behavior.
- Viewer-count transitions, first/final-viewer guards, and native single-viewer animation.
- Compiled logical-server break/drop ownership for the three input slots.
- Place, open, insert, mutate ether, save, restart, and reopen in the native E2E scenario.
- Before/after menu captures.

Still required before full storage parity can contribute to release readiness:

- a live hopper insertion sequence;
- two concurrent real viewers proving first-open and final-close behavior;
- a native block break proving that the three real inputs drop exactly once.

The positive `validateForgeStorageParityMilestone` now covers the accepted bounded Common and Forge
proof above; it does not claim the three deferred live checks. The packaged `ethereal-storage`
scenario adds a real isolated Forge 47.4.9 client and
integrated-world run: 34 of 34 assertions passed in 438 client ticks, with five native framebuffers,
forced save, disconnect, restart, exact persistent-state comparison, and menu reopen. Its frozen
record is the
[`Forge 1.20.1 runtime evidence`](../evidence/forge-1.20.1/README.md).

`validateForgeChannelNetworkMilestone` is now the first incomplete stage in the release dependency
graph. The unconditional `validateForgeReleaseReadinessMilestone` follows it and cannot be
satisfied by channel-shaped class or method stubs, or by reusing the bounded storage evidence. The
release graph must never remove or relax either stage merely to allow `remapJar`.

### Slice 4: static content and storage utilities

Source owners:

- Static content: `DecoBlocks`, `ExtraBlocksRegistry`, and the `beamer`, `peach`, `thuja`, and
  `forestLantern` block packages.
- Utility/storage content: the `furniture`, `closet`, `shelf`, `crate`, `jug`, `spill_barrel`,
  `samovar`, `tuningFork`, and `arcanelightDetector` block packages.
- Closet and Crate menu handlers plus their client screens and renderers.

Implementation:

- Declare all block, item, block-entity, and menu IDs in the authoritative shared catalogs.
- Keep the Forest Lantern jump callback on Architectury's event.
- Use a narrow Forge adapter for tuning-fork sculk frequency behavior.
- Expose Forge item handlers for real inventories.
- Register GeckoLib renderers on the Forge client when required.

Proof:

- The `storage-utilities` E2E scenario covers every interaction, inventory save/drop behavior,
  redstone/game-event behavior, vanilla hopper behavior, Forge capability interoperability, and
  before/after captures.

### Slice 5: Ether graph and machines

Source owners:

- `EtherStorage`, `EtherPipe`, `EtherFork`, `EtherDisplay`, `EtherCounter`, and `EtherGlint`.
- The `etherealChannel`, `etherealFork`, `etherealSocket`, `etherealFurnace`, `generators`, and
  `levitator` block packages, plus `ChannelCase` and `ChannelShapes`.
- The Ethereal Furnace menu.
- `EtherSourceLoader`, `EtherSources`, and their deserializer.

Implementation:

- Keep Ether as Etherology's own unit and transfer semantics. Forge Energy is not a compatible
  replacement.
- Use the shared reload listener for Ether source data.
- Use the tracking-recipient packet boundary for particles and Gecko animations.
- Register every machine block entity, menu, particle, renderer, and codec through the shared
  catalogs and Forge client events.

Dependencies:

Storage, shared packets, deferred block-entity registration, and the Forge client registration
spine must exist first.

Proof:

- Fixed-tick storage to channel to fork to socket/furnace flows.
- Direction, redstone gate, cross-evaporation, generator stall/restart, and glint fill/drain.
- Ether conservation across chunk unload/reload.
- Server/client state, particles, and animations with two clients.
- The complete `ether-network` E2E scenario.

### Slice 6: recipes, synced data, aspects, progression, and workstations

Registry/data owners:

- `RecipesRegistry`, `FeyRecipe`, `FeyInputRecipe`, `FeyRecipeSerializer`, and every runtime
  serializer/type in `recipes/alchemy`, `recipes/empower`, `recipes/jewelry`, `recipes/matrix`,
  and `recipes/staff`.
- `ResourceReloaders`, `RegistriesRegistry`, `AspectsLoader`, and `magic/aspects`.
- Teldecore `data`, `task`, and `content` owners.

Mechanic owners:

- The `brewingCauldron`, `pedestal`, `sedimentary`, `empowerTable`, `matrix`, `inventorTable`,
  `jewelryTable`, and `seal` block packages.
- Their menu handlers, screens, renderers, sounds, and particles.

Forge implementation:

- Deferred-register recipe serializers and recipe types.
- Register ASPECTS, CHAPTERS, and TABS in `DataPackRegistryEvent.NewRegistry` with both the data
  codec and network codec.
- Invalidate aspect caches from a common reload listener or a Forge datapack reload hook.
- Read synchronized client registries through the active client connection registry manager.

Risk:

An unsynchronized datapack registry can appear correct on the server while breaking Teldecore and
Oculus on clients. All three registries are required client data.

Proof:

- Serializer round-trip and malformed-data tests.
- `/reload`, recipe matching, consumption, cancellation, and result tests.
- Client registry synchronization and reconnect tests.
- Server-authoritative quest completion and persistence.
- Native `alchemy`, `armillary`, `seals-aspects`, `staff-lenses`, and `progression-oculus`
  scenarios.

Recipe builders and `FeyRecipeJsonProvider` are datagen tooling. They do not block Forge runtime.

### Slice 7: items, combat, equipment, entities, and boats

Source owners:

- `EItems`, `ToolItems`, `ArmorItems`, all runtime `item` classes, and the `staff`, `lens`, and
  `corruption` magic packages.
- `EntityRegistry`, `RedstoneChargeEntity`, the peach boat helpers, and boat mixins.
- Redstone Charge renderer, arm poses, Prana Vision renderer/HUD, staff UI, and staff model.

Forge implementation:

- Reimplement `IronShield` on vanilla `ShieldItem` and Forge tool-action/item hooks. Retain the
  Etherology shield cone and reflection behavior. Fabric Shield Lib is not a Forge dependency.
- Use Curios Forge for the Prana Vision face/head slot, ticking, rendering, save, and death
  behavior. A vanilla helmet redesign is the only dependency-free alternative and is not
  behaviorally equivalent.
- Spawn Redstone Charge through Architectury's add-entity packet or a Forge entity spawn packet.
- Replace Fabric enum injection with Forge `UseAction.CUSTOM`,
  `BipedEntityModel.ArmPose.create`, and `IClientItemExtensions.getArmPose`.

Boat decision:

`BoatEntity.Type` is not a Forge extensible enum. Either retain and aggressively smoke-test the
current enum-values/codec/BY_ID mixin, or replace peach boats with custom entity types. The custom
entity route is larger but safer. The decision must be explicit before implementation.

Proof:

- Shield angle, projectile reflection, axe behavior, and enchantment behavior.
- Two-handed and staff poses in first and third person.
- Staff construction, lens insertion/removal/mode, malformed packet rejection, and item NBT
  persistence.
- Prana Vision equip, relog, death, tick, and rendering.
- Redstone Charge spawn, tracking, save, and reload.
- Peach and chest boat placement, passenger behavior, save, and reload.
- Native `combat-equipment` and `staff-lenses` scenarios.

### Slice 8: world generation

Source owners:

- `WorldGenRegistry`, `TreesRegistry`, `BiomesGen`, `ConfiguredFeaturesGen`,
  `PlacedFeaturesGen`, and `StructuresGen`.
- Custom feature, placement modifier, tree placer/decorator, and pool-element classes.
- Promoted worldgen JSON and structure NBT resources.

Forge implementation:

- Deferred-register custom `Feature`, placement modifier, trunk placer, foliage placer, tree
  decorator, and structure-pool element codecs.
- Use Forge biome modifier JSON for key/tag based feature additions.
- Use a custom Forge `BiomeModifier` codec for exact temperature predicates.
- Use Forge's extensible grass-color enum hook instead of Fabric ASM and the grass access widener.

Golden Forest decision:

Plain Forge biome modifiers cannot reproduce Biolith's 30-percent replacement of Birch Forest and
Old Growth Birch Forest. Choose one approach and document its expected parity:

1. TerraBlender Forge climate insertion. This is maintainable, but fixed-seed distribution will
   not be byte-for-byte identical.
2. A Forge-specific biome-source/climate mixin. This can approach the original replacement rule,
   but is a high-risk core-worldgen injection.

The port must not claim exact distribution parity if it chooses TerraBlender.

Proof:

- Fixed-seed coordinate and distribution audit over new chunks.
- Attrahite, Thuja, every seal biome/temperature case, Golden Forest, peach trees, bonemeal, and
  monolith placement/rotation.
- Save/reload and native screenshots in the `golden-forest` scenario.

### Slice 9: Forge client foundation and completion

The registration spine should be established early, while each preceding mechanic adds its own
screen or renderer only when its server behavior is accepted.

Source owners:

- `EtherologyClient`.
- `BlockRenderersRegistry`, `ScreenRegistry`, `BlockRenderLayerMapRegistry`,
  `ClientParticleRegistry`, `ColorProvidersRegistry`, `EntityClientRegistry`,
  `KeybindsRegistry`, `RenderingRegistry`, `EventsClientRegistry`, and `ModelPredicates`.
- Loader-neutral screens, particles, Gecko renderers, entity renderers, and block-entity
  renderers.
- `SealBlockRenderer`, `RevelationRenderer`, `OcularOverlay`, `StaffIndicator`, and the Teldecore
  and Oculus UI.

Forge event replacements:

- `FMLClientSetupEvent` with enqueued menu-screen, model-predicate, and render-layer setup.
- `EntityRenderersEvent.RegisterRenderers`.
- `RegisterParticleProvidersEvent`.
- Block and item color registration events.
- `RegisterKeyMappingsEvent`.
- `RegisterGuiOverlaysEvent` and the matching overlay render events.
- Forge client tick events.
- `RenderLevelStageEvent`, with the chosen stage verified against Fabric's final world-render
  behavior.
- `ModelEvent.RegisterAdditional` and `ModelEvent.ModifyBakingResult`.

High-risk model work:

`EtherologyModelProvider`, `MultiItemModel`, and `StaffModel` depend on Fabric's model-loading and
renderer APIs. Forge must register every staff part, then install a Forge delegating `BakedModel`
that selects and composes the parts through item overrides or render passes. FabricBakedModel must
not be compiled into Forge.

UI replacements:

- Rewrite Owo-owned `AspectComponent`, `OculusItemClient`, `ScaledLabelComponent`, and
  `LensWidget` with vanilla widgets and draw APIs.
- Replace `FabricLoader.isModLoaded` with Architectury `Platform.isModLoaded`.
- Replace every direct `ClientPlayNetworking` send with the shared transport.

Proof:

- Missing-model, missing-texture, and fatal client-log gate.
- Resource reload and GUI-scale checks.
- Normal and fabulous graphics checks.
- First-person and third-person equipment/staff captures.
- Every menu, HUD, particle, entity renderer, block-entity renderer, and world overlay.
- A render-thread crash-free soak after all scenario captures.

## Dependency policy

Required for Forge gameplay parity:

- Forge 1.20.1-47.4.9.
- Architectury API 9.2.14.
- GeckoLib 4.8.4 for the shared compile API and Forge GeckoLib 4.7.4 at runtime, constrained by
  metadata to the compatible `[4.7.4,5)` range.
- MixinExtras 0.5.0 whenever retained MixinExtras injectors are enabled.
- Lombok 1.18.36 as compile-only plus annotation processor for common sources that use it.
- Curios Forge for Prana Vision parity.

Conditional:

- TerraBlender Forge only if the maintainable Golden Forest climate-insertion option is selected.

Fabric-only implementations that must not become Forge runtime dependencies:

- Cardinal Components adapters.
- Trinkets.
- Fabric Shield Lib.
- Biolith.
- Fabric ASM/ClassTinkerers.
- Owo registration/UI adapters.
- MixinSquared compatibility.

Optional integrations:

- `client/compat/rei` contains 18 classes. Exclude it from the initial Forge source and runtime;
  later add Forge plugin discovery with optional/compile-only dependency metadata.
- `client/compat/emi` contains 12 classes and follows the same policy.
- `client/datagen` contains 13 Fabric datagen classes. Fabric remains the asset generator and
  Forge consumes the promoted JSON. A Forge datagen lane is required only for Forge-only generated
  resources, not runtime.
- Cloth Config, Mod Menu, and MidnightLib are not required gameplay dependencies.

## Forge mixin policy

Forge currently expects `etherology.forge.mixins.json`, but that file does not yet exist. Create a
minimal Forge-owned configuration. Do not include either broad Fabric configuration unchanged.

Retain initially where no clean Forge hook preserves the behavior:

- Main: `AbstractBlockStateMixin`, `BlockEntityTypeMixin`, `BoatEntityMixin`,
  `BoatEntityTypeAccessor`, `BoatEntityTypeMixin`, `ChestBoatEntityMixin`,
  `DispenserBlockMixin`, `GrassBlockMixin`, `ItemStackMixin`, `LivingEntityAccessor`,
  `LivingEntityMixin`, `PlayerEntityMixin`, `RedstoneViewMixin`, `RedstoneWireBlockMixin`,
  `VibrationListenerAccessor`, and `WallMountedBlockMixin`.
- Client: `GameRendererAccessor`, `GameRendererMixin`, `HeldItemRendererMixin`,
  `InGameHudMixin`, and `PlayerHeldItemFeatureRendererMixin`.

Prefer Forge APIs and remove these injections when the owning slice is ported:

- `AxeItemMixin`: Forge tool actions or modified-state hook.
- `EnchantmentHelperMixin` and `EnchantmentMixin`: Forge item/enchantment applicability hooks.
- `EntityHitResultAccessor` and `ProjectileEntityMixin`: `ProjectileImpactEvent` where it preserves
  the exact behavior.
- `FlowerPotBlockMixin`: reject the seed from its item behavior.
- Registry invokers for foliage, trunk, decorator, placement, and structure-pool element types:
  deferred registration through accessible Forge constructors.
- `ShearsItemMixin`: tool action/mining hooks.
- `SmithingTemplateItemAccessor`: Etherology-owned tooltip text and formatting constants.
- `AbstractClientPlayerEntityMixin`: Forge FOV event.
- `BipedEntityModelMixin` and `PlayerEntityRendererMixin`: Forge arm poses and item client
  extensions.
- `ClientPlayerInteractionManagerMixin`: Forge reach attributes.
- `DrawContextAccessor`, `HandledScreenMixin`, and `ScreenAccessor`: Forge tooltip and container
  screen events.
- `InGameHudAccessor`: Forge GUI overlay drawing.
- `ItemRendererMixin` and `ModelLoaderMixin`: Forge model events and baked-model render passes.
- `KeyBindingAccessor` and `StickyKeyBindingAccessor`: owned movement mappings and public key APIs.
- `TessellatorAccessor`: public Forge tessellator/buffer API.
- `TooltipComponentMixin`: `RegisterClientTooltipComponentFactoriesEvent`.

Exclude from Forge:

- `FabricShieldLibMixin` and `FabricShieldLibClientMixin`.
- `EarlyRisers`.
- `EMixinCanceller`.
- The Fabric grass-color enum bridge in `GoldenForestGrassModifier`.

Dead or datagen-only mixin owners must not enter the Forge runtime config:

- Confirmed dead: `DefaultedListAccessor`, `ModelAccessor`, `WorldRendererAccessor`,
  `TagEntryAccessor`, and `TagGroupLoaderMixin`. `TagExcludeUtil` is deprecated and has no caller
  that populates its exclusions.
- Datagen-only: `BlockFamilyMixin`, `BlockTexturePoolMixin`,
  `OverworldBiomeCreatorAccessor`, and `TreeConfiguredFeaturesAccessor`.

High-risk retained targets require mandatory-injection startup checks on both a dedicated server
and client. The most fragile are boat enum initialization, player/living combat local captures,
the redstone injections, dispenser behavior, `GrassBlockMixin`, and private render-pipeline
injections.

## Access widener replacement

The Fabric access widener contains runtime access for:

- `InGameHud.HeartType`.
- Bundle tooltip row and column methods.
- `FireBlock.registerFlammableBlock`.
- `KeyBinding.boundKey`.
- `BiomeEffects.GrassColorModifier`.

It also contains unused or datagen-only access for `Model.createTextureMap` and
`KeyBinding.timesPressed`.

Prefer Forge enum, tooltip, input, block flammability, and overlay APIs. If a runtime member still
requires access after the owning feature is rewritten, add only that member to
`META-INF/accesstransformer.cfg`. Forge currently neither transforms nor packages the Fabric
access widener.

## Test and release-gate discipline

Every slice has four levels of proof:

1. Pure unit tests for codecs, arithmetic, state transitions, malformed input, and NBT.
2. Loader integration tests for registrations, lifecycle, resource packaging, collision safety,
   and canonical Fabric byte preservation.
3. Native dedicated-server and client startup checks for registries, datapacks, mixins, and
   classloading.
4. The matching packaged E2E scenarios and screenshots from the E2E contract.

The ordered forward gates advance in the same change that completes a positive milestone:

1. Require the just-completed mechanic positively.
2. Keep an explicit next concrete incomplete mechanic in the release dependency graph.
3. Make `verifyForgePortGateClosed` report the first incomplete stage without making that
   diagnostic task an artifact dependency.
4. Keep `validateForgePortInputs`, `remapJar`, and all publishing tasks dependent on every positive
   gate and every declared forward gate, including the unconditional final native-readiness
   backstop.

Current positive gates run real Common and Forge tests/compile and inspect compiled classes; they
do not rely solely on source/comment tokens. Compilation, deterministic bytecode structure, and
resource presence still cannot replace native gameplay evidence. Conversely, native evidence
cannot excuse missing deterministic unit and integration tests.

The final release-readiness task intentionally fails unconditionally until all required slices
pass, a dedicated server starts cleanly, an isolated Forge client completes the required scenario
matrix, fixed-seed worldgen checks pass, and no fatal mixin, access, model, registry, codec, or
synchronization marker remains
in the logs.

## Decisions that must be explicit

Before the relevant slice begins, record the selected alternative and its accepted parity:

1. Golden Forest: maintainable TerraBlender climate insertion or high-risk biome-source mixin.
2. Prana Vision: required Curios integration or a consciously incompatible vanilla-equipment
   redesign.
3. Peach boats: fragile vanilla boat-enum extension or custom boat entity types.
4. Component saves: loader-local persistence only or explicit Cardinal Components to Forge
   capability migration.
5. Staff rendering: native Forge composite baked model; there is no valid direct Fabric renderer
   reuse.

None of these decisions can be hidden behind a successful compile or an approximation described
as exact parity.
