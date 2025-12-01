package com.example.compow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    // ===== USED IN SettingsPage =====
    @Query("SELECT COUNT(*) FROM contacts WHERE category = :category")
    suspend fun getContactCountByCategory(category: ContactCategory): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    // ===== USED IN AlarmService =====
    @Query("SELECT * FROM contacts WHERE category = :category AND is_enabled = 1")
    suspend fun getContactsByCategory(category: ContactCategory): List<ContactEntity>

    // ===== USED IN DestinationPage =====
    @Query("SELECT * FROM contacts ORDER BY created_at DESC")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE is_enabled = 1")
    suspend fun getAllEnabledContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE phone_number = :phoneNumber AND category = :category LIMIT 1")
    suspend fun getContactByNumberAndCategory(phoneNumber: String, category: ContactCategory): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: Long): ContactEntity?

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: Long)

    // ===== UTILITY FUNCTIONS (May be needed) =====
    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Query("UPDATE contacts SET is_enabled = :enabled WHERE category = :category")
    suspend fun updateCategoryEnabled(category: ContactCategory, enabled: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM contacts WHERE phone_number = :phoneNumber)")
    suspend fun isContactExists(phoneNumber: String): Boolean

    @Query("DELETE FROM contacts WHERE category = :category")
    suspend fun deleteAllContactsByCategory(category: ContactCategory)
}