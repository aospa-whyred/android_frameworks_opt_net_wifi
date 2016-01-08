# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_STATIC_JAVA_LIBRARIES := \
	mockito-target \
	android-support-test \
	wifi-service \
	services

LOCAL_JAVA_LIBRARIES := android.test.runner \
	mockito-target \
	android-support-test \
	wifi-service \
	services

LOCAL_JNI_SHARED_LIBRARIES := \
	libwifi-service \
	libc++ \
	libLLVM \
	libutils \
	libunwind \
	libhardware_legacy \
	libbase \
	libhardware \
	libnl \
	libcutils \
	libnetutils \
	libbacktrace \
	libnativehelper \

ifdef WPA_SUPPLICANT_VERSION
LOCAL_JNI_SHARED_LIBRARIES := $(LOCAL_JNI_SHARED_LIBRARIES) \
	libwpa_client
endif

LOCAL_PACKAGE_NAME := FrameworksWifiTests

include $(BUILD_PACKAGE)