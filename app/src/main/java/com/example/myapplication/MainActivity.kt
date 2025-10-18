package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.Manifest
//import android.R
//import com.example.jphacktest1017.R
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.w3c.dom.Text

class MainActivity : AppCompatActivity() {
    private var textViewResult: TextView? = null
    private var textViewAll: TextView? = null
    private var textViewLog: TextView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRecognizerIntent: Intent? = null
    private var isListening = false

    private var getstr: String = ""
    private var input_text: String =""

    // 過去5件の会話履歴を保持するリスト
    private val conversationHistory: MutableList<String> = mutableListOf()
    // 保持する最大件数
    private val MAX_HISTORY_SIZE = 5


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val inputText = findViewById<EditText>(R.id.inputText)
        val actionButton = findViewById<Button>(R.id.actionButton)
        val responseText = findViewById<TextView>(R.id.responseText)

        actionButton.setOnClickListener {
            val apiKey = BuildConfig.api_key

            // --- DEBUG: Check if the API key is being loaded ---
            if (apiKey.isBlank() || !apiKey.startsWith("AIza")) { // A basic check for an empty or invalid-looking key
                responseText.text = "Error: API Key is NOT loaded correctly from local.properties. Please check the file."
                return@setOnClickListener // Stop further execution
            } else {
                // If you want to verify the key is loaded, you can temporarily show a confirmation.
                // For security reasons, NEVER display the full key.
                Log.d("ApiKeyCheck", "Key loaded successfully. Starts with: ${apiKey.take(4)}, Ends with: ${apiKey.takeLast(4)}")
            }
            // --- End of DEBUG ---

            val input_text = inputText.text.toString()
            if (input_text.isBlank()) {
                responseText.text = "Please enter text."
                return@setOnClickListener
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey
            )

            responseText.text = "Generating..." // Provide feedback to the user
            val prompt = "以下の会話文はこのAIシステムが動作しているスマホを持っている人（以下、対象者とする）の周囲の会話、または町中のアナウンスなどを録音したものです。対象者はスマホを操作しており、録音にある会話の内容を十分に把握できなかったため、会話内容を把握したいと考えています。対象者の助けとなるよう、以下2点を実行してください\n1点目：会話内容に、対象者への依頼や対象者がその行為を他者から行動をとがめられていると判断できる文章がが含まれている場合、[要対応][警告]などと太字で示し、行動を促す簡潔な文章や警告を示す文章を優先度と共に示し、優先度が高い順に並べてください。\n例) [要対応] ポストに入っている新聞の回収\n優先度：★★\n[要対応] 祭りのゴミの量削減に向けての意見を述べる\n優先度：★★★\n[警告] 生返事をくり返していることに相手が怒りを感じています。\\n優先度：★★★★\n2点目：対象者に向け、会話内容全体を200文字以内で簡潔に要約してください要約文の中では、対象者のことは「あなた」と表記してください。\n例1）昨日飲んだ珈琲が美味しかったことについての雑談のあと、今日の宿題の内容について尋ねられています。また、相手はあなたがスマホを見ながらしゃべっていることについて、不快感を持っているようです。加えて、今すぐ洗濯物を取り込むよう依頼されています。\n例2)新しい家具の購入についての意見が述べられています。一人は椅子を購入すべきと主張し、もう一人は机の買い換えが急務であると主張しています。あなたの意見を述べるよう求められています。\n1点目、2点目に共通し、次の点に留意してください。話している人が一人であると考えられる場合、その人は対象者ではなく、対象者に話しかけている可能性が高いと考えられます。会話内容から、対象者が実行する必要があると考えられる項目を抽出して提示してください。会話の内容が雑談に終始し、特に要対応事項がない場合は[対応事項無し]と返答してください。話している人が複数人いる場合、一方が対象者であると考えられるばあいと、会議中の録音など、対象者以外に複数の人間が会話に参加している場合の両方が考えられます。この場合、活発に会話に参加している人間は対象者ではない可能性が高く、終始無言であったり、相づちに終始している人間がいれば対象者である可能性があると考えられます。一方が対象者である場合は会話している人間が一人である場合と同様、対象者が実行すべき事項の有無を判断し、実行すべき内容がある場合はそれを提示してください。対象者以外に複数の人間がいる場合は、その場にいる全員ヘの問いかけなど、対象者が把握しておくべき内容を中心に対応事項を抽出し、会話全体の流れが分かるよう要約してください。\n$input_text"

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
        // 音声テキスト変換関連ここから
        textViewResult = findViewById<TextView?>(R.id.textViewResult)
        textViewResult!!.setText("「え？なんつった？」を待機中...")

        textViewAll = findViewById<TextView?>(R.id.textViewAll)
        textViewLog = findViewById<TextView?>(R.id.textViewLog)

        // 権限チェック
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            // 権限がある場合は初期化
            initializeSpeechRecognizer()
            startListening()
        }
        // 音声テキスト変換関連ここまで
    }


    // 権限リクエストの結果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeSpeechRecognizer()
                startListening()
            } else {
                Toast.makeText(this, "マイク権限が必要です", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        // SpeechRecognizer が端末で利用可能かチェック
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "音声認識が利用できません", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Speech recognition is not available on this device.")
            finish()
            return
        }


        // 1. ★★★ Android 標準の SpeechRecognizer を作成 ★★★
        // (ML Kit のオプションは使いません)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer!!.setRecognitionListener(recognitionListener)

        // 2. インテントの設定
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent!!.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP") // 日本語に設定
        speechRecognizerIntent!!.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 途中結果を受け取る
        speechRecognizerIntent!!.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName())
    }

    private fun startListening() {
        if (speechRecognizer != null && !isListening) {
            Log.d(TAG, "startListening() 呼び出し")
            speechRecognizer!!.startListening(speechRecognizerIntent)
            isListening = true
        }
    }

    // リスニングを再開する
    private fun restartListening() {
        // 認識を停止してから再開
        isListening = false
        speechRecognizer!!.startListening(speechRecognizerIntent)
        isListening = true
    }

    private fun updateConversationHistory(newText: String) {
        // 1. リストの末尾に新しい会話を追加
        conversationHistory.add(newText)

        // 2. リストのサイズが最大値（5件）を超えていたら、
        //    超えなくなるまで一番古いもの（先頭）を削除
        while (conversationHistory.size > MAX_HISTORY_SIZE) {
            conversationHistory.removeAt(0) // 先頭の要素を削除
        }

        // (デバッグ確認用) 現在の履歴をログに出力
        Log.d(TAG, "--- 会話履歴 (全${conversationHistory.size}件) ---")
        conversationHistory.forEachIndexed { index, text ->
            Log.d(TAG, "[$index]: $text")
        }
        // Logcatで "SpeechRecognizerDemo" タグをフィルタすると確認できます
    }

    // RecognitionListener: 音声認識イベントを処理する
    // (この中身は ML Kit を使った場合と全く同じ)
    private val recognitionListener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech - リスニング再開")

            isListening = false
            restartListening() // ★音声が途切れたら再開
        }

        override fun onError(error: Int) {
            isListening = false
            val message: String?
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> message = "No match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> message = "Speech timeout"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    message = "Recognizer busy"
                    // ビジーの場合は少し待ってから再試行
//                    Handler().postDelayed(this::restartListening, 500)
                    Handler(Looper.getMainLooper()).postDelayed({
                        // ここで直接 startListening を呼んでまう
                        // （speechRecognizer や intent は、もちろんここで使える前提です）
                        speechRecognizer?.startListening(intent)
                    }, 500)
                    return

                }

                SpeechRecognizer.ERROR_CLIENT -> message = "Error client"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> message =
                    "Error insufficient permissions"

                else -> message = "Error: " + error
            }
            Log.e(TAG, "onError: " + message)
            restartListening() // ★何らかのエラーでも再開
        }

        override fun onResults(results: Bundle) {
            val inputText = findViewById<EditText>(R.id.inputText)
            Log.d(TAG, "onResults (最終結果)")
            val data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (data != null && !data.isEmpty()) {
                val fullText = data.get(0)
                Log.d(TAG, "Full Text: " + fullText)

                // 履歴管理リストを更新する
                updateConversationHistory(fullText)

                // 会話表示処理
                // 1. 履歴リスト (List<String>) の中身を、
                //    改行コード("\n")で連結して、1個の文字列に変換する
                val historyText = conversationHistory.joinToString("\n")
                // 2. 変換した文字列を TextView にセットする
                // 仮として、textViewLogとinputTextの両方に表示
                textViewLog!!.setText(historyText)
                inputText.setText(historyText)

                checkForKeyword(fullText)
            }
        }

        override fun onPartialResults(partialResults: Bundle) {
            val data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (data != null && !data.isEmpty()) {
                val partialText = data.get(0)
                checkForKeyword(partialText) // 途中結果でもキーワードをチェック
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ★★★ キーワード検出のロジック ★★★
    // (ML Kit の場合と全く同じ)
    private fun checkForKeyword(text: String) {
        // 認識結果は句読点（"、"や"？"）が含まれたり含まれなかったり、
        // 表記ゆれ（"え" と "ぇ"）があるため、それらを考慮すると検出率が上がります。

        // 簡単な正規化（句読点と空白を削除）

        val normalizedText = text.replace("？", "")
            .replace("？", "")
            .replace("、", "")
            .replace(" ", "")
            .replace("　", "")

        val target = "えなんつった" // 比較対象も正規化

        getstr = text
        textViewAll!!.setText(getstr)

        if (normalizedText.contains(target)) {
            Log.d(TAG, "★★ キーワード検出！ ★★: " + text)


            // 検出が重複しないように、一度UIを更新
            if (!textViewResult!!.getText().toString().startsWith("検出！")) {
                runOnUiThread(Runnable {
                    textViewResult!!.setText("検出！: " + text)
                })


                // 検出後のアクション（例：音を鳴らす）

                // リスナーをリセットして再開
                isListening = false
                speechRecognizer!!.stopListening() // 一旦停止
                Handler().postDelayed(Runnable { this.startListening() }, 1000) // 1秒後に再開
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (speechRecognizer != null) {
            speechRecognizer!!.stopListening()
            isListening = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (speechRecognizer != null && !isListening) {
            // アプリがフォアグラウンドに戻ってきたら再開
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 必ず SpeechRecognizer を破棄する
        if (speechRecognizer != null) {
            speechRecognizer!!.destroy()
            speechRecognizer = null
        }
    }

    companion object {
        private const val TAG = "SpeechRecognizerDemo"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

        // ★検出したいキーワード
        private const val TARGET_KEYWORD = "え？なんつった？"
    }
}