package com.itools.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.itools.app.ui.components.*
import com.itools.app.util.*

/* ======================= Git cheatsheet ======================= */

private data class MemoSection(val en: String, val es: String, val items: List<Triple<String, String, String>>)

private val GIT_MEMO = listOf(
    MemoSection("Configuration", "Configuración", listOf(
        Triple("Set the global config", "Configurar la configuración global", "git config --global user.name \"[name]\"\ngit config --global user.email \"[email]\""),
    )),
    MemoSection("Get started", "Primeros pasos", listOf(
        Triple("Create a git repo", "Crear un repositorio", "git init"),
        Triple("Clone an existing repository", "Clonar un repositorio existente", "git clone [repo url]"),
        Triple("Add a remote", "Añadir un remoto", "git remote add origin [url]"),
    )),
    MemoSection("Commit", "Commit", listOf(
        Triple("Commit all your tracked files", "Hacer commit de todos los ficheros seguidos", "git commit -am \"[commit message]\""),
        Triple("Add new modifications to your last commit", "Añadir cambios al último commit", "git commit --amend --no-edit"),
    )),
    MemoSection("I’ve made a mistake", "Me he equivocado", listOf(
        Triple("Change last commit message", "Cambiar el mensaje del último commit", "git commit --amend"),
        Triple("Undo most recent commit and keep changes", "Deshacer el último commit manteniendo los cambios", "git reset HEAD~1"),
        Triple("Undo the N most recent commit and keep changes", "Deshacer los N últimos commits manteniendo los cambios", "git reset HEAD~N"),
        Triple("Undo most recent commit and get rid of changes", "Deshacer el último commit y descartar los cambios", "git reset HEAD~1 --hard"),
        Triple("Reset branch to remote state", "Restablecer la rama al estado remoto", "git fetch origin\ngit reset --hard origin/[branch-name]"),
    )),
    MemoSection("Miscellaneous", "Varios", listOf(
        Triple("Renaming the local master branch to main", "Renombrar la rama local master a main", "git branch -m master main"),
        Triple("Stash changes", "Guardar cambios temporalmente (stash)", "git stash\ngit stash pop"),
        Triple("Show commit history graph", "Ver el grafo del historial", "git log --oneline --graph --decorate --all"),
        Triple("Discard changes of a file", "Descartar cambios de un fichero", "git restore [file]"),
        Triple("Create and switch to a new branch", "Crear y cambiar a una rama nueva", "git switch -c [branch-name]"),
        Triple("Delete a local branch", "Borrar una rama local", "git branch -d [branch-name]"),
        Triple("Delete a remote branch", "Borrar una rama remota", "git push origin --delete [branch-name]"),
        Triple("Rebase current branch onto main", "Hacer rebase de la rama actual sobre main", "git fetch origin\ngit rebase origin/main"),
        Triple("Cherry-pick a commit", "Aplicar un commit concreto", "git cherry-pick [commit-hash]"),
        Triple("Create a tag and push it", "Crear una etiqueta y subirla", "git tag v1.0.0\ngit push origin v1.0.0"),
    )),
)

@Composable
fun GitMemoTool() = Memo(GIT_MEMO)

@Composable
private fun Memo(sections: List<MemoSection>) {
    val es = isSpanish()
    ToolColumn {
        sections.forEach { s ->
            Text(if (es) s.es else s.en, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            s.items.forEach { (en, esT, code) ->
                Text(if (es) esT else en, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoBlock(code, Modifier.weight(1f))
                    CopyButton(code)
                }
            }
        }
    }
}

/* ======================= Random port ======================= */

@Composable
fun RandomPortTool() {
    var port by remember { mutableIntStateOf(1024 + secureRandom.nextInt(65536 - 1024)) }
    ToolColumn {
        Text(port.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        ButtonRow {
            CopyButton(port.toString())
            Button(onClick = { port = 1024 + secureRandom.nextInt(65536 - 1024) }) { Text(t("Refresh", "Regenerar")) }
        }
    }
}

/* ======================= Crontab ======================= */

object Cron {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val DAYS = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val MONTHS_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    private val DAYS_EN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val DAYS_ES = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")

    val ALIASES = mapOf(
        "@yearly" to "0 0 1 1 *", "@annually" to "0 0 1 1 *", "@monthly" to "0 0 1 * *",
        "@weekly" to "0 0 * * 0", "@daily" to "0 0 * * *", "@midnight" to "0 0 * * *", "@hourly" to "0 * * * *",
    )

    private val WS = Regex("\\s+")

    private class F(val min: Int, val max: Int, val names: List<String>? = null, val nameBase: Int = 0)
    private val FIELDS = listOf(F(0, 59), F(0, 23), F(1, 31), F(1, 12, MONTHS, 1), F(0, 7, DAYS, 0))

    private fun value(s: String, f: F): Int {
        f.names?.indexOf(s.uppercase())?.takeIf { it >= 0 }?.let { return it + f.nameBase }
        val n = s.toIntOrNull() ?: throw IllegalArgumentException("Invalid value '$s'")
        if (n < f.min || n > f.max) throw IllegalArgumentException("Value $n out of range ${f.min}-${f.max}")
        return n
    }

    /** Validates and normalizes names (JAN, MON...) to numbers. */
    fun normalize(expr: String): List<String> {
        val e = ALIASES[expr.trim().lowercase()] ?: expr.trim()
        val parts = e.split(WS)
        if (parts.size !in 5..6) throw IllegalArgumentException("Expected 5 fields")
        val p = if (parts.size == 6) parts.drop(1) else parts   // accepts a seconds field
        return p.mapIndexed { i, field ->
            val f = FIELDS[i]
            field.split(",").joinToString(",") { item ->
                val (base, step) = item.split("/").let { if (it.size == 2) it[0] to it[1] else if (it.size == 1) it[0] to null else throw IllegalArgumentException("Invalid '$item'") }
                if (step != null && (step.toIntOrNull() ?: 0) <= 0) throw IllegalArgumentException("Invalid step '$step'")
                val b = when {
                    base == "*" || base == "?" -> "*"
                    base.contains("-") -> base.split("-").let { r ->
                        if (r.size != 2) throw IllegalArgumentException("Invalid range '$base'")
                        val a = value(r[0], f); val z = value(r[1], f)
                        "$a-$z"
                    }
                    else -> value(base, f).toString()
                }
                if (step != null) "$b/$step" else b
            }
        }
    }

    fun describe(expr: String, es: Boolean): String {
        val (mi, ho, dom, mon, dow) = normalize(expr)
        fun monthName(n: Int) = (if (es) MONTHS_ES else MONTHS_EN)[n - 1]
        fun dayName(n: Int) = (if (es) DAYS_ES else DAYS_EN)[n % 7]
        fun two(n: String) = n.padStart(2, '0')
        fun list(items: List<String>): String = when (items.size) {
            1 -> items[0]
            else -> items.dropLast(1).joinToString(", ") + (if (es) " y " else " and ") + items.last()
        }
        fun fieldDesc(f: String, unitEn: String, unitEs: String, name: (Int) -> String): String =
            list(f.split(",").map { item ->
                val parts = item.split("/")
                val base = parts[0]; val step = parts.getOrNull(1)
                val b = when {
                    base == "*" -> ""
                    base.contains("-") -> base.split("-").let { if (es) "de ${name(it[0].toInt())} a ${name(it[1].toInt())}" else "${name(it[0].toInt())} through ${name(it[1].toInt())}" }
                    else -> name(base.toInt())
                }
                when {
                    step != null && base == "*" -> if (es) "cada $step $unitEs" else "every $step $unitEn"
                    step != null && !base.contains("-") -> if (es) "cada $step $unitEs a partir de $b" else "every $step $unitEn starting at $b"
                    step != null -> if (es) "cada $step $unitEs, $b" else "every $step $unitEn, $b"
                    else -> b
                }
            })

        val sb = mutableListOf<String>()
        val simpleMin = mi.all { it.isDigit() }; val simpleHour = ho.all { it.isDigit() }
        when {
            simpleMin && simpleHour -> sb += (if (es) "A las " else "At ") + "${two(ho)}:${two(mi)}"
            mi == "*" && ho == "*" -> sb += if (es) "Cada minuto" else "Every minute"
            else -> {
                sb += when {
                    mi == "*" -> if (es) "Cada minuto" else "Every minute"
                    mi.contains("/") || mi.contains("-") || mi.contains(",") ->
                        (if (es) "Minutos: " else "Minutes: ") + fieldDesc(mi, "minutes", "minutos") { it.toString() }
                    else -> if (es) "En el minuto $mi" else "At $mi minutes past the hour"
                }
                if (ho != "*") sb += (if (es) "horas: " else "hours: ") + fieldDesc(ho, "hours", "horas") { two(it.toString()) + ":00" }
            }
        }
        if (dom != "*") sb += (if (es) "el día del mes " else "on day-of-month ") + fieldDesc(dom, "days", "días") { it.toString() }
        if (mon != "*") sb += (if (es) "en " else "in ") + fieldDesc(mon, "months", "meses") { monthName(it) }
        if (dow != "*") sb += (if (es) "los " else "on ") + fieldDesc(dow, "days of the week", "días de la semana") { dayName(it) }
        return sb.joinToString(", ")
    }
}

@Composable
fun CrontabTool() {
    var expr by remember { mutableStateOf("40 * * * *") }
    val es = isSpanish()
    val result = remember(expr, es) { runCatching { Cron.describe(expr, es) } }
    ToolColumn {
        InputField(expr, { expr = it }, "Cron", mono = true,
            error = result.exceptionOrNull()?.let { t("This cron is invalid", "Esta expresión cron no es válida") + ": " + it.message })
        result.getOrNull()?.let { Panel { Text(it, style = MaterialTheme.typography.titleMedium) } }
        MonoBlock(
            """┌──────────── ${t("[optional] seconds (0 - 59)", "[opcional] segundos (0 - 59)")}
| ┌────────── ${t("minute (0 - 59)", "minuto (0 - 59)")}
| | ┌──────── ${t("hour (0 - 23)", "hora (0 - 23)")}
| | | ┌────── ${t("day of month (1 - 31)", "día del mes (1 - 31)")}
| | | | ┌──── ${t("month (1 - 12) OR jan,feb,mar...", "mes (1 - 12) O jan,feb,mar...")}
| | | | | ┌── ${t("day of week (0 - 6, sunday=0) OR sun,mon...", "día de la semana (0 - 6, domingo=0) O sun,mon...")}
| | | | | |
* * * * * * command""", size = 12
        )
        KeyValueTable(listOf(
            "*" to t("Every", "Cualquier valor"), "-" to t("Range of values", "Rango de valores"),
            "," to t("List of values", "Lista de valores"), "/" to t("Steps (e.g. */5)", "Pasos (p. ej. */5)"),
        ) + Cron.ALIASES.map { (k, v) -> k to v }, copy = false)
    }
}

/* ======================= JSON prettify / minify / csv ======================= */

@Composable
fun JsonPrettifyTool() {
    var input by remember { mutableStateOf("{\"hello\": [\"world\"], \"foo\": {\"b\": 2, \"a\": 1}}") }
    var sort by remember { mutableStateOf(true) }
    var indent by remember { mutableIntStateOf(3) }
    val r = remember(input, sort, indent) { if (input.isBlank()) Result.success("") else runCatching { Json.stringify(Json.parse(input), indent, sort) } }
    ToolColumn {
        Panel {
            SwitchRow(t("Sort keys", "Ordenar claves"), sort) { sort = it }
            SliderRow(t("Indent size", "Tamaño de sangría"), indent, 1..10) { indent = it }
        }
        InputField(input, { input = it }, t("Your raw JSON", "Tu JSON"), singleLine = false, minLines = 8, mono = true,
            error = r.exceptionOrNull()?.message?.let { t("Invalid JSON: ", "JSON no válido: ") + it })
        OutputField(r.getOrDefault(""), t("Prettified version of your JSON", "Versión formateada de tu JSON"), minLines = 8)
    }
}

@Composable
fun JsonMinifyTool() = FormatConverter(t("Your raw JSON", "Tu JSON"), t("Minified version of your JSON", "Versión minificada de tu JSON"),
    "{\n  \"hello\": [\n    \"world\"\n  ]\n}") { Json.stringify(Json.parse(it), 0) }

@Composable
fun JsonToCsvTool() = FormatConverter(t("Your raw JSON", "Tu JSON"), "CSV",
    "[\n  {\"Age\": 18, \"Salary\": 20000, \"Name\": \"John\", \"Email\": \"john@example.com\"},\n  {\"Age\": 64, \"Salary\": 15000, \"Name\": \"Tom\", \"Phone\": \"123-4567\"}\n]") { Csv.fromJson(Json.parse(it)) }

/* ======================= SQL prettify ======================= */

object SqlFormatter {
    private val NEWLINE_KW = listOf(
        "SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "OFFSET", "UNION ALL", "UNION", "EXCEPT", "INTERSECT",
        "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM", "RETURNING", "WITH", "ON CONFLICT",
        "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "CROSS JOIN", "FULL JOIN", "JOIN",
        "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "WINDOW",
    )
    private val KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "EXCEPT", "INTERSECT", "INSERT", "INTO",
        "VALUES", "UPDATE", "SET", "DELETE", "RETURNING", "WITH", "ON", "CONFLICT", "LEFT", "RIGHT", "FULL", "OUTER", "INNER", "CROSS", "JOIN",
        "CREATE", "ALTER", "DROP", "TABLE", "AND", "OR", "NOT", "NULL", "IS", "IN", "AS", "DISTINCT", "CASE", "WHEN", "THEN", "ELSE", "END",
        "LIKE", "ILIKE", "BETWEEN", "EXISTS", "ASC", "DESC", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT", "UNIQUE", "INDEX", "VIEW",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "CAST", "TRUE", "FALSE", "OVER", "PARTITION", "WINDOW", "USING", "DO", "NOTHING",
        "ADD", "COLUMN", "CONSTRAINT", "IF", "RECURSIVE", "INTEGER", "INT", "VARCHAR", "TEXT", "BOOLEAN", "DATE", "TIMESTAMP",
    )

    private sealed class Tok(val text: String) {
        class Word(t: String) : Tok(t); class Str(t: String) : Tok(t); class Comment(t: String) : Tok(t)
        class Sym(t: String) : Tok(t); class Num(t: String) : Tok(t)
    }

    private fun tokenize(sql: String): List<Tok> {
        val out = mutableListOf<Tok>(); var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                c.isWhitespace() -> i++
                sql.startsWith("--", i) || c == '#' -> { val e = sql.indexOf('\n', i).let { if (it < 0) sql.length else it }; out += Tok.Comment(sql.substring(i, e).trim()); i = e }
                sql.startsWith("/*", i) -> { val e = sql.indexOf("*/", i + 2).let { if (it < 0) sql.length else it + 2 }; out += Tok.Comment(sql.substring(i, e)); i = e }
                c == '\'' || c == '"' || c == '`' -> {
                    var j = i + 1
                    while (j < sql.length) { if (sql[j] == c) { if (j + 1 < sql.length && sql[j + 1] == c) j += 2 else break } else j++ }
                    out += Tok.Str(sql.substring(i, minOf(j + 1, sql.length))); i = j + 1
                }
                c == '[' -> { val e = sql.indexOf(']', i).let { if (it < 0) sql.length else it + 1 }; out += Tok.Str(sql.substring(i, e)); i = e }
                c.isDigit() -> { var j = i; while (j < sql.length && (sql[j].isLetterOrDigit() || sql[j] == '.')) j++; out += Tok.Num(sql.substring(i, j)); i = j }
                c.isLetter() || c == '_' || c == '@' || c == '$' || c == ':' && i + 1 < sql.length && sql[i + 1].isLetter() -> {
                    var j = i + 1; while (j < sql.length && (sql[j].isLetterOrDigit() || sql[j] in "_.$")) j++
                    out += Tok.Word(sql.substring(i, j)); i = j
                }
                else -> {
                    val two = if (i + 1 < sql.length) sql.substring(i, i + 2) else ""
                    if (two in TWO_CHAR_SYMS) { out += Tok.Sym(two); i += 2 } else { out += Tok.Sym(c.toString()); i++ }
                }
            }
        }
        return out
    }

    enum class KwCase { UPPER, LOWER, PRESERVE }

    private val JOINS = NEWLINE_KW.filter { it.endsWith("JOIN") }.toSet()
    private val NEWLINE_KW_WORDS = NEWLINE_KW.map { it to it.split(" ") }
    private val TWO_CHAR_SYMS = setOf("<=", ">=", "<>", "!=", "||", "::", "->")
    private val LIST_CLAUSES = setOf("SELECT", "GROUP BY", "ORDER BY", "SET", "WITH", "VALUES")
    private val CONDITION_CLAUSES = setOf("WHERE", "HAVING")
    private val SUBQUERY_START = setOf("SELECT", "WITH")
    private val SPACE_BEFORE_PAREN = setOf("IN", "VALUES", "AS", "ON", "AND", "OR", "NOT", "EXISTS")
    private val MULTI_BLANK = Regex("\n{3,}")

    fun format(sql: String, indentSize: Int = 2, kwCase: KwCase = KwCase.UPPER): String {
        val toks = tokenize(sql)
        val sb = StringBuilder()
        val ind = " ".repeat(indentSize)
        var level = 0
        val parenStack = ArrayDeque<Boolean>() // true if the parenthesis opens a subquery
        var needSpace = false
        var clause = ""
        fun kw(s: String) = when (kwCase) { KwCase.UPPER -> s.uppercase(); KwCase.LOWER -> s.lowercase(); KwCase.PRESERVE -> s }
        fun newline(extra: Int = 0) {
            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            sb.append(ind.repeat((level + extra).coerceAtLeast(0))); needSpace = false
        }
        fun emit(s: String) { if (needSpace) sb.append(' '); sb.append(s); needSpace = true }
        var i = 0
        while (i < toks.size) {
            val tk = toks[i]
            if (tk is Tok.Word) {
                val match = NEWLINE_KW_WORDS.firstOrNull { (_, ws) ->
                    ws.indices.all { j -> (toks.getOrNull(i + j) as? Tok.Word)?.text?.equals(ws[j], true) == true }
                }
                if (match != null) {
                    val (nl, words) = match
                    val n = words.size
                    val original = (0 until n).joinToString(" ") { toks[i + it].text }
                    if (nl in JOINS) { newline(1); emit(kw(original)) }
                    else { newline(); emit(kw(original)); clause = nl; newline(1) }
                    i += n; continue
                }
                val up = tk.text.uppercase()
                if ((up == "AND" || up == "OR") && clause in CONDITION_CLAUSES) { newline(1); emit(kw(tk.text)); i++; continue }
                emit(if (up in KEYWORDS) kw(tk.text) else tk.text); i++; continue
            }
            when (tk) {
                is Tok.Comment -> { if (sb.isNotEmpty()) newline(1); emit(tk.text); newline(1) }
                is Tok.Sym -> when (tk.text) {
                    "," -> {
                        sb.append(',')
                        if (parenStack.lastOrNull() != false && clause in LIST_CLAUSES) newline(1)
                        else needSpace = true
                    }
                    ";" -> { sb.append(";\n"); level = 0; clause = ""; parenStack.clear(); needSpace = false }
                    "(" -> {
                        val sub = (toks.getOrNull(i + 1) as? Tok.Word)?.text?.uppercase() in SUBQUERY_START
                        val prevWord = sb.isNotEmpty() && (sb.last().isLetterOrDigit() || sb.last() == '_')
                        val prevTok = toks.getOrNull(i - 1)
                        // function: no space (COUNT(x)); otherwise with a space
                        if (needSpace && !(prevWord && prevTok is Tok.Word && prevTok.text.uppercase() !in SPACE_BEFORE_PAREN)) sb.append(' ')
                        sb.append('('); needSpace = false
                        parenStack.addLast(sub)
                        if (sub) level += 2
                    }
                    ")" -> {
                        val sub = parenStack.removeLastOrNull() ?: false
                        if (sub) { level -= 2; newline(1) }
                        while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
                        sb.append(')'); needSpace = true
                    }
                    ".", "::" -> { while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1); sb.append(tk.text); needSpace = false }
                    else -> emit(tk.text)
                }
                else -> {
                    if (sb.isNotEmpty() && (sb.last() == '.' || sb.endsWith("::"))) needSpace = false
                    emit(tk.text)
                }
            }
            i++
        }
        return sb.lines().joinToString("\n") { it.trimEnd() }.replace(MULTI_BLANK, "\n\n").trim()
    }
}

@Composable
fun SqlPrettifyTool() {
    var input by remember { mutableStateOf("select field1,field2,field3 from my_table where my_condition = 1 and another_condition = 'foo' order by field1 desc;") }
    var kwCase by remember { mutableStateOf(SqlFormatter.KwCase.UPPER) }
    var indent by remember { mutableIntStateOf(2) }
    val out = remember(input, kwCase, indent) { runCatching { SqlFormatter.format(input, indent, kwCase) }.getOrElse { it.message ?: "" } }
    ToolColumn {
        Panel {
            ChipChoice(t("Keyword case", "Mayúsculas en palabras clave"), listOf(
                SqlFormatter.KwCase.UPPER to "UPPERCASE", SqlFormatter.KwCase.LOWER to "lowercase", SqlFormatter.KwCase.PRESERVE to t("Preserve", "Conservar")
            ), kwCase) { kwCase = it }
            ChipChoice(t("Indent style", "Sangría"), listOf(2 to "2", 4 to "4"), indent) { indent = it }
        }
        InputField(input, { input = it }, t("Your SQL query", "Tu consulta SQL"), singleLine = false, minLines = 6, mono = true)
        OutputField(out, t("Prettified version of your query", "Versión formateada de tu consulta"), minLines = 8)
    }
}

/* ======================= Chmod ======================= */

@Composable
fun ChmodTool() {
    // [owner, group, public][read, write, execute]
    val perms = remember { mutableStateListOf(true, true, true, true, false, true, true, false, true) }
    val octal = (0..2).joinToString("") { g -> ((if (perms[g * 3]) 4 else 0) + (if (perms[g * 3 + 1]) 2 else 0) + (if (perms[g * 3 + 2]) 1 else 0)).toString() }
    val symbolic = (0..2).joinToString("") { g -> (if (perms[g * 3]) "r" else "-") + (if (perms[g * 3 + 1]) "w" else "-") + (if (perms[g * 3 + 2]) "x" else "-") }
    ToolColumn {
        Panel {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.3f))
                listOf(t("Owner (u)", "Propietario (u)"), t("Group (g)", "Grupo (g)"), t("Public (o)", "Otros (o)")).forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
            }
            listOf(t("Read (4)", "Lectura (4)"), t("Write (2)", "Escritura (2)"), t("Execute (1)", "Ejecución (1)")).forEachIndexed { r, label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1.3f))
                    (0..2).forEach { g ->
                        Box(Modifier.weight(1f)) { Checkbox(perms[g * 3 + r], { perms[g * 3 + r] = it }) }
                    }
                }
            }
        }
        Text(octal, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(symbolic, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono), modifier = Modifier.align(Alignment.CenterHorizontally))
        OutputField("chmod $octal path", t("Command", "Comando"), singleLine = true)
    }
}

/* ======================= Docker run → compose ======================= */

object DockerCompose {
    /** Splits a shell line honoring quotes and "\" line continuations. */
    fun shellSplit(s: String): List<String> {
        val src = s.replace(Regex("\\\\\\r?\\n"), " ")
        val out = mutableListOf<String>(); val cur = StringBuilder(); var q: Char? = null; var has = false; var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                q != null -> if (c == q) q = null else if (c == '\\' && q == '"' && i + 1 < src.length) { cur.append(src[++i]) } else cur.append(c)
                c == '\'' || c == '"' -> { q = c; has = true }
                c == '\\' && i + 1 < src.length -> { cur.append(src[++i]); has = true }
                c.isWhitespace() -> { if (cur.isNotEmpty() || has) out += cur.toString(); cur.clear(); has = false }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty() || has) out += cur.toString()
        return out
    }

    private val NO_VALUE = setOf("-d", "--detach", "--rm", "-i", "--interactive", "-t", "--tty", "-it", "-ti", "--privileged", "--init", "--read-only", "-P", "--publish-all")

    private val YAML_RESERVED = setOf("true", "false", "yes", "no", "on", "off", "null", "~")

    data class Result(val yaml: String, val warnings: List<String>)

    fun convert(cmd: String): Result {
        var args = shellSplit(cmd.trim())
        if (args.firstOrNull() == "sudo") args = args.drop(1)
        if (args.firstOrNull() == "docker") args = args.drop(1)
        if (args.firstOrNull() == "container") args = args.drop(1)
        if (args.firstOrNull() == "run") args = args.drop(1) else throw IllegalArgumentException("Command must start with 'docker run'")
        val svc = LinkedHashMap<String, Any>()
        val lists = LinkedHashMap<String, MutableList<String>>()
        val maps = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val warnings = mutableListOf<String>()
        fun addL(k: String, v: String) { lists.getOrPut(k) { mutableListOf() } += v }
        var i = 0; var image: String? = null
        while (i < args.size) {
            val a = args[i]
            if (!a.startsWith("-")) { image = a; i++; break }
            var (flag, inline) = if (a.startsWith("--") && a.contains("=")) a.substringBefore("=") to a.substringAfter("=") else a to null
            // short flags combined with their value: -p8080:80
            if (!a.startsWith("--") && a.length > 2 && a !in NO_VALUE && inline == null && a[1] in "pvewuhlm") { flag = a.substring(0, 2); inline = a.substring(2) }
            if (flag in NO_VALUE) {
                when (flag) {
                    "-it", "-ti" -> { svc["stdin_open"] = true; svc["tty"] = true }
                    "-i", "--interactive" -> svc["stdin_open"] = true
                    "-t", "--tty" -> svc["tty"] = true
                    "--privileged" -> svc["privileged"] = true
                    "--init" -> svc["init"] = true
                    "--read-only" -> svc["read_only"] = true
                    "--rm" -> warnings += "--rm"
                }
                i++; continue
            }
            val v = inline ?: args.getOrNull(i + 1) ?: throw IllegalArgumentException("Missing value for $flag")
            i += if (inline != null) 1 else 2
            when (flag) {
                "--name" -> svc["container_name"] = v
                "-p", "--publish" -> addL("ports", v)
                "-v", "--volume", "--mount" -> addL("volumes", v)
                "-e", "--env" -> addL("environment", v)
                "--env-file" -> addL("env_file", v)
                "--network", "--net" -> addL("networks", v)
                "--restart" -> svc["restart"] = v
                "-w", "--workdir" -> svc["working_dir"] = v
                "-u", "--user" -> svc["user"] = v
                "-h", "--hostname" -> svc["hostname"] = v
                "--entrypoint" -> svc["entrypoint"] = v
                "--cap-add" -> addL("cap_add", v)
                "--cap-drop" -> addL("cap_drop", v)
                "--device" -> addL("devices", v)
                "-l", "--label" -> addL("labels", v)
                "--dns" -> addL("dns", v)
                "--dns-search" -> addL("dns_search", v)
                "--add-host" -> addL("extra_hosts", v)
                "--expose" -> addL("expose", v)
                "--link" -> addL("links", v)
                "--tmpfs" -> addL("tmpfs", v)
                "--security-opt" -> addL("security_opt", v)
                "--sysctl" -> v.split("=", limit = 2).let { maps.getOrPut("sysctls") { LinkedHashMap() }[it[0]] = it.getOrElse(1) { "" } }
                "--log-driver" -> maps.getOrPut("logging") { LinkedHashMap() }["driver"] = v
                "--log-opt" -> addL("logging.options", v)
                "-m", "--memory" -> svc["mem_limit"] = v
                "--memory-reservation" -> svc["mem_reservation"] = v
                "--cpus" -> svc["cpus"] = v
                "--cpu-shares", "-c" -> svc["cpu_shares"] = v
                "--shm-size" -> svc["shm_size"] = v
                "--pid" -> svc["pid"] = v
                "--ipc" -> svc["ipc"] = v
                "--platform" -> svc["platform"] = v
                "--stop-signal" -> svc["stop_signal"] = v
                "--mac-address" -> svc["mac_address"] = v
                "--ulimit" -> v.split("=", limit = 2).let { maps.getOrPut("ulimits") { LinkedHashMap() }[it[0]] = it.getOrElse(1) { "" } }
                "--health-cmd" -> maps.getOrPut("healthcheck") { LinkedHashMap() }["test"] = v
                "--health-interval" -> maps.getOrPut("healthcheck") { LinkedHashMap() }["interval"] = v
                "--health-timeout" -> maps.getOrPut("healthcheck") { LinkedHashMap() }["timeout"] = v
                "--health-retries" -> maps.getOrPut("healthcheck") { LinkedHashMap() }["retries"] = v
                "--gpus" -> warnings += "--gpus $v"
                else -> warnings += "$flag $v"
            }
        }
        image ?: throw IllegalArgumentException("No image specified")
        val command = args.drop(i)
        val name = (svc["container_name"] as? String) ?: image.substringAfterLast('/').substringBefore(':').substringBefore('@')
            .replace(Regex("[^a-zA-Z0-9_-]"), "-").ifEmpty { "app" }

        fun q(s: String): String = if (s.isEmpty() || s.any { it in ":{}[],&*#?|<>=!%@`'\"\\" } || s.first().isWhitespace() || s.last().isWhitespace() ||
            s.lowercase() in YAML_RESERVED || s.toDoubleOrNull() != null) Json.quote(s) else s

        val sb = StringBuilder("services:\n  ${q(name)}:\n    image: ${q(image)}\n")
        svc.forEach { (k, v) -> sb.append("    $k: ${if (v is Boolean) v.toString() else q(v.toString())}\n") }
        val logOpts = lists.remove("logging.options")
        lists.forEach { (k, l) ->
            sb.append("    $k:\n"); l.forEach { sb.append("      - ${q(it)}\n") }
        }
        maps.forEach { (k, m) ->
            sb.append("    $k:\n"); m.forEach { (mk, mv) -> sb.append("      $mk: ${q(mv)}\n") }
            if (k == "logging" && logOpts != null) {
                sb.append("      options:\n"); logOpts.forEach { o -> o.split("=", limit = 2).let { sb.append("        ${it[0]}: ${q(it.getOrElse(1) { "" })}\n") } }
            }
        }
        if (logOpts != null && "logging" !in maps) {
            sb.append("    logging:\n      options:\n"); logOpts.forEach { o -> o.split("=", limit = 2).let { sb.append("        ${it[0]}: ${q(it.getOrElse(1) { "" })}\n") } }
        }
        if (command.isNotEmpty()) sb.append("    command: ${command.joinToString(" ") { q(it) }.let { if (command.size > 1) Json.stringify(command, 0) else it }}\n")
        (lists["networks"])?.filter { it !in setOf("host", "bridge", "none") && !it.startsWith("container:") }?.takeIf { it.isNotEmpty() }?.let { nets ->
            sb.append("networks:\n"); nets.forEach { sb.append("  ${q(it)}:\n    external: true\n") }
        }
        val namedVolumes = lists["volumes"]?.map { it.substringBefore(':') }?.filter { !it.startsWith("/") && !it.startsWith(".") && !it.startsWith("~") && !it.startsWith("$") && !it.contains("=") }
        namedVolumes?.takeIf { it.isNotEmpty() }?.let { vols -> sb.append("volumes:\n"); vols.distinct().forEach { sb.append("  ${q(it)}:\n") } }
        return Result(sb.toString().trimEnd(), warnings)
    }
}

@Composable
fun DockerRunToComposeTool() {
    var input by remember { mutableStateOf("docker run -p 80:80 -v /var/run/docker.sock:/tmp/docker.sock:ro --restart always --log-opt max-size=1g nginx") }
    val r = remember(input) { runCatching { DockerCompose.convert(input) } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/yaml")) { u ->
        val y = r.getOrNull()?.yaml
        if (u != null && y != null) writeUri(ctx, u, y.toByteArray())
    }
    ToolColumn {
        InputField(input, { input = it }, t("Your docker run command", "Tu comando docker run"), singleLine = false, minLines = 4, mono = true,
            error = r.exceptionOrNull()?.message)
        r.getOrNull()?.let { res ->
            OutputField(res.yaml, "docker-compose.yml", minLines = 8)
            if (res.warnings.isNotEmpty()) Panel(t("Not translatable / ignored options", "Opciones no traducibles / ignoradas")) {
                res.warnings.forEach { Text("• $it", style = MonoStyle) }
            }
            ButtonRow { Button(onClick = { save.launch("docker-compose.yml") }) { Text(t("Download docker-compose.yml", "Guardar docker-compose.yml")) } }
        }
    }
}

/* ======================= XML / YAML formatters ======================= */

@Composable
fun XmlFormatterTool() {
    var input by remember { mutableStateOf("<hello><world>foo</world><world>bar</world></hello>") }
    var collapse by remember { mutableStateOf(true) }
    var indent by remember { mutableIntStateOf(2) }
    val r = remember(input, collapse, indent) { if (input.isBlank()) Result.success("") else runCatching { XmlFmt.format(input, indent, collapse) } }
    ToolColumn {
        Panel {
            SwitchRow(t("Collapse content", "Contraer contenido"), collapse) { collapse = it }
            SliderRow(t("Indent size", "Tamaño de sangría"), indent, 0..10) { indent = it }
        }
        InputField(input, { input = it }, t("Your XML", "Tu XML"), singleLine = false, minLines = 8, mono = true,
            error = r.exceptionOrNull()?.let { t("Provided XML is not valid.", "El XML no es válido.") + " " + (it.message ?: "") })
        OutputField(r.getOrDefault(""), t("Formatted XML from your XML", "XML formateado"), minLines = 8)
    }
}

@Composable
fun YamlPrettifyTool() {
    var input by remember { mutableStateOf("\"users\":\n  - \"name\": \"John\"\n    \"age\": 30\n  - \"name\": \"Jane\"\n    \"age\": 25\n") }
    var sort by remember { mutableStateOf(false) }
    var indent by remember { mutableIntStateOf(2) }
    val r = remember(input, sort, indent) { if (input.isBlank()) Result.success("") else runCatching { Yaml.stringify(Yaml.parse(input), indent, sort) } }
    ToolColumn {
        Panel {
            SwitchRow(t("Sort keys", "Ordenar claves"), sort) { sort = it }
            SliderRow(t("Indent size", "Tamaño de sangría"), indent, 1..10) { indent = it }
        }
        InputField(input, { input = it }, t("Your raw YAML", "Tu YAML"), singleLine = false, minLines = 8, mono = true,
            error = r.exceptionOrNull()?.message?.let { t("Invalid YAML: ", "YAML no válido: ") + it.take(300) })
        OutputField(r.getOrDefault(""), t("Prettified version of your YAML", "Versión formateada de tu YAML"), minLines = 8)
    }
}

/* ======================= Email normalizer ======================= */

object EmailNormalizer {
    private val GMAIL = setOf("gmail.com", "googlemail.com")
    private val PLUS = setOf("outlook.com", "hotmail.com", "live.com", "msn.com", "icloud.com", "me.com", "mac.com", "protonmail.com", "proton.me", "pm.me", "fastmail.com", "yandex.ru", "yandex.com")
    fun normalize(email: String): String {
        val e = email.trim().lowercase()
        val at = e.lastIndexOf('@')
        if (at <= 0 || at == e.lastIndex) throw IllegalArgumentException("Invalid email")
        var local = e.substring(0, at); var domain = e.substring(at + 1)
        when {
            domain in GMAIL -> { local = local.substringBefore('+').replace(".", ""); domain = "gmail.com" }
            domain in PLUS -> local = local.substringBefore('+')
            domain in setOf("yahoo.com", "ymail.com", "rocketmail.com") -> local = local.substringBefore('-')
        }
        return "$local@$domain"
    }
}

@Composable
fun EmailNormalizerTool() {
    var input by remember { mutableStateOf("") }
    val out = remember(input) {
        input.lines().filter { it.isNotBlank() }.joinToString("\n") { l ->
            runCatching { EmailNormalizer.normalize(l) }.getOrElse { "Unable to parse email: ${l.trim()}" }
        }
    }
    ToolColumn {
        InputField(input, { input = it }, t("Raw emails to normalize (one per line)", "Emails a normalizar (uno por línea)"),
            singleLine = false, minLines = 6, mono = true, placeholder = "John.Doe+tag@gmail.com")
        OutputField(out, t("Normalized emails", "Emails normalizados"), minLines = 6)
    }
}

/* ======================= Regex tester ======================= */

@Composable
fun RegexTesterTool() {
    var pattern by remember { mutableStateOf("(?<word>\\w+)@(\\w+)\\.com") }
    var sample by remember { mutableStateOf("contact: alice@example.com, bob@test.com") }
    var global by remember { mutableStateOf(true) }
    var ignoreCase by remember { mutableStateOf(false) }
    var multiline by remember { mutableStateOf(false) }
    var dotAll by remember { mutableStateOf(false) }
    val regex = remember(pattern, ignoreCase, multiline, dotAll) {
        runCatching {
            val opts = buildSet {
                if (ignoreCase) add(RegexOption.IGNORE_CASE)
                if (multiline) add(RegexOption.MULTILINE)
                if (dotAll) add(RegexOption.DOT_MATCHES_ALL)
            }
            Regex(pattern, opts)
        }
    }
    val matches = remember(regex, sample, global) {
        regex.getOrNull()?.let { r -> if (global) r.findAll(sample).take(1000).toList() else listOfNotNull(r.find(sample)) } ?: emptyList()
    }
    val groupNames = remember(pattern) { Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").findAll(pattern).map { it.groupValues[1] }.toList() }
    val hl = MaterialTheme.colorScheme.primaryContainer
    ToolColumn {
        InputField(pattern, { pattern = it }, t("Regex to test", "Expresión regular"), mono = true,
            error = regex.exceptionOrNull()?.message?.lineSequence()?.firstOrNull())
        Panel {
            SwitchRow(t("Global search (g)", "Búsqueda global (g)"), global) { global = it }
            SwitchRow(t("Case-insensitive (i)", "Ignorar mayúsculas (i)"), ignoreCase) { ignoreCase = it }
            SwitchRow(t("Multiline (m)", "Multilínea (m)"), multiline) { multiline = it }
            SwitchRow(t("Singleline / dotAll (s)", "Punto incluye salto de línea (s)"), dotAll) { dotAll = it }
        }
        InputField(sample, { sample = it }, t("Text to match", "Texto de prueba"), singleLine = false, minLines = 4, mono = true)
        Panel(t("Highlighted matches", "Coincidencias resaltadas")) {
            Text(buildAnnotatedString {
                var last = 0
                matches.filter { it.range.last >= it.range.first }.forEach { m ->
                    if (m.range.first < last) return@forEach
                    append(sample.substring(last, m.range.first))
                    withStyle(SpanStyle(background = hl, fontWeight = FontWeight.Bold)) { append(m.value) }
                    last = m.range.last + 1
                }
                append(sample.substring(last.coerceAtMost(sample.length)))
            }, style = MonoStyle)
        }
        Text(t("Matches: ", "Coincidencias: ") + matches.size, style = MaterialTheme.typography.titleSmall)
        matches.take(100).forEachIndexed { idx, m ->
            val rows = mutableListOf("#${idx + 1} [${m.range.first}-${m.range.last + 1}]" to m.value)
            m.groups.drop(1).forEachIndexed { gi, g -> rows += (t("Group", "Grupo") + " ${gi + 1}") to (g?.value ?: "") }
            groupNames.forEach { n -> runCatching { m.groups[n] }.getOrNull()?.let { rows += "<$n>" to it.value } }
            KeyValueTable(rows)
        }
    }
}

/* ======================= Regex cheatsheet ======================= */

private val REGEX_MEMO = listOf(
    MemoSection("Normal characters", "Caracteres normales", listOf(
        Triple("any character except newline", "cualquier carácter salvo salto de línea", "."),
        Triple("word character [A-Za-z0-9_] / non-word", "carácter de palabra [A-Za-z0-9_] / no palabra", "\\w  \\W"),
        Triple("digit [0-9] / non-digit", "dígito [0-9] / no dígito", "\\d  \\D"),
        Triple("whitespace / non-whitespace", "espacio en blanco / no espacio", "\\s  \\S"),
        Triple("any character in / not in brackets", "cualquier carácter del conjunto / fuera de él", "[abc]  [^abc]  [a-z]"),
    )),
    MemoSection("Anchors", "Anclas", listOf(
        Triple("start / end of string (or line with m flag)", "inicio / fin de texto (o línea con flag m)", "^  $"),
        Triple("word boundary / not word boundary", "límite de palabra / no límite", "\\b  \\B"),
    )),
    MemoSection("Escaped characters", "Caracteres escapados", listOf(
        Triple("tab, line feed, carriage return", "tabulador, salto de línea, retorno de carro", "\\t  \\n  \\r"),
        Triple("unicode / hex character", "carácter unicode / hexadecimal", "\\u00A9  \\x41"),
        Triple("escaped special characters", "caracteres especiales escapados", "\\.  \\*  \\\\  \\+  \\?  \\("),
    )),
    MemoSection("Groups & lookaround", "Grupos y aserciones", listOf(
        Triple("capture group", "grupo de captura", "(abc)"),
        Triple("named capture group", "grupo con nombre", "(?<name>abc)"),
        Triple("backreference to group #1 / named", "referencia al grupo 1 / con nombre", "\\1  \\k<name>"),
        Triple("non-capturing group", "grupo sin captura", "(?:abc)"),
        Triple("positive / negative lookahead", "lookahead positivo / negativo", "(?=abc)  (?!abc)"),
        Triple("positive / negative lookbehind", "lookbehind positivo / negativo", "(?<=abc)  (?<!abc)"),
    )),
    MemoSection("Quantifiers & alternation", "Cuantificadores y alternancia", listOf(
        Triple("0 or more, 1 or more, 0 or 1", "0 o más, 1 o más, 0 o 1", "a*  a+  a?"),
        Triple("exactly 5, 2 or more, between 1 and 3", "exactamente 5, 2 o más, entre 1 y 3", "a{5}  a{2,}  a{1,3}"),
        Triple("lazy (match as few as possible)", "perezoso (lo mínimo posible)", "a+?  a{2,}?"),
        Triple("match ab or cd", "coincide con ab o cd", "ab|cd"),
    )),
    MemoSection("Flags", "Modificadores", listOf(
        Triple("global, case-insensitive, multiline, dotAll, unicode", "global, sin mayúsculas, multilínea, dotAll, unicode", "/pattern/gimsu"),
    )),
)

@Composable
fun RegexMemoTool() = Memo(REGEX_MEMO)
