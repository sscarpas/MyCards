package com.example.mycards

import android.app.Application

class MyCardsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val dao = CardDatabase.getDatabase(this).cardDao()
        CardRepository.init(dao)
    }
}

