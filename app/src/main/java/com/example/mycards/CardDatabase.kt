package com.example.mycards

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [LoyaltyCard::class], version = 1, exportSchema = false)
abstract class CardDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: CardDatabase? = null

        fun getDatabase(context: Context): CardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_database"
                )
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // INSTANCE is set before the first DB operation triggers onCreate
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.cardDao()
                    dao.insert(LoyaltyCard(name = "IKEA Family",   frontImageRes = R.drawable.card_ikea,      backImageRes = R.drawable.card_ikea_back))
                    dao.insert(LoyaltyCard(name = "Żabka",         frontImageRes = R.drawable.card_zabka))
                    dao.insert(LoyaltyCard(name = "Biedronka",     frontImageRes = R.drawable.card_biedronka))
                    dao.insert(LoyaltyCard(name = "Empik",         frontImageRes = R.drawable.card_empik,     backImageRes = R.drawable.card_empik_back))
                }
            }
        }
    }
}

