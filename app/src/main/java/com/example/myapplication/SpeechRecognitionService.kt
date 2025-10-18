package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat

class SpeechRecognitionService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRecognizerIntent: Intent? = null
    private var isListening = false

    private val conversationHistory: MutableList<String> = mutableListOf()
    private val MAX_HISTORY_SIZE = 5

    companion object {
        private const val TAG = "SpeechRecognitionSvc"
        private const val CHANNEL_ID = "SpeechRecognitionServiceChannel"
        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        const val BROADCAST_ACTION_RESULT = "com.example.myapplication.RESULT"
        const val BROADCAST_ACTION_KEYWORD = "com.example.myapplication.KEYWORD"
        const val EXTRA_RESULT_TEXT = "EXTRA_RESULT_TEXT"
        const val EXTRA_KEYWORD_TEXT = "EXTRA_KEYWORD_TEXT"
        const val EXTRA_HISTORY_TEXT = "EXTRA_HISTORY_TEXT"
        const val ACTION_SUMMARIZE = "com.example.myapplication.SUMMARIZE"
        var isRunning = false
        var latestHistory: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        initializeSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_START_FOREGROUND_SERVICE -> {
                    startForegroundService()
                    startListening()
                    isRunning = true
                }
                ACTION_STOP_FOREGROUND_SERVICE -> {
                    stopForegroundService()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.apply {
            stopListening()
            destroy()
        }
        speechRecognizer = null
        isListening = false
        isRunning = false
        latestHistory = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationTapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val notificationTapPendingIntent = PendingIntent.getActivity(this, 0, notificationTapIntent, pendingIntentFlags)

        val summarizeIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SUMMARIZE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val summarizePendingIntent = PendingIntent.getActivity(this, 1, summarizeIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音声認識サービス")
            .setContentText("録音中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(notificationTapPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "要約", summarizePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Speech Recognition Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available on this device.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer!!.setRecognitionListener(recognitionListener)

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun startListening() {
        if (speechRecognizer != null && !isListening) {
            Log.d(TAG, "startListening() called")
            speechRecognizer?.startListening(speechRecognizerIntent)
            isListening = true
        }
    }

    private fun restartListening() {
        isListening = false
        speechRecognizer?.startListening(speechRecognizerIntent)
        isListening = true
    }

    private fun updateConversationHistory(newText: String) {
        conversationHistory.add(newText)
        if (conversationHistory.size > MAX_HISTORY_SIZE) {
            conversationHistory.removeAt(0)
        }
        latestHistory = conversationHistory.joinToString("\n")
    }

    private val recognitionListener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "onReadyForSpeech") }
        override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech - restarting")
            isListening = false
            restartListening()
        }

        override fun onError(error: Int) {
            isListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    "Recognizer busy"
                    Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 500)
                    return
                }
                else -> "Error: $error"
            }
            Log.e(TAG, "onError: $message")
            restartListening()
        }

        override fun onResults(results: Bundle) {
            Log.d(TAG, "onResults (final)")
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { fullText ->
                Log.d(TAG, "Full Text: $fullText")

                updateConversationHistory(fullText)

                sendBroadcast(Intent(BROADCAST_ACTION_RESULT).apply {
                    putExtra(EXTRA_RESULT_TEXT, fullText)
                    putExtra(EXTRA_HISTORY_TEXT, latestHistory)
                })

                checkForKeyword(fullText)
            }
        }

        override fun onPartialResults(partialResults: Bundle) {
            partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partialText ->
                checkForKeyword(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkForKeyword(text: String) {
        val normalizedText = text.replace("[？、。 ]".toRegex(), "")
        if (normalizedText.contains("えなんつった")) {
            Log.d(TAG, "★★ Keyword Detected! ★★: $text")
            sendBroadcast(Intent(BROADCAST_ACTION_KEYWORD).apply {
                putExtra(EXTRA_KEYWORD_TEXT, text)
            })
        }
    }
}
