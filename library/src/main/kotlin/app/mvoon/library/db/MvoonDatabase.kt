package app.mvoon.library.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        PlaybackStateEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FolderSourceEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MvoonDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun playbackState(): PlaybackStateDao
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun folderSources(): FolderSourceDao

    companion object {
        fun create(context: Context): MvoonDatabase =
            Room.databaseBuilder(context.applicationContext, MvoonDatabase::class.java, "mvoon.db")
                // The media index is a cache rebuilt from storage, but user data must survive upgrades:
                // future schema changes ship explicit migrations.
                .build()
    }
}
