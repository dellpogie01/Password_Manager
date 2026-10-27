package com.example.model

data class VaultEntry(
    val id: Long = 0L,
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val category: String = "Personal",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
