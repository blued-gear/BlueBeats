package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import java.util.*
import kotlin.collections.ArrayList

internal class RuleGroup(
    override var share: Rule.Share,
    rules: List<Rule> = emptyList(),
    excludes: List<ExcludeRule> = emptyList()
) : Rule {

    private val rules = ArrayList(rules)
    private val rulesRO: List<Rule> by lazy {
        Collections.unmodifiableList(this.rules)
    }
    private val excludes = ArrayList(excludes)
    private val excludesRO: List<ExcludeRule> by lazy {
        Collections.unmodifiableList(this.excludes)
    }

    override fun generateItems(amount: Int, exclude: ExcludeRule): List<MediaFile> {
        val toExclude = getExcludes().fold(exclude) { a, b ->
            a.union(b)
        }

        val (relativeRules, absoluteRules) = getRules().partition { it.share.isRelative }

        val absoluteItems = absoluteRules.flatMap {
            it.generateItems(it.share.value.toInt(), toExclude)
        }.take(amount)

        val relativeAmount = amount - absoluteItems.size
        val relativeItems = relativeRules.flatMap {
            it.generateItems((relativeAmount * it.share.value).toInt(), toExclude)
        }

        return (absoluteItems + relativeItems).take(amount)
    }

    fun getRules(): List<Rule> {
        return rulesRO
    }

    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    fun removeRule(rule: Rule) {
        rules.remove(rule)
    }

    fun removeRuleAt(idx: Int) {
        rules.removeAt(idx)
    }

    fun getExcludes(): List<ExcludeRule> {
        return excludesRO
    }

    fun addExclude(rule: ExcludeRule) {
        excludes.add(rule)
    }

    fun removeExclude(rule: ExcludeRule) {
        excludes.remove(rule)
    }

    fun removeExcludeAt(idx: Int) {
        excludes.removeAt(idx)
    }
}
