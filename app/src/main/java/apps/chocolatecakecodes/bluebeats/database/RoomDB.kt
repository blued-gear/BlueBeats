package apps.chocolatecakecodes.bluebeats.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import apps.chocolatecakecodes.bluebeats.database.migrations.Migration6to7
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistName
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistsManager
import apps.chocolatecakecodes.bluebeats.media.playlist.StaticPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.StaticPlaylistEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.StaticPlaylistEntry
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylistEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ID3TagsRuleEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ID3TagsRuleEntry
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRuleDirEntry
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRuleEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRuleFileEntry
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RegexRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RegexRuleEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroupEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroupEntry
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.UsertagsRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.UsertagsRuleEntity
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.UsertagsRuleEntry
import java.util.concurrent.atomic.AtomicReference

@Database(version = 7, entities = [
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
    RegexRuleEntity::class
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
    internal abstract fun staticPlaylistDao(): StaticPlaylist.StaticPlaylistDao
    internal abstract fun dynamicPlaylistDao(): DynamicPlaylist.DynamicPlaylistDAO

    internal abstract fun dplRuleGroupDao(): RuleGroup.RuleGroupDao
    internal abstract fun dplIncludeRuleDao(): IncludeRule.IncludeRuleDao
    internal abstract fun dplUsertagsRuleDao(): UsertagsRule.UsertagsRuleDao
    internal abstract fun dplID3TagsRuleDao(): ID3TagsRule.ID3TagsRuleDao
    internal abstract fun dplRegexRuleDao(): RegexRule.RegexRuleDao
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
        udRowValues.put("id", MediaNode.UNSPECIFIED_DIR.entityId)
        udRowValues.put("name", MediaNode.UNSPECIFIED_DIR.name)
        udRowValues.put("parent", MediaNode.NULL_PARENT_ID)
        db.insert("MediaDirEntity", SQLiteDatabase.CONFLICT_FAIL, udRowValues)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {

    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {

    }
}