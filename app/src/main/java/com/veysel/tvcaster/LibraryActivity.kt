package com.veysel.tvcaster

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.veysel.tvcaster.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Son kullanilan medyalar / Oynatma listesi / Sik kullanilanlar /
 * Tarayici gecmisi / En cok ziyaret edilenler listelerini gosteren ekran.
 * "type" parametresine gore ilgili listeyi yukler.
 */
class LibraryActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        val type = intent.getStringExtra("type") ?: "recent"
        val titleText = intent.getStringExtra("title") ?: getString(R.string.app_name)
        findViewById<TextView>(R.id.headerTv).text = titleText

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)

        val db = AppDatabase.get(this)

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            uiScope.launch {
                try {
                    clearAll(db, type)
                    loadList(db, type, recycler)
                    Toast.makeText(this@LibraryActivity, getString(R.string.list_cleared), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
        }

        loadList(db, type, recycler)
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun loadList(db: AppDatabase, type: String, recycler: RecyclerView) {
        uiScope.launch {
            val entries = try {
                fetch(db, type)
            } catch (_: Exception) {
                LinkedHashMap()
            }

            if (entries.isEmpty()) {
                Toast.makeText(this@LibraryActivity, getString(R.string.empty_list), Toast.LENGTH_SHORT).show()
            }

            recycler.adapter = VideoAdapter(
                entries,
                onClick = { url, _ ->
                    val i = Intent(this@LibraryActivity, MainActivity::class.java)
                    i.putExtra("url", url)
                    startActivity(i)
                },
                onLongClick = { url, _ ->
                    uiScope.launch {
                        try {
                            deleteUrl(db, type, url)
                            loadList(db, type, recycler)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
        }
    }

    private suspend fun fetch(db: AppDatabase, type: String): LinkedHashMap<String, String> {
        val entries = LinkedHashMap<String, String>()
        when (type) {
            "recent" -> db.recentDao().all().forEach { entries[it.url] = it.title }
            "history" -> db.historyDao().all().forEach { entries[it.url] = it.title }
            "favorites" -> db.favDao().all().forEach { entries[it.url] = it.title }
            "playlist" -> db.playlistDao().all().forEach { entries[it.url] = it.title }
            "top" -> db.topDao().all().forEach { entries[it.url] = it.title + " (" + it.count + ")" }
        }
        return entries
    }

    private suspend fun deleteUrl(db: AppDatabase, type: String, url: String) {
        when (type) {
            "recent" -> db.recentDao().deleteUrl(url)
            "history" -> db.historyDao().deleteUrl(url)
            "favorites" -> db.favDao().deleteUrl(url)
            "playlist" -> db.playlistDao().deleteUrl(url)
            "top" -> db.topDao().deleteUrl(url)
        }
    }

    private suspend fun clearAll(db: AppDatabase, type: String) {
        when (type) {
            "recent" -> db.recentDao().clear()
            "history" -> db.historyDao().clear()
            "favorites" -> db.favDao().clear()
            "playlist" -> db.playlistDao().clear()
            "top" -> db.topDao().clear()
        }
    }
}
