package io.github.tytebyte_dev.rplayer

/** Разобранная «текущая композиция». artist == null, если разделитель не найден. */
data class TrackInfo(val artist: String?, val title: String) {
    val display: String get() = if (artist != null) "$artist — $title" else title
}

/**
 * ICY / ID3 отдают одну строку вида "Artist - Title".
 * Здесь она делится на исполнителя и название, мусор отбрасывается.
 */
object IcyTitleParser {
    private val SEPARATORS = listOf(" - ", " – ", " — ")
    private val PLACEHOLDERS = setOf("-", "--", "unknown", "n/a", "advert", "advertisement")

    fun parse(raw: String?, stationName: String? = null): TrackInfo? {
        val text = raw.orEmpty()
            .replace("\u0000", "")
            .trim()
            .trim('\'', '"')
            .trim()
        if (text.isEmpty()) return null
        if (text.lowercase() in PLACEHOLDERS) return null
        if (text.contains("adw_ad=", ignoreCase = true)) return null // рекламный маркер
        if (stationName != null && text.equals(stationName.trim(), ignoreCase = true)) return null

        for (sep in SEPARATORS) {
            val i = text.indexOf(sep)
            if (i > 0) {
                val artist = text.substring(0, i).trim()
                val title = text.substring(i + sep.length).trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) return TrackInfo(artist, title)
            }
        }
        return TrackInfo(null, text)
    }
}
