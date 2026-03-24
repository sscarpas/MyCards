package com.example.mycards

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM loyalty_cards ORDER BY id ASC")
    fun getAllCards(): Flow<List<LoyaltyCard>>

    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    suspend fun getById(id: Int): LoyaltyCard?

    @Insert
    suspend fun insert(card: LoyaltyCard)

    @Update
    suspend fun update(card: LoyaltyCard)

    @Query("DELETE FROM loyalty_cards WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM loyalty_cards ORDER BY id ASC")
    suspend fun getAllCardsList(): List<LoyaltyCard>

    @Query("SELECT * FROM loyalty_cards WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): LoyaltyCard?

    /** Returns true if any card has the same name (case-insensitive). Used when adding a new card. */
    @Query("SELECT COUNT(*) > 0 FROM loyalty_cards WHERE LOWER(name) = LOWER(:name)")
    suspend fun nameExists(name: String): Boolean

    /**
     * Returns true if any card OTHER THAN [excludeId] has the same name (case-insensitive).
     * Used when editing an existing card so the card doesn't conflict with itself.
     */
    @Query("SELECT COUNT(*) > 0 FROM loyalty_cards WHERE LOWER(name) = LOWER(:name) AND id != :excludeId")
    suspend fun nameExistsExcluding(name: String, excludeId: Int): Boolean
}
