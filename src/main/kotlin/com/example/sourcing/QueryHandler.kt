package com.example.sourcing

import com.example.sourcing.Event.*
import com.example.sourcing.Query.AccountBalance
import org.springframework.stereotype.Service
import java.text.NumberFormat
import java.time.Instant
import java.util.*

sealed class Event(val timestamp: Instant = Instant.now()) {
    data class AccountCreated(val accountNumber:String, val name: String, val balance: Int): Event()
    data class MoneyDeposited(val accountNumber:String, val amount: Int): Event()
    data class MoneyWithdrawn(val accountNumber:String, val amount: Int): Event()
}

interface EventBus {
    fun send(event: Event)
}

interface EventListener {
    fun notify(event: Event)
}

@Service
class SimpleEventBus(
    private val listeners: List<EventListener>,
): EventBus {
    override fun send(event: Event) = listeners.forEach { it.notify(event) }
}

sealed interface Query {
    data class AccountBalance(val accountNumber: String): Query
}
data class Result<T> (val value: T)

interface QueryHandler<T> {
    operator fun invoke(query: Query): Result<T>
}

interface SimpleQueryHandler: QueryHandler<Map<String, String>>

@Service
class DefaulltQueryHandler: SimpleQueryHandler, EventListener {

    private val accountBalance = mutableMapOf<String, Int>()

    override fun notify(event: Event) =
        when(event){
            is AccountCreated -> accountBalance[event.accountNumber] = event.balance
            is MoneyDeposited -> accountBalance[event.accountNumber] = accountBalance.getOrDefault(event.accountNumber, 0) + event.amount
            is MoneyWithdrawn -> accountBalance[event.accountNumber] = accountBalance.getOrDefault(event.accountNumber, 0) - event.amount
        }

    override fun invoke(query: Query): Result<Map<String, String>> =
        when(query) {
            is AccountBalance -> accountBalance[query.accountNumber]?.let { Result(mapOf(query.accountNumber to it.formatPrice())) } ?: Result(mapOf())
        }

    private fun Int.formatPrice():String =
        NumberFormat.getCurrencyInstance().apply {
            maximumFractionDigits = 0
            currency = Currency.getInstance("EUR")
        }.format(this)


}