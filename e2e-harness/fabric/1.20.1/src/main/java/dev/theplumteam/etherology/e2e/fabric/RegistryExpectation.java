package dev.theplumteam.etherology.e2e.fabric;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

record RegistryExpectation(String registryName, Registry<?> registry, Identifier identifier) {

    boolean isPresent() {
        return registry.containsId(identifier);
    }
}
