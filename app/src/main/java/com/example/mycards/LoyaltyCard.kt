package com.example.mycards

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loyalty_cards")
data class LoyaltyCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val frontImageRes: Int = 0,
    val backImageRes: Int? = null,
    val frontImageUri: String? = null,
    val backImageUri: String? = null
)
