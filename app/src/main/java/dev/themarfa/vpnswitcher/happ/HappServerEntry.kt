package dev.themarfa.vpnswitcher.happ

data class HappServerEntry(
    val name: String,
    val configLine: String,
)

class HappSubscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
