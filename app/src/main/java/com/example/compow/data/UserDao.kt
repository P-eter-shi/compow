package com.example.compow.data

import androidx.room.*

@Dao
interface UserDao {

    // Insert or replace user
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // Get user by ID
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    // Get current user (assumes single user app, returns first user)
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    // Get all users
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    // Check if email exists (for signup validation)
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    suspend fun isEmailExists(email: String): Boolean

    // Check if phone number exists (for signup validation)
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE phone_number = :phoneNumber)")
    suspend fun isPhoneNumberExists(phoneNumber: String): Boolean

    // Get user count
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    // Update user name
    @Query("UPDATE users SET full_name = :newName WHERE userId = :userId")
    suspend fun updateUserName(userId: String, newName: String)

    // Update user email
    @Query("UPDATE users SET email = :newEmail WHERE userId = :userId")
    suspend fun updateEmail(userId: String, newEmail: String)

    // Update user phone number
    @Query("UPDATE users SET phone_number = :newPhoneNumber WHERE userId = :userId")
    suspend fun updatePhoneNumber(userId: String, newPhoneNumber: String)

    // Update profile picture URI
    @Query("UPDATE users SET profile_picture_uri = :newUri WHERE userId = :userId")
    suspend fun updateProfilePictureUri(userId: String, newUri: String?)

    // Update Google ID
    @Query("UPDATE users SET google_id = :googleId WHERE userId = :userId")
    suspend fun updateGoogleId(userId: String, googleId: String?)

    // Get user by Google ID (useful for Google Sign-In)
    @Query("SELECT * FROM users WHERE google_id = :googleId LIMIT 1")
    suspend fun getUserByGoogleId(googleId: String): UserEntity?

    // Get user by email (useful for Google Sign-In)
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    // Update entire user object
    @Update
    suspend fun updateUser(user: UserEntity)

    // Delete user
    @Delete
    suspend fun deleteUser(user: UserEntity)

    // Delete user by ID
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    // Delete all users (useful for logout/reset)
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}