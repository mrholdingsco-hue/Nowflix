package kr.prism.nowflix.data

import java.time.OffsetDateTime

/** Formats an ISO-8601 instant as `yyyy.MM.dd`; blank on empty/unparseable input. */
object PublishDate {
    fun format(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val dt = OffsetDateTime.parse(iso)
            "%04d.%02d.%02d".format(dt.year, dt.monthValue, dt.dayOfMonth)
        } catch (e: Exception) {
            ""
        }
    }
}
