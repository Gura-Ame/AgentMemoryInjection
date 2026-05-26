package com.nobtg.agentMemoryInjection;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.util.function.Consumer;

public class Main {
    public static void main(String[] args) {
        new Test().print();
        try {
            PanamaPureMemoryAgent.performInjection();

            Consumer<ClassNode> changer = classNode -> {
                for (MethodNode method : classNode.methods) {
                    if (method.name.equals("print")) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof LdcInsnNode ldcInsnNode) {
                                ldcInsnNode.cst = "CRACKED";
                            }
                        }
                        break;
                    }
                }
            };

            PanamaPureMemoryAgent.instrumentation.redefineClasses(
                    new ClassDefinition(Test.class, modifyClass(getBytesFromClass(Test.class), changer))
            );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        new Test().print();
    }

    private static byte @NotNull [] getBytesFromClass(@NotNull Class<?> clazz) throws IOException {
        ClassLoader classLoader = clazz.getClassLoader();
        String classPath = clazz.getName().replace(".", "/").concat(".class");
        try (InputStream stream = classLoader.getResourceAsStream(classPath)) {
            if (stream == null) throw new RuntimeException("Stream is null.");
            return stream.readAllBytes();
        }
    }

    private static byte[] modifyClass(byte[] bytes, @NotNull Consumer<ClassNode> changer) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        cr.accept(node, ClassReader.SKIP_DEBUG);
        changer.accept(node);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}