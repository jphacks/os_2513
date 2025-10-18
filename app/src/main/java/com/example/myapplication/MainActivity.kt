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

class MainActivity : AppCompatActivity() {
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

            val prompt = inputText.text.toString()
            if (prompt.isBlank()) {
                responseText.text = "Please enter text."
                return@setOnClickListener
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey
            )

            responseText.text = "Generating..." // Provide feedback to the user

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
    }
}