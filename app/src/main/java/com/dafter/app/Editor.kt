package com.dafter.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

object Media {
    fun decode(path: String, max: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Throwable) {
        null
    }

    fun importImage(ctx: android.content.Context, uri: Uri): String? = try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val rot = when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val name = "i" + Store.uid() + ".jpg"
        FileOutputStream(File(Store.media(ctx), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun Editor(start: Note, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { Store.media(ctx) }
    var title by remember { mutableStateOf(start.title) }
    var body by remember { mutableStateOf(start.body) }
    var folder by remember { mutableStateOf(start.folder) }
    var pinned by remember { mutableStateOf(start.pinned) }
    var priv by remember { mutableStateOf(start.priv) }
    val atts = remember { mutableStateListOf<Att>().apply { addAll(start.atts) } }
    var draw by remember { mutableStateOf(false) }
    var fMenu by remember { mutableStateOf(false) }
    var rec by remember { mutableStateOf<MediaRecorder?>(null) }
    var recName by remember { mutableStateOf("") }
    var secs by remember { mutableIntStateOf(0) }

    fun save() {
        if (title.isBlank() && body.isBlank() && atts.isEmpty()) {
            Store.notes.firstOrNull { it.id == start.id }?.let { Store.purge(ctx, it) }
            return
        }
        val n = start.copy(title = title, body = body, folder = folder, pinned = pinned, priv = priv, atts = atts.toList())
        val old = Store.notes.firstOrNull { it.id == start.id }
        if (old != null && old.copy(updated = 0) == n.copy(updated = 0, created = old.created)) return
        Store.put(n.copy(updated = System.currentTimeMillis(), created = old?.created ?: n.created))
    }

    fun stopRec() {
        val r = rec ?: return
        rec = null
        try {
            r.stop()
            atts.add(Att("audio", recName))
        } catch (_: Exception) {
            File(dir, recName).delete()
        }
        r.release()
    }

    fun startRec() {
        val name = "a" + Store.uid() + ".m4a"
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(File(dir, name).absolutePath)
            r.prepare()
            r.start()
            recName = name
            secs = 0
            rec = r
        } catch (_: Exception) {
            r.release()
            Toast.makeText(ctx, "مقدرتش أسجل", Toast.LENGTH_SHORT).show()
        }
    }

    fun close() {
        stopRec()
        save()
        onClose()
    }

    fun trash() {
        stopRec()
        Store.notes.firstOrNull { it.id == start.id }?.let { Store.put(it.copy(trashed = true)) }
        onClose()
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { Media.importImage(ctx, uri) }
            if (n != null) atts.add(Att("img", n))
        }
    }

    LaunchedEffect(title, body, folder, pinned, priv, atts.size) { delay(600); save() }
    LaunchedEffect(rec) { while (rec != null) { delay(1000); secs++ } }
    DisposableEffect(Unit) { onDispose { rec?.let { try { it.stop() } catch (_: Exception) {}; it.release() } } }
    BackHandler(enabled = !draw) { close() }

    val folderName = if (folder.isEmpty()) "غير مصنف" else Store.folders.firstOrNull { it.id == folder }?.name ?: "غير مصنف"

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Back, "رجوع") { close() }
            Spacer(Modifier.weight(1f))
            Box {
                FChip(folderName, false) { fMenu = true }
                DropdownMenu(fMenu, { fMenu = false }) {
                    DropdownMenuItem(text = { Text("غير مصنف") }, onClick = { folder = ""; fMenu = false }, leadingIcon = { Icon(Ic.Folder, null, Modifier.size(20.dp)) })
                    Store.folders.forEach { f ->
                        DropdownMenuItem(text = { Text(f.name) }, onClick = { folder = f.id; fMenu = false }, leadingIcon = { Icon(Ic.Folder, null, Modifier.size(20.dp)) })
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            IconBtn(Ic.Pin, "تثبيت", tint = if (pinned) cs.onPrimaryContainer else cs.onSurfaceVariant, bg = if (pinned) cs.primaryContainer else Color.Transparent) { pinned = !pinned }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            TextField(
                title, { title = it }, Modifier.fillMaxWidth(), colors = clearFieldColors(),
                textStyle = MaterialTheme.typography.headlineSmall, placeholder = { Text("العنوان", style = MaterialTheme.typography.headlineSmall) },
                maxLines = 3
            )
            TextField(
                body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp), colors = clearFieldColors(),
                textStyle = MaterialTheme.typography.bodyLarge, placeholder = { Text("اكتب ملاحظتك...") }
            )
            Column(Modifier.padding(horizontal = 8.dp)) {
                atts.toList().forEach { a ->
                    val del = { atts.remove(a); File(dir, a.file).delete(); Unit }
                    if (a.type == "audio") AudioRow(File(dir, a.file).path, del) else ImageAtt(File(dir, a.file).path, del)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (rec != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp)).background(cs.errorContainer).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("بسجل... %d:%02d".format(secs / 60, secs % 60), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer)
                IconBtn(Ic.Stop, "إيقاف", tint = cs.onError, bg = cs.error) { stopRec() }
            }
        }

        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp, top = 6.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 6.dp) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconBtn(Ic.Image, "صورة") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    IconBtn(Ic.Mic, "تسجيل", tint = if (rec != null) cs.onError else cs.onSurface, bg = if (rec != null) cs.error else Color.Transparent) {
                        if (rec != null) stopRec()
                        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    IconBtn(Ic.Draw, "رسم") { draw = true }
                    IconBtn(Ic.Lock, "خاصة", tint = if (priv) cs.onPrimaryContainer else cs.onSurface, bg = if (priv) cs.primaryContainer else Color.Transparent) { priv = !priv }
                    IconBtn(Ic.Trash, "حذف", tint = cs.error) { trash() }
                }
            }
        }
    }

    if (draw) DrawPad(
        onCancel = { draw = false },
        onSave = { bmp ->
            draw = false
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    val name = "d" + Store.uid() + ".png"
                    FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    name
                }
                atts.add(Att("draw", n))
            }
        }
    )
}

@Composable
fun ImageAtt(path: String, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bmp = remember(path) { Media.decode(path, 1200) }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        else Spacer(Modifier.height(80.dp))
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) { Icon(Ic.Close, "حذف", Modifier.size(18.dp), tint = Color.White) }
    }
}

@Composable
fun AudioRow(path: String, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(path) { onDispose { mp?.release() } }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(28.dp)).background(cs.secondaryContainer).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBtn(if (playing) Ic.Pause else Ic.Play, "تشغيل", tint = cs.onPrimary, bg = cs.primary) {
            val p = mp
            if (p == null) {
                try {
                    mp = MediaPlayer().apply {
                        setDataSource(path); prepare()
                        setOnCompletionListener { playing = false; seekTo(0) }
                        start()
                    }
                    playing = true
                } catch (_: Exception) {
                }
            } else if (p.isPlaying) {
                p.pause(); playing = false
            } else {
                p.start(); playing = true
            }
        }
        Text("تسجيل صوتي", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconBtn(Ic.Close, "حذف", tint = cs.onSecondaryContainer) { onDelete() }
    }
}

private class Stroke(val color: Int, val width: Float) {
    val path = android.graphics.Path()
}

private fun render(c: android.graphics.Canvas, strokes: List<Stroke>) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { p.color = it.color; p.strokeWidth = it.width; c.drawPath(it.path, p) }
}

@Composable
fun DrawPad(onCancel: () -> Unit, onSave: (Bitmap) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val strokes = remember { mutableStateListOf<Stroke>() }
    var tick by remember { mutableIntStateOf(0) }
    var color by remember { mutableIntStateOf(0xFF1B1B1F.toInt()) }
    var width by remember { mutableFloatStateOf(6f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    BackHandler { onCancel() }
    val palette = listOf(0xFF1B1B1F.toInt(), 0xFFD32F2F.toInt(), 0xFF1976D2.toInt(), 0xFF2E7D32.toInt(), 0xFFF57C00.toInt(), 0xFF7B1FA2.toInt(), 0xFFFFFFFF.toInt())

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Close, "إلغاء") { onCancel() }
            Spacer(Modifier.weight(1f))
            IconBtn(Ic.Undo, "تراجع") { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); tick++ } }
            Spacer(Modifier.width(6.dp))
            PillButton("حفظ", Modifier.width(110.dp).height(44.dp)) {
                if (strokes.isEmpty() || size.width == 0 || size.height == 0) onCancel()
                else {
                    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    c.drawColor(android.graphics.Color.WHITE)
                    render(c, strokes)
                    onSave(bmp)
                }
            }
        }
        Box(
            Modifier.weight(1f).padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val s = Stroke(color, width)
                            s.path.moveTo(o.x, o.y); s.path.lineTo(o.x + 0.1f, o.y)
                            strokes.add(s); tick++
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            strokes.lastOrNull()?.path?.lineTo(ch.position.x, ch.position.y)
                            tick++
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val t = tick
                drawIntoCanvas { render(it.nativeCanvas, strokes) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c)).border(if (color == c) 3.dp else 1.dp, if (color == c) cs.primary else cs.outlineVariant, CircleShape)
                        .clickable { color = c }
                )
            }
            Spacer(Modifier.weight(1f))
            listOf(4f, 8f, 16f).forEach { w ->
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (width == w) cs.primaryContainer else Color.Transparent).clickable { width = w }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size((w + 4).dp).clip(CircleShape).background(cs.onSurface))
                }
            }
        }
    }
}
