package com.example.sasalele.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `workAccountId` TEXT NOT NULL, `clientId` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `nextScheduleTime` INTEGER NOT NULL, `status` TEXT NOT NULL, `messagesJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN lastDailyReminderTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminders ADD COLUMN closedAt INTEGER NOT NULL DEFAULT 0")
        // Length-prefixed account prevents underscore collisions. Preserve all other fields.
        db.execSQL("UPDATE reminders SET id = length(workAccountId) || ':' || workAccountId || clientId")
    }
}

@Database(entities = [MaterialEntity::class, GuidanceMessageEntity::class, ReminderEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun guidanceMessageDao(): GuidanceMessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sasalele_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
