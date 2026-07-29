package com.example.recetapp

data class Ingredient(val name: String = "", val qty: String = "", val unit: String = "g")
data class RecipeStep(val text: String = "", val time: String = "")

data class Recipe(val id: String, val title: String, val image: String)

data class User(
    val name: String,
    val handle: String,
    val bio: String,
    val avatar: String,
    val verified: Boolean,
    val recipes: Int,
    val followers: Int,
    val following: Int
)

enum class Visibility(val label: String, val desc: String) {
    PUBLICA("Pública", "Visible para todos"),
    SEGUIDORES("Seguidores", "Solo tus seguidores"),
    PRIVADA("Privada", "Solo tú")
}
