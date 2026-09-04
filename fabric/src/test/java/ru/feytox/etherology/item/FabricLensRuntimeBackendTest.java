package ru.feytox.etherology.item;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class FabricLensRuntimeBackendTest {

    private static final String ADAPTER =
            "/ru/feytox/etherology/item/FabricLensRuntimeBackend.class";
    private static final String COMPONENT_TYPES =
            "/ru/feytox/etherology/registry/misc/ComponentTypes.class";
    private static final String ENTRYPOINT =
            "/ru/feytox/etherology/EtherologyFabric.class";

    @Test
    void compiledAliasesReadBothSharedFieldsWithoutRecreatingTheirIds()
            throws IOException {
        List<String> fieldEvents = new ArrayList<>();
        AtomicInteger ownedKeyIds = new AtomicInteger();
        reader(COMPONENT_TYPES).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("lens") || value.equals("pseudo_damage")) {
                            ownedKeyIds.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if ((owner.equals(
                                "ru/feytox/etherology/magic/lens/LensDataKeys"
                        ) || owner.equals(
                                "ru/feytox/etherology/registry/misc/ComponentTypes"
                        )) && (fieldName.equals("LENS")
                                || fieldName.equals("PSEUDO_DAMAGE"))) {
                            fieldEvents.add(opcode + ":" + owner + "#" + fieldName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        Opcodes.GETSTATIC
                                + ":ru/feytox/etherology/magic/lens/LensDataKeys#LENS",
                        Opcodes.PUTSTATIC
                                + ":ru/feytox/etherology/registry/misc/ComponentTypes#LENS",
                        Opcodes.GETSTATIC
                                + ":ru/feytox/etherology/magic/lens/LensDataKeys#PSEUDO_DAMAGE",
                        Opcodes.PUTSTATIC
                                + ":ru/feytox/etherology/registry/misc/ComponentTypes#PSEUDO_DAMAGE"
                ),
                fieldEvents
        );
        assertEquals(0, ownedKeyIds.get());
    }

    @Test
    void adapterIsOneSingletonAndDelegatesToTheCanonicalFabricGraph()
            throws IOException {
        AtomicInteger singletonAccess = new AtomicInteger(-1);
        List<String> interfaces = new ArrayList<>();
        reader(ADAPTER).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] implementedInterfaces
            ) {
                if (implementedInterfaces != null) {
                    interfaces.addAll(List.of(implementedInterfaces));
                }
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("INSTANCE")) singletonAccess.set(access);
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of("ru/feytox/etherology/item/LensRuntimeBackend"),
                interfaces
        );
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        assertEquals(requiredAccess, singletonAccess.get() & requiredAccess);

        assertEquals(
                List.of(
                        "ru/feytox/etherology/magic/ether/EtherComponent#decrement"
                                + "(Lnet/minecraft/entity/LivingEntity;F)Z"
                ),
                adapterTrace(
                        "decrementEther",
                        "(Lnet/minecraft/entity/LivingEntity;F)Z"
                )
        );
        assertEquals(
                List.of(
                        "net/minecraft/item/ItemStack#getItem()Lnet/minecraft/item/Item;",
                        "INSTANCEOF:ru/feytox/etherology/item/StaffItem"
                ),
                adapterTrace(
                        "isStaff",
                        "(Lnet/minecraft/item/ItemStack;)Z"
                )
        );

        assertEquals(
                List.of(
                        "ru/feytox/etherology/item/StaffItem#setLensComponent"
                                + "(Lnet/minecraft/item/ItemStack;"
                                + "Lnet/minecraft/item/ItemStack;)V",
                        "ru/feytox/etherology/magic/staff/StaffComponent#getWrapper"
                                + "(Lnet/minecraft/item/ItemStack;)Ljava/util/Optional;",
                        "HANDLE:ru/feytox/etherology/item/FabricLensRuntimeBackend#"
                                + "lambda$placeStaffLens$0"
                                + "(Lru/feytox/etherology/magic/staff/StaffLenses;"
                                + "Lru/feytox/etherology/util/misc/ItemData;)V",
                        "java/util/Optional#ifPresent(Ljava/util/function/Consumer;)V"
                ),
                adapterTrace(
                        "placeStaffLens",
                        "(Lnet/minecraft/item/ItemStack;"
                                + "Lnet/minecraft/item/ItemStack;"
                                + "Lru/feytox/etherology/magic/staff/StaffLenses;)V"
                )
        );
        assertEquals(
                List.of(
                        "NEW:ru/feytox/etherology/magic/staff/StaffPartInfo",
                        "GETSTATIC:ru/feytox/etherology/magic/staff/StaffPart#LENS",
                        "GETSTATIC:ru/feytox/etherology/magic/staff/StaffPattern#EMPTY",
                        "ru/feytox/etherology/magic/staff/StaffPartInfo#<init>"
                                + "(Lru/feytox/etherology/magic/staff/StaffPart;"
                                + "Lru/feytox/etherology/magic/staff/StaffPattern;"
                                + "Lru/feytox/etherology/magic/staff/StaffPattern;)V",
                        "HANDLE:ru/feytox/etherology/magic/staff/StaffComponent#setPartInfo"
                                + "(Lru/feytox/etherology/magic/staff/StaffPartInfo;)"
                                + "Lru/feytox/etherology/magic/staff/StaffComponent;",
                        "ru/feytox/etherology/util/misc/ItemData#set"
                                + "(Ljava/lang/Object;Ljava/util/function/BiFunction;)"
                                + "Lru/feytox/etherology/util/misc/ItemData;",
                        "ru/feytox/etherology/util/misc/ItemData#save()"
                                + "Lru/feytox/etherology/util/misc/ItemData;"
                ),
                adapterTrace(
                        "lambda$placeStaffLens$0",
                        "(Lru/feytox/etherology/magic/staff/StaffLenses;"
                                + "Lru/feytox/etherology/util/misc/ItemData;)V"
                )
        );

        assertEquals(
                List.of(
                        "ru/feytox/etherology/item/FabricLensRuntimeBackend#getStaffLens"
                                + "(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;",
                        "ru/feytox/etherology/magic/staff/StaffComponent#getWrapper"
                                + "(Lnet/minecraft/item/ItemStack;)Ljava/util/Optional;",
                        "HANDLE:ru/feytox/etherology/item/FabricLensRuntimeBackend#"
                                + "lambda$takeStaffLens$1"
                                + "(Lru/feytox/etherology/util/misc/ItemData;)V",
                        "java/util/Optional#ifPresent(Ljava/util/function/Consumer;)V",
                        "GETSTATIC:ru/feytox/etherology/registry/misc/ComponentTypes#STAFF_LENS",
                        "ru/feytox/etherology/util/misc/ItemDataKey#remove"
                                + "(Lnet/minecraft/item/ItemStack;)V"
                ),
                adapterTrace(
                        "takeStaffLens",
                        "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"
                )
        );
        assertEquals(
                List.of(
                        "GETSTATIC:ru/feytox/etherology/magic/staff/StaffPart#LENS",
                        "HANDLE:ru/feytox/etherology/magic/staff/StaffComponent#removePartInfo"
                                + "(Lru/feytox/etherology/magic/staff/StaffPart;)"
                                + "Lru/feytox/etherology/magic/staff/StaffComponent;",
                        "ru/feytox/etherology/util/misc/ItemData#set"
                                + "(Ljava/lang/Object;Ljava/util/function/BiFunction;)"
                                + "Lru/feytox/etherology/util/misc/ItemData;",
                        "ru/feytox/etherology/util/misc/ItemData#save()"
                                + "Lru/feytox/etherology/util/misc/ItemData;"
                ),
                adapterTrace(
                        "lambda$takeStaffLens$1",
                        "(Lru/feytox/etherology/util/misc/ItemData;)V"
                )
        );

        assertEquals(
                List.of(
                        "GETSTATIC:ru/feytox/etherology/registry/misc/ComponentTypes#STAFF_LENS",
                        "ru/feytox/etherology/util/misc/ItemDataKey#get"
                                + "(Lnet/minecraft/item/ItemStack;)Ljava/util/Optional;",
                        "HANDLE:ru/feytox/etherology/util/misc/ItemComponent#stack()"
                                + "Lnet/minecraft/item/ItemStack;",
                        "java/util/Optional#map(Ljava/util/function/Function;)"
                                + "Ljava/util/Optional;",
                        "HANDLE:ru/feytox/etherology/item/FabricLensRuntimeBackend#"
                                + "lambda$getStaffLens$2(Lnet/minecraft/item/ItemStack;)Z",
                        "java/util/Optional#filter(Ljava/util/function/Predicate;)"
                                + "Ljava/util/Optional;",
                        "java/util/Optional#orElse(Ljava/lang/Object;)Ljava/lang/Object;",
                        "CHECKCAST:net/minecraft/item/ItemStack"
                ),
                adapterTrace(
                        "getStaffLens",
                        "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"
                )
        );
        assertEquals(
                List.of("net/minecraft/item/ItemStack#isEmpty()Z"),
                adapterTrace(
                        "lambda$getStaffLens$2",
                        "(Lnet/minecraft/item/ItemStack;)Z"
                )
        );
    }

    @Test
    void fabricBindsBeforeAnyRegistryOrGameplayInitialization() throws IOException {
        List<String> calls = new ArrayList<>();
        reader(ENTRYPOINT).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("onInitialize")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String calledName,
                            String calledDescriptor,
                            boolean isInterface
                    ) {
                        calls.add(owner + "#" + calledName + calledDescriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        "ru/feytox/etherology/item/LensRuntime#bind"
                                + "(Lru/feytox/etherology/item/LensRuntimeBackend;)V",
                        "ru/feytox/etherology/registry/particle/SharedParticleTypes#register()V",
                        "ru/feytox/etherology/Etherology#initialize()V"
                ),
                calls
        );
    }

    private static List<String> adapterTrace(
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<String> events = new ArrayList<>();
        reader(ADAPTER).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName) || !descriptor.equals(methodDescriptor)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            events.add("INSTANCEOF:" + type);
                        } else if (opcode == Opcodes.NEW) {
                            events.add("NEW:" + type);
                        } else if (opcode == Opcodes.CHECKCAST) {
                            events.add("CHECKCAST:" + type);
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        String prefix = switch (opcode) {
                            case Opcodes.GETSTATIC -> "GETSTATIC:";
                            case Opcodes.PUTSTATIC -> "PUTSTATIC:";
                            case Opcodes.GETFIELD -> "GETFIELD:";
                            case Opcodes.PUTFIELD -> "PUTFIELD:";
                            default -> "FIELD:";
                        };
                        events.add(prefix + owner + "#" + name);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String calledName,
                            String calledDescriptor,
                            boolean isInterface
                    ) {
                        events.add(owner + "#" + calledName + calledDescriptor);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle) {
                                events.add(
                                        "HANDLE:"
                                                + handle.getOwner()
                                                + "#"
                                                + handle.getName()
                                                + handle.getDesc()
                                );
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return events;
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = FabricLensRuntimeBackendTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
