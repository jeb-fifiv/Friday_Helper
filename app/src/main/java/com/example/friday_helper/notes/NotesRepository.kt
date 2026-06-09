package com.example.friday_helper.notes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.example.friday_helper.security.SecurePrefs
import com.example.friday_helper.security.SecurityRepository

class NotesRepository(private val context: Context) {
    private val sec = SecurityRepository(context)
    private val plainPrefs = context.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
    private val securePrefs by lazy { SecurePrefs.encrypted(context, PREFS_SECURE) }
    private val prefs: SharedPreferences = choosePrefsAndMigrate()

    private fun choosePrefsAndMigrate(): SharedPreferences {
        val wantSecure = sec.isNotesEncryptionEnabled()
        val plainHas = plainPrefs.contains(KEY_FOLDERS) || plainPrefs.contains(KEY_NOTES)
        val secureHas = securePrefs.contains(KEY_FOLDERS) || securePrefs.contains(KEY_NOTES)

        // Migrate data when toggling encryption
        if (wantSecure && plainHas) {
            val folders = plainPrefs.getString(KEY_FOLDERS, "[]")
            val notes = plainPrefs.getString(KEY_NOTES, "[]")
            securePrefs.edit().putString(KEY_FOLDERS, folders).putString(KEY_NOTES, notes).apply()
            plainPrefs.edit().remove(KEY_FOLDERS).remove(KEY_NOTES).apply()
            return securePrefs
        }
        if (!wantSecure && secureHas) {
            val folders = securePrefs.getString(KEY_FOLDERS, "[]")
            val notes = securePrefs.getString(KEY_NOTES, "[]")
            plainPrefs.edit().putString(KEY_FOLDERS, folders).putString(KEY_NOTES, notes).apply()
            securePrefs.edit().remove(KEY_FOLDERS).remove(KEY_NOTES).apply()
            return plainPrefs
        }
        return if (wantSecure) securePrefs else plainPrefs
    }

    fun getFolders(): List<Folder> {
        val arr = JSONArray(prefs.getString(KEY_FOLDERS, "[]"))
        val result = mutableListOf<Folder>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(Folder(o.getLong("id"), o.getString("name")))
        }
        return result
    }

    fun getNotes(): List<Note> {
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        val result = mutableListOf<Note>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(
                Note(
                    id = o.getLong("id"),
                    title = o.optString("title"),
                    text = o.optString("text"),
                    folderId = if (o.isNull("folderId")) null else o.getLong("folderId")
                )
            )
        }
        return result
    }

    fun addFolder(name: String): Folder {
        val folder = Folder(id = System.currentTimeMillis(), name = name.trim())
        val arr = JSONArray(prefs.getString(KEY_FOLDERS, "[]"))
        arr.put(JSONObject().apply {
            put("id", folder.id)
            put("name", folder.name)
        })
        prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply()
        return folder
    }

    fun addOrUpdateNote(note: Note): Note {
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        var updated = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("id") == note.id) {
                arr.put(i, note.toJson())
                updated = true
                break
            }
        }
        if (!updated) {
            arr.put(note.toJson())
        }
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply()
        return note
    }

    fun deleteNote(id: Long) {
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("id") != id) out.put(o)
        }
        prefs.edit().putString(KEY_NOTES, out.toString()).apply()
    }

    fun deleteFolder(id: Long) {
        // remove folder
        val arr = JSONArray(prefs.getString(KEY_FOLDERS, "[]"))
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("id") != id) out.put(o)
        }
        prefs.edit().putString(KEY_FOLDERS, out.toString()).apply()
        // unassign folder from notes
        val notes = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            if (!o.isNull("folderId") && o.getLong("folderId") == id) {
                o.put("folderId", JSONObject.NULL)
            }
        }
        prefs.edit().putString(KEY_NOTES, notes.toString()).apply()
    }

    private fun Note.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("text", text)
        if (folderId == null) put("folderId", JSONObject.NULL) else put("folderId", folderId)
    }

    companion object {
        private const val PREFS_PLAIN = "notes_data"
        private const val PREFS_SECURE = "notes_data_secure"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_NOTES = "notes"
    }
}

