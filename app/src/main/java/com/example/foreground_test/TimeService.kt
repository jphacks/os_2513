package com.example.foreground_test

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * フォアグラウンドで時刻を更新し続けるサービス。
 * 通知をタップするとアプリが開き、通知のアクションボタンでカウントアップできます。
 */
class TimeService : Service() {

    // 他のコンポーネントがこのサービスのアクションを識別するための定数
    companion object {
        const val ACTION_COUNT_UP = "com.example.foreground_test.ACTION_COUNT_UP"
    }

    // 通知チャネルのID
    private val CHANNEL_ID = "TimeServiceChannel"
    // 通知の一意なID
    private val NOTIFICATION_ID = 1
    // 時刻を更新する間隔（ミリ秒）
    private val UPDATE_INTERVAL_MS: Long = 1000 // 1秒ごとに更新

    // メインスレッドでタスクをスケジュールするためのハンドラ
    private val handler = Handler(Looper.getMainLooper())
    // 定期的に実行されるタスク
    private lateinit var runnable: Runnable

    /**
     * サービスがバインドされるときに呼び出される。
     * 今回はバインド非対応のためnullを返す。
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * サービスが最初に作成されたときに呼び出される。
     */
    override fun onCreate() {
        super.onCreate()
        // 通知チャネルを作成する
        createNotificationChannel()
        // サービスをフォアグラウンドで開始する
        startForeground(NOTIFICATION_ID, buildNotification("サービスを開始しました"))
    }

    /**
     * サービスが開始されるたびに呼び出される。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 時刻の定期更新を開始する
        startUpdatingTime()
        // サービスがシステムによって強制終了された場合、可能であれば再起動する
        return START_STICKY
    }

    /**
     * サービスが破棄されるときに呼び出される。
     */
    override fun onDestroy() {
        super.onDestroy()
        // 定期的なタスクを停止してメモリリークを防ぐ
        handler.removeCallbacks(runnable)
    }

    /**
     * Android 8.0 (API 26) 以降で必要な通知チャネルを作成する。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "時刻表示サービス",
                NotificationManager.IMPORTANCE_LOW // 通知音やバイブレーションを無効にする
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * 通知オブジェクトを構築して返す。
     * @param contentText 通知に表示するテキスト
     * @return 構築された通知オブジェクト
     */
    private fun buildNotification(contentText: String): Notification {
        // 通知をタップしたときにMainActivityを開くためのIntent
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 「カウントアップ」ボタンが押されたときにMainActivityを開き、カウントアップを指示するためのIntent
        val countUpIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_COUNT_UP
        }
        val countUpPendingIntent = PendingIntent.getActivity(
            this, 1, countUpIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知を構築する
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("現在の時刻") // 通知のタイトル
            .setContentText(contentText) // 通知の本文
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // 表示する小さなアイコン
            .setContentIntent(pendingIntent) // 通知全体をタップしたときのアクション
            .setOngoing(true) // ユーザーが手動で通知を削除できないようにする
            .addAction(android.R.drawable.ic_input_add, "カウントアップ", countUpPendingIntent) // 通知に追加するアクションボタン
            .build()
    }

    /**
     * 時刻の定期的な更新を開始する。
     */
    private fun startUpdatingTime() {
        runnable = object : Runnable {
            override fun run() {
                // 現在時刻をフォーマットする
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date())
                // 新しい時刻で通知を再構築する
                val notification = buildNotification("現在時刻: $currentTime")

                // 通知を更新する
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)

                // 指定した時間後にもう一度このタスクを実行するようにスケジュールする
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
        // 最初のタスクを即時実行する
        handler.post(runnable)
    }
}