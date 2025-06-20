package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.view.View
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import io.github.esentsov.PackagePrivate

internal typealias ChangedCallback = (GenericRule) -> Unit

internal object DynPlaylistEditors {

    fun createEditorRoot(
        root: RuleGroup,
        cb: ChangedCallback,
        ctx: Context
    ): View {
        return DynplaylistGroupEditor(root, cb, ctx).apply {
            header.shareBtn.visibility = View.GONE// root-group always has 100% share
        }
    }

    @PackagePrivate
    fun createEditor(
        item: GenericRule,
        cb: ChangedCallback,
        ctx: Context
    ): AbstractDynplaylistEditorView {
        return when(item) {
            is RuleGroup -> DynplaylistGroupEditor(item, cb, ctx)
            is IncludeRule -> DynplaylistIncludeEditor(item, cb, ctx)
            is UsertagsRule -> DynplaylistUsertagsEditor(item, cb, ctx)
            is ID3TagsRule -> DynplaylistID3TagsEditor(item, cb, ctx)
            is RegexRule -> DynplaylistRegexEditor(item, cb, ctx)
            is TimeSpanRule -> DynplaylistTimeSpanEditor(item, cb, ctx)
        }
    }
}
