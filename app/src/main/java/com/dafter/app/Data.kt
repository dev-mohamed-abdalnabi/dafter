package com.dafter.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Att(val type: String, val file: String) // img | audio | draw
data class Folder(val id: String, val name: String)
data class Note(
    val id: String, val title: String = "", val body: String = "", val folder: String = "",
    val pinned: Boolean = false, val priv: Boolean = false, val trashed: Boolean = false,
    val created: Long = System.currentTimeMillis(), val updated: Long = created,
    val atts: List<Att> = emptyList()
)
data class Task(val id: String, val title: String, val due: Long = 0, val done: Boolean = false, val star: Boolean = false)

object Store {
    val notes = mutableStateListOf<Note>()
    val folders = mutableStateListOf<Folder>()
    val tasks = mutableStateListOf<Task>()
    var cur by mutableStateOf("all")      // all | none | private | trash | folderId
    var resume by mutableIntStateOf(0)
    private var f: File? = null
    private var loaded = false

    fun uid() = UUID.randomUUID().toString().substring(0, 8)
    fun media(ctx: Context) = File(ctx.filesDir, "m").apply { mkdirs() }

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        f = File(ctx.filesDir, "data.json")
        try {
            val o = JSONObject(f!!.readText())
            o.optJSONArray("folders")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { folders.add(Folder(it.getString("id"), it.getString("name"))) }
            }
            o.optJSONArray("notes")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { j ->
                    val at = ArrayList<Att>()
                    j.optJSONArray("a")?.let { b ->
                        for (k in 0 until b.length()) b.getJSONObject(k).let { at.add(Att(it.getString("t"), it.getString("f"))) }
                    }
                    notes.add(
                        Note(
                            j.getString("id"), j.optString("title"), j.optString("body"), j.optString("folder"),
                            j.optBoolean("pin"), j.optBoolean("priv"), j.optBoolean("tr"),
                            j.optLong("c"), j.optLong("u"), at
                        )
                    )
                }
            }
            o.optJSONArray("tasks")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let {
                    tasks.add(Task(it.getString("id"), it.getString("t"), it.optLong("d"), it.optBoolean("x"), it.optBoolean("s")))
                }
            }
        } catch (_: Exception) {
        }
    }

    fun save() {
        val o = JSONObject()
        o.put("folders", JSONArray().apply { folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        o.put("notes", JSONArray().apply {
            notes.forEach { n ->
                put(
                    JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("folder", n.folder)
                        .put("pin", n.pinned).put("priv", n.priv).put("tr", n.trashed).put("c", n.created).put("u", n.updated)
                        .put("a", JSONArray().apply { n.atts.forEach { put(JSONObject().put("t", it.type).put("f", it.file)) } })
                )
            }
        })
        o.put("tasks", JSONArray().apply {
            tasks.forEach { put(JSONObject().put("id", it.id).put("t", it.title).put("d", it.due).put("x", it.done).put("s", it.star)) }
        })
        try {
            val file = f ?: return
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        } catch (_: Exception) {
        }
    }

    fun put(n: Note) {
        val i = notes.indexOfFirst { it.id == n.id }
        if (i >= 0) notes[i] = n else notes.add(0, n)
        save()
    }

    fun purge(ctx: Context, n: Note) {
        n.atts.forEach { File(media(ctx), it.file).delete() }
        notes.removeAll { it.id == n.id }
        save()
    }

    fun setTask(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) tasks[i] = t
        save()
    }

    fun renameFolder(id: String, name: String) {
        val i = folders.indexOfFirst { it.id == id }
        if (i >= 0) folders[i] = Folder(id, name)
        save()
    }

    fun deleteFolder(id: String) {
        for (i in notes.indices) if (notes[i].folder == id) notes[i] = notes[i].copy(folder = "")
        folders.removeAll { it.id == id }
        if (cur == id) cur = "all"
        save()
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("p", 0)
    fun svc(c: Context) = sp(c).getBoolean("svc", true)
    fun setSvc(c: Context, v: Boolean) = sp(c).edit().putBoolean("svc", v).apply()
    fun grid(c: Context) = sp(c).getBoolean("grid", true)
    fun setGrid(c: Context, v: Boolean) = sp(c).edit().putBoolean("grid", v).apply()
}

fun fmtDate(t: Long): String {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = t }
    val same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (same) "h:mm a" else "d MMM", Locale("ar")).format(Date(t))
}

fun fmtDue(t: Long): String = SimpleDateFormat("EEE d MMM، h:mm a", Locale("ar")).format(Date(t))

/** Smart task parsing: "اتصل بأحمد بكرة الساعة 5 م" -> title + due time. */
object Smart {
    private val timeRx = Regex("(?:الساعة|الساعه|ساعة|ساعه)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(صباحا|صباحاً|الصبح|ص|مساء|مساءً|بالليل|العصر|م)?")
    private val relRx = Regex("بعد\\s*(\\d+)\\s*(دقيقة|دقيقه|دقايق|ساعة|ساعه|ساعات|يوم|أيام|ايام)")

    fun parse(input: String): Pair<String, Long> {
        var s = input.trim()
        val now = System.currentTimeMillis()
        relRx.find(s)?.let { m ->
            val n = m.groupValues[1].toLong()
            val u = m.groupValues[2]
            val ms = when {
                u.startsWith("د") -> 60_000L
                u.startsWith("س") -> 3_600_000L
                else -> 86_400_000L
            }
            s = s.replace(m.value, "").trim()
            return Pair(s.ifBlank { input.trim() }, now + n * ms)
        }
        var off = -1
        fun cut(vararg w: String): Boolean {
            for (x in w) {
                val p = " $s "
                if (p.contains(" $x ")) {
                    s = p.replaceFirst(" $x ", " ").trim().replace(Regex("\\s+"), " ")
                    return true
                }
            }
            return false
        }
        if (cut("بعد بكرة", "بعد بكره")) off = 2
        else if (cut("بكرة", "بكره", "غدا", "غداً")) off = 1
        else if (cut("النهارده", "النهاردة", "اليوم", "الليلة", "الليله")) off = 0
        var h = -1
        var min = 0
        timeRx.find(s)?.let { m ->
            val hh = m.groupValues[1].toInt()
            min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val suf = m.groupValues[3]
            val pm = suf in setOf("مساء", "مساءً", "بالليل", "العصر", "م")
            val am = suf in setOf("صباحا", "صباحاً", "الصبح", "ص")
            h = when {
                pm -> if (hh < 12) hh + 12 else hh
                am -> if (hh == 12) 0 else hh
                hh in 1..6 -> hh + 12
                else -> hh
            }
            if (h > 23 || min > 59) h = -1
            s = s.replace(m.value, "").trim().replace(Regex("\\s+"), " ")
        }
        if (off == -1 && h == -1) return Pair(input.trim(), 0L)
        val c = Calendar.getInstance()
        if (off > 0) c.add(Calendar.DAY_OF_YEAR, off)
        if (h == -1) {
            if (off == 0) c.timeInMillis = now + 3_600_000L
            else { c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0) }
        } else {
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
        }
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (off == -1 && c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return Pair(s.ifBlank { input.trim() }, c.timeInMillis)
    }
}
