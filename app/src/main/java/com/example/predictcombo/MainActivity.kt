package com.example.predictcombo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PredictComboApp() }
    }
}

@Composable
fun PredictComboApp() {
    var numbersText by remember { mutableStateOf("") }
    var currentBetText by remember { mutableStateOf("10") }
    var lastRoundWon by remember { mutableStateOf(true) }
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Predict Combo — Simple") })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Enter last numbers (comma separated, most recent last):", fontSize = 14.sp)
            OutlinedTextField(
                value = numbersText,
                onValueChange = { numbersText = it },
                placeholder = { Text("e.g. 64,18,24,38,75") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Current bet:", modifier = Modifier.width(100.dp))
                OutlinedTextField(
                    value = currentBetText,
                    onValueChange = { currentBetText = it.filter { ch -> ch.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )

                Spacer(Modifier.width(18.dp))

                Checkbox(checked = lastRoundWon, onCheckedChange = { lastRoundWon = it })
                Text("Last round won", modifier = Modifier.padding(start = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = {
                // Clear prior messages
                errorText = ""
                resultText = ""

                try {
                    val lastNumbers = parseNumbers(numbersText)
                    if (lastNumbers.isEmpty()) throw IllegalArgumentException("Provide at least one number")

                    val currentBet = currentBetText.toIntOrNull() ?: 10

                    val res = predictComboKotlin(lastNumbers, currentBet, lastRoundWon)

                    val json = JSONObject()
                    json.put("top_pick", res.topPick)
                    json.put("backup", res.backup)
                    json.put("next_bet", res.nextBet)
                    val expl = JSONObject()
                    expl.put("last_number", res.explanation.lastNumber)
                    expl.put("last_parity", res.explanation.lastParity)
                    expl.put("last_range", res.explanation.lastRange)
                    expl.put("predicted_parity", res.explanation.predictedParity)
                    expl.put("predicted_range", res.explanation.predictedRange)
                    expl.put("range_reason", res.explanation.rangeReason)
                    expl.put("bet_reason", res.explanation.betReason)
                    json.put("explanation", expl)

                    resultText = json.toString(2)
                } catch (e: Exception) {
                    errorText = e.message ?: "Error"
                }

            }, modifier = Modifier.align(Alignment.Start)) {
                Text("Predict")
            }

            Spacer(Modifier.height(18.dp))

            if (errorText.isNotEmpty()) {
                Text(errorText, color = MaterialTheme.colors.error)
            }

            if (resultText.isNotEmpty()) {
                Text("Result:", fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
                Card(elevation = 4.dp) {
                    Text(resultText, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Notes:\n• Numbers must be between 1 and 75.\n• This app implements a simple prediction heuristic and a martingale bet rule for demonstration only. Don't use it for real gambling without caution.",
                fontSize = 12.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

// ----------------- Logic ported from the Python function -----------------
data class Explanation(
    val lastNumber: Int,
    val lastParity: String,
    val lastRange: String,
    val predictedParity: String,
    val predictedRange: String,
    val rangeReason: String,
    val betReason: String
)

data class PredictResult(
    val topPick: String,
    val backup: String,
    val nextBet: Int,
    val explanation: Explanation
)

fun parseNumbers(text: String): List<Int> {
    if (text.isBlank()) return emptyList()
    return text.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.toInt() }
        .map { if (it < 1 || it > 75) throw IllegalArgumentException("Numbers must be 1..75") else it }
}

fun rangeLabel(n: Int): String = if (n in 1..37) "U" else "O"
fun parityLabel(n: Int): String = if (n % 2 == 0) "E" else "O"

fun predictComboKotlin(lastNumbers: List<Int>, currentBet: Int = 10, lastRoundWon: Boolean = true): PredictResult {
    if (lastNumbers.isEmpty()) throw IllegalArgumentException("Provide at least one last number.")

    val last = lastNumbers.last()
    val lastParity = parityLabel(last)
    val lastRange = rangeLabel(last)

    // 1) Parity prediction: strong alternation bias
    val predictedParity = if (lastParity == "E") "O" else "E"

    // 2) Range prediction: look at last 2-3 ranges
    val ranges = lastNumbers.takeLast(3).map { rangeLabel(it) }
    val predictedRange: String
    val rangeReason: String

    if (ranges.size >= 2 && ranges[ranges.size - 1] == ranges[ranges.size - 2]) {
        predictedRange = if (ranges.last() == "O") "U" else "O"
        rangeReason = "last ranges ${ranges.takeLast(2)} → streak → bounce"
    } else {
        predictedRange = ranges.last()
        rangeReason = "no streak in $ranges → continuation"
    }

    val topPick = "$predictedRange/$predictedParity"
    val backupRange = if (predictedRange == "O") "U" else "O"
    val backup = "$backupRange/$predictedParity"

    val nextBet: Int
    val betReason: String
    if (lastRoundWon) {
        nextBet = 10
        betReason = "last round won → reset to base (10)"
    } else {
        nextBet = currentBet * 2
        betReason = "last round lost → double from $currentBet to $nextBet"
    }

    val explanation = Explanation(
        lastNumber = last,
        lastParity = lastParity,
        lastRange = lastRange,
        predictedParity = predictedParity,
        predictedRange = predictedRange,
        rangeReason = rangeReason,
        betReason = betReason
    )

    return PredictResult(topPick, backup, nextBet, explanation)
}
