package com.dafter.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        enableEdgeToEdge()
        if (Prefs.svc(this)) Svc.start(this)
        setContent { AppTheme { Shell() } }
    }

    override fun onResume() {
        super.onResume()
        Store.resume++
    }
}

@Composable
fun Shell() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Note?>(null) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottom = if (imeOpen) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler(enabled = tab != 0 && editing == null) { tab = 0 }
    val tabs = remember { listOf(NavTab("ملاحظات", Ic.Notes), NavTab("مهام", Ic.Tasks), NavTab("الإعدادات", Ic.Settings)) }

    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(tab, Modifier.fillMaxSize(), label = "tab") { t ->
                when (t) {
                    0 -> NotesScreen(bottom) { editing = it }
                    1 -> TasksScreen(bottom)
                    else -> SettingsScreen(bottom)
                }
            }
            AnimatedVisibility(
                !imeOpen && editing == null, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, cs.surface)))
                        .navigationBarsPadding().padding(top = 28.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (tab == 0 && Store.cur != "trash") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                            PressFab(Ic.Plus, "ملاحظة جديدة") {
                                val cur = Store.cur
                                editing = Note(
                                    Store.uid(),
                                    folder = if (Store.folders.any { it.id == cur }) cur else "",
                                    priv = cur == "private"
                                )
                            }
                        }
                    }
                    FloatingNav(tabs, tab) { tab = it }
                }
            }
            AnimatedVisibility(editing != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                editing?.let { n -> key(n.id) { Editor(n) { editing = null } } }
            }
        }
    }
}

private class FolderDlg(val id: String?, val name: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(bottom: Dp, open: (Note) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(Prefs.grid(ctx)) }
    var menu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf("") }
    var fd by remember { mutableStateOf<FolderDlg?>(null) }
    var moveFor by remember { mutableStateOf<Note?>(null) }
    val sel = Store.cur
    BackHandler(enabled = sel != "all") { Store.cur = "all" }

    val list = Store.notes.filter { n ->
        val inSel = when (sel) {
            "trash" -> n.trashed
            "private" -> n.priv && !n.trashed
            else -> !n.trashed && !n.priv && (sel == "all" || (sel == "none" && n.folder.isEmpty()) || n.folder == sel)
        }
        inSel && (q.isBlank() || n.title.contains(q, true) || n.body.contains(q, true))
    }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })

    val title = when (sel) {
        "all" -> "الملاحظات"; "none" -> "غير مصنف"; "private" -> "الخاصة"; "trash" -> "سلة المحذوفات"
        else -> Store.folders.firstOrNull { it.id == sel }?.name ?: "الملاحظات"
    }

    Box(Modifier.fillMaxSize().glow(cs.primaryContainer, 0.72f)) {
        LazyVerticalGrid(
            GridCells.Fixed(if (grid) 2 else 1), Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
            item(span = full) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("دفتر · ${list.size} ملاحظة", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    Box {
                        CookieIcon(Ic.Menu, 44.dp, cs.primary, cs.onPrimary, desc = "القائمة") { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("مجلد جديد") }, leadingIcon = { Icon(Ic.Folder, null, Modifier.size(20.dp)) }, onClick = { menu = false; fd = FolderDlg(null, "") })
                            DropdownMenuItem(text = { Text("الملاحظات الخاصة") }, leadingIcon = { Icon(Ic.Lock, null, Modifier.size(20.dp)) }, onClick = { menu = false; Store.cur = "private" })
                            DropdownMenuItem(text = { Text("سلة المحذوفات") }, leadingIcon = { Icon(Ic.Trash, null, Modifier.size(20.dp)) }, onClick = { menu = false; Store.cur = "trash" })
                            DropdownMenuItem(text = { Text(if (grid) "عرض قائمة" else "عرض شبكة") }, leadingIcon = { Icon(Ic.Notes, null, Modifier.size(20.dp)) },
                                onClick = { menu = false; grid = !grid; Prefs.setGrid(ctx, grid) })
                            if (sel == "trash") DropdownMenuItem(text = { Text("تفريغ السلة", color = cs.error) }, leadingIcon = { Icon(Ic.Trash, null, Modifier.size(20.dp), tint = cs.error) },
                                onClick = { menu = false; Store.notes.filter { it.trashed }.forEach { Store.purge(ctx, it) } })
                        }
                    }
                }
            }
            item(span = full) {
                TextField(
                    q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp), colors = fieldColors(),
                    placeholder = { Text("ابحث في الملاحظات", maxLines = 1) },
                    leadingIcon = { Icon(Ic.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Ic.Close, "مسح") } }
                )
            }
            item(span = full) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FChip("الكل", sel == "all") { Store.cur = "all" } }
                    item { FChip("غير مصنف", sel == "none") { Store.cur = "none" } }
                    items(Store.folders.toList(), key = { it.id }) { f ->
                        FChip(f.name, sel == f.id, onLong = { fd = FolderDlg(f.id, f.name) }) { Store.cur = f.id }
                    }
                    item {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { fd = FolderDlg(null, "") }, contentAlignment = Alignment.Center) {
                            Icon(Ic.Plus, "مجلد جديد", Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            if (list.isEmpty()) item(span = full) {
                EmptyState(if (sel == "trash") Ic.Trash else Ic.Notes, if (q.isNotBlank()) "مفيش نتائج" else if (sel == "trash") "السلة فاضية" else "مفيش ملاحظات", if (q.isNotBlank()) "جرّب كلمة تانية." else if (sel == "trash") "الملاحظات المحذوفة بتظهر هنا." else "دوس + وابدأ اكتب.")
            }
            items(list, key = { it.id }) { n ->
                Box {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)
                            .combinedClickable(onLongClick = { cardMenu = n.id }, onClick = { if (n.trashed) cardMenu = n.id else open(n) })
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val cover = n.atts.firstOrNull { it.type != "audio" }
                        if (cover != null) {
                            val bmp = remember(cover.file) { Media.decode(File(Store.media(ctx), cover.file).path, 500) }
                            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(if (grid) 110.dp else 150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        }
                        if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = if (grid) 4 else 2, overflow = TextOverflow.Ellipsis)
                        if (n.title.isBlank() && n.body.isBlank() && cover == null) Text("تسجيل صوتي", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(fmtDate(n.updated), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            if (n.atts.any { it.type == "audio" }) Icon(Ic.Mic, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                            if (n.pinned) Icon(Ic.Pin, null, Modifier.size(16.dp), tint = cs.primary)
                        }
                    }
                    DropdownMenu(cardMenu == n.id, { cardMenu = "" }) {
                        if (n.trashed) {
                            DropdownMenuItem(text = { Text("استعادة") }, leadingIcon = { Icon(Ic.Undo, null, Modifier.size(20.dp)) }, onClick = { cardMenu = ""; Store.put(n.copy(trashed = false)) })
                            DropdownMenuItem(text = { Text("حذف نهائي", color = cs.error) }, leadingIcon = { Icon(Ic.Trash, null, Modifier.size(20.dp), tint = cs.error) }, onClick = { cardMenu = ""; Store.purge(ctx, n) })
                        } else {
                            DropdownMenuItem(text = { Text(if (n.pinned) "إلغاء التثبيت" else "تثبيت") }, leadingIcon = { Icon(Ic.Pin, null, Modifier.size(20.dp)) }, onClick = { cardMenu = ""; Store.put(n.copy(pinned = !n.pinned)) })
                            DropdownMenuItem(text = { Text("نقل إلى مجلد") }, leadingIcon = { Icon(Ic.Folder, null, Modifier.size(20.dp)) }, onClick = { cardMenu = ""; moveFor = n })
                            DropdownMenuItem(text = { Text(if (n.priv) "إخراج من الخاصة" else "نقل للخاصة") }, leadingIcon = { Icon(Ic.Lock, null, Modifier.size(20.dp)) }, onClick = { cardMenu = ""; Store.put(n.copy(priv = !n.priv)) })
                            DropdownMenuItem(text = { Text("حذف", color = cs.error) }, leadingIcon = { Icon(Ic.Trash, null, Modifier.size(20.dp), tint = cs.error) }, onClick = { cardMenu = ""; Store.put(n.copy(trashed = true)) })
                        }
                    }
                }
            }
        }
    }

    fd?.let { d ->
        var name by remember(d) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { fd = null },
            title = { Text(if (d.id == null) "مجلد جديد" else "تعديل المجلد") },
            text = { TextField(name, { name = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(), placeholder = { Text("اسم المجلد") }) },
            confirmButton = {
                TextButton({
                    val nm = name.trim()
                    if (nm.isNotEmpty()) {
                        if (d.id == null) { val f = Folder(Store.uid(), nm); Store.folders.add(f); Store.cur = f.id; Store.save() }
                        else Store.renameFolder(d.id, nm)
                    }
                    fd = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                Row {
                    if (d.id != null) TextButton({ Store.deleteFolder(d.id); fd = null }) { Text("حذف", color = cs.error) }
                    TextButton({ fd = null }) { Text("إلغاء") }
                }
            }
        )
    }

    moveFor?.let { n ->
        AlertDialog(
            onDismissRequest = { moveFor = null },
            title = { Text("نقل إلى") },
            text = {
                Column {
                    (listOf(Folder("", "غير مصنف")) + Store.folders).forEach { f ->
                        Text(
                            f.name, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Store.put(n.copy(folder = f.id, priv = false)); moveFor = null }.padding(14.dp),
                            style = MaterialTheme.typography.titleSmall, color = if (n.folder == f.id) cs.primary else cs.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton({ moveFor = null }) { Text("إلغاء") } }
        )
    }
}

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun atTime(addDays: Int, h: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, addDays); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun TasksScreen(bottom: Dp) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var manual by remember { mutableLongStateOf(0L) }
    var dueMenu by remember { mutableStateOf(false) }
    var sub by remember { mutableIntStateOf(0) }
    val parsed = remember(text) { Smart.parse(text) }
    val due = if (parsed.second != 0L) parsed.second else manual

    fun add() {
        val t = parsed.first.trim()
        if (t.isEmpty()) return
        val task = Task(Store.uid(), t, due)
        Store.tasks.add(0, task); Store.save(); Remind.set(ctx, task)
        text = ""; manual = 0L
    }

    val now = System.currentTimeMillis()
    val today = dayStart(now)
    val day = 86_400_000L
    fun grp(t: Task) = when {
        t.due == 0L -> 4
        t.due < now -> 0
        t.due < today + day -> 1
        t.due < today + 2 * day -> 2
        else -> 3
    }
    val names = listOf("متأخرة", "النهارده", "بكرة", "بعد كده", "بدون موعد")
    val active = Store.tasks.filter { !it.done }.sortedWith(compareBy<Task>({ grp(it) }, { !it.star }, { it.due }))
    val done = Store.tasks.filter { it.done }

    Box(Modifier.fillMaxSize().glow(cs.tertiaryContainer, 0.68f)) {
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("المهام", style = MaterialTheme.typography.headlineMedium)
                        Text("دفتر · ${active.size} مهمة", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    CookieIcon(Ic.Tasks, 44.dp, cs.tertiary, cs.onTertiary)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                TextField(
                    text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp), colors = fieldColors(),
                    placeholder = { Text("مهمة جديدة… مثلاً: اتصل بأحمد بكرة الساعة 5 م", maxLines = 1) },
                    leadingIcon = {
                        Box {
                            IconBtn(Ic.Clock, "موعد", tint = if (due != 0L) cs.primary else cs.onSurfaceVariant) { dueMenu = true }
                            DropdownMenu(dueMenu, { dueMenu = false }) {
                                listOf(
                                    "بعد ساعة" to System.currentTimeMillis() + 3_600_000L,
                                    "الليلة 9 م" to (atTime(0, 21).let { if (it > System.currentTimeMillis()) it else atTime(1, 21) }),
                                    "بكرة 9 ص" to atTime(1, 9),
                                    "بعد أسبوع" to atTime(7, 9),
                                    "بدون موعد" to 0L
                                ).forEach { (l, v) -> DropdownMenuItem(text = { Text(l) }, onClick = { manual = v; dueMenu = false }) }
                            }
                        }
                    },
                    trailingIcon = { IconBtn(Ic.Plus, "إضافة", tint = cs.onPrimary, bg = cs.primary) { add() } }
                )
                if (due != 0L) Text("⏰ ${fmtDue(due)}", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Spacer(Modifier.height(14.dp))
                Switcher(listOf("القادمة", "تمت"), sub) { sub = it }
            }
            if (sub == 0) {
                if (active.isEmpty()) item { EmptyState(Ic.Tasks, "مفيش مهام", "اكتب أول مهمة فوق.") }
                active.groupBy { grp(it) }.toSortedMap().forEach { (g, ts) ->
                    item(key = "h$g") { SectionTitle(names[g]) }
                    items(ts, key = { it.id }) { t -> TaskRow(t) }
                }
            } else {
                if (done.isEmpty()) item { EmptyState(Ic.Check, "لسه مخلصتش حاجة", "المهام المنتهية بتظهر هنا.") }
                else {
                    items(done, key = { it.id }) { t -> TaskRow(t) }
                    item {
                        Spacer(Modifier.height(16.dp))
                        PillButton("مسح المنتهية", Modifier.fillMaxWidth(), tonal = true) {
                            done.forEach { Remind.cancel(ctx, it) }
                            Store.tasks.removeAll { it.done }; Store.save()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskRow(t: Task) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val bg by androidx.compose.animation.animateColorAsState(if (t.done) cs.primary else Color.Transparent, label = "tk")
    val overdue = !t.done && t.due != 0L && t.due < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(bg).border(2.dp, if (t.done) cs.primary else cs.onSurfaceVariant, CircleShape)
                .clickable { val n = t.copy(done = !t.done); Store.setTask(n); Remind.set(ctx, n) },
            contentAlignment = Alignment.Center
        ) { if (t.done) Icon(Ic.Check, null, Modifier.size(18.dp), tint = cs.onPrimary) }
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, textDecoration = if (t.done) TextDecoration.LineThrough else null, color = if (t.done) cs.onSurfaceVariant else cs.onSurface)
            if (t.due != 0L) Text(fmtDue(t.due), style = MaterialTheme.typography.labelMedium, color = if (overdue) cs.error else cs.onSurfaceVariant)
        }
        IconBtn(Ic.Star, "مهم", tint = if (t.star) cs.primary else cs.onSurfaceVariant) { Store.setTask(t.copy(star = !t.star)) }
        IconBtn(Ic.Trash, "حذف", tint = cs.onSurfaceVariant) { Remind.cancel(ctx, t); Store.tasks.removeAll { it.id == t.id }; Store.save() }
    }
}

@Composable
fun SettingsScreen(bottom: Dp) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var svc by remember { mutableStateOf(Prefs.svc(ctx)) }
    var grid by remember { mutableStateOf(Prefs.grid(ctx)) }
    val r = Store.resume
    val overlay = remember(r) { Settings.canDrawOverlays(ctx) }
    val notif = remember(r) { ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    val battery = remember(r) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    fun status(ok: Boolean) = if (ok) "شغال ✓" else "اضغط للتفعيل"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom)) {
        item { Text("الإعدادات", Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp), style = MaterialTheme.typography.displaySmall) }
        item { SectionTitle("الملاحظة السريعة", true) }
        item {
            SettingRow(Ic.Notes, "ضغطتين على الباور", "تفتحلك نافذة تكتب فيها ملاحظة بسرعة", trailing = {
                Switch(svc, { svc = it; Prefs.setSvc(ctx, it); if (it) Svc.start(ctx) else Svc.stop(ctx) })
            })
        }
        item {
            SettingRow(Ic.Plus, "الظهور فوق التطبيقات", status(overlay), onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            })
        }
        item {
            SettingRow(Ic.Clock, "الإشعارات", status(notif), onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            })
        }
        item {
            SettingRow(Ic.Settings, "بدون توفير بطارية", status(battery), onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                } catch (_: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            })
        }
        item { Text("على شاومي: فعّل كمان «التشغيل التلقائي» و«عرض نوافذ منبثقة أثناء التشغيل في الخلفية» من إعدادات التطبيق.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        item { SectionTitle("العرض") }
        item {
            SettingRow(Ic.Notes, "عرض الملاحظات شبكة", "اقفلها لعرض قائمة", trailing = {
                Switch(grid, { grid = it; Prefs.setGrid(ctx, it) })
            })
        }
        item { Text("دفتر 1.0 · كل بياناتك محفوظة على موبايلك بس.", Modifier.padding(top = 28.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}
