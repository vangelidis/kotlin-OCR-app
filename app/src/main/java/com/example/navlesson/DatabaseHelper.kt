package com.example.navlesson

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val link: String,
    val type: String // New column
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries")
    suspend fun getAllEntries(): List<Entry>

    @Query("SELECT * FROM entries WHERE text = :text LIMIT 1")
    suspend fun findEntryByText(text: String): Entry?

    @Insert
    suspend fun insertEntry(entry: Entry)
}

@Database(entities = [Entry::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // Recreate the database on each launch
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

suspend fun addEntryToDatabase(context: Context, text: String, link: String, type: String) {
    val db = AppDatabase.getDatabase(context)
    val newEntry = Entry(text = text, link = link, type = type)
    withContext(Dispatchers.IO) {
        db.entryDao().insertEntry(newEntry)
    }
}

suspend fun getAllEntriesFromDatabase(context: Context): List<Entry> {
    val db = AppDatabase.getDatabase(context)
    return withContext(Dispatchers.IO) {
        db.entryDao().getAllEntries()
    }
}


