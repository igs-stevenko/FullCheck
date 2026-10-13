package com.example.fullcheck

import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.fullcheck.databinding.FragmentNativeCheckListBinding
import java.lang.reflect.Method

/**
 * Fragment to display native service check results.
 */
class NativeCheckListFragment : Fragment() {

    private var _binding: FragmentNativeCheckListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNativeCheckListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check all services on page load
        checkAllServices()

        binding.buttonRefresh.setOnClickListener {
            checkAllServices()
        }

        binding.buttonBack.setOnClickListener {
            findNavController().navigate(R.id.action_NativeCheckListFragment_to_FirstFragment)
        }
    }

    private fun checkAllServices() {
        updateServiceStatus(binding.statusIgsblk, isServiceRunning("service.igsblk"))
        updateServiceStatus(binding.statusIgscmd, isServiceRunning("service.igscmd"))
        updateServiceStatus(binding.statusIgstime, isServiceRunning("service.igstime"))
        updateServiceStatus(binding.statusIgsrotation, isServiceRunning("service.igsrotation"))
        updateServiceStatus(binding.statusIgssdctrl, isServiceRunning("service.igssdctrl"))
    }

    private fun updateServiceStatus(textView: TextView, isRunning: Boolean) {
        if (isRunning) {
            textView.text = "OK"
            textView.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            textView.text = "Failed"
            textView.setTextColor(Color.parseColor("#F44336")) // Red
        }
    }

    /**
     * Check if a native service is running using ServiceManager via reflection.
     * This works for system services registered with ServiceManager.
     */
    private fun isServiceRunning(serviceName: String): Boolean {
        return try {
            // Use reflection to access ServiceManager
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod: Method = serviceManagerClass.getMethod("getService", String::class.java)
            val binder: IBinder? = getServiceMethod.invoke(null, serviceName) as? IBinder
            binder != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
