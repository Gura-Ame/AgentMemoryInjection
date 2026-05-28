package com.nobtg.agentMemoryInjection.jvm;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class JNIConstants {
    // === JVMTI 事件模式 (jvmtiEventMode) ===
    public static final int JVMTI_DISABLE = 0;
    public static final int JVMTI_ENABLE = 1;

    // === JVMTI 事件類型 (jvmtiEvent) ===
    public static final int JVMTI_MIN_EVENT_TYPE_VAL = 50;
    public static final int JVMTI_EVENT_VM_INIT = 50;
    public static final int JVMTI_EVENT_VM_DEATH = 51;
    public static final int JVMTI_EVENT_THREAD_START = 52;
    public static final int JVMTI_EVENT_THREAD_END = 53;
    public static final int JVMTI_EVENT_CLASS_FILE_LOAD_HOOK = 54;
    public static final int JVMTI_EVENT_CLASS_LOAD = 55;
    public static final int JVMTI_EVENT_CLASS_PREPARE = 56;
    public static final int JVMTI_EVENT_VM_START = 57;
    public static final int JVMTI_EVENT_EXCEPTION = 58;
    public static final int JVMTI_EVENT_EXCEPTION_CATCH = 59;
    public static final int JVMTI_EVENT_SINGLE_STEP = 60;
    public static final int JVMTI_EVENT_FRAME_POP = 61;
    public static final int JVMTI_EVENT_BREAKPOINT = 62;
    public static final int JVMTI_EVENT_FIELD_ACCESS = 63;
    public static final int JVMTI_EVENT_FIELD_MODIFICATION = 64;
    public static final int JVMTI_EVENT_METHOD_ENTRY = 65;
    public static final int JVMTI_EVENT_METHOD_EXIT = 66;
    public static final int JVMTI_EVENT_NATIVE_METHOD_BIND = 67;
    public static final int JVMTI_EVENT_COMPILED_METHOD_LOAD = 68;
    public static final int JVMTI_EVENT_COMPILED_METHOD_UNLOAD = 69;
    public static final int JVMTI_EVENT_DYNAMIC_CODE_GENERATED = 70;
    public static final int JVMTI_EVENT_DATA_DUMP_REQUEST = 71;
    public static final int JVMTI_EVENT_MONITOR_WAIT = 73;
    public static final int JVMTI_EVENT_MONITOR_WAITED = 74;
    public static final int JVMTI_EVENT_MONITOR_CONTENDED_ENTER = 75;
    public static final int JVMTI_EVENT_MONITOR_CONTENDED_ENTERED = 76;
    public static final int JVMTI_EVENT_RESOURCE_EXHAUSTED = 80;
    public static final int JVMTI_EVENT_GARBAGE_COLLECTION_START = 81;
    public static final int JVMTI_EVENT_GARBAGE_COLLECTION_FINISH = 82;
    public static final int JVMTI_EVENT_OBJECT_FREE = 83;
    public static final int JVMTI_EVENT_VM_OBJECT_ALLOC = 84;
    public static final int JVMTI_EVENT_SAMPLED_OBJECT_ALLOC = 86;
    public static final int JVMTI_EVENT_VIRTUAL_THREAD_START = 87;
    public static final int JVMTI_EVENT_VIRTUAL_THREAD_END = 88;
    public static final int JVMTI_MAX_EVENT_TYPE_VAL = 88;

    // === JNI 布林值 ===
    public static final int JNI_FALSE = 0;
    public static final int JNI_TRUE = 1;

    // === 版本定義 ===
    public static final int JVMTI_VERSION_1_1 = 0x30010100;
    public static final int JNI_VERSION_1_2 = 0x00010002;

    // === JNI 錯誤代碼 ===
    public static final int JNI_OK = 0;
    public static final int JNI_ERR = -1;
    public static final int JNI_EDETACHED = -2;
    public static final int JNI_EVERSION = -3;
    public static final int JNI_ENOMEM = -4;
    public static final int JNI_EEXIST = -5;
    public static final int JNI_EINVAL = -6;

    // === JVMTI 錯誤代碼 ===
    public static final int JVMTI_ERROR_NONE = 0;
    public static final int JVMTI_ERROR_INVALID_THREAD = 10;
    public static final int JVMTI_ERROR_INVALID_THREAD_GROUP = 11;
    public static final int JVMTI_ERROR_INVALID_PRIORITY = 12;
    public static final int JVMTI_ERROR_THREAD_NOT_SUSPENDED = 13;
    public static final int JVMTI_ERROR_THREAD_SUSPENDED = 14;
    public static final int JVMTI_ERROR_THREAD_NOT_ALIVE = 15;
    public static final int JVMTI_ERROR_INVALID_OBJECT = 20;
    public static final int JVMTI_ERROR_INVALID_CLASS = 21;
    public static final int JVMTI_ERROR_CLASS_NOT_PREPARED = 22;
    public static final int JVMTI_ERROR_INVALID_METHODID = 23;
    public static final int JVMTI_ERROR_INVALID_LOCATION = 24;
    public static final int JVMTI_ERROR_INVALID_FIELDID = 25;
    public static final int JVMTI_ERROR_INVALID_MODULE = 26;
    public static final int JVMTI_ERROR_NO_MORE_FRAMES = 31;
    public static final int JVMTI_ERROR_OPAQUE_FRAME = 32;
    public static final int JVMTI_ERROR_TYPE_MISMATCH = 34;
    public static final int JVMTI_ERROR_INVALID_SLOT = 35;
    public static final int JVMTI_ERROR_DUPLICATE = 40;
    public static final int JVMTI_ERROR_NOT_FOUND = 41;
    public static final int JVMTI_ERROR_INVALID_MONITOR = 50;
    public static final int JVMTI_ERROR_NOT_MONITOR_OWNER = 51;
    public static final int JVMTI_ERROR_INTERRUPT = 52;
    public static final int JVMTI_ERROR_INVALID_CLASS_FORMAT = 60;
    public static final int JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION = 61;
    public static final int JVMTI_ERROR_FAILS_VERIFICATION = 62;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED = 63;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED = 64;
    public static final int JVMTI_ERROR_INVALID_TYPESTATE = 65;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED = 66;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED = 67;
    public static final int JVMTI_ERROR_UNSUPPORTED_VERSION = 68;
    public static final int JVMTI_ERROR_NAMES_DONT_MATCH = 69;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED = 70;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_ATTRIBUTE_CHANGED = 72;
    public static final int JVMTI_ERROR_UNSUPPORTED_OPERATION = 73;
    public static final int JVMTI_ERROR_UNMODIFIABLE_CLASS = 79;
    public static final int JVMTI_ERROR_UNMODIFIABLE_MODULE = 80;
    public static final int JVMTI_ERROR_NOT_AVAILABLE = 98;
    public static final int JVMTI_ERROR_MUST_POSSESS_CAPABILITY = 99;
    public static final int JVMTI_ERROR_NULL_POINTER = 100;
    public static final int JVMTI_ERROR_ABSENT_INFORMATION = 101;
    public static final int JVMTI_ERROR_INVALID_EVENT_TYPE = 102;
    public static final int JVMTI_ERROR_ILLEGAL_ARGUMENT = 103;
    public static final int JVMTI_ERROR_NATIVE_METHOD = 104;
    public static final int JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED = 106;
    public static final int JVMTI_ERROR_OUT_OF_MEMORY = 110;
    public static final int JVMTI_ERROR_ACCESS_DENIED = 111;
    public static final int JVMTI_ERROR_WRONG_PHASE = 112;
    public static final int JVMTI_ERROR_INTERNAL = 113;
    public static final int JVMTI_ERROR_UNATTACHED_THREAD = 115;
    public static final int JVMTI_ERROR_INVALID_ENVIRONMENT = 116;
    public static final int JVMTI_ERROR_MAX = 116;

    /**
     * 判讀 JNI 狀態代碼並返回描述文字
     *
     * @param code 傳入的 JNI 狀態代碼
     * @return 狀態描述
     */
    @Contract(pure = true)
    public static @NotNull String getStatusMessage(int code) {
        return switch (code) {
            case JNI_OK -> "Success";
            case JNI_ERR -> "Unknown error";
            case JNI_EDETACHED -> "Thread detached from the VM";
            case JNI_EVERSION -> "JNI version error";
            case JNI_ENOMEM -> "Not enough memory";
            case JNI_EEXIST -> "VM already created";
            case JNI_EINVAL -> "Invalid arguments";
            default -> "Unknown status code: " + code;
        };
    }

    /**
     * 判讀 JVMTI 錯誤代碼並返回對應的錯誤常數名稱與描述
     *
     * @param code 傳入的 JVMTI 錯誤代碼
     * @return 錯誤描述文字
     */
    @Contract(pure = true)
    public static @NotNull String getJvmtiErrorMessage(int code) {
        return switch (code) {
            case JVMTI_ERROR_NONE -> "No error (JVMTI_ERROR_NONE)";
            case JVMTI_ERROR_INVALID_THREAD -> "Invalid thread (JVMTI_ERROR_INVALID_THREAD)";
            case JVMTI_ERROR_INVALID_THREAD_GROUP -> "Invalid thread group (JVMTI_ERROR_INVALID_THREAD_GROUP)";
            case JVMTI_ERROR_INVALID_PRIORITY -> "Invalid priority (JVMTI_ERROR_INVALID_PRIORITY)";
            case JVMTI_ERROR_THREAD_NOT_SUSPENDED -> "Thread not suspended (JVMTI_ERROR_THREAD_NOT_SUSPENDED)";
            case JVMTI_ERROR_THREAD_SUSPENDED -> "Thread suspended (JVMTI_ERROR_THREAD_SUSPENDED)";
            case JVMTI_ERROR_THREAD_NOT_ALIVE -> "Thread not alive (JVMTI_ERROR_THREAD_NOT_ALIVE)";
            case JVMTI_ERROR_INVALID_OBJECT -> "Invalid object (JVMTI_ERROR_INVALID_OBJECT)";
            case JVMTI_ERROR_INVALID_CLASS -> "Invalid class (JVMTI_ERROR_INVALID_CLASS)";
            case JVMTI_ERROR_CLASS_NOT_PREPARED -> "Class not prepared (JVMTI_ERROR_CLASS_NOT_PREPARED)";
            case JVMTI_ERROR_INVALID_METHODID -> "Invalid method ID (JVMTI_ERROR_INVALID_METHODID)";
            case JVMTI_ERROR_INVALID_LOCATION -> "Invalid location (JVMTI_ERROR_INVALID_LOCATION)";
            case JVMTI_ERROR_INVALID_FIELDID -> "Invalid field ID (JVMTI_ERROR_INVALID_FIELDID)";
            case JVMTI_ERROR_INVALID_MODULE -> "Invalid module (JVMTI_ERROR_INVALID_MODULE)";
            case JVMTI_ERROR_NO_MORE_FRAMES -> "No more frames (JVMTI_ERROR_NO_MORE_FRAMES)";
            case JVMTI_ERROR_OPAQUE_FRAME -> "Opaque frame (JVMTI_ERROR_OPAQUE_FRAME)";
            case JVMTI_ERROR_TYPE_MISMATCH -> "Type mismatch (JVMTI_ERROR_TYPE_MISMATCH)";
            case JVMTI_ERROR_INVALID_SLOT -> "Invalid slot (JVMTI_ERROR_INVALID_SLOT)";
            case JVMTI_ERROR_DUPLICATE -> "Duplicate item (JVMTI_ERROR_DUPLICATE)";
            case JVMTI_ERROR_NOT_FOUND -> "Not found (JVMTI_ERROR_NOT_FOUND)";
            case JVMTI_ERROR_INVALID_MONITOR -> "Invalid monitor (JVMTI_ERROR_INVALID_MONITOR)";
            case JVMTI_ERROR_NOT_MONITOR_OWNER -> "Not monitor owner (JVMTI_ERROR_NOT_MONITOR_OWNER)";
            case JVMTI_ERROR_INTERRUPT -> "Interrupted (JVMTI_ERROR_INTERRUPT)";
            case JVMTI_ERROR_INVALID_CLASS_FORMAT -> "Invalid class format (JVMTI_ERROR_INVALID_CLASS_FORMAT)";
            case JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION -> "Circular class definition (JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION)";
            case JVMTI_ERROR_FAILS_VERIFICATION -> "Fails verification (JVMTI_ERROR_FAILS_VERIFICATION)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED -> "Unsupported redefinition: method added (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED -> "Unsupported redefinition: schema changed (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED)";
            case JVMTI_ERROR_INVALID_TYPESTATE -> "Invalid type state (JVMTI_ERROR_INVALID_TYPESTATE)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED -> "Unsupported redefinition: hierarchy changed (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED -> "Unsupported redefinition: method deleted (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED)";
            case JVMTI_ERROR_UNSUPPORTED_VERSION -> "Unsupported version (JVMTI_ERROR_UNSUPPORTED_VERSION)";
            case JVMTI_ERROR_NAMES_DONT_MATCH -> "Names do not match (JVMTI_ERROR_NAMES_DONT_MATCH)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED -> "Unsupported redefinition: class modifiers changed (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED -> "Unsupported redefinition: method modifiers changed (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED)";
            case JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_ATTRIBUTE_CHANGED -> "Unsupported redefinition: class attribute changed (JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_ATTRIBUTE_CHANGED)";
            case JVMTI_ERROR_UNSUPPORTED_OPERATION -> "Unsupported operation (JVMTI_ERROR_UNSUPPORTED_OPERATION)";
            case JVMTI_ERROR_UNMODIFIABLE_CLASS -> "Unmodifiable class (JVMTI_ERROR_UNMODIFIABLE_CLASS)";
            case JVMTI_ERROR_UNMODIFIABLE_MODULE -> "Unmodifiable module (JVMTI_ERROR_UNMODIFIABLE_MODULE)";
            case JVMTI_ERROR_NOT_AVAILABLE -> "Feature not available (JVMTI_ERROR_NOT_AVAILABLE)";
            case JVMTI_ERROR_MUST_POSSESS_CAPABILITY -> "Must possess capability (JVMTI_ERROR_MUST_POSSESS_CAPABILITY)";
            case JVMTI_ERROR_NULL_POINTER -> "Null pointer (JVMTI_ERROR_NULL_POINTER)";
            case JVMTI_ERROR_ABSENT_INFORMATION -> "Absent information (JVMTI_ERROR_ABSENT_INFORMATION)";
            case JVMTI_ERROR_INVALID_EVENT_TYPE -> "Invalid event type (JVMTI_ERROR_INVALID_EVENT_TYPE)";
            case JVMTI_ERROR_ILLEGAL_ARGUMENT -> "Illegal argument (JVMTI_ERROR_ILLEGAL_ARGUMENT)";
            case JVMTI_ERROR_NATIVE_METHOD -> "Native method (JVMTI_ERROR_NATIVE_METHOD)";
            case JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED -> "Class loader unsupported (JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED)";
            case JVMTI_ERROR_OUT_OF_MEMORY -> "Out of memory (JVMTI_ERROR_OUT_OF_MEMORY)";
            case JVMTI_ERROR_ACCESS_DENIED -> "Access denied (JVMTI_ERROR_ACCESS_DENIED)";
            case JVMTI_ERROR_WRONG_PHASE -> "Wrong phase (JVMTI_ERROR_WRONG_PHASE)";
            case JVMTI_ERROR_INTERNAL -> "Internal error (JVMTI_ERROR_INTERNAL)";
            case JVMTI_ERROR_UNATTACHED_THREAD -> "Unattached thread (JVMTI_ERROR_UNATTACHED_THREAD)";
            case JVMTI_ERROR_INVALID_ENVIRONMENT -> "Invalid environment (JVMTI_ERROR_INVALID_ENVIRONMENT)";
            default -> "Unknown JVMTI error code: " + code;
        };
    }
}