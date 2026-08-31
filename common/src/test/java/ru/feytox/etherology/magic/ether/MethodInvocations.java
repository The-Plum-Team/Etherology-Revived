package ru.feytox.etherology.magic.ether;

import java.util.ArrayList;
import java.util.List;

final class MethodInvocations {

    private final List<String> names = new ArrayList<>();

    void add(String owner, String name) {
        names.add(owner + "#" + name);
    }

    int indexOf(String name) {
        for (int index = 0; index < names.size(); index++) {
            if (names.get(index).endsWith("#" + name)) {
                return index;
            }
        }
        return -1;
    }

    int count(String name) {
        int count = 0;
        for (String invocation : names) {
            if (invocation.endsWith("#" + name)) {
                count++;
            }
        }
        return count;
    }
}
