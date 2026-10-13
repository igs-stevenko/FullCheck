# FullCheck APK - ADB 驗證指令手冊

本文件記錄 FullCheck APK 中所有檢測項目的 ADB 驗證方法，供其他 Agent 或自動化腳本使用。

---

## 一、系統資訊 (System Info)

### 1. 系統名稱
**APK 取得方式：** JNI 透過 ioctl 讀取 `/dev/system_info`  
**ADB 驗證：** 需要 native binary，無直接 shell 指令

```bash
# 目前無法透過 ADB 直接取得，需編譯 read_sysinfo_git 工具
# 源碼位置：frameworks/native/services/igs/read_sysinfo_git/
```

---

### 2. 解析度 (HDMI)
**APK 取得方式：**
- 異顯模式：解析 `/vendor/etc/HwComposerEnv.xml` 中第一個 `<Connector>` 的 `SrcW` x `SrcH`
- 同顯模式：`DisplayManager.getDisplay(0).getRealMetrics()`

**ADB 驗證：**
```bash
# 方法一：從 HwComposerEnv.xml 取得 (適用異顯)
adb shell "cat /vendor/etc/HwComposerEnv.xml" | grep -A10 "<Connector>" | head -20

# 方法二：從 dumpsys 取得 (適用同顯)
adb shell "dumpsys display | grep DisplayDeviceInfo"
```

**解析邏輯：**
- 異顯時取 XML 中第一個 Connector 的 `<SrcW>` 和 `<SrcH>`
- 同顯時取 DisplayDeviceInfo 中 displayId=0 的解析度

---

### 3. 解析度 (DVI)
**APK 取得方式：**
- 異顯模式：解析 `/vendor/etc/HwComposerEnv.xml` 中第二個 `<Connector>` 的 `SrcW` x `SrcH`
- 同顯模式：`DisplayManager` 取得 displayId ≠ 0 的 display
- 若無第二輸出：顯示 `N/A`

**ADB 驗證：**
```bash
# 從 HwComposerEnv.xml 取得第二個 Connector
adb shell "cat /vendor/etc/HwComposerEnv.xml"
```

**解析邏輯：**
- 找第二個 `<Connector>` 區塊的 `<SrcW>` 和 `<SrcH>`
- 若只有一個 Connector 則為 N/A

---

### 4. 雙屏模式
**APK 取得方式：** 解析 `/vendor/etc/HwComposerEnv.xml` 的 `Enable` 屬性

**ADB 驗證：**
```bash
adb shell "cat /vendor/etc/HwComposerEnv.xml | grep 'HwComposerEnv.*Enable'"
```

**判斷邏輯：**
| Enable 值 | 模式 | 顯示 |
|-----------|------|------|
| 0 | 同顯 | `同顯` |
| 1 | 異顯 | `異顯 (FbWidth x FbHeight)` |

**取得總解析度 (異顯時)：**
```bash
adb shell "cat /vendor/etc/HwComposerEnv.xml | grep 'DsiplayMode.*FbWidth'"
```

---

### 5. 旋轉角度
**APK 取得方式：** `SystemProperties.get("persist.sys.rotation.einit-1")`

**ADB 驗證：**
```bash
adb shell "getprop persist.sys.rotation.einit-1"
```

**判斷邏輯：**
| Property 值 | 角度 |
|-------------|------|
| (空值) 或 0 | 0° |
| 1 | 90° |
| 2 | 180° |
| 3 | 270° |

---

### 6. OTA 支援
**APK 取得方式：** `SystemProperties.get("ro.ota.enabled")`

**ADB 驗證：**
```bash
adb shell "getprop ro.ota.enabled"
```

**判斷邏輯：**
| Property 值 | 結果 |
|-------------|------|
| true | 支援 (綠色) |
| 其他 | 不支援 (紅色) |

---

### 7. Download 區空間
**APK 取得方式：** `StatFs("/mnt/download")`

**ADB 驗證：**
```bash
adb shell "df -h /mnt/download"
```

**輸出範例：**
```
Filesystem            Size Used Avail Use% Mounted on
/dev/block/mmcblk0p15 3.8G  24K  3.8G   1% /mnt/download
```

**解析：** 取 `Size` 欄位值

---

### 8. RW 區空間
**APK 取得方式：** `StatFs("/mnt/rw")`，不存在則顯示 N/A

**ADB 驗證：**
```bash
adb shell "df -h /mnt/rw 2>/dev/null || echo 'N/A'"
```

**解析：** 取 `Size` 欄位值，若掛載點不存在則為 N/A

---

## 二、Native Service 狀態

### 檢測方式
**APK 取得方式：** 透過反射呼叫 `ServiceManager.getService(name)` 檢查是否回傳非 null

**ADB 驗證：**
```bash
adb shell "service list | grep -E 'igsblk|igscmd|igstime|igsrotation|igssdctrl'"
```

### 服務列表

| 服務名稱 | ADB 單獨檢查指令 |
|----------|------------------|
| service.igsblk | `adb shell "service check service.igsblk"` |
| service.igscmd | `adb shell "service check service.igscmd"` |
| service.igstime | `adb shell "service check service.igstime"` |
| service.igsrotation | `adb shell "service check service.igsrotation"` |
| service.igssdctrl | `adb shell "service check service.igssdctrl"` |

**判斷邏輯：**
- 出現在 `service list` 中 → OK (綠色)
- 未出現 → Failed (紅色)

---

### rotation_service (Native Process)
**APK 取得方式：** `SystemProperties.get("init.svc.igs_rotation_serv")`

**ADB 驗證：**
```bash
adb shell "getprop init.svc.igs_rotation_serv"
```

**判斷邏輯：**
| Property 值 | 結果 |
|-------------|------|
| running | OK (綠色) |
| 其他 | Failed (紅色) |

---

## 三、Library Check

### 檢測方式
**APK 取得方式：** `File("/system/lib64/$libName").exists()`

**ADB 驗證 (一次檢查所有)：**
```bash
adb shell "ls -la /system/lib64/libbaseline_calculator.so /system/lib64/libblk_client.so /system/lib64/libcmd_client.so /system/lib64/libcpu_uuid.so /system/lib64/libemmc_uuid.so /system/lib64/libfactlog.so /system/lib64/libota.so /system/lib64/librotation_client.so /system/lib64/libtime_client.so 2>&1"
```

### Library 列表

| Library 名稱 | ADB 單獨檢查指令 |
|--------------|------------------|
| libbaseline_calculator.so | `adb shell "ls /system/lib64/libbaseline_calculator.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libblk_client.so | `adb shell "ls /system/lib64/libblk_client.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libcmd_client.so | `adb shell "ls /system/lib64/libcmd_client.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libcpu_uuid.so | `adb shell "ls /system/lib64/libcpu_uuid.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libemmc_uuid.so | `adb shell "ls /system/lib64/libemmc_uuid.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libfactlog.so | `adb shell "ls /system/lib64/libfactlog.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libota.so | `adb shell "ls /system/lib64/libota.so 2>/dev/null && echo OK \|\| echo Failed"` |
| librotation_client.so | `adb shell "ls /system/lib64/librotation_client.so 2>/dev/null && echo OK \|\| echo Failed"` |
| libtime_client.so | `adb shell "ls /system/lib64/libtime_client.so 2>/dev/null && echo OK \|\| echo Failed"` |

**判斷邏輯：**
- 檔案存在 → OK (綠色)
- 檔案不存在 → Failed (紅色)

---

## 四、完整驗證腳本

以下腳本可一次執行所有檢測項目：

```bash
#!/bin/bash

ADB="adb"

echo "=========================================="
echo "FullCheck ADB Verification Script"
echo "=========================================="

echo ""
echo "=== 系統資訊 ==="

echo "--- 雙屏模式 & 解析度 ---"
$ADB shell "cat /vendor/etc/HwComposerEnv.xml" | grep -E 'Enable=|FbWidth=|FbHeight=|<Type>|<TypeId>|<SrcW>|<SrcH>' | grep -v '<!--'

echo ""
echo "--- 旋轉角度 ---"
ROTATION=$($ADB shell "getprop persist.sys.rotation.einit-1" | tr -d '\r')
case "$ROTATION" in
  ""|"0") echo "0°" ;;
  "1") echo "90°" ;;
  "2") echo "180°" ;;
  "3") echo "270°" ;;
  *) echo "Unknown ($ROTATION)" ;;
esac

echo ""
echo "--- OTA 支援 ---"
OTA=$($ADB shell "getprop ro.ota.enabled" | tr -d '\r')
if [ "$OTA" = "true" ]; then
  echo "支援"
else
  echo "不支援"
fi

echo ""
echo "--- Download 區空間 ---"
$ADB shell "df -h /mnt/download" | tail -1 | awk '{print $2}'

echo ""
echo "--- RW 區空間 ---"
$ADB shell "df -h /mnt/rw 2>/dev/null | tail -1 | awk '{print \$2}'" || echo "N/A"

echo ""
echo "=== Native Service 狀態 ==="
for svc in service.igsblk service.igscmd service.igstime service.igsrotation service.igssdctrl; do
  result=$($ADB shell "service check $svc 2>&1" | tr -d '\r')
  if [[ "$result" == *"not found"* ]]; then
    echo "$svc: Failed"
  else
    echo "$svc: OK"
  fi
done

echo ""
echo "--- rotation_service ---"
ROT_SVC=$($ADB shell "getprop init.svc.igs_rotation_serv" | tr -d '\r')
if [ "$ROT_SVC" = "running" ]; then
  echo "rotation_service: OK"
else
  echo "rotation_service: Failed"
fi

echo ""
echo "=== Library Check ==="
LIBS="libbaseline_calculator.so libblk_client.so libcmd_client.so libcpu_uuid.so libemmc_uuid.so libfactlog.so libota.so librotation_client.so libtime_client.so"
for lib in $LIBS; do
  result=$($ADB shell "ls /system/lib64/$lib 2>/dev/null" | tr -d '\r')
  if [ -n "$result" ]; then
    echo "$lib: OK"
  else
    echo "$lib: Failed"
  fi
done

echo ""
echo "=========================================="
echo "Verification Complete"
echo "=========================================="
```

---

## 五、預期輸出範例

```
==========================================
FullCheck ADB Verification Script
==========================================

=== 系統資訊 ===
--- 雙屏模式 & 解析度 ---
Enable="1"
FbWidth="3840" FbHeight="1080"
<Type>HDMI-A</Type>
<TypeId>1</TypeId>
<SrcW>1920</SrcW>
<SrcH>1080</SrcH>
<Type>HDMI-A</Type>
<TypeId>2</TypeId>
<SrcW>1920</SrcW>
<SrcH>1080</SrcH>

--- 旋轉角度 ---
270°

--- OTA 支援 ---
不支援

--- Download 區空間 ---
3.8G

--- RW 區空間 ---
8.6M

=== Native Service 狀態 ===
service.igsblk: OK
service.igscmd: OK
service.igstime: OK
service.igsrotation: OK
service.igssdctrl: OK

--- rotation_service ---
rotation_service: OK

=== Library Check ===
libbaseline_calculator.so: OK
libblk_client.so: OK
libcmd_client.so: OK
libcpu_uuid.so: OK
libemmc_uuid.so: OK
libfactlog.so: OK
libota.so: OK
librotation_client.so: OK
libtime_client.so: OK

==========================================
Verification Complete
==========================================
```

---

## 六、注意事項

1. **ADB 連線**：執行前確保設備已透過 ADB 連線
2. **權限**：部分檢查可能需要 root 權限
3. **系統名稱**：目前無法透過 ADB 直接取得，需要編譯專用的 native binary
4. **異顯/同顯判斷**：以 `HwComposerEnv.xml` 的 `Enable` 屬性為準
5. **RW 區**：部分系統可能沒有 RW 分區，會顯示 N/A
