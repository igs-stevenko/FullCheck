#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <android/log.h>

#include "sysinfo_ioctl.h"

#define LOG_TAG "SysInfoJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_fullcheck_SysInfoNative_getProjectName(JNIEnv *env, jobject /* this */) {
    const char *device_path = "/dev/system_info";
    int fd = open(device_path, O_RDONLY);
    if (fd < 0) {
        LOGE("Error opening device %s", device_path);
        return env->NewStringUTF("N/A");
    }

    struct system_info g_sysinfo;
    memset(&g_sysinfo, 0, sizeof(g_sysinfo));

    if (ioctl(fd, GET_SYSINFO, &g_sysinfo) < 0) {
        LOGE("Error using ioctl");
        close(fd);
        return env->NewStringUTF("N/A");
    }

    close(fd);
    
    // Ensure null-terminated string
    char proj_name[21];
    memcpy(proj_name, g_sysinfo.proj_name, 20);
    proj_name[20] = '\0';
    
    LOGI("Project Name: %s", proj_name);
    return env->NewStringUTF(proj_name);
}

JNIEXPORT jstring JNICALL
Java_com_example_fullcheck_SysInfoNative_getGitVersion(JNIEnv *env, jobject /* this */) {
    const char *device_path = "/dev/system_info";
    int fd = open(device_path, O_RDONLY);
    if (fd < 0) {
        LOGE("Error opening device %s", device_path);
        return env->NewStringUTF("N/A");
    }

    struct system_info g_sysinfo;
    memset(&g_sysinfo, 0, sizeof(g_sysinfo));

    if (ioctl(fd, GET_SYSINFO, &g_sysinfo) < 0) {
        LOGE("Error using ioctl");
        close(fd);
        return env->NewStringUTF("N/A");
    }

    close(fd);
    
    // Ensure null-terminated string
    char git_ver[7];
    memcpy(git_ver, g_sysinfo.git_ver, 6);
    git_ver[6] = '\0';
    
    LOGI("Git Version: %s", git_ver);
    return env->NewStringUTF(git_ver);
}

}
