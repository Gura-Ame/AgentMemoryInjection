package com.nobtg.agentMemoryInjection;

import com.nobtg.agentMemoryInjection.jvm.JNIConstants;
import org.jetbrains.annotations.NotNull;
import sun.reflect.ReflectionFactory;

import java.lang.foreign.*;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.util.function.Consumer;

import static com.nobtg.agentMemoryInjection.jvm.AgentLayoutAccessors.*;
import static com.nobtg.agentMemoryInjection.jvm.JNIConstants.*;
import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.*;
import static java.lang.foreign.ValueLayout.*;

public final class PanamaPureMemoryAgent {
    public static Instrumentation instrumentation;
    private static final boolean DEBUG = false;

    public static void performInjection() throws Throwable {
        Linker linker = Linker.nativeLinker();
        String os = System.getProperty("os.name").toLowerCase();
        Path jvmPath = getJvmPath(os);

        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup jvmLookup = SymbolLookup.libraryLookup(jvmPath, arena);

            // Acquire JavaVM* pointer
            MemorySegment jniGetVM = jvmLookup.find("JNI_GetCreatedJavaVMs")
                    .orElseThrow(() -> new RuntimeException("Could not find symbol: JNI_GetCreatedJavaVMs"));

            MethodHandle getCreatedVMs = linker.downcallHandle(jniGetVM,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));

            MemorySegment vmBuffer = arena.allocate(ADDRESS);
            MemorySegment nVMs = arena.allocate(ValueLayout.JAVA_INT);

            if ((int) getCreatedVMs.invokeExact(vmBuffer, 1, nVMs) != 0) {
                throw new RuntimeException("Failed to acquire JavaVM* pointer!");
            }
            MemorySegment javaVM = vmBuffer.get(ADDRESS, 0);

            MemorySegment vtableBase = javaVM.reinterpret(256).get(ADDRESS, 0);
            MemorySegment vtable = vtableBase.reinterpret(256);

            MethodHandle getEnvHandle = findMethod(JNI_INVOKE_INTERFACE_LAYOUT, "GetEnv", vtable,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ValueLayout.JAVA_INT));

            MemorySegment jniEnvPtr = arena.allocate(ADDRESS);
            int getJNIResult = (int) getEnvHandle.invokeExact(javaVM, jniEnvPtr, JNI_VERSION_1_2);
            if (getJNIResult != JNIConstants.JNI_OK) {
                System.err.println("GetEnv(getJNIEnv) 操作失敗: " + JNIConstants.getStatusMessage(getJNIResult));
            }

            MemorySegment jvmTiEnv = createJvmTiEnv(arena, getEnvHandle, javaVM);
            MemorySegment jvmTiVtable = getJvmTiVtable(jvmTiEnv);

            MethodHandle allocateHandle = findMethod(JVMTI_INTERFACE_LAYOUT, "Allocate", jvmTiVtable,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_LONG, ADDRESS));

            long size = JPLIS_AGENT_LAYOUT.byteSize();
            MemorySegment memPtrPtr = arena.allocate(ADDRESS);
            int allocateAgentResult = (int) allocateHandle.invokeExact(jvmTiEnv, size, memPtrPtr);
            if (allocateAgentResult != JVMTI_ERROR_NONE) {
                System.err.println("Allocate(allocateAgent) 操作失敗: " + JNIConstants.getJvmtiErrorMessage(allocateAgentResult));
            }
            long agentSize = JPLIS_AGENT_LAYOUT.byteSize();
            MemorySegment agentPtr = memPtrPtr.get(ADDRESS, 0).reinterpret(agentSize);
            agentPtr.fill((byte) 0);
            if (agentPtr.address() == 0) {
                System.err.println("???");
            }

            MemorySegment normalEnv = agentPtr.asSlice(AGENT_NORMAL_ENV_OFFSET, JPLIS_ENVIRONMENT_LAYOUT.byteSize());
            ENV_JVMTI_VH.set(normalEnv, 0L, jvmTiEnv);
            ENV_AGENT_VH.set(normalEnv, 0L, agentPtr);
            ENV_RETRANS_VH.set(normalEnv, 0L, false);

            MemorySegment retransEnv = agentPtr.asSlice(AGENT_RETRANS_ENV_OFFSET, JPLIS_ENVIRONMENT_LAYOUT.byteSize());
            ENV_JVMTI_VH.set(retransEnv, 0L, MemorySegment.NULL);
            ENV_AGENT_VH.set(retransEnv, 0L, agentPtr);
            ENV_RETRANS_VH.set(retransEnv, 0L, false);

            AGENT_JVM_VH.set(agentPtr, 0L, javaVM);
            MemorySegment persistentJarPath = Arena.global().allocateFrom("");
            AGENT_JAR_VH.set(agentPtr, 0L, persistentJarPath);
            AGENT_PRINT_WARNING_VH.set(agentPtr, 0L, false);

            MethodHandle setEnvironmentLocalStorageHandle =
                    findMethod(JVMTI_INTERFACE_LAYOUT, "SetEnvironmentLocalStorage", jvmTiVtable,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

            MemorySegment mNormalEnvironmentSegment = agentPtr.asSlice(AGENT_NORMAL_ENV_OFFSET, JPLIS_ENVIRONMENT_LAYOUT.byteSize());

            int setEnvironmentLocalStorageResult = (int) setEnvironmentLocalStorageHandle.invokeExact(jvmTiEnv, mNormalEnvironmentSegment);
            if (setEnvironmentLocalStorageResult != JVMTI_ERROR_NONE) {
                System.err.println("SetEnvironmentLocalStorage 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEnvironmentLocalStorageResult));
            }

            AGENT_REDEFINE_AVAILABLE_VH.set(agentPtr, 0L, true);
            AGENT_NATIVE_PREFIX_AVAILABLE_VH.set(agentPtr, 0L, true);

            MethodHandle getCapabilitiesHandle =
                    findMethod(JVMTI_INTERFACE_LAYOUT, "GetCapabilities", jvmTiVtable,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS)),

                    addCapabilitiesHandle =
                            findMethod(JVMTI_INTERFACE_LAYOUT, "AddCapabilities", jvmTiVtable,
                                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, jvmTiEnv,
                    capabilities -> setBit(capabilities, Offsets.CAN_REDEFINE_CLASSES, true)
            );

            AGENT_REDEFINE_ADDED_VH.set(agentPtr, 0L, true);

            MemorySegment retransformerEnv = createJvmTiEnv(arena, getEnvHandle, javaVM);
            MemorySegment retransformerEnvVtable = getJvmTiVtable(retransformerEnv);
            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, retransformerEnv, capabilities ->  {
                setBit(capabilities, Offsets.CAN_RETRANSFORM_CLASSES, true);
                setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true);
            });

            MethodHandle hookHandle = MethodHandles.lookup().findStatic(
                    PanamaPureMemoryAgent.class, "eventHandlerClassFileLoadHook",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
                            MemorySegment.class, MemorySegment.class, MemorySegment.class,
                            MemorySegment.class, int.class, MemorySegment.class,
                            MemorySegment.class, MemorySegment.class)
            );
            MemorySegment hookStub = Linker.nativeLinker()
                    .upcallStub(hookHandle, CLASS_FILE_LOAD_HOOK_DESCRIPTOR, Arena.global());
            MemorySegment callbacks = Arena.global().allocate(JVMTI_EVENT_CALLBACKS_LAYOUT);
            callbacks.fill((byte) 0);
            VarHandle hookVH = JVMTI_EVENT_CALLBACKS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("ClassFileLoadHook"));
            hookVH.set(callbacks, 0L, hookStub);

            long sizeOfCallbacks = JVMTI_EVENT_CALLBACKS_LAYOUT.byteSize();
            MethodHandle setEventCallbacks =
                    findMethod(JVMTI_INTERFACE_LAYOUT, "SetEventCallbacks", retransformerEnvVtable,
                            FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,    // jvmtiError (return)
                                    ValueLayout.ADDRESS,     // jvmtiEnv*
                                    ValueLayout.ADDRESS,     // jvmtiEventCallbacks*
                                    ValueLayout.JAVA_INT     // jint size_of_callbacks
                            ));
            int setEventCallbacksResult = (int) setEventCallbacks.invokeExact(retransformerEnv, callbacks, (int) sizeOfCallbacks);
            if (setEventCallbacksResult != JVMTI_ERROR_NONE) {
                System.err.println("SetEventCallbacks 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEventCallbacksResult));
            }

            ENV_JVMTI_VH.set(retransEnv, 0L, retransformerEnv);
            ENV_AGENT_VH.set(retransEnv, 0L, agentPtr);
            ENV_RETRANS_VH.set(retransEnv, 0L, true);

            setEnvironmentLocalStorageResult = (int) setEnvironmentLocalStorageHandle.invokeExact(retransformerEnv, retransEnv);
            if (setEnvironmentLocalStorageResult != JVMTI_ERROR_NONE) {
                System.err.println("SetEnvironmentLocalStorage 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEnvironmentLocalStorageResult));
            }

            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, jvmTiEnv, capabilities ->
                    setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true)
            );
            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, retransformerEnv, capabilities ->
                    setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true)
            );

            AGENT_NATIVE_PREFIX_ADDED_VH.set(agentPtr, 0L, true);

            // I don't know why cannot activate this cap
//            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, jvmTiEnv, capabilities ->
//                    setBit(capabilities, Offsets.CAN_MAINTAIN_ORIGINAL_METHOD_ORDER, true)
//            , "a");

            MemorySegment callbacks2 = Arena.global().allocate(JVMTI_EVENT_CALLBACKS_LAYOUT);
            callbacks2.fill((byte) 0);
            hookVH.set(callbacks2, 0L, hookStub);
            int setEventCallbacksResult2 = (int) setEventCallbacks.invokeExact(jvmTiEnv, callbacks2, (int) sizeOfCallbacks);
            if (setEventCallbacksResult2 != JVMTI_ERROR_NONE) {
                System.err.println("SetEventCallbacks 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEventCallbacksResult2));
            }

            // 找到 SetEventNotificationMode 的 handle
            MethodHandle setEventNotificationMode = findMethod(JVMTI_INTERFACE_LAYOUT, "SetEventNotificationMode", retransformerEnvVtable,
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

            int JVMTI_ENABLE = 1;
            int JVMTI_EVENT_CLASS_FILE_LOAD_HOOK = 54;

            int res = (int) setEventNotificationMode.invokeExact(
                    retransformerEnv,
                    JVMTI_ENABLE,
                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,
                    MemorySegment.NULL
            );

            if (res != JVMTI_ERROR_NONE) {
                System.err.println("啟用 ClassFileLoadHook 失敗: " + JNIConstants.getJvmtiErrorMessage(res));
            }

            Class<?> clazz = Class.forName("sun.instrument.InstrumentationImpl");
            Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            instrumentation = (Instrumentation)
                    rf.newConstructorForSerialization(clazz, constructor).newInstance(agentPtr.address(), true, true, false);
        }
    }

    private static MemorySegment getJvmTiVtable(@NotNull MemorySegment jvmTiEnv) {
        long vtableSize = JVMTI_INTERFACE_LAYOUT.byteSize();
        MemorySegment jvmTiVtableBase = jvmTiEnv.get(ADDRESS, 0);
        return jvmTiVtableBase.reinterpret(vtableSize);
    }

    public static void eventHandlerClassFileLoadHook(
            MemorySegment jvmtiEnv,
            MemorySegment jniEnv,
            MemorySegment classBeingRedefined,
            MemorySegment loader,
            MemorySegment name,
            MemorySegment protectionDomain,
            int classDataLen,
            MemorySegment classData,
            MemorySegment newClassDataLenPtr,
            MemorySegment newClassDataPtr) {
    }

    private static MethodHandle findMethod(@NotNull GroupLayout layout, String name, @NotNull MemorySegment vtable, FunctionDescriptor descriptor) {
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(name));
        MemorySegment ptr = vtable.get(ADDRESS, offset);
        if (DEBUG) {
            System.out.println(name + " Function Pointer Address: " + ptr);
        }
        return Linker.nativeLinker().downcallHandle(ptr, descriptor);
    }

    private static MemorySegment createJvmTiEnv(@NotNull Arena arena, @NotNull MethodHandle getEnvHandle, MemorySegment javaVM) throws Throwable {
        // pEnv is void!!!!
        MemorySegment pEnv = arena.allocate(ADDRESS);
        int getJvmTiEnvResult = (int) getEnvHandle.invokeExact(javaVM, pEnv, JVMTI_VERSION_1_1);
        if (getJvmTiEnvResult != JNIConstants.JNI_OK) {
            System.err.println("GetEnv(getJvmTiEnv) 操作失敗: " + JNIConstants.getStatusMessage(getJvmTiEnvResult));
        }
        return pEnv.get(ADDRESS, 0).reinterpret(1024);
    }

    private static void addCap(@NotNull Arena arena, @NotNull MethodHandle getCapabilitiesHandle, MethodHandle addCapabilitiesHandle, MemorySegment jvmTiEnv, Consumer<MemorySegment> adder) throws Throwable {
        MemorySegment capabilities = arena.allocate(JVMTI_CAPABILITIES_LAYOUT);
        capabilities.fill((byte) 0);
        int getCapabilitiesResult = (int) getCapabilitiesHandle.invokeExact(jvmTiEnv, capabilities);
        if (getCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("GetCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getCapabilitiesResult));
        }

        adder.accept(capabilities);

        int addCapabilitiesResult = (int) addCapabilitiesHandle.invokeExact(jvmTiEnv, capabilities);
        if (addCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("AddCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(addCapabilitiesResult));
        }
    }

    private static @NotNull Path getJvmPath(@NotNull String os) {
        String javaHome = System.getProperty("java.home");
        if (os.contains("win")) return Paths.get(javaHome, "bin", "server", "jvm.dll");
        if (os.contains("mac")) return Paths.get(javaHome, "lib", "server", "libjvm.dylib");
        return Paths.get(javaHome, "lib", "server", "libjvm.so");
    }
}