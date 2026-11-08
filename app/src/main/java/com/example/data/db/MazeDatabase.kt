package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LevelProgressEntity::class, DailyChallengeEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MazeDatabase : RoomDatabase() {
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun dailyChallengeDao(): DailyChallengeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_challenge` (
                        `dateInt` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `bestTimeMillis` INTEGER NOT NULL,
                        `minMoves` INTEGER NOT NULL,
                        `stars` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateInt`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: MazeDatabase? = null

        fun getInstance(context: Context): MazeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MazeDatabase::class.java,
                    "as_maze.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
