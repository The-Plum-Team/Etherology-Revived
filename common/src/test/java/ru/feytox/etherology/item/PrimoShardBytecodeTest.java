package ru.feytox.etherology.item;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrimoShardBytecodeTest {

    private static final String RESOURCE =
            "/ru/feytox/etherology/item/PrimoShard.class";
    private static final String OWNER =
            "ru/feytox/etherology/item/PrimoShard";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String SETTINGS = "net/minecraft/item/Item$Settings";
    private static final String SEAL_TYPE =
            "ru/feytox/etherology/magic/seal/SealType";

    @Test
    void constructorPreservesDefaultStackLimitAndCapitalizedSealIdentity()
            throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicReference<String> superName = new AtomicReference<>();
        AtomicInteger matchingConstructors = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        List<String> events = new ArrayList<>();
        List<String> settingsCalls = new ArrayList<>();

        classReader().accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String visitedSuperName,
                    String[] interfaces
            ) {
                classAccess.set(access);
                superName.set(visitedSuperName);
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.add(new FieldInfo(access, name, descriptor));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<init>")
                        || !descriptor.equals("(L" + SEAL_TYPE + ";)V")) {
                    return null;
                }
                matchingConstructors.incrementAndGet();
                assertTrue((access & Opcodes.ACC_PUBLIC) != 0);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(SETTINGS)) {
                            events.add("NEW Settings");
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.PUTFIELD
                                && owner.equals(OWNER)
                                && name.equals("sealId")) {
                            events.add("PrimoShard#sealId:PUTFIELD");
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(SETTINGS)) {
                            settingsCalls.add(name + descriptor);
                            if (name.equals("<init>")) {
                                events.add("Settings#<init>");
                            }
                        }
                        if (owner.equals(ITEM) && name.equals("<init>")) {
                            events.add("Item#<init>" + descriptor);
                        }
                        if (owner.equals(SEAL_TYPE) && name.equals("asString")) {
                            events.add("SealType#asString" + descriptor);
                        }
                        if (owner.equals("org/apache/commons/lang3/StringUtils")
                                && name.equals("capitalize")) {
                            events.add("StringUtils#capitalize" + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertEquals(ITEM, superName.get());
        assertEquals(
                List.of(new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                        "sealId",
                        "Ljava/lang/String;"
                )),
                fields
        );
        assertEquals(1, matchingConstructors.get());
        assertEquals(
                List.of(
                        "NEW Settings",
                        "Settings#<init>",
                        "Item#<init>(Lnet/minecraft/item/Item$Settings;)V",
                        "SealType#asString()Ljava/lang/String;",
                        "StringUtils#capitalize(Ljava/lang/String;)Ljava/lang/String;",
                        "PrimoShard#sealId:PUTFIELD"
                ),
                events
        );
        assertEquals(List.of("<init>()V"), settingsCalls);
    }

    @Test
    void tooltipKeepsDarkPurpleLoreAtIndexOneAfterTheBaseTooltip()
            throws IOException {
        AtomicInteger matchingMethods = new AtomicInteger();
        List<String> events = new ArrayList<>();

        classReader().accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("appendTooltip")) {
                    return null;
                }
                matchingMethods.incrementAndGet();
                assertTrue((access & Opcodes.ACC_PUBLIC) != 0);
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean loreFormatted;

                    @Override
                    public void visitInsn(int opcode) {
                        if (loreFormatted && opcode == Opcodes.ICONST_1) {
                            events.add("INDEX:1");
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("lore.etherology.primoshard")) {
                            events.add("LORE_KEY");
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETFIELD
                                && owner.equals(OWNER)
                                && name.equals("sealId")) {
                            events.add("PrimoShard#sealId:GETFIELD");
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/util/Formatting")
                                && name.equals("DARK_PURPLE")) {
                            events.add("Formatting#DARK_PURPLE");
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(ITEM) && name.equals("appendTooltip")) {
                            events.add("Item#appendTooltip");
                        }
                        if (owner.equals("net/minecraft/text/Text")
                                && name.equals("translatable")) {
                            events.add("Text#translatable");
                        }
                        if (owner.equals("net/minecraft/text/MutableText")
                                && name.equals("formatted")) {
                            events.add("MutableText#formatted");
                            loreFormatted = true;
                        }
                        if (owner.equals("java/util/List") && name.equals("add")) {
                            events.add("List#add" + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, matchingMethods.get());
        assertEquals(
                List.of(
                        "Item#appendTooltip",
                        "LORE_KEY",
                        "PrimoShard#sealId:GETFIELD",
                        "Text#translatable",
                        "Formatting#DARK_PURPLE",
                        "MutableText#formatted",
                        "INDEX:1",
                        "List#add(ILjava/lang/Object;)V"
                ),
                events
        );
    }

    private static ClassReader classReader() throws IOException {
        InputStream stream = PrimoShardBytecodeTest.class
                .getResourceAsStream(RESOURCE);
        assertNotNull(stream, "Missing class resource " + RESOURCE);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldInfo(int access, String name, String descriptor) {
    }
}
