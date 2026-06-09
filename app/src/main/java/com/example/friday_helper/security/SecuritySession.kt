package com.example.friday_helper.security

object SecuritySession {
    @Volatile var isUnlocked: Boolean = false
        private set
    @Volatile var isGuest: Boolean = false
        private set

    fun unlockUser() {
        isUnlocked = true
        isGuest = false
    }

    fun enterGuest() {
        isUnlocked = false
        isGuest = true
    }

    fun clear() {
        isUnlocked = false
        isGuest = false
    }
}

