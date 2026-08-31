package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonElement;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.magic.seal.SealType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleType;
import ru.feytox.etherology.util.misc.RGBColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record ParticleProbeState(
        String captureError,
        List<String> etherologyParticleIds,
        Map<String, ParticleEntry> entries,
        List<String> sealTypeOrder,
        Map<String, SealTypeEntry> sealTypes,
        boolean sealTypeCodecRoundTripsExact
) {

    static final String PARTICLE_REGISTRY_ID = "minecraft:particle_type";
    static final String FEY_PARTICLE_TYPE_CLASS =
            FeyParticleType.class.getName();
    static final List<String> EXPECTED_PAYLOAD_FAMILIES = List.of(
            "electricity",
            "item",
            "light",
            "moving",
            "scalable",
            "seal",
            "simple",
            "spark"
    );
    static final Map<String, ParticleSpec> EXPECTED_PARTICLES = expectedParticles();
    static final List<String> EXPECTED_PARTICLE_IDS = EXPECTED_PARTICLES.values()
            .stream()
            .map(ParticleSpec::id)
            .toList();
    static final List<String> EXPECTED_SEAL_TYPE_ORDER = List.of(
            "EMPTY",
            "KETA",
            "RELLA",
            "VIA",
            "CLOS"
    );
    static final Map<String, SealTypeSpec> EXPECTED_SEAL_TYPES = expectedSealTypes();

    static ParticleProbeState capture() {
        List<String> particleIds = Registries.PARTICLE_TYPE.getIds().stream()
                .filter(identifier -> "etherology".equals(identifier.getNamespace()))
                .map(Identifier::toString)
                .sorted()
                .toList();
        Map<String, ParticleEntry> capturedEntries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        EXPECTED_PARTICLES.forEach((path, spec) -> {
            ParticleEntry entry;
            try {
                entry = captureEntry(spec);
            } catch (CommandSyntaxException | RuntimeException exception) {
                entry = ParticleEntry.failed(spec);
                errors.add(path + "=" + exception.getClass().getName());
            }
            capturedEntries.put(path, entry);
        });

        List<String> capturedSealTypeOrder = new ArrayList<>();
        Map<String, SealTypeEntry> capturedSealTypes = new LinkedHashMap<>();
        boolean sealCodecRoundTripsExact = false;
        try {
            for (SealType sealType : SealType.values()) {
                capturedSealTypeOrder.add(sealType.name());
                capturedSealTypes.put(
                        sealType.asString(),
                        captureSealType(sealType)
                );
            }
            sealCodecRoundTripsExact = hasExactSealCodecRoundTrips();
        } catch (RuntimeException exception) {
            errors.add("seal_types=" + exception.getClass().getName());
        }

        return new ParticleProbeState(
                String.join(",", errors),
                particleIds,
                Collections.unmodifiableMap(capturedEntries),
                List.copyOf(capturedSealTypeOrder),
                Collections.unmodifiableMap(capturedSealTypes),
                sealCodecRoundTripsExact
        );
    }

    static ParticleProbeState missing() {
        return new ParticleProbeState(
                "not captured",
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                false
        );
    }

    boolean hasExactRegistry() {
        if (!captureError.isEmpty()
                || !EXPECTED_PARTICLE_IDS.equals(etherologyParticleIds)
                || !EXPECTED_PARTICLES.keySet().equals(entries.keySet())) {
            return false;
        }
        return EXPECTED_PARTICLES.entrySet().stream().allMatch(expectedEntry -> {
            ParticleEntry entry = entries.get(expectedEntry.getKey());
            return entry != null
                    && entry.typeIdentity() != null
                    && expectedEntry.getValue().id().equals(entry.id());
        });
    }

    boolean hasExactPayloadFamilies() {
        return EXPECTED_PAYLOAD_FAMILIES.equals(payloadFamilies());
    }

    boolean hasExactTypeClasses() {
        return allEntriesMatch(entry -> FEY_PARTICLE_TYPE_CLASS.equals(entry.typeClass()));
    }

    boolean hasExactAlwaysSpawnPolicy() {
        return allEntriesMatch(entry -> !entry.shouldAlwaysSpawn());
    }

    boolean hasAllCodecs() {
        return allEntriesMatch(ParticleEntry::codecPresent);
    }

    boolean hasAllParametersFactories() {
        return allEntriesMatch(ParticleEntry::parametersFactoryPresent);
    }

    boolean hasExactFactorySampleEffectClasses() {
        return EXPECTED_PARTICLES.entrySet().stream().allMatch(expectedEntry -> {
            ParticleEntry entry = entries.get(expectedEntry.getKey());
            return entry != null
                    && expectedEntry.getValue().effectClass().equals(
                            entry.factorySampleEffectClass()
                    );
        });
    }

    boolean hasExactFactorySampleTypes() {
        return allEntriesMatch(ParticleEntry::factorySampleTypeMatches);
    }

    boolean hasExactFactorySampleStrings() {
        return EXPECTED_PARTICLES.entrySet().stream().allMatch(expectedEntry -> {
            ParticleEntry entry = entries.get(expectedEntry.getKey());
            return entry != null
                    && expectedEntry.getValue().expectedAsString().equals(
                            entry.factorySampleAsString()
                    );
        });
    }

    boolean hasExactPacketRoundTrips() {
        return allEntriesMatch(ParticleEntry::packetRoundTripExact);
    }

    boolean hasExactCodecRoundTrips() {
        return allEntriesMatch(ParticleEntry::codecRoundTripExact);
    }

    boolean hasExactSealTypeOrder() {
        return EXPECTED_SEAL_TYPE_ORDER.equals(sealTypeOrder);
    }

    boolean hasExactSealTypeCodec() {
        return sealTypeCodecRoundTripsExact
                && EXPECTED_SEAL_TYPES.keySet().equals(sealTypes.keySet())
                && EXPECTED_SEAL_TYPES.entrySet().stream().allMatch(expected -> {
                    SealTypeEntry actual = sealTypes.get(expected.getKey());
                    return actual != null
                            && expected.getValue().enumName().equals(actual.enumName())
                            && expected.getValue().asString().equals(actual.asString());
                });
    }

    boolean hasExactSealTypeColors() {
        return EXPECTED_SEAL_TYPES.entrySet().stream().allMatch(expected -> {
            SealTypeEntry actual = sealTypes.get(expected.getKey());
            return actual != null
                    && Objects.equals(
                            expected.getValue().startColor(),
                            actual.startColor()
                    )
                    && Objects.equals(
                            expected.getValue().endColor(),
                            actual.endColor()
                    );
        });
    }

    boolean hasExactSealTypeTextures() {
        return EXPECTED_SEAL_TYPES.entrySet().stream().allMatch(expected -> {
            SealTypeEntry actual = sealTypes.get(expected.getKey());
            return actual != null
                    && Objects.equals(
                            expected.getValue().textureId(),
                            actual.textureId()
                    )
                    && Objects.equals(
                            expected.getValue().textureLightId(),
                            actual.textureLightId()
                    );
        });
    }

    boolean hasExactTypeContract() {
        return hasExactTypeClasses()
                && hasExactAlwaysSpawnPolicy()
                && hasAllCodecs()
                && hasAllParametersFactories();
    }

    boolean hasExactWireContract() {
        return hasExactPayloadFamilies()
                && hasExactFactorySampleEffectClasses()
                && hasExactFactorySampleTypes()
                && hasExactFactorySampleStrings()
                && hasExactPacketRoundTrips()
                && hasExactCodecRoundTrips()
                && hasExactSealTypeOrder()
                && hasExactSealTypeCodec()
                && hasExactSealTypeColors()
                && hasExactSealTypeTextures();
    }

    boolean sameStateAtServerStarted(ParticleProbeState startedState) {
        return hasSameRegistry(startedState)
                && hasSameTypeContract(startedState)
                && hasSameWireContract(startedState);
    }

    boolean hasSameRegistry(ParticleProbeState other) {
        if (!captureError.equals(other.captureError)
                || !etherologyParticleIds.equals(other.etherologyParticleIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        return EXPECTED_PARTICLES.keySet().stream().allMatch(path -> {
            ParticleEntry entry = entries.get(path);
            ParticleEntry otherEntry = other.entries.get(path);
            return entry != null
                    && otherEntry != null
                    && entry.typeIdentity() != null
                    && entry.typeIdentity() == otherEntry.typeIdentity()
                    && entry.id().equals(otherEntry.id());
        });
    }

    boolean hasSameTypeContract(ParticleProbeState other) {
        return EXPECTED_PARTICLES.keySet().stream().allMatch(path -> {
            ParticleEntry entry = entries.get(path);
            ParticleEntry otherEntry = other.entries.get(path);
            return entry != null
                    && otherEntry != null
                    && entry.typeClass().equals(otherEntry.typeClass())
                    && entry.shouldAlwaysSpawn() == otherEntry.shouldAlwaysSpawn()
                    && entry.codecPresent() == otherEntry.codecPresent()
                    && entry.parametersFactoryPresent()
                    == otherEntry.parametersFactoryPresent();
        });
    }

    boolean hasSameWireContract(ParticleProbeState other) {
        return sealTypeOrder.equals(other.sealTypeOrder)
                && sealTypes.equals(other.sealTypes)
                && sealTypeCodecRoundTripsExact
                == other.sealTypeCodecRoundTripsExact
                && EXPECTED_PARTICLES.keySet().stream().allMatch(path -> {
            ParticleEntry entry = entries.get(path);
            ParticleEntry otherEntry = other.entries.get(path);
            return entry != null
                    && otherEntry != null
                    && entry.family().equals(otherEntry.family())
                    && entry.factorySampleEffectClass().equals(
                            otherEntry.factorySampleEffectClass()
                    )
                    && entry.factorySampleTypeMatches()
                    == otherEntry.factorySampleTypeMatches()
                    && entry.factorySampleAsString().equals(
                            otherEntry.factorySampleAsString()
                    )
                    && entry.packetRoundTripExact()
                    == otherEntry.packetRoundTripExact()
                    && entry.codecRoundTripExact()
                    == otherEntry.codecRoundTripExact();
        });
    }

    List<String> payloadFamilies() {
        return entries.values().stream()
                .map(ParticleEntry::family)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean allEntriesMatch(
            java.util.function.Predicate<ParticleEntry> predicate
    ) {
        return entries.size() == EXPECTED_PARTICLES.size()
                && entries.values().stream().allMatch(predicate);
    }

    private static ParticleEntry captureEntry(
            ParticleSpec spec
    ) throws CommandSyntaxException {
        Identifier identifier = Identifier.of("etherology", spec.path());
        ParticleType<?> particleType = Registries.PARTICLE_TYPE
                .getOrEmpty(identifier)
                .orElse(null);
        if (particleType == null) {
            return ParticleEntry.failed(spec);
        }

        Identifier registeredId = Registries.PARTICLE_TYPE.getId(particleType);
        ParticleEffect.Factory<?> parametersFactory = particleType.getParametersFactory();
        Codec<?> codec = particleType.getCodec();
        ParticleEffect factorySample = parametersFactory == null
                ? null
                : readFactorySample(
                        particleType,
                        parametersFactory,
                        spec.commandParameters()
                );
        boolean sampleTypeMatches = factorySample != null
                && factorySample.getType() == particleType;
        String sampleAsString = factorySample == null ? "" : factorySample.asString();

        return new ParticleEntry(
                particleType,
                registeredId == null ? "" : registeredId.toString(),
                spec.family(),
                particleType.getClass().getName(),
                particleType.shouldAlwaysSpawn(),
                codec != null,
                parametersFactory != null,
                factorySample == null ? "" : factorySample.getClass().getName(),
                sampleTypeMatches,
                sampleAsString,
                factorySample != null
                        && packetRoundTripExact(particleType, parametersFactory, factorySample),
                factorySample != null
                        && codec != null
                        && codecRoundTripExact(particleType, codec, factorySample)
        );
    }

    private static SealTypeEntry captureSealType(SealType sealType) {
        return new SealTypeEntry(
                sealType.name(),
                sealType.asString(),
                colorValue(sealType.getStartColor()),
                colorValue(sealType.getEndColor()),
                identifierValue(sealType.getTextureId()),
                identifierValue(sealType.getTextureLightId())
        );
    }

    private static boolean hasExactSealCodecRoundTrips() {
        for (SealType sealType : SealType.values()) {
            JsonElement encoded = SealType.CODEC.encodeStart(JsonOps.INSTANCE, sealType)
                    .result()
                    .orElse(null);
            if (encoded == null
                    || !encoded.isJsonPrimitive()
                    || !sealType.asString().equals(encoded.getAsString())) {
                return false;
            }
            SealType decoded = SealType.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .result()
                    .orElse(null);
            if (decoded != sealType) {
                return false;
            }
        }
        return true;
    }

    private static String colorValue(RGBColor color) {
        return color == null ? null : color.r() + "," + color.g() + "," + color.b();
    }

    private static String identifierValue(Identifier identifier) {
        return identifier == null ? null : identifier.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ParticleEffect readFactorySample(
            ParticleType<?> particleType,
            ParticleEffect.Factory<?> factory,
            String commandParameters
    ) throws CommandSyntaxException {
        StringReader reader = new StringReader(commandParameters);
        ParticleEffect effect = ((ParticleEffect.Factory) factory).read(
                (ParticleType) particleType,
                reader
        );
        if (reader.canRead()) {
            throw new IllegalStateException("Particle sample was not fully consumed");
        }
        return effect;
    }

    private static boolean packetRoundTripExact(
            ParticleType<?> particleType,
            ParticleEffect.Factory<?> factory,
            ParticleEffect effect
    ) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            effect.write(buffer);
            ParticleEffect decoded = readPacketSample(particleType, factory, buffer);
            return buffer.readableBytes() == 0 && sameEffect(effect, decoded, particleType);
        } catch (RuntimeException exception) {
            return false;
        } finally {
            buffer.release();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ParticleEffect readPacketSample(
            ParticleType<?> particleType,
            ParticleEffect.Factory<?> factory,
            PacketByteBuf buffer
    ) {
        return ((ParticleEffect.Factory) factory).read(
                (ParticleType) particleType,
                buffer
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean codecRoundTripExact(
            ParticleType<?> particleType,
            Codec<?> codec,
            ParticleEffect effect
    ) {
        Codec<ParticleEffect> effectCodec = (Codec) codec;
        DataResult<JsonElement> encoded = effectCodec.encodeStart(
                JsonOps.INSTANCE,
                effect
        );
        JsonElement encodedValue = encoded.result().orElse(null);
        if (encodedValue == null) {
            return false;
        }
        ParticleEffect decoded = effectCodec.parse(JsonOps.INSTANCE, encodedValue)
                .result()
                .orElse(null);
        return sameEffect(effect, decoded, particleType);
    }

    private static boolean sameEffect(
            ParticleEffect expected,
            ParticleEffect actual,
            ParticleType<?> particleType
    ) {
        return actual != null
                && actual.getClass() == expected.getClass()
                && actual.getType() == particleType
                && actual.asString().equals(expected.asString());
    }

    private static Map<String, ParticleSpec> expectedParticles() {
        Map<String, ParticleSpec> particles = new LinkedHashMap<>();
        addSimple(particles, "alchemy");
        addMoving(particles, "armillary_sphere");
        add(
                particles,
                "electricity1",
                "electricity",
                "ElectricityParticleEffect",
                " SIMPLE",
                "SIMPLE"
        );
        add(
                particles,
                "electricity2",
                "electricity",
                "ElectricityParticleEffect",
                " MATRIX",
                "MATRIX"
        );
        addSimple(particles, "energy_absorption");
        addMoving(particles, "ether_dot");
        addMoving(particles, "ether_star");
        addMoving(particles, "glint_particle");
        addSimple(particles, "haze");
        add(
                particles,
                "item",
                "item",
                "ItemParticleEffect",
                " minecraft:diamond 1 2 3",
                "minecraft:diamond 1.0 2.0 3.0"
        );
        add(
                particles,
                "light",
                "light",
                "LightParticleEffect",
                " SIMPLE 1 2 3",
                "SIMPLE 1.0 2.0 3.0"
        );
        addScalable(particles, "lightning_bolt");
        addSimple(particles, "redstone_flash");
        addSimple(particles, "redstone_stream");
        addScalable(particles, "resonation");
        addSimple(particles, "rising");
        addScalable(particles, "scalable_sweep");
        add(
                particles,
                "seal",
                "seal",
                "SealParticleEffect",
                " KETA 1 2 3",
                "KETA 1.0 2.0 3.0"
        );
        addSimple(particles, "shockwave");
        add(
                particles,
                "spark",
                "spark",
                "SparkParticleEffect",
                " 1 2 3 JEWELRY",
                "1.0 2.0 3.0 JEWELRY"
        );
        addSimple(particles, "steam");
        addMoving(particles, "vital");
        return Collections.unmodifiableMap(particles);
    }

    private static Map<String, SealTypeSpec> expectedSealTypes() {
        Map<String, SealTypeSpec> sealTypes = new LinkedHashMap<>();
        addSealType(sealTypes, "EMPTY", null, null, null, null);
        addSealType(
                sealTypes,
                "KETA",
                "128,205,247",
                "105,128,231",
                "etherology:textures/block/keta_seal.png",
                "etherology:textures/block/keta_seal_light.png"
        );
        addSealType(
                sealTypes,
                "RELLA",
                "177,229,106",
                "106,182,81",
                "etherology:textures/block/rella_seal.png",
                "etherology:textures/block/rella_seal_light.png"
        );
        addSealType(
                sealTypes,
                "VIA",
                "248,122,95",
                "205,58,76",
                "etherology:textures/block/via_seal.png",
                "etherology:textures/block/via_seal_light.png"
        );
        addSealType(
                sealTypes,
                "CLOS",
                "106,182,81",
                "208,158,89",
                "etherology:textures/block/clos_seal.png",
                "etherology:textures/block/clos_seal_light.png"
        );
        return Collections.unmodifiableMap(sealTypes);
    }

    private static void addSealType(
            Map<String, SealTypeSpec> sealTypes,
            String enumName,
            String startColor,
            String endColor,
            String textureId,
            String textureLightId
    ) {
        String asString = enumName.toLowerCase();
        sealTypes.put(
                asString,
                new SealTypeSpec(
                        enumName,
                        asString,
                        startColor,
                        endColor,
                        textureId,
                        textureLightId
                )
        );
    }

    private static void addSimple(
            Map<String, ParticleSpec> particles,
            String path
    ) {
        add(particles, path, "simple", "SimpleParticleEffect", "", "");
    }

    private static void addMoving(
            Map<String, ParticleSpec> particles,
            String path
    ) {
        add(
                particles,
                path,
                "moving",
                "MovingParticleEffect",
                " 1 2 3",
                "1.0 2.0 3.0"
        );
    }

    private static void addScalable(
            Map<String, ParticleSpec> particles,
            String path
    ) {
        add(
                particles,
                path,
                "scalable",
                "ScalableParticleEffect",
                " 1.5",
                "1.5"
        );
    }

    private static void add(
            Map<String, ParticleSpec> particles,
            String path,
            String family,
            String effectClassName,
            String commandParameters,
            String expectedParameters
    ) {
        particles.put(
                path,
                new ParticleSpec(
                        path,
                        family,
                        "ru.feytox.etherology.particle.effects." + effectClassName,
                        commandParameters,
                        expectedParameters
                )
        );
    }

    record ParticleSpec(
            String path,
            String family,
            String effectClass,
            String commandParameters,
            String expectedParameters
    ) {

        String id() {
            return "etherology:" + path;
        }

        String expectedAsString() {
            return expectedParameters.isEmpty()
                    ? id()
                    : id() + " " + expectedParameters;
        }
    }

    record SealTypeSpec(
            String enumName,
            String asString,
            String startColor,
            String endColor,
            String textureId,
            String textureLightId
    ) {
    }

    record SealTypeEntry(
            String enumName,
            String asString,
            String startColor,
            String endColor,
            String textureId,
            String textureLightId
    ) {
    }

    record ParticleEntry(
            Object typeIdentity,
            String id,
            String family,
            String typeClass,
            boolean shouldAlwaysSpawn,
            boolean codecPresent,
            boolean parametersFactoryPresent,
            String factorySampleEffectClass,
            boolean factorySampleTypeMatches,
            String factorySampleAsString,
            boolean packetRoundTripExact,
            boolean codecRoundTripExact
    ) {

        static ParticleEntry failed(ParticleSpec spec) {
            return new ParticleEntry(
                    null,
                    "",
                    spec.family(),
                    "",
                    true,
                    false,
                    false,
                    "",
                    false,
                    "",
                    false,
                    false
            );
        }
    }
}
