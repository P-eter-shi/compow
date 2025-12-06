package com.example.compow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ContactEntity::class,
        UserEntity::class,
        AlertLogEntity::class
    ],
    version = 2, // Incremented version for schema changes
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun userDao(): UserDao
    abstract fun alertLogDao(): AlertLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 1 to version 2
         * Changes:
         * - Removed yearOfStudy field from users table
         * - Removed courseOfStudy field from users table
         * - Added googleId field to users table
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new users table with updated schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users_new (
                        userId TEXT PRIMARY KEY NOT NULL,
                        full_name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        phone_number TEXT NOT NULL,
                        profile_picture_uri TEXT,
                        google_id TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Copy data from old table to new table
                // Only copy fields that exist in both schemas
                db.execSQL(
                    """
                    INSERT INTO users_new (userId, full_name, email, phone_number, profile_picture_uri, google_id, created_at)
                    SELECT userId, full_name, email, phone_number, profile_picture_uri, NULL, created_at
                    FROM users
                    """.trimIndent()
                )

                // Drop old table
                db.execSQL("DROP TABLE IF EXISTS users")

                // Rename new table to original name
                db.execSQL("ALTER TABLE users_new RENAME TO users")

                // Recreate indices if any (add here if you had indices on users table)
                // Example: database.execSQL("CREATE INDEX IF NOT EXISTS index_users_email ON users(email)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "compow_database"
                )
                    // Add migration instead of destructive migration for production
                    .addMigrations(MIGRATION_1_2)
                    // Fallback only for development - REMOVE in production
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Populate database with initial data on first creation
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(database)
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Called every time database is opened
            // Can be used for data validation or cleanup
        }

        suspend fun populateDatabase(database: AppDatabase) {
            val contactDao = database.contactDao()

            // Add default emergency services contact
            // This will be available to all users in the Community category
            try {
                contactDao.insertContact(
                    ContactEntity(
                        name = "Emergency Services (Kenya)",
                        phoneNumber = "+254999",
                        category = ContactCategory.COMMUNITY,
                        isEnabled = false // Disabled by default, user can enable
                    )
                )

                // Add more default emergency contacts for Kenya
                contactDao.insertContact(
                    ContactEntity(
                        name = "Police Emergency",
                        phoneNumber = "+254999",
                        category = ContactCategory.COMMUNITY,
                        isEnabled = false
                    )
                )

                contactDao.insertContact(
                    ContactEntity(
                        name = "Ambulance Services",
                        phoneNumber = "+254999",
                        category = ContactCategory.COMMUNITY,
                        isEnabled = false
                    )
                )
            } catch (e: Exception) {
                // Handle duplicate entries silently
                // This prevents crash if database is reset and populated again
            }
        }
    }
}