package at.clavierhaus.unisonmaster.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One catalogue of words: an app's, or a part of one. Generated from a
 * `strings.tsv` by `scripts/i18n.py`; FOSS has `StringsTable`, Pro adds
 * its own and registers it at start ([Strings.register]). A catalogue
 * carries a language whole or not at all — the check script refuses a
 * hole — so "does this catalogue speak German" is a yes or a no, and the
 * app can say "<catalogue> in <language>: not available yet".
 */
interface Catalog {
    /** A name for the .po files and the message ("clavier-tuner", "clavier-tuner-pro"). */
    val name: String
    /** The table's own version, apart from the app's: the day its English last changed. */
    val version: String
    /** Languages carried, BCP-47, English first. Regional tags (de-CH) only where they differ from the base. */
    val languages: List<String>
    val table: Map<String, Array<String>>
}

/**
 * The words the apps show (docs/I18N.md). Tables of keys and languages,
 * generated from `i18n/strings.tsv` in each repository; the code names a
 * key ([K]) and never holds a word.
 *
 * [Strings.language] is the language in force: the system's by default,
 * or the one chosen in Settings. A word is looked up in the language in
 * force, then its base ("de-CH" → "de"), then English; a language a
 * catalogue does not carry falls back the same way, and [missing] says
 * which catalogues those are, so that the screen can say so.
 *
 * Money, tax and what an invoice or a report must say by law are *not*
 * translations and live apart: `pro/finance/Jurisdiction`.
 */
object Strings {
    private var catalogs: List<Catalog> = listOf(StringsTable)

    /** Adds a catalogue (Pro's, at start); a catalogue of the same name replaces the earlier one. */
    fun register(catalog: Catalog) { catalogs = catalogs.filter { it.name != catalog.name } + catalog }
    fun unregister(name: String) { catalogs = catalogs.filter { it.name != name } }

    /** Every language any catalogue carries, English first. */
    val languages: List<String> get() = (listOf("en") + catalogs.flatMap { it.languages }).distinct()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    /** Sets the language: a tag some catalogue has, or the nearest ("de-AT" → "de"), else English. */
    fun use(tag: String?) { _language.value = resolve(tag) }

    fun resolve(tag: String?): String {
        if (tag == null) return "en"
        val t = tag.lowercase().replace('_', '-')
        val known = languages
        known.firstOrNull { it.lowercase() == t }?.let { return it }
        val base = t.substringBefore('-')
        return known.firstOrNull { it.lowercase() == base } ?: "en"
    }

    /** The catalogues that do not carry [language] — whole, not partly — and will show English instead. */
    fun missing(language: String = _language.value): List<Catalog> =
        catalogs.filter { c -> chain(language).filter { it != "en" }.none { it in c.languages } }

    /** The word for [key] in the language in force, with [args] filled into {0}, {1}, … */
    fun get(key: String, vararg args: Any?): String = get(_language.value, key, *args)

    fun get(language: String, key: String, vararg args: Any?): String {
        val catalog = catalogs.firstOrNull { key in it.table } ?: return key
        val texts = catalog.table.getValue(key)
        var s = ""
        for (lang in chain(language)) {
            val i = catalog.languages.indexOf(lang)
            if (i >= 0 && texts[i].isNotEmpty()) { s = texts[i]; break }
        }
        if (s.isEmpty()) s = texts[0]
        for ((n, a) in args.withIndex()) s = s.replace("{$n}", a.toString())
        return s
    }

    /** The tags tried for a language, most specific first: de-CH, de, en. */
    private fun chain(language: String): List<String> {
        val base = language.substringBefore('-')
        return listOf(language, base, "en").distinct()
    }
}

/** Shorthand: `t(K.sampling_title)`, `t(K.sampling_count, 3, 19)`. */
fun t(key: String, vararg args: Any?): String = Strings.get(key, *args)
