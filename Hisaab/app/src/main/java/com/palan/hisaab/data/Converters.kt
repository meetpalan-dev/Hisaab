package com.palan.hisaab.data

import androidx.room.TypeConverter
import com.palan.hisaab.data.entity.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}
