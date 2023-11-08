package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.view.View
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.GenericRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RegexRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.UsertagsRule
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
