package at.clavierhaus.unisonmaster.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The words the apps show (docs/I18N.md). One table, `i18n/strings.tsv`,
 * every language side by side, generated into [StringsTable] by
 * `scripts/i18n.py`; the code names a key ([K]) and never holds a word.
 *
 * [Strings.language] is the language in force: the system's by default,
 * or the one chosen in Settings. A word missing in a language falls back
 * to English, and the check script refuses the table before it gets that
 * far.
 *
 * Money, tax and what an invoice or a report must say by law are *not*
 * translations and live apart: [at.clavierhaus.unisonmaster.i18n.Jurisdiction]
 * in Pro.
 */
object Strings {
    /** Languages the table has, BCP-47, English first. */
    val languages: List<String> get() = StringsTable.languages

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    /** Sets the language: a tag the table has, or the nearest ("de-AT" → "de"), else English. */
    fun use(tag: String?) { _language.value = resolve(tag) }

    fun resolve(tag: String?): String {
        if (tag == null) return "en"
        val t = tag.lowercase().replace('_', '-')
        StringsTable.languages.firstOrNull { it.lowercase() == t }?.let { return it }
        val base = t.substringBefore('-')
        return StringsTable.languages.firstOrNull { it.lowercase() == base } ?: "en"
    }

    /** The word for [key] in the language in force, with [args] filled into {0}, {1}, … */
    fun get(key: String, vararg args: Any?): String = get(_language.value, key, *args)

    fun get(language: String, key: String, vararg args: Any?): String {
        val texts = StringsTable.table[key] ?: return key
        val i = StringsTable.languages.indexOf(language).takeIf { it >= 0 } ?: 0
        var s = texts[i].ifEmpty { texts[0] }
        for ((n, a) in args.withIndex()) s = s.replace("{$n}", a.toString())
        return s
    }
}

/** Shorthand: `t(K.sampling_title)`, `t(K.sampling_count, 3, 19)`. */
fun t(key: String, vararg args: Any?): String = Strings.get(key, *args)
