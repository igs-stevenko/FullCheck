package com.example.fullcheck

/**
 * Native library wrapper for reading system info from /dev/system_info
 */
class SysInfoNative {
    
    companion object {
        init {
            System.loadLibrary("fullcheck")
        }
    }
    
    external fun getProjectName(): String
    external fun getGitVersion(): String
}
