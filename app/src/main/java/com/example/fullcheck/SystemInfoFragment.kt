package com.example.fullcheck

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.StatFs
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.fullcheck.databinding.FragmentSystemInfoBinding
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method

/**
 * Fragment to display system information.
 */
class SystemInfoFragment : Fragment() {

    private var _binding: FragmentSystemInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSystemInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSystemInfo()

        binding.buttonBack.setOnClickListener {
            findNavController().navigate(R.id.action_SystemInfoFragment_to_FirstFragment)
        }
    }

    private fun loadSystemInfo() {
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

        // Get resolutions for HDMI and DVI
        loadDisplayResolutions()

        // Get display mode (同顯/異顯)
        loadDisplayMode()

        // Get rotation from system property (persist.sys.rotation.einit-1)
        // Value: 0=0°, 1=90°, 2=180°, 3=270°
        val rotationValue = getSystemProperty("persist.sys.rotation.einit-1")
        val rotationDegree = when (rotationValue) {
            "0" -> "0°"
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
            binding.valueOtaSupport.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            binding.valueOtaSupport.text = "不支援"
            binding.valueOtaSupport.setTextColor(Color.parseColor("#F44336")) // Red
        }

        // Get storage info for download and rw partitions
        binding.valueStorageDownload.text = getStorageSize("/mnt/download")
        binding.valueStorageRw.text = getStorageSize("/mnt/rw")
    }

    private fun loadDisplayResolutions() {
        val displayManager = requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        var hdmiResolution: String? = null
        var dviResolution: String? = null

        for (display in displays) {
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            val resolution = "${metrics.widthPixels}x${metrics.heightPixels}"

            // Display type: Display.TYPE_INTERNAL (1) = HDMI, Display.TYPE_EXTERNAL (2) = DVI
            // Or check by display ID: 0 = primary (HDMI), others = secondary (DVI)
            when {
                display.displayId == Display.DEFAULT_DISPLAY -> {
                    // Primary display = HDMI (Built-in Screen, local:0)
                    hdmiResolution = resolution
                }
                else -> {
                    // Secondary display = DVI (HDMI Screen, local:1)
                    dviResolution = resolution
                }
            }
        }

        binding.valueResolutionHdmi.text = hdmiResolution ?: "N/A"
        binding.valueResolutionDvi.text = dviResolution ?: "N/A"
    }

    /**
     * Load display mode from /vendor/etc/HwComposerEnv.xml
     * Enable="0" -> 同顯 (Same display)
     * Enable="1" -> 異顯 (Different display)
     */
    private fun loadDisplayMode() {
        try {
            val xmlFile = File("/vendor/etc/HwComposerEnv.xml")
            if (!xmlFile.exists()) {
                binding.valueDisplayMode.text = "N/A"
                return
            }

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(FileInputStream(xmlFile), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "HwComposerEnv") {
                    val enableValue = parser.getAttributeValue(null, "Enable")
                    binding.valueDisplayMode.text = when (enableValue) {
                        "0" -> "同顯"
                        "1" -> "異顯"
                        else -> "Unknown ($enableValue)"
                    }
                    return
                }
                eventType = parser.next()
            }
            binding.valueDisplayMode.text = "N/A"
        } catch (e: Exception) {
            e.printStackTrace()
            binding.valueDisplayMode.text = "N/A"
        }
    }

    /**
     * Get storage size for a given mount path.
     * Returns formatted size (e.g., "3.8G", "8.6M") or "N/A" if not available.
     */
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

    /**
     * Format bytes to human readable size (G, M, K).
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1fG", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1fM", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1fK", bytes / 1_000.0)
            else -> "${bytes}B"
        }
    }

    /**
     * Get system property using reflection.
     */
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
