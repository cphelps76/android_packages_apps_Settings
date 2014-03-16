LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := bouncycastle telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := guava android-support-v4 jsr305
ifdef DOLBY_DAP
LOCAL_JAVA_LIBRARIES += framework_ext
else
LOCAL_STATIC_JAVA_LIBRARIES += libsds
endif #DOLBY_DAP

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := Settings
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
include $(BUILD_PACKAGE)

ifndef DOLBY_DAP
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libsds:ds.jar
include $(BUILD_MULTI_PREBUILT)
endif

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
