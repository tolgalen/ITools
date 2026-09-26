package com.itools.app.util

import android.util.Xml
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.schema.CoreSchema
import org.tomlj.Toml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.math.BigDecimal

/* ============================== JSON ============================== */

class JsonException(msg: String) : Exception(msg)

/** Custom JSON parser: keeps key order and reports errors with their position. */
object Json {
    fun parse(text: String): Any? {
        val p = P(text); p.ws()
        val v = p.value(); p.ws()
        if (p.i < text.length) p.fail("Unexpected '${text[p.i]}'")
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun fail(msg: String): Nothing {
            val line = s.substring(0, i.coerceAtMost(s.length)).count { it == '\n' } + 1
            val col = i - s.lastIndexOf('\n', (i - 1).coerceAtLeast(0))
            throw JsonException("$msg (line $line, col $col)")
        }
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (i >= s.length) fail("Unexpected end of input")
            return when (val c = s[i]) {
                '{' -> obj(); '[' -> arr(); '"' -> str()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("Unexpected '$c'")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) fail("Invalid literal"); i += w.length; return v }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') fail("Expected property name")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') fail("Expected ':'"); i++; ws()
                m[k] = value(); ws()
                if (i >= s.length) fail("Unterminated object")
                when (s[i]) { ',' -> i++; '}' -> { i++; return m }; else -> fail("Expected ',' or '}'") }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws(); l += value(); ws()
                if (i >= s.length) fail("Unterminated array")
                when (s[i]) { ',' -> i++; ']' -> { i++; return l }; else -> fail("Expected ',' or ']'") }
            }
        }
        fun str(): String {
            val sb = StringBuilder(); i++
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("Bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { if (i + 4 > s.length) fail("Bad unicode escape"); sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val raw = s.substring(st, i)
            return raw.toLongOrNull() ?: if (raw.any { it in ".eE" }) (raw.toBigDecimalOrNull() ?: fail("Bad number"))
            else (raw.toBigIntegerOrNull() ?: fail("Bad number"))
        }
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r")
            '\t' -> append("\\t"); '\b' -> append("\\b"); '\u000c' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private fun num(n: Number): String = when (n) {
        is BigDecimal -> n.toString()
        is Double -> if (n % 1.0 == 0.0 && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()
        is Float -> num(n.toDouble())
        else -> n.toString()
    }

    /** indent = 0 → minified. */
    fun stringify(v: Any?, indent: Int = 2, sortKeys: Boolean = false): String {
        val sb = StringBuilder()
        val nl = if (indent > 0) "\n" else ""
        val colon = if (indent > 0) ": " else ":"
        fun pad(level: Int) { repeat(indent * level) { sb.append(' ') } }
        fun w(x: Any?, level: Int) {
            when (x) {
                null -> sb.append("null")
                is String -> sb.append(quote(x))
                is Boolean -> sb.append(x)
                is Number -> sb.append(num(x))
                is Map<*, *> -> {
                    if (x.isEmpty()) { sb.append("{}"); return }
                    val entries = if (sortKeys) x.entries.sortedBy { it.key.toString() } else x.entries.toList()
                    sb.append("{").append(nl)
                    entries.forEachIndexed { idx, (k, vv) ->
                        pad(level + 1); sb.append(quote(k.toString())).append(colon)
                        w(vv, level + 1)
                        if (idx < entries.lastIndex) sb.append(",")
                        sb.append(nl)
                    }
                    pad(level); sb.append("}")
                }
                is Iterable<*> -> {
                    val l = x.toList()
                    if (l.isEmpty()) { sb.append("[]"); return }
                    sb.append("[").append(nl)
                    l.forEachIndexed { idx, vv ->
                        pad(level + 1); w(vv, level + 1)
                        if (idx < l.lastIndex) sb.append(",")
                        sb.append(nl)
                    }
                    pad(level); sb.append("]")
                }
                is Array<*> -> w(x.toList(), level)
                else -> sb.append(quote(x.toString()))
            }
        }
        w(v, 0)
        return sb.toString()
    }
}

/* ============================== YAML ============================== */

object Yaml {
    private fun loader() = Load(LoadSettings.builder().setSchema(CoreSchema()).build())
    fun parse(text: String): Any? = loader().loadFromString(text)
    fun parseAll(text: String): List<Any?> = loader().loadAllFromString(text).toList()

    fun stringify(v: Any?, indent: Int = 2, sortKeys: Boolean = false): String {
        val settings = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(indent.coerceIn(1, 10))
            .setWidth(Int.MAX_VALUE)
            .build()
        return Dump(settings).dumpToString(normalize(v, sortKeys))
    }

    /** Converts BigDecimal to Double and sorts keys if requested (snakeyaml cannot represent BigDecimal). */
    fun normalize(v: Any?, sortKeys: Boolean = false): Any? = when (v) {
        is Map<*, *> -> {
            val e = if (sortKeys) v.entries.sortedBy { it.key.toString() } else v.entries.toList()
            LinkedHashMap<Any?, Any?>().apply { e.forEach { (k, x) -> put(k, normalize(x, sortKeys)) } }
        }
        is Iterable<*> -> v.map { normalize(it, sortKeys) }
        is BigDecimal -> v.toDouble()
        else -> v
    }
}

/* ============================== TOML ============================== */

object TomlFmt {
    fun parse(text: String): Any? {
        val r = Toml.parse(text)
        if (r.hasErrors()) throw IllegalArgumentException(r.errors().joinToString("\n") { it.toString() })
        return Json.parse(r.toJson())
    }

    private val bare = Regex("^[A-Za-z0-9_-]+$")
    private fun key(k: String) = if (bare.matches(k)) k else Json.quote(k)

    private fun inline(v: Any?): String = when (v) {
        null -> "\"\""
        is String -> Json.quote(v)
        is Boolean, is Number -> if (v is BigDecimal) v.toPlainString() else v.toString()
        is Map<*, *> -> v.entries.filter { it.value != null }.joinToString(", ", "{ ", " }") { "${key(it.key.toString())} = ${inline(it.value)}" }
        is Iterable<*> -> v.joinToString(", ", "[ ", " ]") { inline(it) }
        else -> Json.quote(v.toString())
    }

    private fun isTableArray(v: Any?) = v is Iterable<*> && v.any() && v.all { it is Map<*, *> }

    fun stringify(root: Any?): String {
        require(root is Map<*, *>) { "TOML root must be an object" }
        val sb = StringBuilder()
        fun table(m: Map<*, *>, path: List<String>) {
            val simple = m.entries.filter { it.value != null && it.value !is Map<*, *> && !isTableArray(it.value) }
            simple.forEach { (k, v) -> sb.append(key(k.toString())).append(" = ").append(inline(v)).append('\n') }
            m.entries.filter { it.value is Map<*, *> }.forEach { (k, v) ->
                val p = path + key(k.toString())
                val sub = v as Map<*, *>
                // Only emit a header if the table has simple values or is empty
                val hasSimple = sub.values.any { it != null && it !is Map<*, *> && !isTableArray(it) } || sub.isEmpty()
                if (hasSimple) { if (sb.isNotEmpty()) sb.append('\n'); sb.append('[').append(p.joinToString(".")).append("]\n") }
                table(sub, p)
            }
            m.entries.filter { isTableArray(it.value) }.forEach { (k, v) ->
                val p = path + key(k.toString())
                (v as Iterable<*>).forEach { item ->
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append("[[").append(p.joinToString(".")).append("]]\n")
                    table(item as Map<*, *>, p)
                }
            }
        }
        table(root, emptyList())
        return sb.toString().trimEnd() + "\n"
    }
}

/* ============================== XML ============================== */

object XmlFmt {
    /** XML → compact object in xml-js style (_attributes, _text, _cdata, _comment). */
    fun toTree(xml: String): Map<String, Any?> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val root = LinkedHashMap<String, Any?>()
        val stack = ArrayDeque<LinkedHashMap<String, Any?>>().apply { addLast(root) }
        fun add(target: MutableMap<String, Any?>, k: String, v: Any?) {
            val cur = target[k]
            target[k] = when {
                !target.containsKey(k) -> v
                cur is MutableList<*> -> (cur as MutableList<Any?>).apply { add(v) }
                else -> mutableListOf(cur, v)
            }
        }
        var ev = p.nextToken()
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val el = LinkedHashMap<String, Any?>()
                    if (p.attributeCount > 0) el["_attributes"] = LinkedHashMap<String, Any?>().apply {
                        for (i in 0 until p.attributeCount) put(p.getAttributeName(i), p.getAttributeValue(i))
                    }
                    add(stack.last(), p.name, el); stack.addLast(el)
                }
                XmlPullParser.END_TAG -> stack.removeLast()
                XmlPullParser.TEXT -> p.text.trim().takeIf { it.isNotEmpty() && stack.size > 1 }?.let { add(stack.last(), "_text", it) }
                XmlPullParser.CDSECT -> if (stack.size > 1) add(stack.last(), "_cdata", p.text)
                XmlPullParser.COMMENT -> add(stack.last(), "_comment", p.text.trim())
                XmlPullParser.ENTITY_REF -> if (stack.size > 1) add(stack.last(), "_text", p.text ?: "")
            }
            ev = p.nextToken()
        }
        return root
    }

    private fun esc(s: String, attr: Boolean = false) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .let { if (attr) it.replace("\"", "&quot;") else it }

    /** Compact object → indented XML. */
    fun fromTree(v: Any?, indent: Int = 2): String {
        val sb = StringBuilder()
        // A single local function: Kotlin does not allow mutual recursion between local functions
        fun node(name: String, value: Any?, level: Int) {
            val pad = " ".repeat(indent * level)
            when (name) {
                "_text" -> { sb.append(pad).append(esc(value.toString())).append('\n'); return }
                "_cdata" -> { sb.append("$pad<![CDATA[$value]]>\n"); return }
                "_comment" -> { sb.append("$pad<!--$value-->\n"); return }
                "_declaration" -> return
            }
            when (value) {
                is Iterable<*> -> value.forEach { node(name, it, level) }
                is Map<*, *> -> {
                    val attrs = (value["_attributes"] as? Map<*, *>)?.entries?.joinToString("") { " ${it.key}=\"${esc(it.value.toString(), true)}\"" } ?: ""
                    val children = value.entries.filter { it.key != "_attributes" }
                    if (children.isEmpty()) { sb.append("$pad<$name$attrs/>\n"); return }
                    val onlyText = children.all { it.key == "_text" }
                    if (onlyText) { sb.append("$pad<$name$attrs>${esc(children.joinToString("") { it.value.toString() })}</$name>\n"); return }
                    sb.append("$pad<$name$attrs>\n")
                    children.forEach { (k, c) -> node(k.toString(), c, level + 1) }
                    sb.append("$pad</$name>\n")
                }
                null -> sb.append("$pad<$name/>\n")
                else -> sb.append("$pad<$name>${esc(value.toString())}</$name>\n")
            }
        }
        require(v is Map<*, *>) { "Root must be an object" }
        v.forEach { (k, c) -> node(k.toString(), c, 0) }
        return sb.toString().trimEnd()
    }

    /** Re-indents XML, keeping comments, CDATA and processing instructions. */
    fun format(xml: String, indent: Int = 2, collapse: Boolean = true): String {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val sb = StringBuilder()
        var level = 0
        var openTagPending = false   // last opened tag, still without children
        var pendingText: String? = null
        val decl = Regex("^\\s*<\\?xml[^>]*\\?>").find(xml)?.value?.trim()
        if (decl != null) sb.append(decl).append('\n')
        fun pad() = " ".repeat(indent * level)
        fun flushOpen() { if (openTagPending) { sb.append(">"); if (pendingText == null) sb.append('\n'); openTagPending = false } }
        var ev = p.nextToken()
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    if (openTagPending) { sb.append(">\n"); openTagPending = false }
                    pendingText?.let { sb.append(pad()).append(esc(it)).append('\n'); pendingText = null }
                    sb.append(pad()).append('<').append(p.name)
                    for (i in 0 until p.attributeCount) sb.append(' ').append(p.getAttributeName(i)).append("=\"").append(esc(p.getAttributeValue(i), true)).append('"')
                    openTagPending = true; level++
                }
                XmlPullParser.END_TAG -> {
                    level--
                    when {
                        openTagPending && pendingText == null -> { sb.append("/>\n"); openTagPending = false }
                        openTagPending && collapse -> { sb.append('>').append(esc(pendingText!!)).append("</").append(p.name).append(">\n"); openTagPending = false; pendingText = null }
                        else -> {
                            if (openTagPending) { sb.append(">\n"); openTagPending = false }
                            pendingText?.let { sb.append(" ".repeat(indent * (level + 1))).append(esc(it)).append('\n'); pendingText = null }
                            sb.append(pad()).append("</").append(p.name).append(">\n")
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> {
                    val txt = if (ev == XmlPullParser.ENTITY_REF) p.text ?: "" else p.text.trim()
                    if (txt.isNotEmpty()) pendingText = (pendingText ?: "") + txt
                }
                XmlPullParser.CDSECT, XmlPullParser.COMMENT, XmlPullParser.PROCESSING_INSTRUCTION, XmlPullParser.DOCDECL -> {
                    val raw = when (ev) {
                        XmlPullParser.CDSECT -> "<![CDATA[${p.text}]]>"
                        XmlPullParser.COMMENT -> "<!--${p.text}-->"
                        XmlPullParser.DOCDECL -> "<!DOCTYPE${p.text}>"
                        else -> if (p.text.startsWith("xml ")) null else "<?${p.text}?>"
                    }
                    if (raw != null) {
                        if (openTagPending) { sb.append(">\n"); openTagPending = false }
                        pendingText?.let { sb.append(pad()).append(esc(it)).append('\n'); pendingText = null }
                        sb.append(pad()).append(raw).append('\n')
                    }
                }
            }
            ev = p.nextToken()
        }
        return sb.toString().trimEnd()
    }
}

/* ============================== CSV ============================== */

object Csv {
    private fun cell(v: Any?): String {
        val s = when (v) { null -> ""; is Map<*, *>, is Iterable<*> -> Json.stringify(v, 0); else -> v.toString() }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    fun fromJson(v: Any?): String {
        val rows: List<Map<*, *>> = when (v) {
            is Map<*, *> -> listOf(v)
            is Iterable<*> -> v.map { it as? Map<*, *> ?: mapOf("value" to it) }
            else -> throw IllegalArgumentException("Expected an array of objects")
        }
        val headers = LinkedHashSet<String>().apply { rows.forEach { r -> r.keys.forEach { add(it.toString()) } } }
        return buildString {
            append(headers.joinToString(",") { cell(it) }).append('\n')
            rows.forEach { r -> append(headers.joinToString(",") { cell(r[it]) }).append('\n') }
        }.trimEnd()
    }
}
