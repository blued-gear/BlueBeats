package apps.chocolatecakecodes.bluebeats.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.*
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.*
import java.util.concurrent.atomic.AtomicReference

@Database(version = 4, entities = [
    MediaDirEntity::class, MediaFileEntity::class,
    ID3TagEntity::class, UserTagEntity::class, UserTagRelation::class,
    PlaylistName::class,
    StaticPlaylistEntity::class, StaticPlaylistEntry::class,
    DynamicPlaylistEntity::class,
    RuleGroupEntity::class, RuleGroupEntry::class,
    IncludeRuleEntity::class, IncludeRuleFileEntry::class, IncludeRuleDirEntry::class,
    UsertagsRuleEntity::class, UsertagsRuleEntry::class
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
        udRowValues.put("id", MediaNode.UNSPECIFIED_DIR.entity.id)
        udRowValues.put("name", MediaNode.UNSPECIFIED_DIR.entity.name)
        udRowValues.put("parent", MediaNode.UNSPECIFIED_DIR.entity.parent)
        db.insert("MediaDirEntity", SQLiteDatabase.CONFLICT_FAIL, udRowValues)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {

    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {

    }
}