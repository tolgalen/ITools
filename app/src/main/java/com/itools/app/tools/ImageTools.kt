package com.itools.app.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.itools.app.ui.components.*
import com.itools.app.util.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ======================= Component: color field ======================= */

@Composable
private fun ColorInput(label: String, value: String, onChange: (String) -> Unit) {
    val c = remember(value) { ColorConv.parse(value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(40.dp)
                .background(c?.let { Color(it.argb) } ?: Color.Transparent, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        )
        InputField(value, onChange, label, Modifier.weight(1f), mono = true,
            error = if (c == null) t("Invalid color", "Color no válido") else null)
    }
}

@Composable
private fun QrPreviewAndActions(content: String, fg: Int, bg: Int, ecl: ErrorCorrectionLevel, fileName: String) {
    val ctx = LocalContext.current
    val bmp = remember(content, fg, bg, ecl) { runCatching { Qr.bitmap(content, 720, fg, bg, ecl) }.getOrNull() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { u ->
        if (u != null && bmp != null) writeUri(ctx, u, bmp.toPng())
    }
    if (bmp != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(bmp.asImageBitmap(), "QR", Modifier.size(260.dp))
        }
        ButtonRow {
            Button(onClick = { save.launch("$fileName.png") }) { Text(t("Download PNG", "Guardar PNG")) }
            OutlinedButton(onClick = { shareBitmap(ctx, bmp, fileName) }) { Text(t("Share", "Compartir")) }
        }
    }
}

private val ECL_OPTIONS = listOf(
    ErrorCorrectionLevel.L to "Low", ErrorCorrectionLevel.M to "Medium",
    ErrorCorrectionLevel.Q to "Quartile", ErrorCorrectionLevel.H to "High",
)

/* ======================= QR code generator ======================= */

@Composable
fun QrCodeTool() {
    var text by remember { mutableStateOf("https://it-tools.tech") }
    var fg by remember { mutableStateOf("#000000ff") }
    var bg by remember { mutableStateOf("#ffffffff") }
    var ecl by remember { mutableStateOf(ErrorCorrectionLevel.M) }
    ToolColumn {
        Panel {
            InputField(text, { text = it }, t("Text", "Texto"), singleLine = false, minLines = 2,
                placeholder = t("Your link or text...", "Tu enlace o texto..."))
            ColorInput(t("Foreground color", "Color de primer plano"), fg) { fg = it }
            ColorInput(t("Background color", "Color de fondo"), bg) { bg = it }
            ChipChoice(t("Error resistance", "Resistencia a errores"), ECL_OPTIONS, ecl) { ecl = it }
        }
        QrPreviewAndActions(
            text,
            ColorConv.parse(fg)?.argb ?: 0xFF000000.toInt(),
            ColorConv.parse(bg)?.argb ?: 0xFFFFFFFF.toInt(),
            ecl, "qr-code"
        )
    }
}

/* ======================= WiFi QR code ======================= */

object WifiQr {
    private val SPECIAL = Regex("([\\\\;,:\"])")
    private fun esc(s: String) = s.replace(SPECIAL, "\\\\$1")
    fun build(ssid: String, password: String, enc: String, hidden: Boolean,
              eapMethod: String = "", identity: String = "", anonymous: Boolean = false, phase2: String = ""): String {
        if (ssid.isEmpty()) return ""
        if (enc == "nopass") return "WIFI:S:${esc(ssid)};${if (hidden) "H:true;" else ""};"
        if (enc == "WPA2-EAP") {
            if (password.isEmpty() || eapMethod.isEmpty() || (!anonymous && identity.isEmpty())) return ""
            val id = if (anonymous) "A:anon;" else "I:${esc(identity)};"
            val ph2 = if (phase2.isNotEmpty() && phase2 != "None") "PH2:$phase2;" else ""
            return "WIFI:S:${esc(ssid)};T:WPA2-EAP;P:${esc(password)};E:$eapMethod;$ph2$id${if (hidden) "H:true;" else ""};"
        }
        if (password.isEmpty()) return ""
        return "WIFI:S:${esc(ssid)};T:$enc;P:${esc(password)};${if (hidden) "H:true;" else ""};"
    }
}

@Composable
fun WifiQrTool() {
    var enc by remember { mutableStateOf("WPA") }
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(false) }
    var eap by remember { mutableStateOf("PEAP") }
    var identity by remember { mutableStateOf("") }
    var anon by remember { mutableStateOf(false) }
    var phase2 by remember { mutableStateOf("None") }
    var fg by remember { mutableStateOf("#000000ff") }
    var bg by remember { mutableStateOf("#ffffffff") }
    val content = WifiQr.build(ssid, pass, enc, hidden, eap, identity, anon, phase2)
    ToolColumn {
        Panel {
            Dropdown(t("Encryption method", "Método de cifrado"), listOf(
                "nopass" to t("No password", "Sin contraseña"), "WPA" to "WPA/WPA2", "WEP" to "WEP", "WPA2-EAP" to "WPA2-EAP"
            ), enc) { enc = it }
            InputField(ssid, { ssid = it }, "SSID", placeholder = t("Your WiFi SSID...", "SSID de tu WiFi..."))
            SwitchRow(t("Hidden SSID", "SSID oculto"), hidden) { hidden = it }
            if (enc != "nopass") InputField(pass, { pass = it }, t("Password", "Contraseña"), password = true)
            if (enc == "WPA2-EAP") {
                Dropdown(t("EAP method", "Método EAP"), listOf("MD5", "POTP", "GTC", "TLS", "IKEv2", "SIM", "AKA", "AKA'", "TTLS", "PWD", "LEAP", "PSK", "FAST", "TEAP", "EKE", "NOOB", "PEAP").map { it to it }, eap) { eap = it }
                SwitchRow(t("Anonymous identity", "Identidad anónima"), anon) { anon = it }
                if (!anon) InputField(identity, { identity = it }, t("Identity", "Identidad"))
                Dropdown(t("EAP Phase 2 method", "Método EAP fase 2"), listOf("None", "MSCHAPV2").map { it to it }, phase2) { phase2 = it }
            }
            ColorInput(t("Foreground color", "Color de primer plano"), fg) { fg = it }
            ColorInput(t("Background color", "Color de fondo"), bg) { bg = it }
        }
        if (content.isNotEmpty()) {
            OutputField(content, t("QR content", "Contenido del QR"), singleLine = true)
            QrPreviewAndActions(content, ColorConv.parse(fg)?.argb ?: 0xFF000000.toInt(),
                ColorConv.parse(bg)?.argb ?: 0xFFFFFFFF.toInt(), ErrorCorrectionLevel.M, "wifi-qr-code")
        } else Hint(t("Fill in the SSID and password to generate the QR code.", "Rellena el SSID y la contraseña para generar el QR."))
    }
}

/* ======================= SVG placeholder ======================= */

@Composable
fun SvgPlaceholderTool() {
    var w by remember { mutableIntStateOf(600) }
    var h by remember { mutableIntStateOf(350) }
    var bg by remember { mutableStateOf("#cccccc") }
    var fg by remember { mutableStateOf("#333333") }
    var fontSize by remember { mutableIntStateOf(26) }
    var custom by remember { mutableStateOf("") }
    var useExact by remember { mutableStateOf(false) }
    val text = custom.ifEmpty { "${w}x$h" }
    val svg = remember(w, h, bg, fg, fontSize, text, useExact) {
        val size = if (useExact) "width=\"$w\" height=\"$h\" " else ""
        "<svg xmlns=\"http://www.w3.org/2000/svg\" ${size}viewBox=\"0 0 $w $h\">\n" +
            "  <rect width=\"$w\" height=\"$h\" fill=\"$bg\"></rect>\n" +
            "  <text x=\"50%\" y=\"50%\" dominant-baseline=\"middle\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"${fontSize}px\" fill=\"$fg\">" +
            HtmlEntities.escape(text) + "</text>\n</svg>"
    }
    val dataUri = "data:image/svg+xml;base64," + svg.toByteArray().toB64()
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { u ->
        if (u != null) writeUri(ctx, u, svg.toByteArray())
    }
    val bgC = ColorConv.parse(bg)?.argb ?: 0xFFCCCCCC.toInt()
    val fgC = ColorConv.parse(fg)?.argb ?: 0xFF333333.toInt()
    ToolColumn {
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(w, { w = it }, t("Width (px)", "Ancho (px)"), Modifier.weight(1f), 1, 10000)
                NumberField(h, { h = it }, t("Height (px)", "Alto (px)"), Modifier.weight(1f), 1, 10000)
            }
            ColorInput(t("Background", "Fondo"), bg) { bg = it }
            ColorInput(t("Text color", "Color del texto"), fg) { fg = it }
            NumberField(fontSize, { fontSize = it }, t("Font size", "Tamaño de fuente"), min = 1, max = 1000)
            InputField(custom, { custom = it }, t("Custom text", "Texto personalizado"), placeholder = "${w}x$h")
            SwitchRow(t("Use exact size", "Usar tamaño exacto"), useExact) { useExact = it }
        }
        OutputField(svg, "SVG HTML element", minLines = 4)
        OutputField(dataUri, "SVG in Base64", minLines = 2)
        ButtonRow {
            Button(onClick = { save.launch("placeholder.svg") }) { Text(t("Download SVG", "Guardar SVG")) }
        }
        // Preview drawn with Canvas (Android does not render SVG natively)
        Canvas(Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h.coerceAtLeast(1))) {
            drawRect(Color(bgC))
            val scale = size.width / w
            drawIntoCanvas { c ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fgC; textSize = fontSize * scale; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
                }
                val y = size.height / 2 - (p.descent() + p.ascent()) / 2
                c.nativeCanvas.drawText(text, size.width / 2, y, p)
            }
        }
    }
}

/* ======================= Camera recorder ======================= */

@SuppressLint("MissingPermission")
@Composable
fun CameraRecorderTool() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camOk by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var micOk by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camOk = granted(Manifest.permission.CAMERA); micOk = granted(Manifest.permission.RECORD_AUDIO)
    }
    if (!camOk) {
        ToolColumn {
            Hint(t("Camera permission is required to use this tool.", "Se necesita permiso de cámara para usar esta herramienta."))
            Button(onClick = { askPerms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                Text(t("Grant permission", "Conceder permiso"))
            }
        }
        return
    }
    var front by remember { mutableStateOf(false) }
    var withAudio by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var status by remember { mutableStateOf("") }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val videoCapture = remember {
        VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
    }
    val savedPhoto = t("Photo saved in Pictures/ITools", "Foto guardada en Pictures/ITools")
    val savedVideo = t("Video saved in Movies/ITools", "Vídeo guardado en Movies/ITools")
    val errorTxt = t("Error", "Error")

    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, imageCapture, videoCapture)
            }.onFailure { status = "$errorTxt: ${it.message}" }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { runCatching { future.get().unbindAll() } }
    }

    fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun takePhoto() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ITools-${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ITools")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                status = savedPhoto; Toast.makeText(ctx, savedPhoto, Toast.LENGTH_SHORT).show()
            }
            override fun onError(e: ImageCaptureException) { status = "$errorTxt: ${e.message}" }
        })
    }

    fun toggleVideo() {
        recording?.let { it.stop(); recording = null; return }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ITools-${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ITools")
        }
        val out = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        var pending = videoCapture.output.prepareRecording(ctx, out)
        if (withAudio && micOk) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(ctx)) { ev ->
            if (ev is VideoRecordEvent.Finalize) {
                status = if (ev.hasError()) "$errorTxt: ${ev.error}" else savedVideo
                if (!ev.hasError()) Toast.makeText(ctx, savedVideo, Toast.LENGTH_SHORT).show()
                recording = null
            }
        }
    }

    ToolColumn {
        AndroidView({ previewView }, Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black))
        ButtonRow {
            Button(onClick = { takePhoto() }, enabled = recording == null) { Text(t("Take a photo", "Hacer foto")) }
            Button(
                onClick = { toggleVideo() },
                colors = if (recording != null) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) { Text(if (recording != null) t("Stop recording", "Detener grabación") else t("Record video", "Grabar vídeo")) }
            OutlinedButton(onClick = { front = !front }, enabled = recording == null) { Text(t("Switch camera", "Cambiar cámara")) }
        }
        SwitchRow(t("Record audio", "Grabar audio"), withAudio && micOk) {
            if (!micOk) askPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) else withAudio = it
        }
        if (status.isNotEmpty()) Hint(status)
    }
}
