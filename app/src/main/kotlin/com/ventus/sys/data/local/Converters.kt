package com.ventus.sys.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/** Room has no native LocalDate column type — stored as epoch-day Long. */
class Converters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
