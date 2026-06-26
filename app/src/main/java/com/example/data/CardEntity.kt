package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val rarity: String, // "Common", "Uncommon", "Rare", "Epic", "Legendary"
    val imagePath: String, // File path inside internal storage
    val power: Int,
    val shield: Int,
    val type: String, // E.g., "Cosmic Beast", "Legendary Warrior"
    val timestamp: Long = System.currentTimeMillis()
)
