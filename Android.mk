LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES := telephony-common
#LOCAL_JAVA_LIBRARIES += telephony-common2
LOCAL_JAVA_LIBRARIES += radio_interactor_common
LOCAL_JNI_SHARED_LIBRARIES := libjni_sprdslt

LOCAL_PACKAGE_NAME := SprdSlt
LOCAL_CERTIFICATE := platform


include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
