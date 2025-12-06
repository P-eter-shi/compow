package com.example.compow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,

    @ColumnInfo(name = "full_name")
    val fullName: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "profile_picture_uri")
    val profilePictureUri: String? = null,

    @ColumnInfo(name = "google_id")
    val googleId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)