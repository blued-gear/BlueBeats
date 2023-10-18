package apps.chocolatecakecodes.bluebeats.database

import android.database.Cursor

object DbUtils {
    inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
        val ret = ArrayList<T>(this.count)
        while(this.moveToNext())
            ret.add(transform(this))
        return ret
    }
}
