package com.example.mycards

import kotlinx.coroutines.flow.Flow

object CardRepository {

    private lateinit var dao: CardDao

    fun init(dao: CardDao) {
        this.dao = dao
    }

    val allCards: Flow<List<LoyaltyCard>>
        get() = dao.getAllCards()

    suspend fun getAllCardsList(): List<LoyaltyCard> = dao.getAllCardsList()

    suspend fun getByName(name: String): LoyaltyCard? = dao.getByName(name)

    suspend fun nameExists(name: String): Boolean = dao.nameExists(name)

    suspend fun nameExistsExcluding(name: String, excludeId: Int): Boolean =
        dao.nameExistsExcluding(name, excludeId)

    suspend fun getById(id: Int): LoyaltyCard? = dao.getById(id)

    suspend fun addCard(card: LoyaltyCard) = dao.insert(card)

    /**
     * Persists [updated] to the database.
     * If the front or back URI changed, the old file is deleted from disk first.
     */
    suspend fun updateCard(updated: LoyaltyCard, originalFrontUri: String?, originalBackUri: String?) {
        if (originalFrontUri != updated.frontImageUri) ImageStorage.deleteFromAppStorage(originalFrontUri)
        if (originalBackUri  != updated.backImageUri)  ImageStorage.deleteFromAppStorage(originalBackUri)
        dao.update(updated)
    }

    /**
     * Deletes the card from the database and removes its image files from disk.
     * Image cleanup happens first so no orphaned files are left if the app is
     * killed between the two operations.
     */
    suspend fun deleteCard(card: LoyaltyCard) {
        ImageStorage.deleteFromAppStorage(card.frontImageUri)
        ImageStorage.deleteFromAppStorage(card.backImageUri)
        dao.deleteById(card.id)
    }
}
