package apps.chocolatecakecodes.bluebeats.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration8to9 : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        addNameToRules(db)
    }

    private fun addNameToRules(db: SupportSQLiteDatabase) {
        listOf(
            "ID3TagsRuleEntity",
            "IncludeRuleEntity",
            "RegexRuleEntity",
            "RuleGroupEntity",
            "TimeSpanRuleEntity",
            "UsertagsRuleEntity",
        ).forEach {
            db.execSQL("ALTER TABLE $it ADD COLUMN name TEXT NOT NULL DEFAULT '';")
        }
    }
}
