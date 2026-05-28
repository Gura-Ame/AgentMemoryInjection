package com.nobtg.agentMemoryInjection;

import com.nobtg.agentMemoryInjection.jvm.JNIConstants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.ReflectionFactory;

import java.lang.foreign.*;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

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

    private static MemorySegment allocateJPLISAgent(@NotNull Arena arena, @NotNull PtrWithVtable jvmTiPWV) throws Throwable {
        MethodHandle allocateHandle = findMethod(JVMTI_INTERFACE_LAYOUT, "Allocate", jvmTiPWV.vtable,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_LONG, ADDRESS));

        long agentStructSize = JPLIS_AGENT_LAYOUT.byteSize();
        MemorySegment pPtr = arena.allocate(ADDRESS);
        int allocateAgentResult = (int) allocateHandle.invokeExact(jvmTiPWV.ptr, agentStructSize, pPtr);
        if (allocateAgentResult != JVMTI_ERROR_NONE) {
            System.err.println("Allocate(allocateAgent) 操作失敗: " + JNIConstants.getJvmtiErrorMessage(allocateAgentResult));
        }

        return pPtr.get(ADDRESS, 0).reinterpret(agentStructSize);
    }

    private static void initializeJPLISAgent(@NotNull Arena arena, MemorySegment javaVM, @NotNull PtrWithVtable jvmTiPWV
            , @NotNull MemorySegment agentPtr, MemorySegment agentMNormalEnvironment, MemorySegment agentMRetransformEnvironment) throws Throwable {
        agentPtr.fill((byte) 0);

        mJVM.set(agentPtr, 0L, javaVM);

        mJVMTIEnv.set(agentMNormalEnvironment, 0L, jvmTiPWV.ptr);
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

        MemorySegment persistentJarPath = Arena.global().allocateFrom("");
        mJarfile.set(agentPtr, 0L, persistentJarPath);
        mPrintWarning.set(agentPtr, 0L, false);

        setEnvironmentLocalStorageHandle(jvmTiPWV.ptr, jvmTiPWV.vtable, agentMNormalEnvironment);
        checkCapabilities(arena, jvmTiPWV.ptr, jvmTiPWV.vtable, agentPtr);

        MemorySegment phasePtr = arena.allocate(JVMTI_PHASE_LAYOUT);
        getPhase(jvmTiPWV.ptr, jvmTiPWV.vtable, phasePtr);
        int phase = phasePtr.get(JAVA_INT, 0);
        if (phase != JVMTI_PHASE_LIVE && phase != JVMTI_PHASE_ONLOAD) {
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

    private static void convertCapabilityAttributes(Arena arena, Linker linker, @NotNull PtrWithVtable jvmTiPWV
            , MemorySegment agentPtr, MemorySegment agentMNormalEnvironment
            , MemorySegment agentMRetransformEnvironment) throws Throwable {
        addRedefineClassesCapability(arena, jvmTiPWV, agentPtr);
        retransformableEnvironment(arena, linker, agentPtr, agentMRetransformEnvironment);
        addNativeMethodPrefixCapability(arena, agentPtr, agentMNormalEnvironment, agentMRetransformEnvironment);
        addOriginalMethodOrderCapability(arena, jvmTiPWV);
    }

    // this feature is testing by JDK
    // If you able it, it won't get you anything, even throw an exceptional massage in console.
    // So let's disable it.
    private static final boolean IS_THIS_FEATURE_OK = false;

    private static void addOriginalMethodOrderCapability(Arena arena, PtrWithVtable jvmTiPWV)
            throws Throwable {
        if (!IS_THIS_FEATURE_OK) return;
        addCap(arena, jvmTiPWV, capabilities ->
                setBit(capabilities, Offsets.CAN_MAINTAIN_ORIGINAL_METHOD_ORDER, true)
        );
    }

    private static void addRedefineClassesCapability(Arena arena, PtrWithVtable jvmTiPWV
            , MemorySegment agentPtr) throws Throwable {
        boolean redefineAvailable = (boolean) mRedefineAvailable.get(agentPtr, 0L);
        boolean redefineAdded = (boolean) mRedefineAdded.get(agentPtr, 0L);
        if (!redefineAvailable || redefineAdded) return;

        int addCapabilitiesResult = addCap(arena, jvmTiPWV,
                capabilities -> setBit(capabilities, CAN_REDEFINE_CLASSES, true)
        );

        if (addCapabilitiesResult == JVMTI_ERROR_WRONG_PHASE) return;
        if (addCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("AddCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(addCapabilitiesResult));
        }

        mRedefineAdded.set(agentPtr, 0L, true);
    }

    private static void retransformableEnvironment(Arena arena, Linker linker
            , MemorySegment agentPtr, MemorySegment agentMRetransformEnvironment) throws Throwable {
        if (!mJVMTIEnv.get(agentMRetransformEnvironment, 0L).equals(MemorySegment.NULL)) {
            return;
        }

        MemorySegment javaVM = (MemorySegment) mJVM.get(agentPtr, 0L);
        PtrWithVtable javaVMPWV = PtrWithVtable.createJNI(javaVM.reinterpret(ADDRESS.byteSize()));
        MemorySegment retransformerEnv = createJvmTiEnv(arena, javaVMPWV);
        PtrWithVtable retransformerEnvPWV = PtrWithVtable.createJVMTi(retransformerEnv);
        addCap(arena, retransformerEnvPWV, capabilities -> {
            setBit(capabilities, Offsets.CAN_RETRANSFORM_CLASSES, true);

            if ((boolean) mNativeMethodPrefixAdded.get(agentPtr, 0L)) {
                setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true);
            }
        });

        MemorySegment callbacks = Arena.global().allocate(JVMTI_EVENT_CALLBACKS_LAYOUT);
        callbacks.fill((byte) 0);
        ClassFileLoadHook.set(callbacks, 0L, eventHandlerClassFileLoadHookStub.apply(linker));

        setEventCallbacks(retransformerEnv, retransformerEnvPWV.vtable, callbacks);

        mJVMTIEnv.set(agentMRetransformEnvironment, 0L, retransformerEnv);
        mIsRetransformer.set(agentMRetransformEnvironment, 0L, true);

        setEnvironmentLocalStorageHandle(retransformerEnv, retransformerEnvPWV.vtable, agentMRetransformEnvironment);
    }

    private static void setEventCallbacks(MemorySegment jvmTiEnv, MemorySegment jvmTiEnvVtable, MemorySegment callbacks) throws Throwable {
        MethodHandle setEventCallbacks =
                findMethod(JVMTI_INTERFACE_LAYOUT, "SetEventCallbacks", jvmTiEnvVtable,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        long sizeOfCallbacks = JVMTI_EVENT_CALLBACKS_LAYOUT.byteSize();
        int setEventCallbacksResult = (int) setEventCallbacks.invokeExact(jvmTiEnv, callbacks, (int) sizeOfCallbacks);
        if (setEventCallbacksResult != JVMTI_ERROR_NONE) {
            System.err.println("SetEventCallbacks 操作失敗: " + JNIConstants.getJvmtiErrorMessage(setEventCallbacksResult));
        }
    }

    private static void addNativeMethodPrefixCapability(Arena arena, MemorySegment agentPtr
            , MemorySegment agentMNormalEnvironment, MemorySegment agentMRetransformEnvironment) throws Throwable {
        if ((!(boolean) mNativeMethodPrefixAvailable.get(agentPtr, 0L)) ||
                (boolean) mNativeMethodPrefixAdded.get(agentPtr, 0L)) {
            return;
        }

        MemorySegment jvmTiEnv = (MemorySegment) mJVMTIEnv.get(agentMNormalEnvironment, 0L);
        jvmTiEnv = jvmTiEnv.reinterpret(ADDRESS.byteSize());
        PtrWithVtable jvmTiPWV = PtrWithVtable.createJVMTi(jvmTiEnv);
        enableNativeMethodPrefixCapability(arena, jvmTiPWV);

        jvmTiEnv = (MemorySegment) mJVMTIEnv.get(agentMRetransformEnvironment, 0L);
        if (jvmTiEnv.address() != 0L) {
            jvmTiPWV = PtrWithVtable.createJVMTi(jvmTiEnv.reinterpret(ADDRESS.byteSize()));
            enableNativeMethodPrefixCapability(arena, jvmTiPWV);
        }

        mNativeMethodPrefixAdded.set(agentPtr, 0L, true);
    }

    private static void enableNativeMethodPrefixCapability(Arena arena, PtrWithVtable jvmTiPWV) throws Throwable {
        addCap(arena, jvmTiPWV, capabilities ->
                setBit(capabilities, Offsets.CAN_SET_NATIVE_METHOD_PREFIX, true)
        );
    }

    private static void setLivePhaseEventHandlers(Linker linker, @NotNull PtrWithVtable jvmTiPWV) throws Throwable {
        MemorySegment callbacks = Arena.global().allocate(JVMTI_EVENT_CALLBACKS_LAYOUT);
        callbacks.fill((byte) 0);
        ClassFileLoadHook.set(callbacks, 0L, eventHandlerClassFileLoadHookStub.apply(linker));
        setEventCallbacks(jvmTiPWV.ptr, jvmTiPWV.vtable, callbacks);
        // We don't need to delete VMInit hooks
        // Therefore, we don't need check_phase_ret_false
        // So, that's all
    }

    public static void performInjection() throws Throwable {
        Linker linker = Linker.nativeLinker();
        String os = System.getProperty("os.name").toLowerCase();
        Path jvmPath = getJvmPath(os);

        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup jvmLookup = SymbolLookup.libraryLookup(jvmPath, arena);

            MemorySegment javaVM = getJavaVMPtr(arena, linker, jvmLookup);
            PtrWithVtable javaVMPWV = PtrWithVtable.createJNI(javaVM);

            MemorySegment jvmTiEnv = createJvmTiEnv(arena, javaVMPWV);
            PtrWithVtable jvmTiPWV = PtrWithVtable.createJVMTi(jvmTiEnv);

            MemorySegment agentPtr = allocateJPLISAgent(arena, jvmTiPWV);
            MemorySegment agentMNormalEnvironment = agentPtr.asSlice(mNormalEnvironment, JPLIS_ENVIRONMENT_LAYOUT.byteSize());
            MemorySegment agentMRetransformEnvironment = agentPtr.asSlice(mRetransformEnvironment, JPLIS_ENVIRONMENT_LAYOUT.byteSize());

            initializeJPLISAgent(arena, javaVM, jvmTiPWV, agentPtr, agentMNormalEnvironment, agentMRetransformEnvironment);

            // BE VERY CAREFUL ABOUT THIS CLASSLOADING PROBLEM
            // If you don't init this, it "MAY" crash accidentally.
            inHook.set(false); // 強制初始化，hook 啟動前就建立好 ThreadLocal entry

            convertCapabilityAttributes(arena, linker, jvmTiPWV, agentPtr, agentMNormalEnvironment, agentMRetransformEnvironment);
            setLivePhaseEventHandlers(linker, jvmTiPWV);

            Class<?> clazz = Class.forName("sun.instrument.InstrumentationImpl");
            Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            instrumentation = (Instrumentation)
                    rf.newConstructorForSerialization(clazz, constructor).newInstance(agentPtr.address(), true, true, false);
        }
    }

    private static void setEventNotificationMode(MemorySegment jvmTiEnv, MemorySegment jvmTiEnvVtable, int mode, int eventType) throws Throwable {
        MethodHandle setEventNotificationMode =
                findMethod(JVMTI_INTERFACE_LAYOUT, "SetEventNotificationMode", jvmTiEnvVtable,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

        int res = (int) setEventNotificationMode.invokeExact(
                jvmTiEnv,
                mode,
                eventType,
                MemorySegment.NULL
        );

        if (res != JVMTI_ERROR_NONE) {
            System.err.println("啟用 ClassFileLoadHook 失敗: " + JNIConstants.getJvmtiErrorMessage(res));
        }
    }

    private static final ThreadLocal<Boolean> inHook = ThreadLocal.withInitial(() -> false);

    private static final VarHandle ClassFileLoadHook =
            JVMTI_EVENT_CALLBACKS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("ClassFileLoadHook"));

    private static final MethodHandle transform;

    static {
        try {
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();

            Constructor<MethodHandles.Lookup> objectConstructor =
                    MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);

            Constructor<?> lookupConstructor = rf.newConstructorForSerialization(
                    MethodHandles.Lookup.class,
                    objectConstructor
            );

            MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup)
                    lookupConstructor.newInstance(Object.class, null, -1);

            Class<?> implClass = Class.forName("sun.instrument.InstrumentationImpl");
            transform = trustedLookup.findVirtual(implClass, "transform", MethodType.methodType(byte[].class, Module.class, ClassLoader.class, String.class, Class.class, ProtectionDomain.class, byte[].class, boolean.class));
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    // NOT com.nobtg.SomeClass
    // IS com/nobtg/SomeClass
    // WARNING: If you modify the codes which are outside the try-catch block, YOU SHOULD BE VERY CAREFUL
    // Because it WON'T print or dump anything even JVM crashed.
    // You NEVER know what causes the crash.
    private static void eventHandlerClassFileLoadHook(
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
        Arena arena = Arena.ofConfined();
        try {
            PtrWithVtable jvmTiPWV = PtrWithVtable.createJVMTi(jvmtiEnv.reinterpret(ADDRESS.byteSize()));
            if (!getJPLISEnvironment(arena, jvmTiPWV).equals(MemorySegment.NULL)) {

            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            inHook.set(false);
            arena.close();
        }
    }

    private record PtrWithVtable(MemorySegment ptr, MemorySegment vtable) {
        @Contract("_, _, _ -> new")
        public static @NotNull PtrWithVtable create(MemorySegment ptr, long vtableOffset, long vtableSize) {
            return new PtrWithVtable(ptr, ptr.get(ADDRESS, vtableOffset).reinterpret(vtableSize));
        }

        public static @NotNull PtrWithVtable createWithZeroIndex(MemorySegment ptr, long vtableSize) {
            return create(ptr, 0L, vtableSize);
        }

        public static @NotNull PtrWithVtable createJVMTi(MemorySegment ptr) {
            return createWithZeroIndex(ptr, JVMTI_INTERFACE_LAYOUT.byteSize());
        }

        public static @NotNull PtrWithVtable createJNI(MemorySegment ptr) {
            return createWithZeroIndex(ptr, JNI_INVOKE_INTERFACE_LAYOUT.byteSize());
        }
    }

    private static final MethodHandle eventHandlerClassFileLoadHookHandle;

    static {
        try {
            eventHandlerClassFileLoadHookHandle = MethodHandles.lookup().findStatic(
                    PanamaPureMemoryAgent.class, "eventHandlerClassFileLoadHook",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
                            MemorySegment.class, MemorySegment.class, MemorySegment.class,
                            MemorySegment.class, int.class, MemorySegment.class,
                            MemorySegment.class, MemorySegment.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Function<Linker, MemorySegment> eventHandlerClassFileLoadHookStub = linker ->
            linker.upcallStub(eventHandlerClassFileLoadHookHandle,
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                            JAVA_INT, ADDRESS, ADDRESS, ADDRESS), Arena.global());

    record TriKey(long key1, GroupLayout key2, String key3) {
    }

    private static final HashMap<TriKey, MethodHandle> cache = new HashMap<>();

    private static MethodHandle findMethod(@NotNull GroupLayout layout, String name, @NotNull MemorySegment vtable, FunctionDescriptor descriptor) {
        TriKey cacheKey = new TriKey(vtable.address(), layout, name);
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(name));
        MemorySegment ptr = vtable.get(ADDRESS, offset);
        if (DEBUG) {
            System.out.println(name + " Function Pointer Address: " + ptr);
        }

        MethodHandle result = Linker.nativeLinker().downcallHandle(ptr, descriptor);
        cache.put(cacheKey, result);
        return result;
    }

    private static MemorySegment createJvmTiEnv(@NotNull Arena arena, @NotNull PtrWithVtable javaVMPWV) throws Throwable {
        MemorySegment pEnv = arena.allocate(ADDRESS);

        MethodHandle getEnvHandle = findMethod(JNI_INVOKE_INTERFACE_LAYOUT, "GetEnv", javaVMPWV.vtable,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ValueLayout.JAVA_INT));

        int getJvmTiEnvResult = (int) getEnvHandle.invokeExact(javaVMPWV.ptr, pEnv, JVMTI_VERSION_1_1);
        if (getJvmTiEnvResult != JNIConstants.JNI_OK) {
            System.err.println("GetEnv(getJvmTiEnv) 操作失敗: " + JNIConstants.getStatusMessage(getJvmTiEnvResult));
        }
        return pEnv.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
    }

    private static @NotNull MemorySegment getJPLISEnvironment(Arena arena, PtrWithVtable jvmTiPWV) throws Throwable {
        MemorySegment environment = getEnvironmentLocalStorage(arena, jvmTiPWV);
        if (environment.address() != 0L &&
                ((MemorySegment) mJVMTIEnv.get(environment, 0L)).address() == jvmTiPWV.ptr.address()) {
            return environment;
        }
        return MemorySegment.NULL;
    }

    private static MemorySegment getEnvironmentLocalStorage(@NotNull Arena arena, @NotNull PtrWithVtable jvmTiPWV) throws Throwable {
        MemorySegment pEnv = arena.allocate(ADDRESS);
        MethodHandle getEnvironmentLocalStorageHandle = findMethod(JVMTI_INTERFACE_LAYOUT, "GetEnvironmentLocalStorage", jvmTiPWV.vtable,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

        int getEnvironmentLocalStorageResult = (int) getEnvironmentLocalStorageHandle.invokeExact(jvmTiPWV.ptr, pEnv);
        if (getEnvironmentLocalStorageResult != JVMTI_ERROR_NONE) {
            System.err.println("GetEnvironmentLocalStorage 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getEnvironmentLocalStorageResult));
        }
        return pEnv.get(ADDRESS, 0).reinterpret(JPLIS_ENVIRONMENT_LAYOUT.byteSize());
    }

    private static int addCap(@NotNull Arena arena, @NotNull PtrWithVtable jvmTiPWV, Consumer<MemorySegment> adder) throws Throwable {
        MethodHandle getCapabilitiesHandle =
                findMethod(JVMTI_INTERFACE_LAYOUT, "GetCapabilities", jvmTiPWV.vtable,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS)),

                addCapabilitiesHandle =
                        findMethod(JVMTI_INTERFACE_LAYOUT, "AddCapabilities", jvmTiPWV.vtable,
                                FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));

        MemorySegment capabilities = arena.allocate(JVMTI_CAPABILITIES_LAYOUT);
        capabilities.fill((byte) 0);
        int getCapabilitiesResult = (int) getCapabilitiesHandle.invokeExact(jvmTiPWV.ptr, capabilities);
        if (getCapabilitiesResult != JVMTI_ERROR_NONE) {
            System.err.println("GetCapabilities 操作失敗: " + JNIConstants.getJvmtiErrorMessage(getCapabilitiesResult));
        }

        adder.accept(capabilities);

        return (int) addCapabilitiesHandle.invokeExact(jvmTiPWV.ptr, capabilities);
    }

    private static @NotNull Path getJvmPath(@NotNull String os) {
        String javaHome = System.getProperty("java.home");
        if (os.contains("win")) return Paths.get(javaHome, "bin", "server", "jvm.dll");
        if (os.contains("mac")) return Paths.get(javaHome, "lib", "server", "libjvm.dylib");
        return Paths.get(javaHome, "lib", "server", "libjvm.so");
    }
}