package com.itools.app.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itools.app.ui.components.*
import com.itools.app.util.secureRandom
import java.text.Normalizer

/* ======================= Lorem ipsum ======================= */

private val LOREM_WORDS = ("a ac accumsan ad adipiscing aenean aliquam aliquet amet ante aptent arcu at auctor augue bibendum blandit class commodo " +
    "condimentum congue consectetur consequat conubia convallis cras cubilia curabitur curae cursus dapibus diam dictum dictumst dignissim dis dolor " +
    "donec dui duis efficitur egestas eget eleifend elementum elit enim erat eros est et etiam eu euismod ex facilisi facilisis fames faucibus felis " +
    "fermentum feugiat finibus fringilla fusce gravida habitant habitasse hac hendrerit himenaeos iaculis id imperdiet in inceptos integer interdum ipsum " +
    "justo lacinia lacus laoreet lectus leo libero ligula litora lobortis lorem luctus maecenas magna magnis malesuada massa mattis mauris maximus metus mi " +
    "molestie mollis montes morbi mus nam nascetur natoque nec neque netus nibh nisi nisl non nostra nulla nullam nunc odio orci ornare parturient pellentesque " +
    "penatibus per pharetra phasellus placerat platea porta porttitor posuere potenti praesent pretium primis proin pulvinar purus quam quis quisque rhoncus " +
    "ridiculus risus rutrum sagittis sapien scelerisque sed sem semper senectus sit sociosqu sodales sollicitudin suscipit suspendisse taciti tellus tempor " +
    "tempus tincidunt torquent tortor tristique turpis ullamcorper ultrices ultricies urna ut varius vehicula vel velit venenatis vestibulum vitae vivamus " +
    "viverra volutpat vulputate").split(" ")

object Lorem {
    private fun rnd(r: IntRange) = r.first + secureRandom.nextInt(r.last - r.first + 1)
    fun generate(paragraphs: Int, sentences: IntRange, words: IntRange, startWithLorem: Boolean, html: Boolean): String {
        val ps = (0 until paragraphs).map { p ->
            (0 until rnd(sentences)).joinToString(" ") { s ->
                val w = (0 until rnd(words)).map { LOREM_WORDS[secureRandom.nextInt(LOREM_WORDS.size)] }.toMutableList()
                if (p == 0 && s == 0 && startWithLorem) {
                    val base = listOf("lorem", "ipsum", "dolor", "sit", "amet")
                    base.forEachIndexed { i, b -> if (i < w.size) w[i] = b else w += b }
                }
                w.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
            }
        }
        return if (html) ps.joinToString("\n\n") { "<p>$it</p>" } else ps.joinToString("\n\n")
    }
}

@Composable
fun LoremIpsumTool() {
    var paragraphs by remember { mutableIntStateOf(1) }
    var sMin by remember { mutableIntStateOf(3) }; var sMax by remember { mutableIntStateOf(8) }
    var wMin by remember { mutableIntStateOf(8) }; var wMax by remember { mutableIntStateOf(15) }
    var start by remember { mutableStateOf(true) }
    var html by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(0) }
    val text = remember(paragraphs, sMin, sMax, wMin, wMax, start, html, seed) {
        Lorem.generate(paragraphs, minOf(sMin, sMax)..maxOf(sMin, sMax), minOf(wMin, wMax)..maxOf(wMin, wMax), start, html)
    }
    ToolColumn {
        Panel {
            SliderRow(t("Paragraphs", "Párrafos"), paragraphs, 1..20) { paragraphs = it }
            SliderRow(t("Min sentences per paragraph", "Mín. frases por párrafo"), sMin, 1..50) { sMin = it }
            SliderRow(t("Max sentences per paragraph", "Máx. frases por párrafo"), sMax, 1..50) { sMax = it }
            SliderRow(t("Min words per sentence", "Mín. palabras por frase"), wMin, 1..50) { wMin = it }
            SliderRow(t("Max words per sentence", "Máx. palabras por frase"), wMax, 1..50) { wMax = it }
            SwitchRow(t("Start with lorem ipsum ?", "¿Empezar con lorem ipsum?"), start) { start = it }
            SwitchRow(t("As html ?", "¿Como HTML?"), html) { html = it }
        }
        OutputField(text, "Lorem ipsum", minLines = 6, mono = false)
        ButtonRow { Button(onClick = { seed++ }) { Text(t("Refresh", "Regenerar")) } }
    }
}

/* ======================= Text statistics ======================= */

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB"); var v = n.toDouble(); var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.2f %s".format(v, units[i])
}

private val WORD = Regex("\\S+")
private val SENTENCE = Regex("[^.!?\\s][^.!?]*(?:[.!?](?!['\"]?\\s|$)[^.!?]*)*[.!?]?['\"]?(?=\\s|$)")
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

private class TextStats(val chars: Int, val nonSpace: Int, val words: Int, val lines: Int, val sentences: Int, val paragraphs: Int, val bytes: Long)

private fun textStats(text: String) = TextStats(
    chars = text.codePointCount(0, text.length),
    nonSpace = text.count { !it.isWhitespace() },
    words = WORD.findAll(text).count(),
    lines = if (text.isEmpty()) 0 else text.lines().size,
    sentences = SENTENCE.findAll(text).count(),
    paragraphs = text.split(PARAGRAPH_BREAK).count { it.isNotBlank() },
    bytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
)

@Composable
fun TextStatsTool() {
    var text by remember { mutableStateOf("") }
    val st = remember(text) { textStats(text) }
    ToolColumn {
        InputField(text, { text = it }, t("Your text", "Tu texto"), singleLine = false, minLines = 8)
        KeyValueTable(listOf(
            t("Character count", "Caracteres") to st.chars.toString(),
            t("Characters (no spaces)", "Caracteres (sin espacios)") to st.nonSpace.toString(),
            t("Word count", "Palabras") to st.words.toString(),
            t("Line count", "Líneas") to st.lines.toString(),
            t("Sentences", "Frases") to st.sentences.toString(),
            t("Paragraphs", "Párrafos") to st.paragraphs.toString(),
            t("Byte size", "Tamaño en bytes") to "${formatBytes(st.bytes)} (${st.bytes} B)",
        ), copy = false)
    }
}

/* ======================= Emoji picker ======================= */

private data class EmojiGroup(val en: String, val es: String, val items: List<Pair<String, String>>)

private fun emojis(s: String) = s.trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore(' ') to it.substringAfter(' ') }

private val EMOJI_GROUPS = listOf(
    EmojiGroup("Smileys & emotion", "Caras y emociones", emojis("""
        😀 grinning face|😃 grinning face with big eyes|😄 grinning face with smiling eyes|😁 beaming face|😆 grinning squinting face|😅 grinning face with sweat|
        🤣 rolling on the floor laughing|😂 face with tears of joy|🙂 slightly smiling face|🙃 upside-down face|😉 winking face|😊 smiling face with smiling eyes|
        😇 smiling face with halo|🥰 smiling face with hearts|😍 smiling face with heart-eyes|🤩 star-struck|😘 face blowing a kiss|😋 face savoring food|
        😛 face with tongue|😜 winking face with tongue|🤪 zany face|🤑 money-mouth face|🤗 hugging face|🤭 face with hand over mouth|🤫 shushing face|
        🤔 thinking face|🤐 zipper-mouth face|🤨 face with raised eyebrow|😐 neutral face|😑 expressionless face|😶 face without mouth|😏 smirking face|
        😒 unamused face|🙄 face with rolling eyes|😬 grimacing face|😌 relieved face|😔 pensive face|😪 sleepy face|🤤 drooling face|😴 sleeping face|
        😷 face with medical mask|🤒 face with thermometer|🤕 face with head-bandage|🤢 nauseated face|🤮 face vomiting|🥵 hot face|🥶 cold face|
        😵 face with crossed-out eyes|🤯 exploding head|🤠 cowboy hat face|🥳 partying face|😎 smiling face with sunglasses|🤓 nerd face|🧐 face with monocle|
        😕 confused face|😟 worried face|🙁 slightly frowning face|😮 face with open mouth|😲 astonished face|😳 flushed face|🥺 pleading face|
        😢 crying face|😭 loudly crying face|😱 face screaming in fear|😖 confounded face|😞 disappointed face|😓 downcast face with sweat|😩 weary face|
        😫 tired face|🥱 yawning face|😤 face with steam from nose|😡 enraged face|😠 angry face|🤬 face with symbols on mouth|😈 smiling face with horns|
        💀 skull|💩 pile of poo|🤡 clown face|👻 ghost|👽 alien|🤖 robot|😺 grinning cat|❤️ red heart|🧡 orange heart|💛 yellow heart|💚 green heart|
        💙 blue heart|💜 purple heart|🖤 black heart|🤍 white heart|💔 broken heart|💯 hundred points|💢 anger symbol|💥 collision|💫 dizzy|💬 speech balloon|💤 zzz
    """)),
    EmojiGroup("People & body", "Personas y cuerpo", emojis("""
        👋 waving hand|🤚 raised back of hand|✋ raised hand|🖖 vulcan salute|👌 ok hand|🤌 pinched fingers|✌️ victory hand|🤞 crossed fingers|🤟 love-you gesture|
        🤘 sign of the horns|🤙 call me hand|👈 backhand index pointing left|👉 backhand index pointing right|👆 backhand index pointing up|👇 backhand index pointing down|
        👍 thumbs up|👎 thumbs down|✊ raised fist|👊 oncoming fist|👏 clapping hands|🙌 raising hands|👐 open hands|🤝 handshake|🙏 folded hands|✍️ writing hand|
        💪 flexed biceps|🧠 brain|👀 eyes|👁️ eye|👶 baby|🧒 child|👦 boy|👧 girl|🧑 person|👨 man|👩 woman|🧓 older person|👮 police officer|
        👷 construction worker|💂 guard|🕵️ detective|🧑‍💻 technologist|🧑‍🔬 scientist|🧑‍🚀 astronaut|🧑‍🍳 cook|🧑‍🏫 teacher|🦸 superhero|🧙 mage|🏃 person running|🚶 person walking
    """)),
    EmojiGroup("Animals & nature", "Animales y naturaleza", emojis("""
        🐶 dog face|🐱 cat face|🐭 mouse face|🐹 hamster|🐰 rabbit face|🦊 fox|🐻 bear|🐼 panda|🐨 koala|🐯 tiger face|🦁 lion|🐮 cow face|🐷 pig face|
        🐸 frog|🐵 monkey face|🐔 chicken|🐧 penguin|🐦 bird|🦆 duck|🦅 eagle|🦉 owl|🐺 wolf|🐗 boar|🐴 horse face|🦄 unicorn|🐝 honeybee|🐛 bug|🦋 butterfly|
        🐌 snail|🐞 lady beetle|🐢 turtle|🐍 snake|🦖 t-rex|🐙 octopus|🦀 crab|🐬 dolphin|🐳 spouting whale|🦈 shark|🐘 elephant|🦒 giraffe|🐪 camel|
        🌵 cactus|🎄 christmas tree|🌲 evergreen tree|🌳 deciduous tree|🌴 palm tree|🌱 seedling|🍀 four leaf clover|🍁 maple leaf|🍄 mushroom|🌷 tulip|
        🌹 rose|🌻 sunflower|🌸 cherry blossom|🌞 sun with face|🌙 crescent moon|⭐ star|🌟 glowing star|⚡ high voltage|🔥 fire|🌈 rainbow|☀️ sun|
        ☁️ cloud|🌧️ cloud with rain|❄️ snowflake|⛄ snowman without snow|🌊 water wave|💧 droplet|🌍 globe showing europe-africa
    """)),
    EmojiGroup("Food & drink", "Comida y bebida", emojis("""
        🍏 green apple|🍎 red apple|🍐 pear|🍊 tangerine|🍋 lemon|🍌 banana|🍉 watermelon|🍇 grapes|🍓 strawberry|🍒 cherries|🍑 peach|🥭 mango|🍍 pineapple|
        🥥 coconut|🥝 kiwi fruit|🍅 tomato|🥑 avocado|🍆 eggplant|🥕 carrot|🌽 ear of corn|🌶️ hot pepper|🥔 potato|🥐 croissant|🍞 bread|🧀 cheese wedge|
        🥚 egg|🍳 cooking|🥓 bacon|🍔 hamburger|🍟 french fries|🍕 pizza|🌭 hot dog|🌮 taco|🌯 burrito|🥗 green salad|🍝 spaghetti|🍜 steaming bowl|🍣 sushi|
        🍤 fried shrimp|🍦 soft ice cream|🍩 doughnut|🍪 cookie|🎂 birthday cake|🍰 shortcake|🍫 chocolate bar|🍬 candy|🍭 lollipop|🍯 honey pot|
        ☕ hot beverage|🍵 teacup without handle|🍺 beer mug|🍻 clinking beer mugs|🍷 wine glass|🍸 cocktail glass|🍹 tropical drink|🥂 clinking glasses|🥤 cup with straw
    """)),
    EmojiGroup("Travel & places", "Viajes y lugares", emojis("""
        🚗 automobile|🚕 taxi|🚙 sport utility vehicle|🚌 bus|🏎️ racing car|🚓 police car|🚑 ambulance|🚒 fire engine|🚚 delivery truck|🚜 tractor|🚲 bicycle|
        🛵 motor scooter|🏍️ motorcycle|🚂 locomotive|🚆 train|🚇 metro|✈️ airplane|🚀 rocket|🛸 flying saucer|🚁 helicopter|⛵ sailboat|🚢 ship|⚓ anchor|
        🏠 house|🏡 house with garden|🏢 office building|🏥 hospital|🏦 bank|🏫 school|🏰 castle|🗼 tokyo tower|🗽 statue of liberty|⛪ church|🏔️ snow-capped mountain|
        🏖️ beach with umbrella|🏝️ desert island|🌋 volcano|🗺️ world map|🧭 compass|⛺ tent|🌃 night with stars|🎡 ferris wheel|🎢 roller coaster
    """)),
    EmojiGroup("Activities", "Actividades", emojis("""
        ⚽ soccer ball|🏀 basketball|🏈 american football|⚾ baseball|🎾 tennis|🏐 volleyball|🏉 rugby football|🎱 pool 8 ball|🏓 ping pong|🏸 badminton|
        🥊 boxing glove|⛳ flag in hole|🎣 fishing pole|🎿 skis|🏆 trophy|🥇 1st place medal|🥈 2nd place medal|🥉 3rd place medal|🏅 sports medal|🎖️ military medal|
        🎫 ticket|🎭 performing arts|🎨 artist palette|🎬 clapper board|🎤 microphone|🎧 headphone|🎼 musical score|🎹 musical keyboard|🥁 drum|🎸 guitar|🎻 violin|
        🎲 game die|♟️ chess pawn|🎯 bullseye|🎳 bowling|🎮 video game|🧩 puzzle piece|🎉 party popper|🎊 confetti ball|🎈 balloon|🎁 wrapped gift
    """)),
    EmojiGroup("Objects", "Objetos", emojis("""
        ⌚ watch|📱 mobile phone|💻 laptop|⌨️ keyboard|🖥️ desktop computer|🖨️ printer|🖱️ computer mouse|💽 computer disk|💾 floppy disk|💿 optical disk|📷 camera|
        🎥 movie camera|📺 television|📻 radio|🔋 battery|🔌 electric plug|💡 light bulb|🔦 flashlight|🕯️ candle|📡 satellite antenna|💰 money bag|💳 credit card|
        💎 gem stone|🔧 wrench|🔨 hammer|🛠️ hammer and wrench|⚙️ gear|🔩 nut and bolt|🧰 toolbox|🧲 magnet|🔬 microscope|🔭 telescope|💊 pill|🧪 test tube|
        🔑 key|🗝️ old key|🔒 locked|🔓 unlocked|🔐 locked with key|📦 package|📫 closed mailbox|✉️ envelope|📧 e-mail|📝 memo|📁 file folder|📂 open file folder|
        📅 calendar|📈 chart increasing|📉 chart decreasing|📊 bar chart|📋 clipboard|📌 pushpin|📎 paperclip|✂️ scissors|🗑️ wastebasket|🔍 magnifying glass tilted left|
        🧯 fire extinguisher|🛒 shopping cart|🚪 door|🛏️ bed|🚿 shower|⏰ alarm clock|⏳ hourglass not done|🔔 bell|📢 loudspeaker|📣 megaphone
    """)),
    EmojiGroup("Symbols", "Símbolos", emojis("""
        ✅ check mark button|☑️ check box with check|✔️ check mark|❌ cross mark|❎ cross mark button|➕ plus|➖ minus|➗ divide|✖️ multiply|❓ red question mark|
        ❗ red exclamation mark|⚠️ warning|🚫 prohibited|⛔ no entry|🔴 red circle|🟠 orange circle|🟡 yellow circle|🟢 green circle|🔵 blue circle|🟣 purple circle|
        ⚫ black circle|⚪ white circle|🟥 red square|🟩 green square|🟦 blue square|🔺 red triangle pointed up|🔻 red triangle pointed down|♻️ recycling symbol|
        ⚛️ atom symbol|☢️ radioactive|☣️ biohazard|⬆️ up arrow|⬇️ down arrow|⬅️ left arrow|➡️ right arrow|↩️ right arrow curving left|🔄 counterclockwise arrows button|
        🔁 repeat button|▶️ play button|⏸️ pause button|⏹️ stop button|⏺️ record button|⏩ fast-forward button|🔀 shuffle tracks button|🔇 muted speaker|🔊 speaker high volume|
        📶 antenna bars|🛜 wireless|🆗 ok button|🆕 new button|🆓 free button|🆘 sos button|ℹ️ information|🔣 input symbols|#️⃣ keycap number sign|©️ copyright|®️ registered|™️ trade mark|
        💲 heavy dollar sign|💱 currency exchange|♾️ infinity|🏁 chequered flag|🚩 triangular flag|🏳️ white flag|🏴 black flag|🏳️‍🌈 rainbow flag|🇪🇸 flag spain|🇬🇧 flag united kingdom|🇺🇸 flag united states
    """)),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerTool() {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    val es = isSpanish()
    ToolColumn {
        InputField(query, { query = it }, t("Search emojis (e.g. 'smile')...", "Buscar emojis (en inglés, p. ej. 'smile')..."))
        selected?.let { (e, name) ->
            val cps = e.codePoints().toArray()
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(e, fontSize = 40.sp)
                    Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleSmall) }
                    CopyButton(e)
                }
                KeyValue("Unicode", cps.joinToString(" ") { "U+" + Integer.toHexString(it).uppercase().padStart(4, '0') })
                KeyValue("HTML", cps.joinToString("") { "&#x" + Integer.toHexString(it).uppercase() + ";" })
                KeyValue(t("Escaped", "Escapado"), e.map { "\\u" + Integer.toHexString(it.code).padStart(4, '0') }.joinToString(""))
            }
        }
        val q = query.trim().lowercase()
        EMOJI_GROUPS.forEach { g ->
            val items = if (q.isEmpty()) g.items else g.items.filter { it.second.contains(q) }
            if (items.isNotEmpty()) {
                Text(if (es) g.es else g.en, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEach { item ->
                        Box(
                            Modifier.size(44.dp)
                                .background(if (selected == item) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selected = item; copyText(ctx, item.first) },
                            contentAlignment = Alignment.Center
                        ) { Text(item.first, fontSize = 26.sp) }
                    }
                }
            }
        }
    }
}

/* ======================= String obfuscator ======================= */

fun obfuscate(s: String, keepFirst: Int, keepLast: Int, keepSpace: Boolean, replacement: String = "*"): String {
    val cps = s.codePoints().toArray()
    return cps.mapIndexed { i, cp ->
        val c = String(Character.toChars(cp))
        when {
            i < keepFirst || i >= cps.size - keepLast -> c
            keepSpace && Character.isWhitespace(cp) -> c
            else -> replacement
        }
    }.joinToString("")
}

@Composable
fun ObfuscatorTool() {
    var input by remember { mutableStateOf("Lorem ipsum dolor sit amet") }
    var first by remember { mutableIntStateOf(4) }
    var last by remember { mutableIntStateOf(4) }
    var keepSpace by remember { mutableStateOf(true) }
    ToolColumn {
        InputField(input, { input = it }, t("String to obfuscate", "Texto a ofuscar"), singleLine = false, minLines = 2)
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(first, { first = it }, t("Keep first", "Mantener al inicio"), Modifier.weight(1f), 0, 1000)
                NumberField(last, { last = it }, t("Keep last", "Mantener al final"), Modifier.weight(1f), 0, 1000)
            }
            SwitchRow(t("Keep spaces", "Mantener espacios"), keepSpace) { keepSpace = it }
        }
        OutputField(obfuscate(input, first, last, keepSpace), t("Obfuscated string", "Texto ofuscado"))
    }
}

/* ======================= Text diff ======================= */

object Diff {
    enum class Op { EQ, ADD, DEL }
    fun diff(a: List<String>, b: List<String>): List<Pair<Op, String>> {
        // Trim the common prefix/suffix to speed things up
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
        val x = a.subList(pre, a.size - suf); val y = b.subList(pre, b.size - suf)
        val out = mutableListOf<Pair<Op, String>>()
        a.take(pre).forEach { out += Op.EQ to it }
        if (x.size.toLong() * y.size > 4_000_000) {
            x.forEach { out += Op.DEL to it }; y.forEach { out += Op.ADD to it }
        } else {
            val n = x.size; val m = y.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
                dp[i][j] = if (x[i] == y[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            var i = 0; var j = 0
            while (i < n && j < m) {
                when {
                    x[i] == y[j] -> { out += Op.EQ to x[i]; i++; j++ }
                    dp[i + 1][j] >= dp[i][j + 1] -> { out += Op.DEL to x[i]; i++ }
                    else -> { out += Op.ADD to y[j]; j++ }
                }
            }
            while (i < n) out += Op.DEL to x[i++]
            while (j < m) out += Op.ADD to y[j++]
        }
        a.takeLast(suf).forEach { out += Op.EQ to it }
        return out
    }
    private val TOKEN = Regex("\\s+|[^\\s]+")
    fun tokens(s: String): List<String> = TOKEN.findAll(s).map { it.value }.toList()
}

@Composable
fun TextDiffTool() {
    var a by remember { mutableStateOf("Hello world\nThis is the original text\nSame line") }
    var b by remember { mutableStateOf("Hello world!\nThis is the modified text\nSame line\nNew line") }
    var byWord by remember { mutableStateOf(false) }
    val del = Color(0x55E53935); val add = Color(0x5543A047)
    val d = remember(a, b, byWord) { if (byWord) Diff.diff(Diff.tokens(a), Diff.tokens(b)) else Diff.diff(a.lines(), b.lines()) }
    ToolColumn {
        InputField(a, { a = it }, t("Original text", "Texto original"), singleLine = false, minLines = 4, mono = true)
        InputField(b, { b = it }, t("Modified text", "Texto modificado"), singleLine = false, minLines = 4, mono = true)
        ChipChoice(null, listOf(false to t("By line", "Por línea"), true to t("By word", "Por palabra")), byWord) { byWord = it }
        val (adds, dels) = remember(d) { d.count { it.first == Diff.Op.ADD } to d.count { it.first == Diff.Op.DEL } }
        Hint("+$adds  −$dels")
        Panel {
            Text(buildAnnotatedString {
                d.forEach { (op, s) ->
                    val prefix = if (byWord) "" else when (op) { Diff.Op.ADD -> "+ "; Diff.Op.DEL -> "- "; Diff.Op.EQ -> "  " }
                    val style = when (op) {
                        Diff.Op.ADD -> SpanStyle(background = add)
                        Diff.Op.DEL -> SpanStyle(background = del, textDecoration = if (byWord) TextDecoration.LineThrough else null)
                        Diff.Op.EQ -> SpanStyle()
                    }
                    withStyle(style) { append(prefix + s) }
                    if (!byWord) append("\n")
                }
            }, style = MonoStyle)
        }
    }
}

/* ======================= Numeronym ======================= */

private val WHITESPACE = Regex("\\s+")

fun numeronym(word: String): String {
    val w = word.trim()
    return if (w.length <= 3) w else "${w.first()}${w.length - 2}${w.last()}"
}

@Composable
fun NumeronymTool() {
    var word by remember { mutableStateOf("") }
    ToolColumn {
        InputField(word, { word = it }, t("Enter a word, e.g. 'internationalization'", "Escribe una palabra, p. ej. 'internationalization'"))
        OutputField(word.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ") { numeronym(it) },
            t("Your numeronym will be here, e.g. 'i18n'", "Aquí aparecerá tu numerónimo, p. ej. 'i18n'"), singleLine = true)
    }
}

/* ======================= ASCII art ======================= */

object AsciiArt {
    private val GLYPHS: Map<Char, List<String>> = mapOf(
        'A' to " ### |#   #|#####|#   #|#   #", 'B' to "#### |#   #|#### |#   #|#### ", 'C' to " ####|#    |#    |#    | ####",
        'D' to "#### |#   #|#   #|#   #|#### ", 'E' to "#####|#    |#### |#    |#####", 'F' to "#####|#    |#### |#    |#    ",
        'G' to " ####|#    |#  ##|#   #| ####", 'H' to "#   #|#   #|#####|#   #|#   #", 'I' to "###| # | # | # |###",
        'J' to "  ###|   # |   # |#  # | ##  ", 'K' to "#   #|#  # |###  |#  # |#   #", 'L' to "#    |#    |#    |#    |#####",
        'M' to "#   #|## ##|# # #|#   #|#   #", 'N' to "#   #|##  #|# # #|#  ##|#   #", 'O' to " ### |#   #|#   #|#   #| ### ",
        'P' to "#### |#   #|#### |#    |#    ", 'Q' to " ### |#   #|# # #|#  # | ## #", 'R' to "#### |#   #|#### |#  # |#   #",
        'S' to " ####|#    | ### |    #|#### ", 'T' to "#####|  #  |  #  |  #  |  #  ", 'U' to "#   #|#   #|#   #|#   #| ### ",
        'V' to "#   #|#   #|#   #| # # |  #  ", 'W' to "#   #|#   #|# # #|## ##|#   #", 'X' to "#   #| # # |  #  | # # |#   #",
        'Y' to "#   #| # # |  #  |  #  |  #  ", 'Z' to "#####|   # |  #  | #   |#####",
        '0' to " ### |#  ##|# # #|##  #| ### ", '1' to " # |## | # | # |###", '2' to " ### |#   #|  ## | #   |#####",
        '3' to "#### |    #| ### |    #|#### ", '4' to "#   #|#   #|#####|    #|    #", '5' to "#####|#    |#### |    #|#### ",
        '6' to " ### |#    |#### |#   #| ### ", '7' to "#####|    #|   # |  #  |  #  ", '8' to " ### |#   #| ### |#   #| ### ",
        '9' to " ### |#   #| ####|    #| ### ", ' ' to "   |   |   |   |   ", '!' to "#|#|#| |#", '?' to " ### |#   #|  ## |     |  #  ",
        '.' to " | | | |#", ',' to "  |  |  | #|# ", '-' to "    |    |####|    |    ", ':' to " |#| |#| ", '\'' to "#|#| | | ",
        '_' to "     |     |     |     |#####", '/' to "    #|   # |  #  | #   |#    ", '+' to "   | # |###| # |   ", '=' to "   |###|   |###|   ",
        '(' to " #|# |# |# | #", ')' to "# | #| #| #|# ", '#' to " # # |#####| # # |#####| # # ", '@' to " ### |# ###|# # #|# ###| ### ",
        '*' to "     |# # #| ### |# # #|     ", '&' to " ##  |#  # | ##  |#  # | ## #",
    ).mapValues { it.value.split("|") }

    enum class Style(val en: String, val es: String) {
        BLOCK("Block", "Bloque"), SHADOW("Shadow", "Sombra"), HASH("Hash", "Almohadilla"), STARS("Stars", "Asteriscos"),
        LETTERS("Letters", "Letras"), DOTS("Dots", "Puntos"), SHADE("Shade", "Sombreado"), BANNER("Banner", "Pancarta")
    }

    private fun glyph(c: Char) = GLYPHS[c.uppercaseChar()] ?: GLYPHS[Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().uppercaseChar()] ?: GLYPHS.getValue('?')

    fun render(text: String, style: Style, spacing: Int, maxWidth: Int): String =
        text.lines().joinToString("\n\n") { line -> renderLine(line, style, spacing, maxWidth) }

    private fun renderLine(line: String, style: Style, spacing: Int, maxWidth: Int): String {
        // Split into chunks that fit in maxWidth columns
        val chunks = mutableListOf<String>(); var cur = StringBuilder(); var w = 0
        for (c in line) {
            val gw = glyph(c)[0].length + spacing
            if (w + gw > maxWidth && cur.isNotEmpty()) { chunks += cur.toString(); cur = StringBuilder(); w = 0 }
            cur.append(c); w += gw
        }
        chunks += cur.toString()
        return chunks.joinToString("\n\n") { chunk ->
            val rows = (0 until 5).map { r -> chunk.map { c -> glyph(c)[r] + " ".repeat(spacing) }.joinToString("") }
            paint(rows, chunk, style, spacing)
        }
    }

    private fun paint(rows: List<String>, chunk: String, style: Style, spacing: Int): String {
        val width = rows.maxOf { it.length }
        fun on(r: Int, c: Int) = r in rows.indices && c >= 0 && c < rows[r].length && rows[r][c] == '#'
        return when (style) {
            Style.SHADOW -> (0..5).joinToString("\n") { r ->
                (0..width).joinToString("") { c -> if (on(r, c)) "█" else if (on(r - 1, c - 1)) "░" else " " }.trimEnd()
            }
            Style.BANNER -> {
                val body = rows.map { it.map { ch -> if (ch == '#') '█' else ' ' }.joinToString("").trimEnd() }
                val border = "═".repeat(width + 2)
                (listOf("╔$border╗") + body.map { "║ " + it.padEnd(width) + " ║" } + listOf("╚$border╝")).joinToString("\n")
            }
            else -> {
                // For LETTERS each column uses the letter of its glyph
                val letterMap = if (style == Style.LETTERS) buildString {
                    chunk.forEach { c -> append(c.uppercaseChar().toString().repeat(glyph(c)[0].length)); append(" ".repeat(spacing)) }
                } else ""
                rows.joinToString("\n") { row ->
                    row.mapIndexed { i, ch ->
                        if (ch != '#') ' ' else when (style) {
                            Style.BLOCK -> '█'; Style.HASH -> '#'; Style.STARS -> '*'; Style.DOTS -> '•'; Style.SHADE -> '▓'
                            Style.LETTERS -> letterMap.getOrElse(i) { '#' }
                            else -> '#'
                        }
                    }.joinToString("").trimEnd()
                }
            }
        }
    }
}

@Composable
fun AsciiArtTool() {
    var text by remember { mutableStateOf("Ascii ART") }
    var style by remember { mutableStateOf(AsciiArt.Style.BLOCK) }
    var spacing by remember { mutableIntStateOf(1) }
    var width by remember { mutableIntStateOf(60) }
    val es = isSpanish()
    val art = remember(text, style, spacing, width) { AsciiArt.render(text, style, spacing, width) }
    ToolColumn {
        InputField(text, { text = it }, t("Your text", "Tu texto"), singleLine = false, minLines = 2)
        Panel {
            Dropdown(t("Font", "Fuente"), AsciiArt.Style.entries.map { it to (if (es) it.es else it.en) }, style) { style = it }
            SliderRow(t("Letter spacing", "Espaciado"), spacing, 0..4) { spacing = it }
            SliderRow(t("Width", "Ancho"), width, 20..200) { width = it }
        }
        Row(verticalAlignment = Alignment.Top) {
            MonoBlock(art, Modifier.weight(1f), size = 9)
            CopyButton(art)
        }
    }
}
