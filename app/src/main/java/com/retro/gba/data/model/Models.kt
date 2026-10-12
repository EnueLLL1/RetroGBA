package com.retro.gba.data.model

import com.google.gson.annotations.SerializedName

data class Achievement(
    @SerializedName("ID")
    val id: Long,
    @SerializedName("Title")
    val title: String,
    @SerializedName("Description")
    val description: String,
    @SerializedName("Points")
    val points: Int,
    @SerializedName("BadgeName")
    val badgeName: String,
    @SerializedName("MemAddr")
    val memAddr: String? = null,
    val isUnlocked: Boolean = false,
    val unlockDate: String? = null
) {
    val badgeUrl: String
        get() = if (isUnlocked) {
            "https://media.retroachievements.org/Badge/$badgeName.png"
        } else {
            "https://media.retroachievements.org/Badge/${badgeName}_lock.png"
        }
}

data class GameInfo(
    @SerializedName("ID")
    val id: Long,
    @SerializedName("Title")
    val title: String,
    @SerializedName("ConsoleID")
    val consoleId: Int = 5, // 5 = Game Boy Advance
    @SerializedName("ImageIcon")
    val imageIcon: String? = null,
    val romHash: String? = null,
    val totalAchievements: Int = 0,
    val totalPoints: Int = 0
) {
    val iconUrl: String
        get() = imageIcon?.let { "https://media.retroachievements.org$it" } ?: ""
}

data class UserProfile(
    val username: String,
    val totalPoints: Int,
    val hardcorePoints: Int,
    val rank: Int,
    val avatarUrl: String
)

data class RomItem(
    val uri: String,
    val fileName: String,
    val title: String,
    val md5Hash: String,
    val gameId: Long? = null,
    val unlockedAchievements: Int = 0,
    val totalAchievements: Int = 0,
    val totalPoints: Int = 0,
    val badgeIconUrl: String? = null
)

