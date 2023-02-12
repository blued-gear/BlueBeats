package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile

internal interface Rule {

    /** determines how much items a rule should add to the resulting collection */
    data class Share(
        val value: Float,
        /** if true the value is a percentage between 0 and 1.0;
         *     else it is the absolute amount of items which should be generated (should be cast to int) */
        val isRelative: Boolean
    )

    var share: Share

    /**
     * returns a collection of media from this rule
     * @param amount the expected amount of media to return
     * @param exclude collection of paths to exclude from item-generation
     */
    fun generateItems(amount: Int, exclude: ExcludeRule = ExcludeRule.EMPTY_EXCLUDE): List<MediaFile>
}
