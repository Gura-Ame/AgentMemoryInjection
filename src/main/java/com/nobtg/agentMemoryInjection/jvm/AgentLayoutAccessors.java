package com.nobtg.agentMemoryInjection.jvm;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.invoke.VarHandle;

import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.JPLIS_AGENT_LAYOUT;
import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.JPLIS_ENVIRONMENT_LAYOUT;

public final class AgentLayoutAccessors {
    public static final VarHandle ENV_JVMTI_VH = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mJVMTIEnv"));
    public static final VarHandle ENV_AGENT_VH   = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mAgent"));
    public static final VarHandle ENV_RETRANS_VH = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mIsRetransformer"));

    public static final VarHandle AGENT_JVM_VH            = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mJVM"));
    public static final VarHandle AGENT_IMPL_VH           = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mInstrumentationImpl"));
    public static final VarHandle AGENT_PREMAIN_CALLER_VH = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mPremainCaller"));
    public static final VarHandle AGENT_AGENTMAIN_CALLER_VH = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mAgentmainCaller"));
    public static final VarHandle AGENT_TRANSFORM_VH      = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mTransform"));

    public static final VarHandle AGENT_REDEFINE_AVAILABLE_VH       = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mRedefineAvailable"));
    public static final VarHandle AGENT_REDEFINE_ADDED_VH           = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mRedefineAdded"));
    public static final VarHandle AGENT_NATIVE_PREFIX_AVAILABLE_VH  = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mNativeMethodPrefixAvailable"));
    public static final VarHandle AGENT_NATIVE_PREFIX_ADDED_VH      = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mNativeMethodPrefixAdded"));

    public static final VarHandle AGENT_CLASS_NAME_VH = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mAgentClassName"));
    public static final VarHandle AGENT_OPTIONS_VH    = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mOptionsString"));
    public static final VarHandle AGENT_JAR_VH        = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mJarfile"));

    public static final VarHandle AGENT_PRINT_WARNING_VH = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mPrintWarning"));

    public static final long AGENT_NORMAL_ENV_OFFSET = JPLIS_AGENT_LAYOUT.byteOffset(PathElement.groupElement("mNormalEnvironment"));
    public static final long AGENT_RETRANS_ENV_OFFSET = JPLIS_AGENT_LAYOUT.byteOffset(PathElement.groupElement("mRetransformEnvironment"));
}