package apps.chocolatecakecodes.bluebeats.media.playlist.impl

import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.storage.RuleStorage
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB

internal class RuleStorageImpl : RuleStorage {

    override fun newRuleGroup(share: Share): RuleGroup {
        return RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(share)
    }

    override fun newIncludeRule(share: Share): IncludeRule {
        return RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(share)
    }

    override fun newUsertagsRule(share: Share): UsertagsRule {
        return RoomDB.DB_INSTANCE.dplUsertagsRuleDao().createNew(share)
    }

    override fun newID3TagsRule(share: Share): ID3TagsRule {
        return RoomDB.DB_INSTANCE.dplID3TagsRuleDao().create(share)
    }

    override fun newRegexRule(share: Share): RegexRule {
        return RoomDB.DB_INSTANCE.dplRegexRuleDao().createNew(share)
    }

    override fun newTimeSpanRule(share: Share): TimeSpanRule {
        return RoomDB.DB_INSTANCE.dplTimeSpanRuleDao().createNew(share)
    }
}