package apps.chocolatecakecodes.bluebeats.database.maintenance

import android.util.Log
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.database.RoomDB

internal class PlaylistRuleGarbageCollector {

    private val room = RoomDB.DB_INSTANCE

    fun run() {
        Log.d("PlaylistRuleGarbageCollector", "starting GC")

        val rules = gatherRules().associateBy(this::itemKey).toMutableMap()
        markUnused(gatherGCRoots(), rules)

        rules.values.forEach {
            Log.i("PlaylistRuleGarbageCollector", "removing unused rule ${it.rule}")
            it.delete()
        }

        Log.d("PlaylistRuleGarbageCollector", "GC done")
    }

    private fun gatherRules(): List<RuleItem> {
        return listOf(
            room.dplID3TagsRuleDao().loadAll().map { RuleItem(it.id, it, { room.dplID3TagsRuleDao().delete(it) }) },
            room.dplIncludeRuleDao().loadAll().map { RuleItem(it.id, it, { room.dplIncludeRuleDao().delete(it) }) },
            room.dplRegexRuleDao().loadAll().map { RuleItem(it.id, it, { room.dplRegexRuleDao().delete(it) }) },
            room.dplRuleGroupDao().loadAll().map { RuleItem(it.id, it, { room.dplRuleGroupDao().delete(it) }) },
            room.dplTimeSpanRuleDao().loadAll().map { RuleItem(it.id, it, { room.dplTimeSpanRuleDao().delete(it) }) },
            room.dplUsertagsRuleDao().loadAll().map { RuleItem(it.id, it, { room.dplUsertagsRuleDao().delete(it) }) },
        ).flatten()
    }

    private fun gatherGCRoots(): List<DynamicPlaylist> {
        return room.playlistManager().listAllPlaylistsWithType(PlaylistType.DYNAMIC).map {
            room.dynamicPlaylistDao().load(it.value)
        }
    }

    private fun markUnused(roots: List<DynamicPlaylist>, rules: MutableMap<Int, RuleItem>) {
        roots.forEach { pl ->
            processRule(pl.rootRuleGroup, rules)
        }
    }

    private fun processRule(rule: GenericRule, rules: MutableMap<Int, RuleItem>) {
        rules.remove(itemKey(rule))

        if(rule is RuleGroup) {
            rule.getRules().forEach {
                processRule(it.first, rules)
            }
        }
    }

    private fun itemKey(item: RuleItem): Int {
        return arrayOf(item.id, item.rule.javaClass).contentHashCode()
    }

    private fun itemKey(item: GenericRule): Int {
        return arrayOf(item.id, item.javaClass).contentHashCode()
    }
}

private class RuleItem(
    val id: Long,
    val rule: GenericRule,
    val delete: () -> Unit,
)
