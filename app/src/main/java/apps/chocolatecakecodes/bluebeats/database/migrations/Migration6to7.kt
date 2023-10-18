package apps.chocolatecakecodes.bluebeats.database.migrations

import androidx.room.migration.Migration
import androidx.room.util.useCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import apps.chocolatecakecodes.bluebeats.database.DbUtils.map
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItemSerializer

object Migration6to7 : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        upgradeStaticPlaylistsToPlaylistItems(db)
    }

    private fun upgradeStaticPlaylistsToPlaylistItems(db: SupportSQLiteDatabase) {
        val newItems = readStaticPlEntriesWithFile(db).map {
            Pair(it.first, transformStaticPlEntryToItem(db, it.second))
        }

        changeStaticPlTableCols(db)

        writeStaticPlWithItem(db, newItems)
    }

    /**
     * @return List<Pair<entryId, fileId>>
     */
    private fun readStaticPlEntriesWithFile(db: SupportSQLiteDatabase): List<Pair<Long, Long>> {
        return SupportSQLiteQueryBuilder.builder("StaticPlaylistEntry").apply {
            this.columns(arrayOf("id", "media"))
        }.create().let {
            db.query(it)
        }.let { cursor ->
            cursor.useCursor {
                val colIdxId = cursor.getColumnIndexOrThrow("id")
                val colIdxMedia = cursor.getColumnIndexOrThrow("media")

                it.map {
                    Pair(
                        it.getLong(colIdxId),
                        it.getLong(colIdxMedia)
                    )
                }
            }
        }
    }

    private fun writeStaticPlWithItem(db: SupportSQLiteDatabase, entries: List<Pair<Long, String>>) {
        db.compileStatement("UPDATE StaticPlaylistEntry SET item = ? WHERE id = ?;").use { update ->
            entries.forEach { (id, item) ->
                update.bindString(1, item)
                update.bindLong(2, id)
                update.executeUpdateDelete()
            }
        }
    }

    private fun transformStaticPlEntryToItem(db: SupportSQLiteDatabase, fileId: Long): String {
        val fileItem = MediaFileItem(MediaFile.new(fileId,
            "", MediaFile.Type.OTHER, { MediaNode.UNSPECIFIED_DIR }))
        return PlaylistItemSerializer.INSTANCE.serialize(fileItem)
    }

    private fun changeStaticPlTableCols(db: SupportSQLiteDatabase) {
        // recreate table to remove 'media' col and add 'item' col
        // see schema
        db.execSQL("CREATE TABLE StaticPlaylistEntry__new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlist` INTEGER NOT NULL, `item` TEXT NOT NULL, `pos` INTEGER NOT NULL, FOREIGN KEY(`playlist`) REFERENCES `StaticPlaylistEntity`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT);")
        db.execSQL("INSERT INTO StaticPlaylistEntry__new (id, playlist, pos, item) SELECT id, playlist, pos, '' FROM StaticPlaylistEntry;")
        db.execSQL("DROP INDEX `index_StaticPlaylistEntry_playlist`;")
        db.execSQL("DROP TABLE StaticPlaylistEntry;")
        db.execSQL("ALTER TABLE StaticPlaylistEntry__new RENAME TO StaticPlaylistEntry;")
        db.execSQL("CREATE INDEX `index_StaticPlaylistEntry_playlist` ON StaticPlaylistEntry (playlist);")
    }
}