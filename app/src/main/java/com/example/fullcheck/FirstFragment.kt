package com.example.fullcheck

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.IBinder
import android.os.StatFs
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.fullcheck.databinding.FragmentFirstBinding
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method

/**
 * Main fragment displaying all system info and native service status.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    // Data class for HwComposer info
    data class HwComposerInfo(
        val enable: Int,           // 0=同顯, 1=異顯
        val fbWidth: Int,          // Total framebuffer width
        val fbHeight: Int,         // Total framebuffer height
        val connectors: List<ConnectorInfo>
    )

    data class ConnectorInfo(
        val type: String,
        val typeId: Int,
        val srcW: Int,
        val srcH: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAllInfo()
    }

    private fun loadAllInfo() {
        // === System Info ===
        
        // Get system name (Project Name + Git Ver) from native library
        try {
            val sysInfo = SysInfoNative()
            val projectName = sysInfo.getProjectName()
            val gitVer = sysInfo.getGitVersion()
            binding.valueSystemName.text = "$projectName ($gitVer)"
        } catch (e: Exception) {
            e.printStackTrace()
            binding.valueSystemName.text = "N/A"
        }

        // Parse HwComposerEnv.xml for display info
        val hwInfo = parseHwComposerEnv()
        
        // Get resolutions for HDMI and DVI
        loadDisplayResolutions(hwInfo)

        // Get display mode (同顯/異顯) with total resolution
        loadDisplayMode(hwInfo)

        // Get rotation from system property
        val rotationValue = getSystemProperty("persist.sys.rotation.einit-1")
        val rotationDegree = when (rotationValue) {
            "", "0" -> "0°"
            "1" -> "90°"
            "2" -> "180°"
            "3" -> "270°"
            else -> "Unknown ($rotationValue)"
        }
        binding.valueRotation.text = rotationDegree

        // Get OTA support from system property
        val otaEnabled = getSystemProperty("ro.ota.enabled")
        if (otaEnabled.equals("true", ignoreCase = true)) {
            binding.valueOtaSupport.text = "支援"
            binding.valueOtaSupport.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.valueOtaSupport.text = "不支援"
            binding.valueOtaSupport.setTextColor(Color.parseColor("#F44336"))
        }

        // Get storage info
        binding.valueStorageDownload.text = getStorageSize("/mnt/download")
        binding.valueStorageRw.text = getStorageSize("/mnt/rw")

        // === Native Service Status ===
        checkAllServices()

        // === Library Check ===
        checkAllLibraries()
    }

    private fun parseHwComposerEnv(): HwComposerInfo? {
        try {
            val xmlFile = File("/vendor/etc/HwComposerEnv.xml")
            if (!xmlFile.exists()) {
                return null
            }

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(FileInputStream(xmlFile), "UTF-8")

            var enable = -1
            var fbWidth = 0
            var fbHeight = 0
            val connectors = mutableListOf<ConnectorInfo>()

            var currentType = ""
            var currentTypeId = 0
            var currentSrcW = 0
            var currentSrcH = 0
            var inConnector = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "HwComposerEnv" -> {
                                enable = parser.getAttributeValue(null, "Enable")?.toIntOrNull() ?: -1
                            }
                            "DsiplayMode" -> {
                                fbWidth = parser.getAttributeValue(null, "FbWidth")?.toIntOrNull() ?: 0
                                fbHeight = parser.getAttributeValue(null, "FbHeight")?.toIntOrNull() ?: 0
                            }
                            "Connector" -> {
                                inConnector = true
                                currentType = ""
                                currentTypeId = 0
                                currentSrcW = 0
                                currentSrcH = 0
                            }
                            "Type" -> {
                                if (inConnector) {
                                    currentType = parser.nextText()
                                }
                            }
                            "TypeId" -> {
                                if (inConnector) {
                                    currentTypeId = parser.nextText().toIntOrNull() ?: 0
                                }
                            }
                            "SrcW" -> {
                                if (inConnector) {
                                    currentSrcW = parser.nextText().toIntOrNull() ?: 0
                                }
                            }
                            "SrcH" -> {
                                if (inConnector) {
                                    currentSrcH = parser.nextText().toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Connector" && inConnector) {
                            connectors.add(ConnectorInfo(currentType, currentTypeId, currentSrcW, currentSrcH))
                            inConnector = false
                        }
                    }
                }
                eventType = parser.next()
            }

            return HwComposerInfo(enable, fbWidth, fbHeight, connectors)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun loadDisplayResolutions(hwInfo: HwComposerInfo?) {
        if (hwInfo != null && hwInfo.enable == 1 && hwInfo.connectors.isNotEmpty()) {
            // 異顯模式：從 HwComposerEnv.xml 取得各 Connector 解析度
            val firstConnector = hwInfo.connectors.getOrNull(0)
            val secondConnector = hwInfo.connectors.getOrNull(1)

            binding.valueResolutionHdmi.text = if (firstConnector != null) {
                "${firstConnector.srcW}x${firstConnector.srcH}"
            } else {
                "N/A"
            }

            binding.valueResolutionDvi.text = if (secondConnector != null) {
                "${secondConnector.srcW}x${secondConnector.srcH}"
            } else {
                "N/A"
            }
        } else {
            // 同顯模式或無法解析 XML：從 DisplayManager 取得
            val displayManager = requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            var hdmiResolution: String? = null
            var dviResolution: String? = null

            for (display in displays) {
                val metrics = android.util.DisplayMetrics()
                display.getRealMetrics(metrics)
                val resolution = "${metrics.widthPixels}x${metrics.heightPixels}"

                when {
                    display.displayId == Display.DEFAULT_DISPLAY -> {
                        hdmiResolution = resolution
                    }
                    else -> {
                        dviResolution = resolution
                    }
                }
            }

            binding.valueResolutionHdmi.text = hdmiResolution ?: "N/A"
            binding.valueResolutionDvi.text = dviResolution ?: "N/A"
        }
    }

    private fun loadDisplayMode(hwInfo: HwComposerInfo?) {
        if (hwInfo == null) {
            binding.valueDisplayMode.text = "N/A"
            return
        }

        when (hwInfo.enable) {
            0 -> {
                // 同顯：不顯示解析度
                binding.valueDisplayMode.text = "同顯"
            }
            1 -> {
                // 異顯：顯示總解析度
                val totalResolution = if (hwInfo.fbWidth > 0 && hwInfo.fbHeight > 0) {
                    "${hwInfo.fbWidth}x${hwInfo.fbHeight}"
                } else {
                    val displayManager = requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    val metrics = android.util.DisplayMetrics()
                    defaultDisplay?.getRealMetrics(metrics)
                    "${metrics.widthPixels}x${metrics.heightPixels}"
                }
                binding.valueDisplayMode.text = "異顯 ($totalResolution)"
            }
            else -> {
                binding.valueDisplayMode.text = "Unknown"
            }
        }
    }

    private fun checkAllLibraries() {
        updateServiceStatus(binding.statusLibBaseline, isLibraryExists("libbaseline_calculator.so"))
        updateServiceStatus(binding.statusLibBlk, isLibraryExists("libblk_client.so"))
        updateServiceStatus(binding.statusLibCmd, isLibraryExists("libcmd_client.so"))
        updateServiceStatus(binding.statusLibCpuUuid, isLibraryExists("libcpu_uuid.so"))
        updateServiceStatus(binding.statusLibEmmcUuid, isLibraryExists("libemmc_uuid.so"))
        updateServiceStatus(binding.statusLibFactlog, isLibraryExists("libfactlog.so"))
        updateServiceStatus(binding.statusLibOta, isLibraryExists("libota.so"))
        updateServiceStatus(binding.statusLibRotation, isLibraryExists("librotation_client.so"))
        updateServiceStatus(binding.statusLibTime, isLibraryExists("libtime_client.so"))
    }

    private fun isLibraryExists(libName: String): Boolean {
        val lib64 = File("/system/lib64/$libName")
        val lib32 = File("/system/lib/$libName")
        return lib64.exists() || lib32.exists()
    }

    private fun checkAllServices() {
        updateServiceStatus(binding.statusIgsblk, isServiceRunning("service.igsblk"))
        updateServiceStatus(binding.statusIgscmd, isServiceRunning("service.igscmd"))
        updateServiceStatus(binding.statusIgstime, isServiceRunning("service.igstime"))
        updateServiceStatus(binding.statusIgsrotation, isServiceRunning("service.igsrotation"))
        updateServiceStatus(binding.statusIgssdctrl, isServiceRunning("service.igssdctrl"))
        updateServiceStatus(binding.statusRotationService, isProcessRunning("igs_rotation_serv"))
    }

    private fun updateServiceStatus(textView: TextView, isRunning: Boolean) {
        if (isRunning) {
            textView.text = "OK"
            textView.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            textView.text = "Failed"
            textView.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod: Method = serviceManagerClass.getMethod("getService", String::class.java)
            val binder: IBinder? = getServiceMethod.invoke(null, serviceName) as? IBinder
            binder != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if a native process is running by checking init.svc property.
     */
    private fun isProcessRunning(processName: String): Boolean {
        return try {
            // Check init.svc.* property for service status
            val serviceStatus = getSystemProperty("init.svc.$processName")
            serviceStatus.equals("running", ignoreCase = true)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getStorageSize(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return "N/A"
            }
            val statFs = StatFs(path)
            val totalBytes = statFs.blockSizeLong * statFs.blockCountLong
            formatSize(totalBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1fG", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1fM", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1fK", bytes / 1_000.0)
            else -> "${bytes}B"
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod: Method = systemPropertiesClass.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as? String ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
