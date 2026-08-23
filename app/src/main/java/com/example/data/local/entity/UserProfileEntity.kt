package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Long = 1,
    val googleSub: String = "",
    val name: String = "User",
    val email: String = "",
    val profilePictureUrl: String = "",
    val emailVerified: Boolean = true,
    val currencySymbol: String = "₹",
    val isDarkMode: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isPrivacyBlurEnabled: Boolean = true,
    val blurTimeoutSeconds: Int = 5,
    val isOnboardingCompleted: Boolean = false,
    val dashboardCardsConfig: String = "BALANCE:true,OFFICIAL:true,SPENDING:true,MONTHLY:true,INSIGHTS:true,LOANS:true,RECENT:true",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

