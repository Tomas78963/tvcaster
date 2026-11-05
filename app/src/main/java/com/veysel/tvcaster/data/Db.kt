package com.veysel.tvcaster.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/* ---------------- Tablolar ---------------- */

@Entity(tableName = "recent_media")
data class RecentMedia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val addedAt: Long
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val addedAt: Long
)

@Entity(tableName = "playlist")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
    val addedAt: Long
)

@Entity(tableName = "top_sites", primaryKeys = ["url"])
data class TopSite(
    val url: String,
    val title: String,
    val count: Int
)

/* ---------------- DAO'lar ---------------- */

@Dao
interface RecentMediaDao {
    @Query("SELECT * FROM recent_media ORDER BY addedAt DESC LIMIT 100")
    suspend fun all(): List<RecentMedia>

    @Insert
    suspend fun insert(item: RecentMedia)

    @Query("DELETE FROM recent_media WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM recent_media")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 200")
    suspend fun all(): List<HistoryEntry>

    @Insert
    suspend fun insert(item: HistoryEntry)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC LIMIT 200")
    suspend fun all(): List<FavoriteItem>

    @Insert
    suspend fun insert(item: FavoriteItem)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY position ASC LIMIT 500")
    suspend fun all(): List<PlaylistItem>

    @Insert
    suspend fun insert(item: PlaylistItem)

    @Query("SELECT COUNT(*) FROM playlist")
    suspend fun count(): Int

    @Query("DELETE FROM playlist WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM playlist")
    suspend fun clear()
}

@Dao
interface TopSiteDao {
    @Query("SELECT * FROM top_sites ORDER BY count DESC LIMIT 100")
    suspend fun all(): List<TopSite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TopSite)

    @Query("UPDATE top_sites SET count = count + 1 WHERE url = :url")
    suspend fun increment(url: String): Int

    @Query("DELETE FROM top_sites WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM top_sites")
    suspend fun clear()
}

/* ---------------- Veritabani ---------------- */

@Database(
    entities = [
        RecentMedia::class,
        HistoryEntry::class,
        FavoriteItem::class,
        PlaylistItem::class,
        TopSite::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentMediaDao
    abstract fun historyDao(): HistoryDao
    abstract fun favDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun topDao(): TopSiteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tvcaster.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
