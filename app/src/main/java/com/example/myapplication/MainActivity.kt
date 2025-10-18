package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var textViewResult: TextView
    private lateinit var textViewAll: TextView
    private lateinit var textViewLog: TextView
    private lateinit var inputText: EditText
    private lateinit var actionButton: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SpeechRecognitionService.BROADCAST_ACTION_RESULT -> {
                    val historyText = intent.getStringExtra(SpeechRecognitionService.EXTRA_HISTORY_TEXT)
                    textViewLog.text = historyText
                    inputText.setText(historyText)
                }
                SpeechRecognitionService.BROADCAST_ACTION_KEYWORD -> {
                    val keywordText = intent.getStringExtra(SpeechRecognitionService.EXTRA_KEYWORD_TEXT)
                    if (!textViewResult.text.toString().startsWith("検出！")) {
                        textViewResult.text = "検出！: $keywordText"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        inputText = findViewById(R.id.inputText)
        actionButton = findViewById(R.id.actionButton)
        val responseText = findViewById<TextView>(R.id.responseText)
        val finishButton = findViewById<Button>(R.id.finishButton)
        textViewResult = findViewById(R.id.textViewResult)
        textViewAll = findViewById(R.id.textViewAll)
        textViewLog = findViewById(R.id.textViewLog)

        textViewResult.text = "「え？なんつった？」を待機中..."

        finishButton.setOnClickListener {
            stopSpeechService()
            finish()
        }

        actionButton.setOnClickListener {
            val apiKey = BuildConfig.api_key

            if (apiKey.isBlank() || !apiKey.startsWith("AIza")) {
                responseText.text = "Error: API Key is NOT loaded correctly from local.properties. Please check the file."
                return@setOnClickListener
            } else {
                Log.d("ApiKeyCheck", "Key loaded successfully. Starts with: ${apiKey.take(4)}, Ends with: ${apiKey.takeLast(4)}")
            }

            val inputTextContent = inputText.text.toString()
            if (inputTextContent.isBlank()) {
                responseText.text = "Please enter text."
                return@setOnClickListener
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey
            )

            responseText.text = "Generating..."
            val prompt = "以下の会話文はこのAIシステムが動作しているスマホを持っている人（以下、対象者とする）の周囲の会話、または町中のアナウンスなどを録音したものです。対象者はスマホを操作しており、録音にある会話の内容を十分に把握できなかったため、会話内容を把握したいと考えています。対象者の助けとなるよう、以下2点を実行してください\n1点目：会話内容に、対象者への依頼や対象者がその行為を他者から行動をとがめられていると判断できる文章がが含まれている場合、[要対応][警告]などと太字で示し、行動を促す簡潔な文章や警告を示す文章を優先度と共に示し、優先度が高い順に並べてください。\n例) [要対応] ポストに入っている新聞の回収\n優先度：★★\n[要対応] 祭りのゴミの量削減に向けての意見を述べる\n優先度：★★★\n[警告] 生返事をくり返していることに相手が怒りを感じています。\n優先度：★★★★\n2点目：対象者に向け、会話内容全体を200文字以内で簡潔に要約してください要約文の中では、対象者のことは「あなた」と表記してください。\n例1）昨日飲んだ珈琲が美味しかったことについての雑談のあと、今日の宿題の内容について尋ねられています。また、相手はあなたがスマホを見ながらしゃべっていることについて、不快感を持っているようです。加えて、今すぐ洗濯物を取り込むよう依頼されています。\n例2)新しい家具の購入についての意見が述べられています。一人は椅子を購入すべきと主張し、もう一人は机の買い換えが急務であると主張しています。あなたの意見を述べるよう求められています。\n1点目、2点目に共通し、次の点に留意してください。話している人が一人であると考えられる場合、その人は対象者ではなく、対象者に話しかけている可能性が高いと考えられます。会話内容から、対象者が実行する必要があると考えられる項目を抽出して提示してください。会話の内容が雑談に終始し、特に要対応事項がない場合は[対応事項無し]と返答してください。話している人が複数人いる場合、一方が対象者であると考えられるばあいと、会議中の録音など、対象者以外に複数の人間が会話に参加している場合の両方が考えられます。この場合、活発に会話に参加している人間は対象者ではない可能性が高く、終始無言であったり、相づちに終始している人間がいれば対象者である可能性があると考えられます。一方が対象者である場合は会話している人間が一人である場合と同様、対象者が実行すべき事項の有無を判断し、実行すべき内容がある場合はそれを提示してください。対象者以外に複数の人間がいる場合は、その場にいる全員ヘの問いかけなど、対象者が把握しておくべき内容を中心に対応事項を抽出し、会話全体の流れが分かるよう要約してください。\n$inputTextContent"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = generativeModel.generateContent(prompt)
                    withContext(Dispatchers.Main) {
                        responseText.text = response.text
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        responseText.text = "Error: ${e.message}"
                        Log.e("GeminiApp", "API Error", e)
                    }
                }
            }
        }

        checkPermissionsAndStartService()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == SpeechRecognitionService.ACTION_SUMMARIZE) {
            actionButton.post { actionButton.performClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(SpeechRecognitionService.BROADCAST_ACTION_RESULT)
            addAction(SpeechRecognitionService.BROADCAST_ACTION_KEYWORD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        SpeechRecognitionService.latestHistory?.let {
            textViewLog.text = it
            inputText.setText(it)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            startSpeechService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startSpeechService()
            } else {
                Toast.makeText(this, "必要な権限がありません", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSpeechService() {
        val intent = Intent(this, SpeechRecognitionService::class.java).apply {
            action = SpeechRecognitionService.ACTION_START_FOREGROUND_SERVICE
        }
        startService(intent)
    }

    private fun stopSpeechService() {
        val intent = Intent(this, SpeechRecognitionService::class.java).apply {
            action = SpeechRecognitionService.ACTION_STOP_FOREGROUND_SERVICE
        }
        startService(intent)
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
    }
}