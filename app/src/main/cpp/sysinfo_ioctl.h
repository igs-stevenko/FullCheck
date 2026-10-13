#ifndef __SYSINFO_IOCTL_H__
#define __SYSINFO_IOCTL_H__

#include <stdbool.h>

struct system_info {
    unsigned char git_ver[6];
    bool secureboot_enabled;
    unsigned char proj_name[20];
};

#define MAGIC_NUM           0x12

// 定義 IOCTL 操作指令
#define GET_SYSINFO         _IOWR(MAGIC_NUM, 0, struct system_info)

#endif
