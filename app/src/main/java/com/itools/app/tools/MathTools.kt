package com.itools.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itools.app.ui.components.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.*

/* ======================= Math evaluator ======================= */

object MathEval {
    private val CONSTS = mapOf("pi" to PI, "e" to E, "tau" to 2 * PI, "phi" to (1 + sqrt(5.0)) / 2, "infinity" to Double.POSITIVE_INFINITY)
    private fun fact(n: Double): Double {
        if (n < 0 || n != floor(n)) throw IllegalArgumentException("Factorial requires a non-negative integer")
        var r = 1.0; var i = 2.0
        while (i <= n) { r *= i; i++ }
        return r
    }
    private val FUNCS: Map<String, (List<Double>) -> Double> = mapOf(
        "sqrt" to { a: List<Double> -> sqrt(a[0]) }, "cbrt" to { a: List<Double> -> cbrt(a[0]) }, "abs" to { a: List<Double> -> abs(a[0]) },
        "sin" to { a: List<Double> -> sin(a[0]) }, "cos" to { a: List<Double> -> cos(a[0]) }, "tan" to { a: List<Double> -> tan(a[0]) },
        "asin" to { a: List<Double> -> asin(a[0]) }, "acos" to { a: List<Double> -> acos(a[0]) }, "atan" to { a: List<Double> -> atan(a[0]) },
        "atan2" to { a: List<Double> -> atan2(a[0], a[1]) }, "sinh" to { a: List<Double> -> sinh(a[0]) }, "cosh" to { a: List<Double> -> cosh(a[0]) }, "tanh" to { a: List<Double> -> tanh(a[0]) },
        "log" to { a: List<Double> -> if (a.size == 2) ln(a[0]) / ln(a[1]) else ln(a[0]) }, "ln" to { a: List<Double> -> ln(a[0]) },
        "log10" to { a: List<Double> -> log10(a[0]) }, "log2" to { a: List<Double> -> log2(a[0]) }, "exp" to { a: List<Double> -> exp(a[0]) },
        "floor" to { a: List<Double> -> floor(a[0]) }, "ceil" to { a: List<Double> -> ceil(a[0]) }, "round" to { a: List<Double> -> if (a.size == 2) (a[0] * 10.0.pow(a[1])).roundToLong() / 10.0.pow(a[1]) else a[0].roundToLong().toDouble() },
        "min" to { a: List<Double> -> a.min() }, "max" to { a: List<Double> -> a.max() }, "pow" to { a: List<Double> -> a[0].pow(a[1]) },
        "sign" to { a: List<Double> -> sign(a[0]) }, "mod" to { a: List<Double> -> a[0].mod(a[1]) }, "factorial" to { a: List<Double> -> fact(a[0]) },
        "hypot" to { a: List<Double> -> sqrt(a.sumOf { it * it }) }, "mean" to { a: List<Double> -> a.average() }, "sum" to { a: List<Double> -> a.sum() },
    )

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun peek(): Char? { ws(); return s.getOrNull(i) }
        fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }
        fun expr(): Double {
            var v = term()
            while (true) v = when {
                eat('+') -> v + term()
                eat('-') -> v - term()
                else -> return v
            }
        }
        fun term(): Double {
            var v = unary()
            while (true) {
                val c = peek()
                v = when {
                    c == '*' -> { i++; v * unary() }
                    c == '/' -> { i++; v / unary() }
                    c == '%' -> { i++; v.mod(unary()) }
                    // implicit multiplication: 2pi, 3(4), 2sqrt(4)
                    c != null && (c == '(' || c.isLetter()) -> v * unary()
                    else -> return v
                }
            }
        }
        fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }
        fun power(): Double {
            val b = postfix()
            return if (eat('^')) b.pow(unary()) else b
        }
        fun postfix(): Double {
            var v = atom()
            while (true) {
                ws()
                if (s.getOrNull(i) == '!' && s.getOrNull(i + 1) != '=') { i++; v = fact(v) } else break
            }
            return v
        }
        fun atom(): Double {
            val c = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            if (eat('(')) { val v = expr(); if (!eat(')')) throw IllegalArgumentException("Missing ')'"); return v }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E') && s.getOrNull(i + 1)?.let { it.isDigit() || it == '-' || it == '+' } == true) {
                    i += 2; while (i < s.length && s[i].isDigit()) i++
                }
                return s.substring(st, i).toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                val name = s.substring(st, i).lowercase()
                if (peek() == '(' && name in FUNCS) {
                    i++
                    val args = mutableListOf<Double>()
                    if (!eat(')')) {
                        do { args += expr() } while (eat(','))
                        if (!eat(')')) throw IllegalArgumentException("Missing ')'")
                    }
                    if (args.isEmpty()) throw IllegalArgumentException("Function $name requires arguments")
                    return FUNCS.getValue(name)(args)
                }
                return CONSTS[name] ?: throw IllegalArgumentException("Undefined symbol $name")
            }
            throw IllegalArgumentException("Unexpected '$c'")
        }
    }

    fun eval(expr: String): Double {
        val p = P(expr.replace('×', '*').replace('÷', '/').replace("**", "^"))
        val v = p.expr()
        if (p.peek() != null) throw IllegalArgumentException("Unexpected '${p.peek()}'")
        return v
    }

    fun format(v: Double): String = when {
        v.isNaN() -> "NaN"
        v.isInfinite() -> if (v > 0) "Infinity" else "-Infinity"
        v == floor(v) && abs(v) < 1e15 -> v.toLong().toString()
        else -> "%.14g".format(Locale.US, v).let { if (it.contains('e')) it else it.trimEnd('0').trimEnd('.') }
    }
}

@Composable
fun MathEvaluatorTool() {
    var input by remember { mutableStateOf("") }
    val r = remember(input) { if (input.isBlank()) null else runCatching { MathEval.format(MathEval.eval(input)) } }
    ToolColumn {
        InputField(input, { input = it }, t("Your math expression", "Tu expresión matemática"), singleLine = false, minLines = 3, mono = true,
            placeholder = "2*sqrt(6) + cos(pi) - 3^2")
        r?.let { res ->
            res.onSuccess { OutputField(it, t("Result", "Resultado"), singleLine = true) }
            res.onFailure { ErrorText(it.message ?: "Error") }
        }
        Hint(t("Functions: ", "Funciones: ") + "sqrt, cbrt, abs, sin, cos, tan, asin, acos, atan, atan2, sinh, cosh, tanh, log, ln, log10, log2, exp, floor, ceil, round, min, max, pow, sign, mod, factorial, hypot, mean, sum. " +
            t("Constants: ", "Constantes: ") + "pi, e, tau, phi. " + t("Operators: ", "Operadores: ") + "+ - * / % ^ !")
    }
}

/* ======================= ETA calculator ======================= */

@Composable
fun EtaTool() {
    var total by remember { mutableIntStateOf(186) }
    var consumed by remember { mutableIntStateOf(3) }
    var span by remember { mutableIntStateOf(5) }
    var unitSec by remember { mutableIntStateOf(60) }
    var startText by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) }
    val start = runCatching { LocalDateTime.parse(startText) }.getOrNull()
    val durationSec = if (consumed > 0) total.toDouble() / consumed * span * unitSec else Double.NaN
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    ToolColumn {
        Hint(t("With a concrete example, if you wash 5 plates in 3 minutes and you have 500 plates to wash, it will take you 5 hours to wash them all.",
            "Por ejemplo, si lavas 5 platos en 3 minutos y tienes 500 platos, tardarás 5 horas en lavarlos todos."))
        Panel {
            NumberField(total, { total = it }, t("Amount of element to consume", "Cantidad de elementos a consumir"), min = 0)
            InputField(startText, { startText = it }, t("The consumption started at", "El consumo empezó el"), mono = true,
                error = if (start == null) "yyyy-MM-ddTHH:mm" else null)
            Text(t("Amount of unit consumed by time span", "Cantidad consumida por intervalo"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(consumed, { consumed = it }, t("Units", "Unidades"), Modifier.weight(1f), min = 0)
                Text(t("in", "en"))
                NumberField(span, { span = it }, t("Span", "Intervalo"), Modifier.weight(1f), min = 0)
            }
            ChipChoice(null, listOf(1 to t("seconds", "segundos"), 60 to t("minutes", "minutos"), 3600 to t("hours", "horas"), 86400 to t("days", "días")), unitSec) { unitSec = it }
        }
        if (!durationSec.isNaN() && start != null) {
            val end = start.plusSeconds(durationSec.toLong())
            KeyValueTable(listOf(
                t("Total duration", "Duración total") to humanDuration(durationSec),
                t("It will end", "Terminará el") to end.format(fmt),
            ))
        }
    }
}

/* ======================= Percentage calculator ======================= */

@Composable
fun PercentageTool() {
    var x1 by remember { mutableStateOf("") }; var y1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }; var y2 by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }
    fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
    fun f(v: Double?) = v?.takeIf { it.isFinite() }?.let { MathEval.format(it) } ?: ""
    ToolColumn {
        Panel(t("What is X % of Y", "¿Cuánto es X % de Y?")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(x1, { x1 = it }, "X", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                InputField(y1, { y1 = it }, "Y", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            OutputField(f(d(x1)?.let { x -> d(y1)?.let { y -> x / 100 * y } }), t("Result", "Resultado"), singleLine = true)
        }
        Panel(t("X is what percent of Y", "X es qué porcentaje de Y")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(x2, { x2 = it }, "X", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                InputField(y2, { y2 = it }, "Y", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            OutputField(f(d(x2)?.let { x -> d(y2)?.let { y -> x / y * 100 } }), t("Result (%)", "Resultado (%)"), singleLine = true)
        }
        Panel(t("What is the percentage increase/decrease", "¿Cuál es el incremento/descenso porcentual?")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(from, { from = it }, t("From", "Desde"), Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                InputField(to, { to = it }, t("To", "Hasta"), Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            OutputField(f(d(from)?.let { a -> d(to)?.let { b -> (b - a) / a * 100 } }), t("Result (%)", "Resultado (%)"), singleLine = true)
        }
    }
}

/* ======================= Chronometer ======================= */

fun formatMs(ms: Long): String {
    val h = ms / 3_600_000; val m = ms / 60_000 % 60; val s = ms / 1000 % 60; val cs = ms % 1000
    return (if (h > 0) "%d:%02d:%02d.%03d".format(h, m, s, cs) else "%02d:%02d.%03d".format(m, s, cs))
}

@Composable
fun ChronometerTool() {
    var running by remember { mutableStateOf(false) }
    var accumulated by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    val laps = remember { mutableStateListOf<Long>() }
    LaunchedEffect(running) {
        while (running) { now = System.nanoTime(); delay(16) }
    }
    val elapsed = accumulated + if (running) (now - startedAt) / 1_000_000 else 0L
    ToolColumn {
        Text(formatMs(elapsed), style = MaterialTheme.typography.displayMedium.copy(fontFamily = Mono), fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        ButtonRow {
            if (!running) Button(onClick = { startedAt = System.nanoTime(); now = startedAt; running = true }) { Text(t("Start", "Iniciar")) }
            else Button(onClick = { accumulated += (System.nanoTime() - startedAt) / 1_000_000; running = false }) { Text(t("Stop", "Parar")) }
            OutlinedButton(onClick = { laps.add(0, elapsed) }, enabled = running) { Text(t("Lap", "Vuelta")) }
            OutlinedButton(onClick = { running = false; accumulated = 0; laps.clear() }) { Text(t("Reset", "Reiniciar")) }
        }
        if (laps.isNotEmpty()) KeyValueTable(laps.mapIndexed { i, v -> "#${laps.size - i}" to formatMs(v) })
    }
}

/* ======================= Temperature converter ======================= */

private data class Scale(val key: String, val en: String, val es: String, val unit: String, val toK: (Double) -> Double, val fromK: (Double) -> Double)

private val SCALES = listOf(
    Scale("K", "Kelvin", "Kelvin", "K", { it }, { it }),
    Scale("C", "Celsius", "Celsius", "°C", { it + 273.15 }, { it - 273.15 }),
    Scale("F", "Fahrenheit", "Fahrenheit", "°F", { (it + 459.67) * 5 / 9 }, { it * 9 / 5 - 459.67 }),
    Scale("R", "Rankine", "Rankine", "°R", { it * 5 / 9 }, { it * 9 / 5 }),
    Scale("De", "Delisle", "Delisle", "°De", { 373.15 - it * 2 / 3 }, { (373.15 - it) * 3 / 2 }),
    Scale("N", "Newton", "Newton", "°N", { it * 100 / 33 + 273.15 }, { (it - 273.15) * 33 / 100 }),
    Scale("Re", "Réaumur", "Réaumur", "°Ré", { it * 5 / 4 + 273.15 }, { (it - 273.15) * 4 / 5 }),
    Scale("Ro", "Rømer", "Rømer", "°Rø", { (it - 7.5) * 40 / 21 + 273.15 }, { (it - 273.15) * 21 / 40 + 7.5 }),
)

@Composable
fun TemperatureTool() {
    var kelvin by remember { mutableDoubleStateOf(273.15) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val es = isSpanish()
    ToolColumn {
        SCALES.forEach { sc ->
            val shown = if (editing == sc.key) editText
            else ((sc.fromK(kelvin) * 100).roundToLong() / 100.0).let { MathEval.format(it) }
            InputField(shown, { txt ->
                editing = sc.key; editText = txt
                txt.replace(',', '.').toDoubleOrNull()?.let { kelvin = sc.toK(it) }
            }, (if (es) sc.es else sc.en) + " (${sc.unit})", keyboard = KeyboardType.Decimal, mono = true)
        }
    }
}

/* ======================= Benchmark builder ======================= */

private val MEASURE_SEPARATORS = Regex("[\\s;]+")

private class Suite(title: String, values: String) {
    var title by mutableStateOf(title)
    var values by mutableStateOf(values)
    val numbers get() = values.split(MEASURE_SEPARATORS).mapNotNull { it.replace(',', '.').toDoubleOrNull() }
}

@Composable
fun BenchmarkTool() {
    val suites = remember { mutableStateListOf(Suite("Suite 1", "5 10"), Suite("Suite 2", "8 12")) }
    var unit by remember { mutableStateOf("ms") }
    fun fmt(d: Double) = if (d.isNaN()) "—" else "%.3f".format(Locale.US, d).trimEnd('0').trimEnd('.') + " $unit"
    ToolColumn {
        InputField(unit, { unit = it }, t("Unit (for display)", "Unidad (para mostrar)"))
        suites.forEachIndexed { idx, s ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InputField(s.title, { s.title = it }, t("Suite name", "Nombre de la suite"), Modifier.weight(1f))
                    TextButton(onClick = { suites.removeAt(idx) }, enabled = suites.size > 1) { Text(t("Delete", "Eliminar")) }
                }
                InputField(s.values, { s.values = it }, t("Measures (space or line separated)", "Medidas (separadas por espacio o línea)"),
                    singleLine = false, minLines = 2, mono = true, keyboard = KeyboardType.Text)
            }
        }
        ButtonRow {
            Button(onClick = { suites.add(Suite("Suite ${suites.size + 1}", "")) }) { Text(t("Add suite", "Añadir suite")) }
            OutlinedButton(onClick = { suites.forEach { it.values = "" } }) { Text(t("Reset suites", "Vaciar medidas")) }
        }
        val stats = suites.map { s ->
            val n = s.numbers
            val mean = if (n.isEmpty()) Double.NaN else n.average()
            val variance = if (n.isEmpty()) Double.NaN else n.sumOf { (it - mean).pow(2) } / n.size
            Triple(s.title, mean, variance) to n.size
        }.sortedWith(compareBy { if (it.first.second.isNaN()) Double.MAX_VALUE else it.first.second })
        val best = stats.firstOrNull()?.first?.second ?: Double.NaN
        Panel(t("Results", "Resultados")) {
            stats.forEachIndexed { pos, (st, count) ->
                val (title, mean, variance) = st
                Text("${pos + 1}. $title", fontWeight = FontWeight.SemiBold)
                Text(
                    t("Samples", "Muestras") + ": $count · " + t("Mean", "Media") + ": ${fmt(mean)} · " +
                        t("Variance", "Varianza") + ": ${if (variance.isNaN()) "—" else "%.3f".format(Locale.US, variance)}" +
                        if (pos > 0 && !mean.isNaN() && !best.isNaN()) " · +${fmt(mean - best)} (×${"%.2f".format(Locale.US, mean / best)})" else "",
                    style = MaterialTheme.typography.bodySmall
                )
                if (pos < stats.lastIndex) HorizontalDivider()
            }
        }
    }
}
