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
import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.Offsets.CAN_REDEFINE_CLASSES;
import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.Offsets.CAN_SET_NATIVE_METHOD_PREFIX;
import static java.lang.foreign.ValueLayout.*;

public final class PanamaPureMemoryAgent {
    public static Instrumentation instrumentation;
    private static final boolean DEBUG = false;

    //    _JNI_IMPORT_OR_EXPORT_ jint JNICALL
//    JNI_GetCreatedJavaVMs(JavaVM **, jsize, jsize *);
    private static MemorySegment getJavaVMPtr(@NotNull Arena arena, @NotNull Linker linker, @NotNull SymbolLookup jvmDllLookup) throws Throwable {
        MemorySegment jniGetVM = jvmDllLookup.find("JNI_GetCreatedJavaVMs")
                .orElseThrow(() -> new RuntimeException("Could not find symbol: JNI_GetCreatedJavaVMs"));

        MethodHandle getCreatedVMs = linker.downcallHandle(jniGetVM,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));

        MemorySegment vmBuffer = arena.allocate(ADDRESS);
        MemorySegment nVMs = arena.allocate(ValueLayout.JAVA_INT);

        if ((int) getCreatedVMs.invokeExact(vmBuffer, 1, nVMs) != 0) {
            throw new RuntimeException("Failed to acquire JavaVM* pointer!");
        }

        if (nVMs.get(JAVA_INT, 0) != 1) {
            throw new RuntimeException("Why you get not only one but also another JVM??");
        }

        return vmBuffer.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
    }

    private static MemorySegment allocateJPLISAgent(@NotNull Arena arena, MemorySegment jvmTiEnv, MemorySegment jvmTiVtable) throws Throwable {
        MethodHandle allocateHandle = findMethod(JVMTI_INTERFACE_LAYOUT, "Allocate", jvmTiVtable,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_LONG, ADDRESS));

        long agentStructSize = JPLIS_AGENT_LAYOUT.byteSize();
        MemorySegment pPtr = arena.allocate(ADDRESS);
        int allocateAgentResult = (int) allocateHandle.invokeExact(jvmTiEnv, agentStructSize, pPtr);
        if (allocateAgentResult != JVMTI_ERROR_NONE) {
            System.err.println("Allocate(allocateAgent) 操作失敗: " + JNIConstants.getJvmtiErrorMessage(allocateAgentResult));
        }

        return pPtr.get(ADDRESS, 0).reinterpret(agentStructSize);
    }

    private static void initializeJPLISAgent(@NotNull Arena arena, MemorySegment javaVM, MemorySegment jvmTiEnv, MemorySegment jvmTiVTable
            , @NotNull MemorySegment agentPtr, MemorySegment agentMNormalEnvironment, MemorySegment agentMRetransformEnvironment) throws Throwable {
        agentPtr.fill((byte) 0);

        mJVM.set(agentPtr, 0L, javaVM);

        mJVMTIEnv.set(agentMNormalEnvironment, 0L, jvmTiEnv);
        mAgent.set(agentMNormalEnvironment, 0L, agentPtr);
        mIsRetransformer.set(agentMNormalEnvironment, 0L, false);

        mJVMTIEnv.set(agentMRetransformEnvironment, 0L, MemorySegment.NULL);
        mAgent.set(agentMRetransformEnvironment, 0L, agentPtr);
        mIsRetransformer.set(agentMRetransformEnvironment, 0L, false);

        mAgentmainCaller.set(agentPtr, 0L, MemorySegment.NULL);
        mInstrumentationImpl.set(agentPtr, 0L, MemorySegment.NULL);
        mPremainCaller.set(agentPtr, 0L, MemorySegment.NULL);
        mTransform.set(agentPtr, 0L, MemorySegment.NULL);
        mRedefineAvailable.set(agentPtr, 0L, false);
        mRedefineAdded.set(agentPtr, 0L, false);
        mNativeMethodPrefixAvailable.set(agentPtr, 0L, false);
        mNativeMethodPrefixAdded.set(agentPtr, 0L, false);
        mAgentClassName.set(agentPtr, 0L, MemorySegment.NULL);
        mOptionsString.set(agentPtr, 0L, MemorySegment.NULL);

        MemorySegment persistentJarPath = arena.allocateFrom("");
        mJarfile.set(agentPtr, 0L, persistentJarPath);
        mPrintWarning.set(agentPtr, 0L, false);

        setEnvironmentLocalStorageHandle(jvmTiEnv, jvmTiVTable, agentMNormalEnvironment);
        checkCapabilities(arena, jvmTiEnv, jvmTiVTable, agentPtr);

        MemorySegment phasePtr = arena.allocate(JVMTI_PHASE_LAYOUT);
        getPhase(jvmTiEnv, jvmTiVTable, phasePtr);
        int phase = phasePtr.get(JAVA_INT, 0);
        if (phase == JVMTI_PHASE_LIVE) {
            System.err.println("JPLIS_INIT_ERROR_NONE");
        } else if (phase != JVMTI_PHASE_ONLOAD) {
            /* called too early or called too late; either way bail out */
            System.err.println("JPLIS_INIT_ERROR_FAILURE");
        }

        // Following in VMInit...
        // But I don't want to write that
        // It doesn't matter because we're after the init phrase.
    }

    private static void setEnvironmentLocalStorageHandle(MemorySegment jvmTiEnv, MemorySegment jvmTiVtable, MemorySegment environment) throws Throwable {
        MethodHandle setEnvironmentLocalStorageHandle =
                findMethod(JVMTI_INTERFACE_LAYOUT, "SetEnvironmentLocalStorage", jvmTiVtable,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

        int setEnvironmentLocalStorageResult = (int) setEnvironmentLocalStorageHandle.invokeExact(jvmTiEnv, environment);
        if (setEnvironmentLocalStorageResult != JVMTI_ERROR_NONE) {
            System.err.println("SetEnvironmentLocalStorage 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEnvironmentLocalStorageResult));
        }
    }

    private static MemorySegment getJNIEnv(@NotNull Arena arena, MemorySegment javaVM, @NotNull MethodHandle getEnvHandle) throws Throwable {
        MemorySegment pEnv = arena.allocate(ADDRESS);
        int getJNIResult = (int) getEnvHandle.invokeExact(javaVM, pEnv, JNI_VERSION_1_2);
        if (getJNIResult != JNIConstants.JNI_OK) {
            System.err.println("GetEnv(getJNIEnv) 操作失敗: " + JNIConstants.getStatusMessage(getJNIResult));
        }
        return pEnv.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
    }

    private static int getPotentialCapabilities(MemorySegment jvmTiEnv, MemorySegment jvmTiVtable, MemorySegment potentialCapabilities) throws Throwable {
        MethodHandle getPotentialCapabilitiesHandle =
                findMethod(JVMTI_INTERFACE_LAYOUT, "GetPotentialCapabilities", jvmTiVtable,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

        return (int) getPotentialCapabilitiesHandle.invokeExact(jvmTiEnv, potentialCapabilities);
    }

    private static void checkCapabilities(@NotNull Arena arena, MemorySegment jvmTiEnv, MemorySegment jvmTiVtable, MemorySegment agentPtr) throws Throwable {
        MemorySegment potentialCapabilities = arena.allocate(JVMTI_CAPABILITIES_LAYOUT);
        potentialCapabilities.fill((byte) 0);

        int getPotentialCapabilitiesResult = getPotentialCapabilities(jvmTiEnv, jvmTiVtable, potentialCapabilities);
        // check_phase_ret
        if (getPotentialCapabilitiesResult == JVMTI_ERROR_WRONG_PHASE) {
            return;
        }
        if (getPotentialCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("SetEnvironmentLocalStorage 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getPotentialCapabilitiesResult));
        }

        if (getBit(potentialCapabilities, CAN_REDEFINE_CLASSES)) {
            mRedefineAvailable.set(agentPtr, 0L, true);
        } else {
            System.err.println("JVM Unsupported Redefine Classes.");
        }

        if (getBit(potentialCapabilities, CAN_SET_NATIVE_METHOD_PREFIX)) {
            mNativeMethodPrefixAvailable.set(agentPtr, 0L, true);
        } else {
            System.err.println("JVM Unsupported Set Native Method Prefix.");
        }
    }

    private static void getPhase(MemorySegment jvmTiEnv, MemorySegment jvmTiVtable, MemorySegment phrase) throws Throwable {
        MethodHandle getPhaseHandle =
                findMethod(JVMTI_INTERFACE_LAYOUT, "GetPhase", jvmTiVtable,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

        int getPhaseResult = (int) getPhaseHandle.invokeExact(jvmTiEnv, phrase);
        if (getPhaseResult != JVMTI_ERROR_NONE) {
            System.err.println("getPhaseHandle 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getPhaseResult));
        }
    }

    private static void convertCapabilityAttributes(Arena arena, MemorySegment jvmTiEnv, MemorySegment agentPtr
            , MethodHandle getCapabilitiesHandle, MethodHandle addCapabilitiesHandle) throws Throwable {
        addRedefineClassesCapability(arena, jvmTiEnv, agentPtr, getCapabilitiesHandle, addCapabilitiesHandle);
    }

    private static void addRedefineClassesCapability(Arena arena, MemorySegment jvmTiEnv, MemorySegment agentPtr
            , MethodHandle getCapabilitiesHandle, MethodHandle addCapabilitiesHandle) throws Throwable {
        boolean redefineAvailable = (boolean) mRedefineAvailable.get(agentPtr, 0L);
        boolean redefineAdded = (boolean) mRedefineAdded.get(agentPtr, 0L);
        if (!redefineAvailable || redefineAdded) return;

        int addCapabilitiesResult = addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, jvmTiEnv,
                capabilities -> setBit(capabilities, CAN_REDEFINE_CLASSES, true)
        );

        if (addCapabilitiesResult == JVMTI_ERROR_WRONG_PHASE) return;
        if (addCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("AddCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(addCapabilitiesResult));
        }

        mRedefineAdded.set(agentPtr, 0L, true);
    }

    public static void performInjection() throws Throwable {
        Linker linker = Linker.nativeLinker();
        String os = System.getProperty("os.name").toLowerCase();
        Path jvmPath = getJvmPath(os);

        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup jvmLookup = SymbolLookup.libraryLookup(jvmPath, arena);

            MemorySegment javaVM = getJavaVMPtr(arena, linker, jvmLookup);
            MemorySegment javaVMFunctions = javaVM.get(ADDRESS, 0).reinterpret(JNI_INVOKE_INTERFACE_LAYOUT.byteSize());

            MethodHandle getEnvHandle = findMethod(JNI_INVOKE_INTERFACE_LAYOUT, "GetEnv", javaVMFunctions,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ValueLayout.JAVA_INT));

            MemorySegment jvmTiEnv = createJvmTiEnv(arena, getEnvHandle, javaVM);
            MemorySegment jvmTiVtable = getJvmTiVtable(jvmTiEnv);

            MemorySegment agentPtr = allocateJPLISAgent(arena, jvmTiEnv, jvmTiVtable);
            MemorySegment agentMNormalEnvironment = agentPtr.asSlice(mNormalEnvironment, JPLIS_ENVIRONMENT_LAYOUT.byteSize());
            MemorySegment agentMRetransformEnvironment = agentPtr.asSlice(mRetransformEnvironment, JPLIS_ENVIRONMENT_LAYOUT.byteSize());

            initializeJPLISAgent(arena, javaVM, jvmTiEnv, jvmTiVtable, agentPtr, agentMNormalEnvironment, agentMRetransformEnvironment);

            MethodHandle getCapabilitiesHandle =
                    findMethod(JVMTI_INTERFACE_LAYOUT, "GetCapabilities", jvmTiVtable,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS)),

                    addCapabilitiesHandle =
                            findMethod(JVMTI_INTERFACE_LAYOUT, "AddCapabilities", jvmTiVtable,
                                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

            convertCapabilityAttributes(arena, jvmTiEnv, agentPtr, getCapabilitiesHandle, addCapabilitiesHandle);

            MemorySegment retransformerEnv = createJvmTiEnv(arena, getEnvHandle, javaVM);
            MemorySegment retransformerEnvVtable = getJvmTiVtable(retransformerEnv);
            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, retransformerEnv, capabilities -> {
                setBit(capabilities, Offsets.CAN_RETRANSFORM_CLASSES, true);
                setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true);
            });

            // BE VERY CAREFUL ABOUT THIS CLASSLOADING PROBLEM
            // If you don't init this, it "MAY" crash accidentally.
            inHook.set(false); // 強制初始化，hook 啟動前就建立好 ThreadLocal entry

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

            mJVMTIEnv.set(agentMRetransformEnvironment, 0L, retransformerEnv);
            mAgent.set(agentMRetransformEnvironment, 0L, agentPtr);
            mIsRetransformer.set(agentMRetransformEnvironment, 0L, true);

            setEnvironmentLocalStorageHandle(retransformerEnv, retransformerEnvVtable, agentMRetransformEnvironment);

            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, jvmTiEnv, capabilities ->
                    setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true)
            );
            addCap(arena, getCapabilitiesHandle, addCapabilitiesHandle, retransformerEnv, capabilities ->
                    setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true)
            );

            mNativeMethodPrefixAdded.set(agentPtr, 0L, true);

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

    private static final ThreadLocal<Boolean> inHook = ThreadLocal.withInitial(() -> false);

    // NOT com.nobtg.SomeClass
    // IS com/nobtg/SomeClass
    // WARNING: If you modify the codes which are outside the try-catch block, YOU SHOULD BE VERY CAREFUL
    // Because it WON'T print or dump anything even JVM crashed.
    // You NEVER know what causes the crash.
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

        // 先擋遞迴，不依賴任何可能觸發 class loading 的操作
        if (inHook.get()) return;

        inHook.set(true);
        try {
            if (name.equals(MemorySegment.NULL)) return;

            // reinterpret 給夠大的範圍讓 getString 找到 null terminator
            String className = name.reinterpret(Long.MAX_VALUE).getString(0);

            System.out.println("hook: " + className);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            inHook.set(false);
        }
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
        MemorySegment pEnv = arena.allocate(ADDRESS);
        int getJvmTiEnvResult = (int) getEnvHandle.invokeExact(javaVM, pEnv, JVMTI_VERSION_1_1);
        if (getJvmTiEnvResult != JNIConstants.JNI_OK) {
            System.err.println("GetEnv(getJvmTiEnv) 操作失敗: " + JNIConstants.getStatusMessage(getJvmTiEnvResult));
        }
        return pEnv.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
    }

    private static int addCap(@NotNull Arena arena, @NotNull MethodHandle getCapabilitiesHandle, MethodHandle addCapabilitiesHandle, MemorySegment jvmTiEnv, Consumer<MemorySegment> adder) throws Throwable {
        MemorySegment capabilities = arena.allocate(JVMTI_CAPABILITIES_LAYOUT);
        capabilities.fill((byte) 0);
        int getCapabilitiesResult = (int) getCapabilitiesHandle.invokeExact(jvmTiEnv, capabilities);
        if (getCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("GetCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getCapabilitiesResult));
        }

        adder.accept(capabilities);

        return (int) addCapabilitiesHandle.invokeExact(jvmTiEnv, capabilities);
    }

    private static @NotNull Path getJvmPath(@NotNull String os) {
        String javaHome = System.getProperty("java.home");
        if (os.contains("win")) return Paths.get(javaHome, "bin", "server", "jvm.dll");
        if (os.contains("mac")) return Paths.get(javaHome, "lib", "server", "libjvm.dylib");
        return Paths.get(javaHome, "lib", "server", "libjvm.so");
    }
}