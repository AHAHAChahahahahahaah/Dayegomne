package com.example.data

import kotlinx.coroutines.flow.Flow

class CardRepository(private val cardDao: CardDao) {
    val allCards: Flow<List<CardEntity>> = cardDao.getAllCards()

    suspend fun getCardById(id: Long): CardEntity? {
        return cardDao.getCardById(id)
    }

    suspend fun insertCard(card: CardEntity): Long {
        return cardDao.insertCard(card)
    }

    suspend fun deleteCard(card: CardEntity) {
        cardDao.deleteCard(card)
    }

    suspend fun deleteCardById(id: Long) {
        cardDao.deleteCardById(id)
    }
}
