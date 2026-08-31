# Forge 1.20.1 bootstrap, storage, channel, sound, registry, Ether-source, enchantment, and particle status

The Forge lane now has a native JavaFML entry point, a loader-neutral lifecycle handshake, and
accepted bounded item, storage, channel, sound-registry, game-event, loot-condition,
Ether-source reload, enchantment-registry/server, and particle-registry/server milestones.
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
serializer with a synthetic server loot table. `SharedParticleTypes` now owns the exact 22
particle IDs and their server-side payload/codec/wire contracts. Channel cases, client particle
factories and rendering, the remaining loot and recipes, the wider machine/network graph, most of
the authoritative registry spine, enchanting applicability and combat behavior, and a playable
Etherology port remain incomplete.
The lane cannot produce or publish a release artifact.

The JavaFML entry point first exposes its mod event bus through Architectury's `EventBuses`, then
`EtherologyBootstrap` attaches the shared block, item, block-entity, screen-handler, sound-event,
game-event, loot-condition, enchantment, and particle registries during `@Mod` construction, before Forge
registry events run. It delegates
handshake idempotence and failure state to `BootstrapLifecycle`. `PlatformRegistrar` supplies the
loader boundary. The native `ForgePlatformRegistrar` listens for `FMLCommonSetupEvent` and uses
`enqueueWork` so the handshake runs on Forge's setup work queue instead of its parallel
event-dispatch thread.

Common also owns the `EtherSourceLoader` server-data listener and its default
`ether_sources` resources. `EtherologyBootstrap` installs the Common
`ResourceReloaders` path for Forge, while Fabric retains only its canonical
initializer call into that same idempotent registration. Neither loader owns a
copied listener implementation or shadow default-data resource.

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
- `validateForgeEtherSourceReloadStaticMilestone` is expected to pass. It requires the historical
  registry-foundation gate and proves Common-only listener/deserializer/default-data ownership,
  both loader bootstrap paths, exact packaged resources, and the isolated reload-probe contract.
- `verifyRegistryFoundationServerProbe` is expected to pass without launching Minecraft. It builds and
  tests the server-only probe, validates its Java 17 Loom configuration and exact `main` plus
  probe-mod binding, requires the dev-launch-injector bootstrap, and proves that probe classes do
  not enter the production artifact. The actual launch is owned by
  `scripts/e2e/forge_server.py run`, which invokes
  `:forge:1.20.1:runRegistryFoundationServerProbe` inside the isolated profile.
- `validateForgeRegistryFoundationServerEvidenceArchiveIntegrity` is expected to pass. It runs
  the 63 runner/verifier safety tests and validates the immutable five-file v4 archive independently
  of the ignored live runtime.
- `validateForgeRegistryFoundationMilestone` is the historical foundation aggregate. It requires the
  sound, game-event, and loot-condition artifact proofs, current probe isolation checks, and frozen
  dedicated-server evidence before the broader authoritative registry gate may run.
- `validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity` is expected to pass. It validates
  the immutable five-file v6 archive and its schema-4, 72-assertion runner/verifier contract without
  consulting the ignored live runtime.
- `validateForgeEtherSourceReloadMilestone` is an accepted predecessor gate. It requires the
  static Ether-source ownership/resource proof, the inherited registry foundation, and the frozen
  v6 native reload evidence. That archive and its v7 successor are historical now that v10
  supplies the current cumulative dedicated-server proof.
- `validateForgeEnchantmentRegistryStaticMilestone` is expected to pass. It proves
  `SharedEnchantments` is the sole Common deferred owner of `etherology:peal` and
  `etherology:reflection`, preserves their exact concrete classes and properties, requires
  Fabric's direct initializer attachment and Forge's Common-bootstrap attachment, and validates
  the exact singular `data/minecraft/tags/enchantment/non_treasure.json` resource across loader
  artifacts.
- `validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity` is expected to pass. It runs
  the v7 runner/verifier safety suite and validates the immutable five-file schema-5,
  95-assertion archive without consulting ignored live runtime state.
- `validateForgeEnchantmentRegistryMilestone` is an accepted predecessor gate. It requires the
  static/resource proof and historical frozen v7 native reload evidence.
- `validateForgeParticleRegistryStaticMilestone` is expected to pass. It proves
  `SharedParticleTypes` is the sole Common deferred owner of the exact 22 canonical IDs, preserves
  the eight payload families, codecs, command/packet formats, seal metadata, and
  `shouldAlwaysSpawn = false`, and checks Common/Fabric/Forge bytecode and artifact isolation.
- `validateForgeParticleRegistryServerEvidenceArchiveIntegrity` is expected to pass. It runs the
  v10 runner/verifier safety suite and validates the immutable five-file schema-6,
  138-assertion archive without consulting ignored live runtime state.
- `validateForgeParticleRegistryMilestone` is the current combined positive gate. It requires the
  static/bytecode/artifact proof and frozen v10 native reload evidence before the remaining
  authoritative registry gate may run.
- `validateForgeChannelCurrentArtifactDiagnostic` is deliberately not an acceptance dependency.
  It now fails because the later sound milestone changed the whole production JAR relative to the
  Channel capture. That expected byte mismatch is not a Channel regression and does not establish
  current equality.
- `verifyForgePortGateClosed` is expected to pass. It is a diagnostic task, not an artifact gate:
  after requiring every accepted positive gate, it reports the first incomplete forward stage.
  That stage is now the broader authoritative registry spine; the bounded sound, game-event,
  loot-condition, Ether-source reload, enchantment-registry, and particle-registry steps are no longer in its
  missing-condition list.
- `validateForgeReleaseReadinessMilestone` is a permanent final backstop after every bounded
  forward gate. It fails unconditionally until the full gameplay graph and complete packaged
  native Forge client, dedicated-server, persistence, and E2E matrix are implemented and the task
  itself is replaced by concrete acceptance checks. The bounded Storage and Channel runs, static
  sound gate, registry-foundation dedicated-server proof, bounded Ether-source reload proof, and
  bounded enchantment-registry and particle-registry proofs are not sufficient. Class or method-name
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
The immutable v2 game-event-only archive remains historical evidence, and v4 superseded it as the
registry-foundation proof. The v6 Ether-source reload and v7 enchantment-registry archives also
remain historical evidence; v10 is the current cumulative dedicated-server proof.

The probe observes `ServerStoppedEvent` and atomically publishes its report before scheduling a
probe-only terminator. That path joins the actual stopped-event server thread before `System.exit`
and works around a proven non-daemon Loom-userdev thread leak that would otherwise keep Gradle
alive; it does not enter the production artifact. The external runner bounds and validates the
process log, server log, report, saved world, lifecycle, crash state, completion-marker order, and
exit result.
The earlier Fabric `v20` packaged startup evidence predates this registry rebuild and therefore
does not claim equality with the current Fabric production JAR.

## Accepted bounded Ether-source reload milestone

`EtherSourceLoader`, `EtherSources`, the deserializer, `ResourceReloaders`, and
the default `data/etherology/ether_sources/default.json` resource now have one
Common implementation/resource owner. The default data contains exactly 23
entries and corrects the legacy item ID to
`etherology:primoshard_rella = 4`; `minecraft:redstone` starts at `2`. Fabric's
canonical initializer and Forge's Common bootstrap install the same idempotent
listener. The static gate rejects copied loader implementations, duplicate
registration, shadow resources, and probe content in production artifacts.

That artifact proof is paired with the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v6` Loom-userdev profile. Its headless
`ether-source-reload` scenario ran the real
`:forge:1.20.1:runRegistryFoundationServerProbe` task on Java 17 and Forge
47.4.9. Report schema 4 passed 72 of 72 ordered assertions. The initial map was
exactly the Common-owned 23 entries. A probe pack then overrode redstone and
added diamond through a real `reload` command; the completed map contained
exactly 24 entries with `minecraft:redstone = 9.5` and
`minecraft:diamond = 13`.

The game-event registry and its two exact tags remained stable. The
loot-condition registry and evaluated behavior remained stable while the probe
`LootTable` instance was replaced, as expected for a real reload. The world was
saved, the server completed normal `stop(false)`, the launcher exited with code
zero, and the copied log contains no `ERROR` or `FATAL` marker. No screenshots
were created or claimed because this is a headless dedicated-server proof.

The immutable
[`ether-source-reload-server-v6`](../evidence/forge-1.20.1/ether-source-reload-server-v6)
archive records profile-manifest SHA-256
`2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0`,
server-log SHA-256
`0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`,
and archive-manifest SHA-256
`6d552536f74c018ce56e238fcb5a3aacd8fa363c76293863514adf9d7bafc2e0`.
This v6 archive remains immutable historical evidence. Its exact Ether-source and inherited
registry assertions are re-proved by the cumulative v7 and current v10 records below.
It does not prove furnace or machine consumption, the wider Ether network, the
full authoritative registry, native sound playback, Forge custom sculk
frequency, Attrahite drops, or release readiness.

## Accepted bounded SharedEnchantments registry and server milestone

`SharedEnchantments` is the sole Common deferred owner of exactly `etherology:peal` and
`etherology:reflection`. Their concrete classes are exactly
`ru.feytox.etherology.registry.misc.PealEnchantment` and
`ru.feytox.etherology.registry.misc.ReflectionEnchantment`. Peal is `COMMON`, targets `WEAPON`,
uses `MAINHAND`, has maximum level 3, minimum powers `[1, 12, 23]`, and maximum powers
`[21, 32, 43]`. Reflection is `COMMON`, targets `BREAKABLE`, uses `MAINHAND` and `OFFHAND`, has
maximum level 1, minimum power `[1]`, and maximum power `[21]`.

Fabric directly attaches `SharedEnchantments` from its canonical initializer without invoking
`EtherologyBootstrap`; Forge attaches the same owner through that Common bootstrap before registry
events. Fabric's retained `EtherEnchantments` owns policy/gameplay consumption only, not
registration or construction. The static gate checks that ownership and supplier lifecycle across
the Common JAR, both loader-transformed Common JARs, Fabric development/remapped production JARs,
and Forge shadow JAR. Both loader artifacts package the exact additive singular resource
`data/minecraft/tags/enchantment/non_treasure.json`, whose runtime tag ID is
`minecraft:non_treasure`, with `replace: false` and exactly Peal and Reflection.

That static/resource proof is paired with the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v7` Loom-userdev profile. Its headless
`enchantment-registry` scenario ran
`:forge:1.20.1:runRegistryFoundationServerProbe` on Java 17 and Forge 47.4.9. Report schema 5
passed 95 of 95 ordered cumulative assertions. The probe captured both enchantments after
server-data load, rechecked exact registry state at server start, performed a real `reload`, and
proved exact IDs, concrete classes, levels/powers, object/registry identity, and
`minecraft:non_treasure` membership remained stable. It also re-proved the earlier game-event and
tags, loot-condition registry and evaluated behavior, and initial/reloaded Ether-source maps.

The exact lifecycle was `tags_updated_initial > server_started > reload_requested >
tags_updated_reload > reload_command_returned > stop_requested > server_stopping >
server_stopped`. The world saved, normal `stop(false)` completed, the launcher exited zero, and
the copied log contains no `ERROR` or `FATAL` marker. No screenshots were created or claimed for
this headless dedicated-server proof.

The immutable
[`enchantment-registry-server-v7`](../evidence/forge-1.20.1/enchantment-registry-server-v7)
archive is the immutable historical enchantment-registry proof. It records profile-manifest SHA-256
`36b0f67d7ef55cd8e34aac92dd4e5866e17d5be4ffa91987793c913dd60f5773`, report SHA-256
`1f5209c53fab524db662e7e7ef8ba044ba773fc9800dc3d3086e840093dc5aef`, server-log SHA-256
`b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`, and archive-manifest
SHA-256 `377acc9241417a169ac2f9dbe1f555d5918509a486daf40d89533de4d414feec`.

This acceptance does not prove enchanting-table, anvil, book, or item applicability; Peal's
shockwave; projectile reflection; client visuals or screenshots; full combat parity; or release
readiness. Those remain explicit later consumer-mechanic and native-client/full-E2E obligations.

## Accepted bounded SharedParticleTypes registry and server milestone

`SharedParticleTypes` is the sole Common deferred owner of the exact 22 canonical particle-type
IDs. Fabric attaches it directly before the retained initializer path, while Forge attaches the
same owner through `EtherologyBootstrap` before registry events. The legacy eager
`EtherParticleTypes` owner is removed. The moved Common effect classes preserve all eight payload
families, their codecs and parameter factories, command and packet serialization, the exact seal
order/colors/textures, and `shouldAlwaysSpawn = false`. `ItemParticleEffect` parses its item ID
with `Identifier.fromCommandInput`, including namespaced values such as `minecraft:diamond`.

`validateForgeParticleRegistryStaticMilestone` checks declaration multiplicity, construction,
supplier lifecycle, parser bytecode, consumer access, and class/artifact isolation across Common,
both loader-transformed Common artifacts, Fabric development/remapped production, and the Forge
shadow artifact. `validateForgeParticleRegistryMilestone` combines that proof with the immutable
[`particle-registry-server-v10`](../evidence/forge-1.20.1/particle-registry-server-v10) archive.

The fresh repository-owned `etherology-e2e-forge-server-1.20.1-v10` profile ran the headless
`particle-registry` scenario through `:forge:1.20.1:runRegistryFoundationServerProbe` on Java 17
and Forge 47.4.9. Report schema 6 passed all 138 ordered assertions. The exact 22 IDs, type
classes, spawn flags, codecs, factories, sample strings, command/packet/codec round trips, and seal
metadata matched after server-data load, at server start, and after a real `reload`. The cumulative
run also re-proved the earlier enchantment, game-event/tag, loot-condition, and Ether-source
contracts. The world saved, `stop(false)` completed, the launcher exited zero, and the copied log
contains no fatal or client-startup marker.

The archive binds profile-manifest SHA-256
`5b3def0df2aacfea5db04b92975925c25223f117941ceb576cc6b3e6616f14e4`, report SHA-256
`ab829d182e648385f6052fea469bef3a18a6a972f0baa98be6b83569897f3d75`, server-log SHA-256
`44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`, and archive-manifest
SHA-256 `29fddf549c4b8728911fe1d048816d0353256ef7a7e533b62d0461249c485ed1`.

This milestone is server registry/wire proof only. It does not install or exercise Forge client
particle factories or renderers, emit particles through gameplay, or produce screenshots. Those
remain explicit native-client and mechanic-level E2E obligations.

## Forward fail-closed broader authoritative registry milestone

The next forward gate remains the rest of the authoritative registry spine. The temporary block,
item, block-entity, and screen-handler catalogs must converge into one active declaration owner per
canonical ID without shadowing Fabric classes or resolving suppliers during declaration. Entity,
recipe, effect, remaining game-event and loot consumers, tree, world-generation, and
lifecycle-hook ownership also remains incomplete. Enchantment and particle registry ownership are
no longer in this list; enchanting applicability, Peal shockwave behavior, projectile reflection,
and client particle rendering remain later gameplay/client obligations. The bounded
registry-foundation and Ether-source reload server proofs do not satisfy the full
registry/catalog placement-and-save smoke. The unfinished portions
of the Ether graph stay behind that work.

`validateForgeReleaseReadinessMilestone` remains behind the forward registry and gameplay gates
and keeps the artifact path closed for every subsequent dedicated-server, client, persistence, and
E2E slice.

## Remaining implementation blockers

- The common JAR contains component state/access contracts, the loader handshake,
  `etherology:ether`, the accepted bounded ethereal-storage vertical, and the accepted bounded
  ethereal-channel foundation. `SharedSounds`, `SharedGameEvents`, `SharedLootConditions`,
  `SharedEnchantments`, and `SharedParticleTypes` now own the accepted Common sound, resonance,
  Fortune-scaled condition, enchantment, and particle declarations;
  `ResourceReloaders` and `EtherSourceLoader` own the accepted Common Ether-source data path. The
  other temporary catalogs have not yet converged into the authoritative registry spine.
- The canonical initializer remains in the 322-file Fabric main source graph. Direct loader imports
  and their transitive ownership closure still require per-slice decisions as classes move to
  Common; the initial audit's raw import counts are no longer treated as a current snapshot.
- The canonical initializer directly reaches unported loader APIs for dynamic registries, the
  remaining reload lifecycle, networking, commands, block and wood type builders, brewing recipes,
  and biome modification. The Ether-source server-data listener is now Common-owned. Fabric's
  resonance frequency 10 is registered through its supported hook; Forge
  47 has no supported equivalent, so that custom frequency remains deferred.
- Component contracts are shared, but only Fabric component adapters exist. Forge capability
  adapters for Ether, corruption, Teldecore, and visited state do not exist yet.
- A minimal Forge-owned mixin configuration now supplies the accepted channel lever support. The
  remaining retained mixins and any narrowly required access-transformer entries still need to be
  selected and proven with their owning slices; the broad Fabric access widener is not packaged.
- The client tree has 211 Java files; 64 directly import Fabric, REI/EMI, owo, Biolith, Trinkets, or
  Fabric Shield Lib APIs and still need common-versus-loader ownership decisions.

The next gameplay slice is the broader authoritative registry spine after the accepted
SharedSounds, SharedGameEvents, SharedLootConditions, Ether-source reload, SharedEnchantments, and
SharedParticleTypes foundations. A release remains invalid until that gate, the full-catalog dedicated-server
placement/save smoke, enchanting applicability, Peal shockwave and projectile reflection behavior,
client visuals/screenshots, Forge particle factories/rendering, full combat, the deferred channel and sculk-frequency work, every
subsequent gameplay system, and the full native E2E matrix are ported and accepted.
