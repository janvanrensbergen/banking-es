package com.example.sourcing

import com.example.sourcing.Command.CreateAccount
import com.example.sourcing.Event.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


data class Account(val number: String, val balance: Int) {
    val uncommittedEvents = mutableListOf<Event>()

    constructor(command: CreateAccount) : this(command.accountNumber, command.balance) {
        uncommittedEvents.add(AccountCreated(command.accountNumber, command.name, command.balance))
    }

    fun reset() = this.apply { uncommittedEvents.clear() }

    fun deposit(amount: Int): Account =
        this.copy(balance = this.balance + amount)
            .apply { uncommittedEvents.add(MoneyDeposited(number, amount)) }

    fun withdraw(amount: Int): Account =
        this.copy(balance = this.balance - amount)
            .apply { uncommittedEvents.add(MoneyWithdrawn(number, amount)) }

    fun apply(event: Event): Account =
        when (event) {
            is AccountCreated -> this.copy(number = event.accountNumber, balance = event.balance)
            is MoneyDeposited -> this.copy(balance = this.balance + event.amount)
            is MoneyWithdrawn -> this.copy(balance = this.balance - event.amount)
        }

}

interface AccountRepository {
    fun find(accountNumber: String): Account?
    fun save(account: Account): Account
}

@Service
class EventSourceAccountRepository(
    private val eventBus: EventBus,
) : AccountRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val store: MutableMap<String, List<Event>> = mutableMapOf()

    override fun find(accountNumber: String): Account? {
        val events = store[accountNumber]?.sortedBy { it.timestamp }
        val result = events
            ?.fold(Account("", 0)) { account, event ->
                account.apply(event)
            }

        logger.info("Events: {}", events)
        logger.info("Account: {}", result)
        return result
    }

    override fun save(account: Account): Account {
        val events = store[account.number] ?: emptyList()
        store[account.number] = events + account.uncommittedEvents
        account.uncommittedEvents.forEach { eventBus.send(it) }
        return account.reset()
    }
}