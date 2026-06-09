package com.example.friday_helper.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.doAfterTextChanged
import com.example.friday_helper.R
import com.example.friday_helper.settings.SettingsRepository
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var repo: SettingsRepository
    private val items = mutableListOf<ContactRow>()
    private val allItems = mutableListOf<ContactRow>()
    private lateinit var adapter: ContactsAdapter
    private var currentQuery: String = ""

    private val contactsPermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadContacts()
            } else {
                Toast.makeText(this, R.string.toast_contacts_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_contacts)

        contactsPermissionLauncher
        repo = SettingsRepository(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<View>(R.id.rootContacts)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val recycler = findViewById<RecyclerView>(R.id.contactsRecycler)
        val searchInput = findViewById<EditText>(R.id.contactsSearchInput)
        val searchWrap = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.contactsSearchWrap)

        adapter = ContactsAdapter(items) { row -> editAliases(row) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        searchInput.doAfterTextChanged {
            currentQuery = it?.toString().orEmpty()
            applyFilter()
        }

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(root, cfg)
            ThemeApplier.applyFont(root, this, cfg.fontResId)
            val textColor = if (cfg.backgroundColor == Color.WHITE) Color.BLACK else Color.WHITE
            findViewById<TextView>(R.id.tvTitle).setTextColor(textColor)
            findViewById<TextView>(R.id.contactsEmpty).setTextColor(textColor)
            searchWrap.boxBackgroundColor = cfg.backgroundColor
            searchWrap.setBoxStrokeColorStateList(ColorStateList.valueOf(textColor))
            searchWrap.defaultHintTextColor = ColorStateList.valueOf(textColor)
            searchInput.setTextColor(textColor)
            searchInput.setHintTextColor(ColorStateList.valueOf(textColor))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val aliasesMap = repo.getContactAliasesMap()
                val out = mutableListOf<ContactRow>()
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
                )?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val seen = hashSetOf<String>()
                    while (nameIdx >= 0 && numIdx >= 0 && c.moveToNext()) {
                        val name = c.getString(nameIdx).orEmpty()
                        val phone = c.getString(numIdx).orEmpty()
                        if (name.isBlank() || phone.isBlank()) continue
                        val key = SettingsRepository.contactAliasKey(name, phone)
                        if (!seen.add(key)) continue
                        out += ContactRow(name.trim(), phone.trim(), aliasesMap[key].orEmpty())
                    }
                }
                out
            }
            items.clear()
            allItems.clear()
            allItems.addAll(list)
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = currentQuery.trim().lowercase()
        val filtered = if (q.isBlank()) {
            allItems
        } else {
            allItems.filter { row ->
                row.name.lowercase().contains(q) ||
                    row.phone.contains(q) ||
                    row.aliases.any { it.lowercase().contains(q) }
            }
        }
        items.clear()
        items.addAll(filtered)
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.contactsEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun editAliases(row: ContactRow) {
        val input = EditText(this).apply {
            setText(row.aliases.joinToString(", "))
            hint = getString(R.string.contacts_aliases_hint)
            setPadding(24, 24, 24, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.contacts_aliases_edit_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val aliases = input.text?.toString().orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                repo.setAliasesForContact(SettingsRepository.contactAliasKey(row.name, row.phone), aliases)
                row.aliases = aliases
                applyFilter()
                Toast.makeText(this, R.string.contacts_aliases_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    data class ContactRow(val name: String, val phone: String, var aliases: List<String>)

    class ContactsAdapter(
        private val items: List<ContactRow>,
        private val onEditAliases: (ContactRow) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_alias, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.phone.text = item.phone
            holder.aliases.text = if (item.aliases.isEmpty()) {
                holder.itemView.context.getString(R.string.contacts_aliases) + ": —"
            } else {
                holder.itemView.context.getString(R.string.contacts_aliases) + ": " + item.aliases.joinToString(", ")
            }
            holder.editBtn.setOnClickListener { onEditAliases(item) }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.contactName)
            val phone: TextView = v.findViewById(R.id.contactPhone)
            val aliases: TextView = v.findViewById(R.id.contactAliases)
            val editBtn: Button = v.findViewById(R.id.editAliasesBtn)
        }
    }
}
