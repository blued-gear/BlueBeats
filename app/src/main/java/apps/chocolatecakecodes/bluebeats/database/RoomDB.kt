package apps.chocolatecakecodes.bluebeats.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.database.dao.media.ID3TagDAO
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.dao.media.UserTagsDAO
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.DynamicPlaylistDAO
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.ID3TagsRuleDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.IncludeRuleDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.PlaylistName
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.PlaylistsManager
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.RegexRuleDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.RuleGroupDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.StaticPlaylistDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.TimeSpanRuleDao
import apps.chocolatecakecodes.bluebeats.database.dao.playlists.UsertagsRuleDao
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagReferenceEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagTypeEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagValueEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaDirEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.UserTagEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.UserTagRelation
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.DynamicPlaylistEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ID3TagsRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ID3TagsRuleEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleDirEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleFileEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RegexRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RuleGroupEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RuleGroupEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.StaticPlaylistEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.StaticPlaylistEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.TimeSpanRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.UsertagsRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.UsertagsRuleEntry
import apps.chocolatecakecodes.bluebeats.database.migrations.Migration6to7
import java.util.concurrent.atomic.AtomicReference

@Database(
    version = 8,
    entities = [
        MediaDirEntity::class, MediaFileEntity::class,
        ID3TagTypeEntity::class, ID3TagValueEntity::class, ID3TagReferenceEntity::class,
        UserTagEntity::class, UserTagRelation::class,
        PlaylistName::class,
        StaticPlaylistEntity::class, StaticPlaylistEntry::class,
        DynamicPlaylistEntity::class,
        RuleGroupEntity::class, RuleGroupEntry::class,
        IncludeRuleEntity::class, IncludeRuleFileEntry::class, IncludeRuleDirEntry::class,
        UsertagsRuleEntity::class, UsertagsRuleEntry::class,
        ID3TagsRuleEntity::class, ID3TagsRuleEntry::class,
        RegexRuleEntity::class,
        TimeSpanRuleEntity::class
    ],
    autoMigrations = [
        AutoMigration(from = 7, to = 8)
    ])
internal abstract class RoomDB : RoomDatabase() {

    companion object{
        private var dbInstance: AtomicReference<RoomDB> = AtomicReference(null)

        val DB_INSTANCE: RoomDB
            get(){
                val dbInst = dbInstance.get()
                if(dbInst === null)
                    throw IllegalStateException("not initialized yet")
                return dbInst
            }

        fun init(context: Context){
            synchronized(dbInstance) {
                if (dbInstance.get() !== null)
                    return
                
                val dbInst = Room.databaseBuilder(context, RoomDB::class.java, "MediaDB.db")
                    .addCallback(DBUpgradeCallback())
                    .addMigrations(Migration6to7)
                    .build()
                dbInstance.set(dbInst)
            }
        }
    }

    internal abstract fun mediaDirDao(): MediaDirDAO
    internal abstract fun mediaFileDao(): MediaFileDAO
    internal abstract fun id3TagDao(): ID3TagDAO
    internal abstract fun userTagDao(): UserTagsDAO

    internal abstract fun playlistManager(): PlaylistsManager
    internal abstract fun staticPlaylistDao(): StaticPlaylistDao
    internal abstract fun dynamicPlaylistDao(): DynamicPlaylistDAO

    internal abstract fun dplRuleGroupDao(): RuleGroupDao
    internal abstract fun dplIncludeRuleDao(): IncludeRuleDao
    internal abstract fun dplUsertagsRuleDao(): UsertagsRuleDao
    internal abstract fun dplID3TagsRuleDao(): ID3TagsRuleDao
    internal abstract fun dplRegexRuleDao(): RegexRuleDao
    internal abstract fun dplTimeSpanRuleDao(): TimeSpanRuleDao
}

private class DBUpgradeCallback : RoomDatabase.Callback(){

    override fun onCreate(db: SupportSQLiteDatabase) {
        // insert NULL dir
        val nullRowValues = ContentValues(3)
        nullRowValues.put("id", MediaNode.NULL_PARENT_ID)
        nullRowValues.put("name", "~NULL~")
        nullRowValues.put("parent", MediaNode.NULL_PARENT_ID)
        db.insert("MediaDirEntity", SQLiteDatabase.CONFLICT_FAIL, nullRowValues)

        // insert UNSPECIFIED_DIR
        val udRowValues = ContentValues(3)
        udRowValues.put("id", MediaNode.UNSPECIFIED_NODE_ID)
        udRowValues.put("name", MediaNode.UNSPECIFIED_DIR.name)
        udRowValues.put("parent", MediaNode.NULL_PARENT_ID)
        db.insert("MediaDirEntity", SQLiteDatabase.CONFLICT_FAIL, udRowValues)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {

    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {

    }
}