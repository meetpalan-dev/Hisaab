package com.palan.hisaab.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
