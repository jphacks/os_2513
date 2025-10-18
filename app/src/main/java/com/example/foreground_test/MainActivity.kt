package com.example.foreground_test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * アプリのメイン画面となるアクティビティ。
 * サービスの開始・停止、カウンターの操作を行います。
 */
class MainActivity : AppCompatActivity() {

    // 通知権限のリクエストコード
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    // カウント数を保持する変数
    private var count = 0
    // カウント数を表示するTextView
    private lateinit var countTextView: TextView

    /**
     * アクティビティが作成されるときに呼び出される。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // レイアウトからビューを取得
        countTextView = findViewById(R.id.count_text_view)

        // Android 13 (TIRAMISU) 以降で通知の権限を確認・要求する
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }

        // 各ボタンの参照を取得
        val startButton: Button = findViewById(R.id.start_service_button)
        val stopButton: Button = findViewById(R.id.stop_service_button)
        val countButton: Button = findViewById(R.id.count_button)

        // サービス開始ボタンのクリックリスナー
        startButton.setOnClickListener {
            startForegroundService()
            // カウンターをリセット
            count = 0
            countTextView.text = count.toString()
        }

        // サービス停止ボタンのクリックリスナー
        stopButton.setOnClickListener {
            stopService(Intent(this, TimeService::class.java))
            // カウンターをリセット
            count = 0
            countTextView.text = count.toString()
            Toast.makeText(this, "サービスを停止しました", Toast.LENGTH_SHORT).show()
        }

        // カウントボタンのクリックリスナー
        countButton.setOnClickListener {
            count++
            countTextView.text = count.toString()
        }

        // アクティビティ起動時に渡されたIntentを処理する
        handleIntent(intent)
    }

    /**
     * アクティビティが既に起動している状態で新しいIntentを受け取ったときに呼び出される。
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Intentを処理して、カウントアップのアクションが含まれているか確認する。
     * @param intent 処理対象のIntent
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == TimeService.ACTION_COUNT_UP) {
            count++
            countTextView.text = count.toString()
        }
    }

    /**
     * TimeServiceをフォアグラウンドで開始する。
     */
    private fun startForegroundService() {
        val serviceIntent = Intent(this, TimeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "サービスを開始しました", Toast.LENGTH_SHORT).show()
    }

    // 権限リクエストの結果を処理 (今回は実装を省略)
    // override fun onRequestPermissionsResult(...) { ... }
}