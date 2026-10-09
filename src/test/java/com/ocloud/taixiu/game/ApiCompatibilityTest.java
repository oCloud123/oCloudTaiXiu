package com.ocloud.taixiu.game;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApiCompatibilityTest {
    @Test void everyReferencedServerApiMethodAndFieldExistsWithTheSameBinarySignature() throws Exception {
        List<String> failures = new ArrayList<>();
        List<byte[]> bytecodes = new ArrayList<>();
        for (String directory : List.of("build/classes/java/main", "build/classes/java/nativeGui")) {
            boolean nativeGui = directory.endsWith("nativeGui");
            if (nativeGui) {
                try { Class.forName("io.papermc.paper.dialog.Dialog", false, getClass().getClassLoader()); }
                catch (ClassNotFoundException ignored) { continue; }
            }
            try (var paths = Files.walk(Path.of(directory))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    bytecodes.add(Files.readAllBytes(path));
                }
            }
        }
        assertFalse(bytecodes.isEmpty(), "No plugin bytecode was checked");
        for (byte[] bytecode : bytecodes) {
                    var reader = new ClassReader(bytecode);
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                    checkMethod(owner, method, descriptor, isInterface, failures);
                                }
                                @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                                    checkField(owner, field, descriptor, failures);
                                }
                                @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle handle, Object... arguments) {
                                    for (Object argument : arguments) {
                                        if (argument instanceof Handle target && target.getTag() >= Opcodes.H_INVOKEVIRTUAL) {
                                            checkMethod(target.getOwner(), target.getName(), target.getDesc(), target.isInterface(), failures);
                                        }
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures.stream().distinct().toList()));
    }

    private static boolean api(String owner) {
        return owner.startsWith("org/bukkit/") || owner.startsWith("io/papermc/")
                || owner.startsWith("net/kyori/") || owner.startsWith("org/joml/");
    }

    private static void checkMethod(String owner, String name, String descriptor, boolean isInterface, List<String> failures) {
        if (!api(owner)) return;
        String reference = owner + "." + name + descriptor;
        try {
            Class<?> type = Class.forName(owner.replace('/', '.'), false, ApiCompatibilityTest.class.getClassLoader());
            if (type.isInterface() != isInterface) { failures.add(reference + ": class/interface changed"); return; }
            if (name.equals("<init>")) {
                for (var constructor : type.getConstructors()) {
                    if (MethodType.methodType(void.class, constructor.getParameterTypes()).descriptorString().equals(descriptor)) return;
                }
            } else {
                for (var method : type.getMethods()) {
                    if (method.getName().equals(name) && MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                            .descriptorString().equals(descriptor)) return;
                }
                if (type.isInterface()) {
                    for (var method : Object.class.getMethods()) {
                        if (method.getName().equals(name) && MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                                .descriptorString().equals(descriptor)) return;
                    }
                }
            }
            failures.add(reference + ": method absent");
        } catch (ClassNotFoundException | LinkageError ex) { failures.add(reference + ": " + ex); }
    }

    private static void checkField(String owner, String name, String descriptor, List<String> failures) {
        if (!api(owner)) return;
        try {
            var field = Class.forName(owner.replace('/', '.'), false, ApiCompatibilityTest.class.getClassLoader()).getField(name);
            if (!Type.getDescriptor(field.getType()).equals(descriptor)) failures.add(owner + "." + name + ": field type changed");
        } catch (ReflectiveOperationException | LinkageError ex) { failures.add(owner + "." + name + ": " + ex); }
    }
}
