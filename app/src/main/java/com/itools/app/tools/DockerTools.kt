package com.itools.app.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.itools.app.ui.components.*
import com.itools.app.util.Json
import com.itools.app.util.Yaml
import com.itools.app.util.writeUri

/* ======================= Compose file format ======================= */

/** Compose file format version; `null` everywhere means the Compose Specification (no top-level `version` key). */
data class ComposeVersion(val major: Int, val minor: Int) : Comparable<ComposeVersion> {
    override fun compareTo(other: ComposeVersion) = compareValuesBy(this, other, { it.major }, { it.minor })
    override fun toString() = "$major.$minor"

    companion object {
        val ALL: List<ComposeVersion> = (9 downTo 0).map { ComposeVersion(3, it) } + (4 downTo 0).map { ComposeVersion(2, it) }
    }
}

/* ======================= docker run → Compose ======================= */

object DockerCompose {
    /** Splits a shell line honoring quotes and "\" line continuations. */
    fun shellSplit(s: String): List<String> {
        val src = s.replace(LINE_CONTINUATION, " ")
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

    private val LINE_CONTINUATION = Regex("\\\\\\r?\\n")
    private val NON_NAME = Regex("[^a-zA-Z0-9_-]")
    private val NO_VALUE = setOf("-d", "--detach", "--rm", "-i", "--interactive", "-t", "--tty", "-it", "-ti", "--privileged", "--init", "--read-only", "-P", "--publish-all")
    private val NETWORK_MODES = setOf("host", "none", "bridge")
    private val YAML_RESERVED = setOf("true", "false", "yes", "no", "on", "off", "null", "~", "y", "n")

    data class Result(val yaml: String, val warnings: List<String>)

    /** Number-like text ("80:80", "0.5", "1e3") that YAML 1.1 parsers could read as a number (sexagesimal, float...). */
    private val NUMBER_LIKE = Regex("^[-+]?[0-9][0-9:._eE+-]*$")

    /** Quotes a YAML scalar only when a plain scalar would be misread. */
    private fun q(s: String): String = if (
        s.isEmpty() || s.first() in "?:,[]{}#&*!|>'\"%@`" || s == "-" || s.startsWith("- ") ||
        s.first().isWhitespace() || s.last().isWhitespace() || ": " in s || " #" in s || s.endsWith(":") ||
        s.any { it < ' ' } || s.lowercase() in YAML_RESERVED || NUMBER_LIKE.matches(s)
    ) Json.quote(s) else s

    private fun scalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Number -> v.toString()
        else -> q(v.toString())
    }

    /** Minimal block-style YAML writer with configurable indentation (maps, lists, list items that are maps). */
    fun toYaml(root: Map<String, Any?>, indent: Int): String {
        val sb = StringBuilder()
        fun pad(level: Int) = " ".repeat(indent * level)
        fun writeMap(m: Map<*, *>, level: Int) {
            m.forEach { (k, v) ->
                sb.append(pad(level)).append(q(k.toString())).append(':')
                when (v) {
                    null -> sb.append('\n')                                   // e.g. a named volume: "data:"
                    is Map<*, *> -> if (v.isEmpty()) sb.append(" {}\n") else { sb.append('\n'); writeMap(v, level + 1) }
                    is List<*> -> {
                        sb.append('\n')
                        v.forEach { item ->
                            if (item is Map<*, *>) {
                                // Map item: first entry after "- ", the rest aligned with it
                                val nested = toYaml(item.mapKeys { it.key.toString() }, indent).trimEnd().lines()
                                nested.forEachIndexed { i, line -> sb.append(pad(level + 1)).append(if (i == 0) "- " else "  ").append(line).append('\n') }
                            } else sb.append(pad(level + 1)).append("- ").append(scalar(item)).append('\n')
                        }
                    }
                    else -> sb.append(' ').append(scalar(v)).append('\n')
                }
            }
        }
        writeMap(root, 0)
        return sb.toString()
    }

    /** `--mount type=bind,source=/a,target=/b,readonly` → Compose long volume syntax. */
    private fun mount(spec: String, warnings: MutableList<String>): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        spec.split(',').filter { it.isNotBlank() }.forEach { part ->
            val k = part.substringBefore('=').trim(); val v = part.substringAfter('=', "").trim()
            when (k) {
                "type" -> m["type"] = v
                "source", "src" -> m["source"] = v
                "target", "destination", "dst" -> m["target"] = v
                "readonly", "ro" -> m["read_only"] = v.isEmpty() || v == "true" || v == "1"
                "volume-nocopy" -> m["volume"] = mapOf("nocopy" to true)
                else -> warnings += "--mount $k"
            }
        }
        return m
    }

    fun convert(cmd: String, version: ComposeVersion? = null, indent: Int = 2): Result {
        var args = shellSplit(cmd.trim())
        if (args.firstOrNull() == "sudo") args = args.drop(1)
        if (args.firstOrNull() == "docker") args = args.drop(1)
        if (args.firstOrNull() == "container") args = args.drop(1)
        if (args.firstOrNull() == "run") args = args.drop(1) else throw IllegalArgumentException("Command must start with 'docker run'")
        val svc = LinkedHashMap<String, Any?>()
        val lists = LinkedHashMap<String, MutableList<Any>>()
        val maps = LinkedHashMap<String, LinkedHashMap<String, Any?>>()
        val warnings = mutableListOf<String>()
        fun addL(k: String, v: Any) { lists.getOrPut(k) { mutableListOf() } += v }
        fun map(k: String) = maps.getOrPut(k) { LinkedHashMap() }
        fun keyValue(v: String) = v.substringBefore('=') to v.substringAfter('=', "")
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
                    "--rm", "-P", "--publish-all" -> warnings += flag
                }
                i++; continue
            }
            val v = inline ?: args.getOrNull(i + 1) ?: throw IllegalArgumentException("Missing value for $flag")
            i += if (inline != null) 1 else 2
            when (flag) {
                "--name" -> svc["container_name"] = v
                "-p", "--publish" -> addL("ports", v)
                "-v", "--volume" -> addL("volumes", v)
                "--mount" -> addL("volumes", mount(v, warnings))
                "-e", "--env" -> addL("environment", v)
                "--env-file" -> addL("env_file", v)
                "--network", "--net" -> if (v in NETWORK_MODES || v.startsWith("container:")) svc["network_mode"] = v else addL("networks", v)
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
                "--sysctl" -> keyValue(v).let { (k, x) -> map("sysctls")[k] = x }
                "--log-driver" -> map("logging")["driver"] = v
                "--log-opt" -> keyValue(v).let { (k, x) ->
                    @Suppress("UNCHECKED_CAST")
                    (map("logging").getOrPut("options") { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>)[k] = x
                }
                "-m", "--memory" -> svc["mem_limit"] = v
                "--memory-reservation" -> svc["mem_reservation"] = v
                "--cpus" -> svc["cpus"] = v.toDoubleOrNull() ?: v
                "--cpu-shares", "-c" -> svc["cpu_shares"] = v.toLongOrNull() ?: v
                "--shm-size" -> svc["shm_size"] = v
                "--pid" -> svc["pid"] = v
                "--ipc" -> svc["ipc"] = v
                "--platform" -> svc["platform"] = v
                "--stop-signal" -> svc["stop_signal"] = v
                "--mac-address" -> svc["mac_address"] = v
                "--ulimit" -> keyValue(v).let { (k, x) ->
                    val soft = x.substringBefore(':').toLongOrNull(); val hard = x.substringAfter(':', "").toLongOrNull()
                    map("ulimits")[k] = if (hard != null) linkedMapOf("soft" to soft, "hard" to hard) else soft ?: x
                }
                "--health-cmd" -> map("healthcheck")["test"] = v
                "--health-interval" -> map("healthcheck")["interval"] = v
                "--health-timeout" -> map("healthcheck")["timeout"] = v
                "--health-start-period" -> map("healthcheck")["start_period"] = v
                "--health-retries" -> map("healthcheck")["retries"] = v.toLongOrNull() ?: v
                else -> warnings += "$flag $v"
            }
        }
        image ?: throw IllegalArgumentException("No image specified")
        val command = args.drop(i)
        val name = (svc["container_name"] as? String) ?: image.substringAfterLast('/').substringBefore(':').substringBefore('@')
            .replace(NON_NAME, "-").ifEmpty { "app" }

        val service = LinkedHashMap<String, Any?>()
        service["image"] = image
        service.putAll(svc)
        lists.forEach { (k, l) -> service[k] = l }
        maps.forEach { (k, m) -> service[k] = m }
        if (command.isNotEmpty()) service["command"] = if (command.size == 1) command[0] else command
        adapt(service, version, warnings)

        val root = LinkedHashMap<String, Any?>()
        if (version != null) root["version"] = version.toString()
        root["services"] = mapOf(name to service)
        (lists["networks"])?.map { it.toString() }?.takeIf { it.isNotEmpty() }?.let { nets ->
            root["networks"] = nets.associateWith { mapOf("external" to true) }
        }
        // Named volumes (not paths) must be declared at the top level
        val named = lists["volumes"].orEmpty().mapNotNull { v ->
            when (v) {
                is String -> v.substringBefore(':')
                is Map<*, *> -> (v["source"] as? String)?.takeIf { v["type"] == "volume" }
                else -> null
            }
        }.filter { it.isNotEmpty() && !it.startsWith("/") && !it.startsWith(".") && !it.startsWith("~") && !it.startsWith("$") && !it.contains("=") }
        if (named.isNotEmpty()) root["volumes"] = named.distinct().associateWith { null }
        return Result(toYaml(root, indent).trimEnd(), warnings)
    }

    /** Removes or moves the keys that the selected file format does not support. */
    private fun adapt(svc: MutableMap<String, Any?>, version: ComposeVersion?, warnings: MutableList<String>) {
        if (version == null) return
        fun drop(key: String, needs: String) { if (svc.remove(key) != null) warnings += "$key: requires $needs (removed)" }
        @Suppress("UNCHECKED_CAST") val health = svc["healthcheck"] as? MutableMap<String, Any?>
        fun below(major: Int, minor: Int) = version < ComposeVersion(major, minor)
        if (version.major == 2) {
            if (below(2, 1)) { drop("healthcheck", "2.1+"); drop("sysctls", "2.1+") }
            else if (below(2, 3) && health?.remove("start_period") != null) warnings += "healthcheck.start_period: requires 2.3+ (removed)"
            if (below(2, 2)) { drop("init", "2.2+"); drop("cpus", "2.2+") }
            if (below(2, 4)) drop("platform", "2.4+")
        } else {
            if (below(3, 4) && health?.remove("start_period") != null) warnings += "healthcheck.start_period: requires 3.4+ (removed)"
            if (below(3, 5)) drop("shm_size", "3.5+")
            if (below(3, 7)) drop("init", "3.7+")
            drop("platform", "2.4 / Compose Specification")
            drop("cpu_shares", "2.x / Compose Specification")
            // In 3.x resource limits live under deploy.resources (cpus as a string)
            val limits = LinkedHashMap<String, Any?>(); val reservations = LinkedHashMap<String, Any?>()
            svc.remove("cpus")?.let { limits["cpus"] = it.toString() }
            svc.remove("mem_limit")?.let { limits["memory"] = it }
            svc.remove("mem_reservation")?.let { reservations["memory"] = it }
            if (limits.isNotEmpty() || reservations.isNotEmpty()) {
                svc["deploy"] = mapOf("resources" to buildMap {
                    if (limits.isNotEmpty()) put("limits", limits)
                    if (reservations.isNotEmpty()) put("reservations", reservations)
                })
                warnings += "deploy.resources: docker-compose v1 only applies it with --compatibility"
            }
        }
    }
}

/* ======================= Compose → docker run ======================= */

object ComposeToRun {
    data class Result(val script: String, val warnings: List<String>)

    private val SAFE = Regex("^[A-Za-z0-9_./:=@%+,-]+$")
    private val DURATION = Regex("^(\\d+)(s|m)?$")
    /** Keys that have no docker run equivalent (reported as warnings). */
    private val UNSUPPORTED = setOf("build", "depends_on", "profiles", "scale", "configs", "secrets", "extends", "develop", "post_start", "pre_stop", "pull_policy")

    /** Quotes a value for a POSIX shell only when it is needed. */
    fun shq(s: String): String = if (SAFE.matches(s)) s else "'" + s.replace("'", "'\\''") + "'"

    private fun str(v: Any?): String = when (v) {
        null -> ""
        is String, is Number, is Boolean -> v.toString()
        else -> Json.stringify(v, 0)
    }

    private fun list(v: Any?): List<String> = when (v) {
        null -> emptyList()
        is List<*> -> v.map(::str)
        else -> listOf(str(v))
    }

    /** "KEY=VALUE" items from either a list or a map (a null map value gives just "KEY"). */
    private fun pairs(v: Any?, sep: String = "="): List<String> = when (v) {
        is Map<*, *> -> v.map { (k, x) -> if (x == null) k.toString() else "$k$sep${str(x)}" }
        else -> list(v)
    }

    /** Services ordered so that dependencies (depends_on) come first. */
    private fun ordered(services: Map<String, Map<*, *>>): List<String> {
        val out = LinkedHashSet<String>(); val visiting = HashSet<String>()
        fun visit(n: String) {
            if (n in out || !visiting.add(n)) return
            val deps = services[n]?.get("depends_on").let { if (it is Map<*, *>) it.keys.map(Any?::toString) else list(it) }
            deps.filter { it in services }.forEach(::visit)
            out += n
        }
        services.keys.forEach(::visit)
        return out.toList()
    }

    fun convert(yaml: String, indent: Int = 2, detached: Boolean = true): Result {
        val root = Yaml.parse(yaml) as? Map<*, *> ?: throw IllegalArgumentException("Not a Compose file")
        @Suppress("UNCHECKED_CAST")
        val services = ((root["services"] as? Map<*, *>)
            // Legacy version 1 files have the services at the top level
            ?: root.takeIf { r -> r.isNotEmpty() && r.values.all { it is Map<*, *> && ("image" in it || "build" in it) } })
            ?.entries?.associate { (k, v) -> k.toString() to (v as? Map<*, *> ?: emptyMap<Any, Any>()) }
            ?: throw IllegalArgumentException("No services found")
        if (services.isEmpty()) throw IllegalArgumentException("No services found")
        val warnings = mutableListOf<String>()
        val pad = " ".repeat(indent)
        val out = StringBuilder()

        // Networks declared in the file (not external) must exist before docker run
        val topNetworks = root["networks"] as? Map<*, *>
        val toCreate = topNetworks?.filter { (_, cfg) -> (cfg as? Map<*, *>)?.get("external") != true }?.keys?.map { it.toString() }.orEmpty()
        toCreate.forEach { out.append("docker network create ").append(shq(it)).append('\n') }
        if (toCreate.isNotEmpty()) out.append('\n')

        ordered(services).forEach { name ->
            val s = services.getValue(name)
            val image = s["image"]?.let(::str)
            if (image == null) { warnings += "$name: no image, skipped (docker run cannot build)"; return@forEach }
            val flags = mutableListOf<String>()
            fun add(flag: String, value: String) { flags += "$flag ${shq(value)}" }
            fun flag(flag: String, on: Any?) { if (on == true) flags += flag }
            var entryArgs = emptyList<String>()
            s.forEach { (keyAny, v) ->
                val key = keyAny.toString()
                when (key) {
                    "image" -> {}
                    "container_name" -> {}
                    "restart" -> add("--restart", str(v))
                    "working_dir" -> add("-w", str(v))
                    "user" -> add("-u", str(v))
                    "hostname" -> add("-h", str(v))
                    "domainname" -> add("--domainname", str(v))
                    "entrypoint" -> {
                        // docker run --entrypoint only takes the executable; the rest goes before the command
                        val parts = if (v is List<*>) v.map(::str) else DockerCompose.shellSplit(str(v))
                        parts.firstOrNull()?.let { add("--entrypoint", it) }
                        entryArgs = parts.drop(1)
                    }
                    "command" -> {}
                    "ports" -> (v as? List<*>).orEmpty().forEach { p ->
                        if (p is Map<*, *>) {
                            val published = p["published"]?.let(::str); val target = str(p["target"])
                            val hostIp = p["host_ip"]?.let(::str); val proto = p["protocol"]?.let(::str)
                            add("-p", listOfNotNull(hostIp, published, target).joinToString(":") + (proto?.let { "/$it" } ?: ""))
                        } else add("-p", str(p))
                    }
                    "expose" -> list(v).forEach { add("--expose", it) }
                    "volumes" -> (v as? List<*>).orEmpty().forEach { vol ->
                        if (vol is Map<*, *>) {
                            val parts = mutableListOf("type=${str(vol["type"] ?: "volume")}")
                            vol["source"]?.let { parts += "source=${str(it)}" }
                            vol["target"]?.let { parts += "target=${str(it)}" }
                            if (vol["read_only"] == true) parts += "readonly"
                            add("--mount", parts.joinToString(","))
                        } else add("-v", str(vol))
                    }
                    "tmpfs" -> list(v).forEach { add("--tmpfs", it) }
                    "environment" -> pairs(v).forEach { add("-e", it) }
                    "env_file" -> (if (v is List<*>) v else listOf(v)).forEach { f ->
                        add("--env-file", if (f is Map<*, *>) str(f["path"]) else str(f))
                    }
                    "networks" -> (if (v is Map<*, *>) v.keys.map { it.toString() } else list(v)).forEach { add("--network", it) }
                    "network_mode" -> add("--network", str(v))
                    "cap_add" -> list(v).forEach { add("--cap-add", it) }
                    "cap_drop" -> list(v).forEach { add("--cap-drop", it) }
                    "devices" -> list(v).forEach { add("--device", it) }
                    "labels" -> pairs(v).forEach { add("-l", it) }
                    "dns" -> list(v).forEach { add("--dns", it) }
                    "dns_search" -> list(v).forEach { add("--dns-search", it) }
                    "dns_opt" -> list(v).forEach { add("--dns-option", it) }
                    "extra_hosts" -> pairs(v, ":").forEach { add("--add-host", it.replaceFirst('=', ':')) }
                    "links" -> list(v).forEach { add("--link", it) }
                    "security_opt" -> list(v).forEach { add("--security-opt", it) }
                    "sysctls" -> pairs(v).forEach { add("--sysctl", it) }
                    "group_add" -> list(v).forEach { add("--group-add", it) }
                    "logging" -> (v as? Map<*, *>)?.let { l ->
                        l["driver"]?.let { add("--log-driver", str(it)) }
                        pairs(l["options"]).forEach { add("--log-opt", it) }
                    }
                    "mem_limit" -> add("-m", str(v))
                    "memswap_limit" -> add("--memory-swap", str(v))
                    "mem_reservation" -> add("--memory-reservation", str(v))
                    "cpus" -> add("--cpus", str(v))
                    "cpu_shares" -> add("--cpu-shares", str(v))
                    "cpuset" -> add("--cpuset-cpus", str(v))
                    "shm_size" -> add("--shm-size", str(v))
                    "pid" -> add("--pid", str(v))
                    "ipc" -> add("--ipc", str(v))
                    "platform" -> add("--platform", str(v))
                    "runtime" -> add("--runtime", str(v))
                    "userns_mode" -> add("--userns", str(v))
                    "stop_signal" -> add("--stop-signal", str(v))
                    "stop_grace_period" -> DURATION.find(str(v))?.let { m ->
                        val secs = m.groupValues[1].toLong() * (if (m.groupValues[2] == "m") 60 else 1)
                        add("--stop-timeout", secs.toString())
                    } ?: run { warnings += "$name.stop_grace_period: ${str(v)}" }
                    "mac_address" -> add("--mac-address", str(v))
                    "oom_score_adj" -> add("--oom-score-adj", str(v))
                    "oom_kill_disable" -> flag("--oom-kill-disable", v)
                    "ulimits" -> (v as? Map<*, *>)?.forEach { (k, x) ->
                        add("--ulimit", "$k=" + if (x is Map<*, *>) "${str(x["soft"])}:${str(x["hard"])}" else str(x))
                    }
                    "healthcheck" -> (v as? Map<*, *>)?.let { h ->
                        val test = h["test"]
                        when {
                            h["disable"] == true -> flags += "--no-healthcheck"
                            test is List<*> && test.firstOrNull() == "NONE" -> flags += "--no-healthcheck"
                            test is List<*> && test.firstOrNull() == "CMD-SHELL" -> add("--health-cmd", test.drop(1).joinToString(" ", transform = ::str))
                            test is List<*> && test.firstOrNull() == "CMD" -> add("--health-cmd", test.drop(1).joinToString(" ") { shq(str(it)) })
                            test != null -> add("--health-cmd", str(test))
                        }
                        h["interval"]?.let { add("--health-interval", str(it)) }
                        h["timeout"]?.let { add("--health-timeout", str(it)) }
                        h["start_period"]?.let { add("--health-start-period", str(it)) }
                        h["retries"]?.let { add("--health-retries", str(it)) }
                    }
                    "deploy" -> (v as? Map<*, *>)?.let { d ->
                        val res = d["resources"] as? Map<*, *>
                        (res?.get("limits") as? Map<*, *>)?.let { l ->
                            l["memory"]?.let { add("-m", str(it)) }; l["cpus"]?.let { add("--cpus", str(it)) }
                        }
                        (res?.get("reservations") as? Map<*, *>)?.get("memory")?.let { add("--memory-reservation", str(it)) }
                        (d.keys - "resources").forEach { warnings += "$name.deploy.$it (Swarm only)" }
                    }
                    "privileged" -> flag("--privileged", v)
                    "init" -> flag("--init", v)
                    "read_only" -> flag("--read-only", v)
                    "stdin_open" -> flag("-i", v)
                    "tty" -> flag("-t", v)
                    else -> if (!key.startsWith("x-")) warnings += "$name.$key" + if (key in UNSUPPORTED) " (no docker run equivalent)" else " (unknown key)"
                }
            }
            val cmd = s["command"].let { c ->
                when (c) {
                    null -> entryArgs.map(::shq)
                    is List<*> -> (entryArgs + c.map(::str)).map(::shq)
                    else -> entryArgs.map(::shq) + str(c)   // a string command is already shell syntax
                }
            }
            out.append("# ").append(name).append('\n')
            out.append("docker run").append(if (detached) " -d" else "").append(" \\\n")
            out.append(pad).append("--name ").append(shq(str(s["container_name"] ?: name))).append(" \\\n")
            flags.forEach { out.append(pad).append(it).append(" \\\n") }
            out.append(pad).append(shq(image)).append(if (cmd.isEmpty()) "" else " " + cmd.joinToString(" ")).append("\n\n")
        }
        return Result(out.toString().trimEnd(), warnings)
    }
}

/* ======================= Docker run ⇄ Compose converter ======================= */

private const val SAMPLE_RUN = "docker run -d --name web -p 8080:80 -v web-data:/usr/share/nginx/html:ro \\\n" +
    "  -e TZ=Europe/Madrid --restart unless-stopped --memory 512m --cpus 0.5 \\\n  --health-cmd 'curl -f http://localhost/' --health-retries 3 nginx:alpine"

private const val SAMPLE_COMPOSE = "services:\n  db:\n    image: postgres:16\n    environment:\n      POSTGRES_PASSWORD: secret\n    volumes:\n      - db-data:/var/lib/postgresql/data\n" +
    "  web:\n    image: nginx:alpine\n    depends_on:\n      - db\n    ports:\n      - \"8080:80\"\n    restart: unless-stopped\n    networks:\n      - front\n" +
    "networks:\n  front:\nvolumes:\n  db-data:\n"

@Composable
fun DockerConverterTool() {
    var toCompose by remember { mutableStateOf(true) }
    var runInput by remember { mutableStateOf(SAMPLE_RUN) }
    var composeInput by remember { mutableStateOf(SAMPLE_COMPOSE) }
    var version by remember { mutableStateOf<ComposeVersion?>(null) }
    var indent by remember { mutableIntStateOf(2) }
    var detached by remember { mutableStateOf(true) }
    val result = remember(toCompose, runInput, composeInput, version, indent, detached) {
        runCatching {
            if (toCompose) DockerCompose.convert(runInput, version, indent).let { it.yaml to it.warnings }
            else ComposeToRun.convert(composeInput, indent, detached).let { it.script to it.warnings }
        }
    }
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (toCompose) "application/yaml" else "text/x-sh")) { u ->
        val text = result.getOrNull()?.first
        if (u != null && text != null) writeUri(ctx, u, (text + "\n").toByteArray())
    }
    val versions = listOf<Pair<ComposeVersion?, String>>(null to t("Compose Specification (no version key)", "Compose Specification (sin clave version)")) +
        ComposeVersion.ALL.map { it to "version: \"$it\"" }
    ToolColumn {
        ChipChoice(t("Direction", "Dirección"), listOf(true to "docker run → Compose", false to "Compose → docker run"), toCompose) { toCompose = it }
        Panel {
            if (toCompose) Dropdown(t("Compose file format", "Formato del fichero Compose"), versions, version) { version = it }
            ChipChoice(t("Indentation", "Sangría"), listOf(2 to t("2 spaces", "2 espacios"), 4 to t("4 spaces", "4 espacios")), indent) { indent = it }
            if (!toCompose) SwitchRow(t("Detached mode (-d)", "Modo en segundo plano (-d)"), detached) { detached = it }
        }
        val v = version
        if (toCompose) Hint(when {
            v == null -> t("Current format used by Docker Compose v2: the top-level \"version\" key is obsolete and all options are available.",
                "Formato actual de Docker Compose v2: la clave \"version\" es obsoleta y todas las opciones están disponibles.")
            v.major == 3 -> t("Version 3.x: resource limits go under deploy.resources; options not supported by the chosen version are removed and listed below.",
                "Versión 3.x: los límites de recursos van en deploy.resources; las opciones que no admite la versión elegida se quitan y se listan abajo.")
            else -> t("Version 2.x: legacy format for docker-compose v1; options not supported by the chosen version are removed and listed below.",
                "Versión 2.x: formato antiguo de docker-compose v1; las opciones que no admite la versión elegida se quitan y se listan abajo.")
        })
        if (toCompose) InputField(runInput, { runInput = it }, t("Your docker run command", "Tu comando docker run"), singleLine = false, minLines = 4, mono = true,
            error = result.exceptionOrNull()?.message)
        else InputField(composeInput, { composeInput = it }, t("Your Compose file", "Tu fichero Compose"), singleLine = false, minLines = 8, mono = true,
            error = result.exceptionOrNull()?.let { (it.message ?: it.javaClass.simpleName).take(300) })
        result.getOrNull()?.let { (text, warnings) ->
            OutputField(text, if (toCompose) "compose.yaml" else "docker run", minLines = 8)
            if (warnings.isNotEmpty()) Panel(
                if (toCompose) t("Not translatable / removed options", "Opciones no traducibles / quitadas")
                else t("Keys without a docker run equivalent", "Claves sin equivalente en docker run")
            ) {
                warnings.forEach { Text("• $it", style = MonoStyle) }
            }
            ButtonRow {
                Button(onClick = { save.launch(if (toCompose) "compose.yaml" else "docker-run.sh") }) {
                    Text(if (toCompose) t("Download compose.yaml", "Guardar compose.yaml") else t("Download docker-run.sh", "Guardar docker-run.sh"))
                }
            }
        }
    }
}
