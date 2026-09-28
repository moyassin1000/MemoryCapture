package com.memorycapture.app.projection

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper

class MediaProjectionController(context: Context) {
    private val manager = context.getSystemService(MediaProjectionManager::class.java)
    private var projection: MediaProjection? = null

    fun start(resultCode: Int, data: Intent, onStopped: () -> Unit): MediaProjection {
        stop()

        val created = requireNotNull(manager.getMediaProjection(resultCode, data)) {
            "MediaProjection could not be created from the granted result data."
        }

        created.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    projection = null
                    onStopped()
                }
            },
            Handler(Looper.getMainLooper()),
        )

        projection = created
        return created
    }

    fun current(): MediaProjection? = projection

    fun stop() {
        projection?.stop()
        projection = null
    }
}
