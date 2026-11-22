package com.example.sakurapet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.abs

class SakuraOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var petView: LinearLayout? = null
    private var tts: TextToSpeech? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("sakura_pet", "小樱桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val notification: Notification = Notification.Builder(this, "sakura_pet")
                .setContentTitle("小樱正在陪伴你")
                .setContentText("点击桌宠和她打招呼，长按可拖动")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build()
            startForeground(21, notification)
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { tts?.setLanguage(Locale.SIMPLIFIED_CHINESE); tts?.setPitch(1.16f); tts?.setSpeechRate(0.91f) } }
        showPet()
    }
    private fun showPet() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8); setBackgroundColor(0x00FFFFFF) }
        val bubble = TextView(this).apply { text = "主人～我在这里哦♡"; textSize = 11f; setTextColor(0xFF493746.toInt()); setPadding(12, 8, 12, 8); setBackgroundColor(0xEEFFF3F8.toInt()) }
        val image = ImageView(this).apply { setImageResource(R.drawable.sakura); scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(bubble, LinearLayout.LayoutParams(170, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(image, LinearLayout.LayoutParams(170, 220))
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(190, 270, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 180 }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt(); if (abs(dx) + abs(dy) > 8) moved = true; params.x = startX + dx; params.y = startY + dy; windowManager.updateViewLayout(root, params); true }
                MotionEvent.ACTION_UP -> { if (!moved) { bubble.text = "嘿嘿，主人点我啦！♡"; tts?.speak("主人～我在这里陪你哦！", TextToSpeech.QUEUE_FLUSH, null, "pet-tap") }; true }
                else -> true
            }
        }
        petView = root
        windowManager.addView(root, params)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { petView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; petView = null; tts?.stop(); tts?.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
