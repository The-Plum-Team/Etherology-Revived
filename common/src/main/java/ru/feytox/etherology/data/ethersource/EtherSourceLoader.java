package ru.feytox.etherology.data.ethersource;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.profiler.Profiler;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Atomically replaces Ether-source item values from the server-data {@code ether_sources} folder.
 */
public final class EtherSourceLoader extends JsonDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Owns the one listener instance shared by registration and Ether-source queries.
     */
    public static final EtherSourceLoader INSTANCE = new EtherSourceLoader();

    private Map<Identifier, Float> etherItems = ImmutableMap.of();
    private boolean loaded;

    private EtherSourceLoader() {
        super(GSON, "ether_sources");
    }

    Map<Identifier, Float> getEtherItems() {
        if (!loaded) {
            LOGGER.error("EtherSources didn't reloaded!");
            return new HashMap<>();
        }
        return etherItems;
    }

    @Override
    protected void apply(
            Map<Identifier, JsonElement> prepared,
            ResourceManager manager,
            Profiler profiler
    ) {
        ImmutableMap.Builder<Identifier, Float> builder = ImmutableMap.builder();
        prepared.forEach((resourceId, json) -> {
            try {
                builder.putAll(EtherSourcesDeserializer.deserialize(json));
            } catch (IllegalStateException
                     | InvalidIdentifierException
                     | NumberFormatException
                     | UnsupportedOperationException exception) {
                LOGGER.error("Couldn't parse EtherSources from {}", resourceId, exception);
            }
        });

        etherItems = builder.build();
        loaded = true;
    }
}
