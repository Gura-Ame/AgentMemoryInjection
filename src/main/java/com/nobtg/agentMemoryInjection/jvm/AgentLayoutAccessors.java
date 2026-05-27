package com.nobtg.agentMemoryInjection.jvm;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.invoke.VarHandle;

import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.JPLIS_AGENT_LAYOUT;
import static com.nobtg.agentMemoryInjection.jvm.JPLISLayouts.JPLIS_ENVIRONMENT_LAYOUT;

public final class AgentLayoutAccessors {
    public static final VarHandle mJVMTIEnv = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mJVMTIEnv"));
    public static final VarHandle mAgent = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mAgent"));
    public static final VarHandle mIsRetransformer = JPLIS_ENVIRONMENT_LAYOUT.varHandle(PathElement.groupElement("mIsRetransformer"));

    public static final VarHandle mJVM = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mJVM"));
    public static final VarHandle mInstrumentationImpl = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mInstrumentationImpl"));
    public static final VarHandle mPremainCaller = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mPremainCaller"));
    public static final VarHandle mAgentmainCaller = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mAgentmainCaller"));
    public static final VarHandle mTransform = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mTransform"));

    public static final VarHandle mRedefineAvailable = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mRedefineAvailable"));
    public static final VarHandle mRedefineAdded = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mRedefineAdded"));
    public static final VarHandle mNativeMethodPrefixAvailable = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mNativeMethodPrefixAvailable"));
    public static final VarHandle mNativeMethodPrefixAdded = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mNativeMethodPrefixAdded"));

    public static final VarHandle mAgentClassName = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mAgentClassName"));
    public static final VarHandle mOptionsString = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mOptionsString"));
    public static final VarHandle mJarfile = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mJarfile"));

    public static final VarHandle mPrintWarning = JPLIS_AGENT_LAYOUT.varHandle(PathElement.groupElement("mPrintWarning"));

    public static final long mNormalEnvironment = JPLIS_AGENT_LAYOUT.byteOffset(PathElement.groupElement("mNormalEnvironment"));
    public static final long mRetransformEnvironment = JPLIS_AGENT_LAYOUT.byteOffset(PathElement.groupElement("mRetransformEnvironment"));
}