package ru.feytox.etherology.block.etherealStorage;

import java.util.ArrayList;
import java.util.List;

final class InvocationInventory {

    private static final String OWNED_CLASS =
            "ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationBlockEntity";

    private final List<String> invocations = new ArrayList<>();

    void add(String owner, String name) {
        invocations.add(owner + "#" + name);
    }

    boolean has(String owner, String name) {
        return indexOf(owner, name) >= 0;
    }

    boolean hasOwned(String name) {
        return indexOfOwned(name) >= 0;
    }

    int indexOf(String owner, String name) {
        return invocations.indexOf(owner + "#" + name);
    }

    int indexOfOwned(String name) {
        return indexOf(OWNED_CLASS, name);
    }

    int count(String name) {
        int count = 0;
        for (String invocation : invocations) {
            if (invocation.endsWith("#" + name)) {
                count++;
            }
        }
        return count;
    }
}
