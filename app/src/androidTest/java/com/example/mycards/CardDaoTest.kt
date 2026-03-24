package com.example.mycards

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [CardDao].
 *
 * Each test gets a fresh in-memory database so tests are fully isolated.
 * The SeedCallback is NOT triggered here because we bypass CardDatabase.getDatabase(),
 * so every test starts with an empty table.
 */
@RunWith(AndroidJUnit4::class)
class CardDaoTest {

    private lateinit var database: CardDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── getAllCards ──────────────────────────────────────────────────────────

    @Test
    fun emptyDatabase_getAllCards_emitsEmptyList() = runTest {
        val cards = dao.getAllCards().first()
        assertTrue("Expected empty list from fresh database", cards.isEmpty())
    }

    @Test
    fun insert_getAllCards_returnsInsertedCard() = runTest {
        dao.insert(LoyaltyCard(name = "IKEA Family", frontImageRes = 0))

        val cards = dao.getAllCards().first()

        assertEquals("Should contain exactly one card", 1, cards.size)
        assertEquals("IKEA Family", cards[0].name)
    }

    @Test
    fun insertMultiple_getAllCards_returnsAllCardsOrderedById() = runTest {
        dao.insert(LoyaltyCard(name = "Card A", frontImageRes = 0))
        dao.insert(LoyaltyCard(name = "Card B", frontImageRes = 0))
        dao.insert(LoyaltyCard(name = "Card C", frontImageRes = 0))

        val cards = dao.getAllCards().first()

        assertEquals("Should contain three cards", 3, cards.size)
        assertTrue("Cards should be ordered by id ascending", cards[0].id < cards[1].id)
        assertTrue("Cards should be ordered by id ascending", cards[1].id < cards[2].id)
    }

    // ── getById ─────────────────────────────────────────────────────────────

    @Test
    fun insert_getById_returnsCorrectCard() = runTest {
        dao.insert(LoyaltyCard(name = "Żabka", frontImageRes = 0))
        val insertedId = dao.getAllCards().first()[0].id

        val card = dao.getById(insertedId)

        assertNotNull("Card should be found by its generated id", card)
        assertEquals("Żabka", card!!.name)
    }

    @Test
    fun getById_unknownId_returnsNull() = runTest {
        val card = dao.getById(9999)
        assertNull("Non-existent id should return null", card)
    }

    // ── URI fields ───────────────────────────────────────────────────────────

    @Test
    fun insertCard_withFrontUri_persistsUriCorrectly() = runTest {
        val uri = "file:///data/user/0/com.example.mycards/files/images/test.jpg"
        dao.insert(LoyaltyCard(name = "Photo Card", frontImageUri = uri))

        val card = dao.getAllCards().first()[0]

        assertEquals("Front URI should be persisted unchanged", uri, card.frontImageUri)
    }

    @Test
    fun insertCard_withBothUris_persistsBothCorrectly() = runTest {
        dao.insert(
            LoyaltyCard(
                name = "Two-sided card",
                frontImageUri = "file:///front.jpg",
                backImageUri  = "file:///back.jpg"
            )
        )

        val card = dao.getAllCards().first()[0]

        assertEquals("file:///front.jpg", card.frontImageUri)
        assertEquals("file:///back.jpg",  card.backImageUri)
    }

    @Test
    fun insertCard_withNoBackUri_backUriIsNull() = runTest {
        dao.insert(LoyaltyCard(name = "Front only", frontImageUri = "file:///front.jpg"))

        val card = dao.getAllCards().first()[0]

        assertNull("Back URI should default to null", card.backImageUri)
    }

    // ── autoGenerate id ──────────────────────────────────────────────────────

    @Test
    fun insert_idIsAutoAssigned_notZero() = runTest {
        dao.insert(LoyaltyCard(name = "Auto ID card"))

        val card = dao.getAllCards().first()[0]

        assertNotEquals("Room should auto-assign a non-zero id", 0, card.id)
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    fun insertThenDelete_getAllCards_returnsEmptyList() = runTest {
        dao.insert(LoyaltyCard(name = "To Delete"))
        val id = dao.getAllCards().first()[0].id

        dao.deleteById(id)

        val cards = dao.getAllCards().first()
        assertTrue("List should be empty after the only card is deleted", cards.isEmpty())
    }

    @Test
    fun insertThenDelete_getById_returnsNull() = runTest {
        dao.insert(LoyaltyCard(name = "To Delete"))
        val id = dao.getAllCards().first()[0].id

        dao.deleteById(id)

        assertNull("Deleted card should not be findable by id", dao.getById(id))
    }

    @Test
    fun deleteOne_otherCardsRemain() = runTest {
        dao.insert(LoyaltyCard(name = "Keep me"))
        dao.insert(LoyaltyCard(name = "Delete me"))
        val allBefore = dao.getAllCards().first()
        val idToDelete = allBefore.first { it.name == "Delete me" }.id

        dao.deleteById(idToDelete)

        val remaining = dao.getAllCards().first()
        assertEquals("Only one card should remain", 1, remaining.size)
        assertEquals("Correct card should remain", "Keep me", remaining[0].name)
    }

    @Test
    fun deleteNonExistentId_doesNotThrowAndTableUnchanged() = runTest {
        dao.insert(LoyaltyCard(name = "Safe card"))

        dao.deleteById(9999) // no-op, should not throw

        assertEquals("Existing card should be unaffected", 1, dao.getAllCards().first().size)
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    fun update_nameChange_persistsNewName() = runTest {
        dao.insert(LoyaltyCard(name = "Old Name"))
        val inserted = dao.getAllCards().first()[0]

        dao.update(inserted.copy(name = "New Name"))

        val updated = dao.getById(inserted.id)
        assertEquals("Name should be updated", "New Name", updated?.name)
    }

    @Test
    fun update_frontUriChange_persistsNewUri() = runTest {
        dao.insert(LoyaltyCard(name = "Card", frontImageUri = "file:///old.jpg"))
        val inserted = dao.getAllCards().first()[0]

        dao.update(inserted.copy(frontImageUri = "file:///new.jpg"))

        val updated = dao.getById(inserted.id)
        assertEquals("Front URI should be updated", "file:///new.jpg", updated?.frontImageUri)
    }

    @Test
    fun update_removeBackUri_backIsNull() = runTest {
        dao.insert(LoyaltyCard(name = "Card", frontImageUri = "file:///f.jpg", backImageUri = "file:///b.jpg"))
        val inserted = dao.getAllCards().first()[0]

        dao.update(inserted.copy(backImageUri = null))

        val updated = dao.getById(inserted.id)
        assertNull("Back URI should be null after removal", updated?.backImageUri)
    }
}
