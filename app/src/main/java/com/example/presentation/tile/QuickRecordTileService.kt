package com.example.presentation.tile

import android.content.Intent
import android.service.quicksettings.TileService
import com.example.MainActivity

class QuickRecordTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_QUICK_RECORD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        try {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
