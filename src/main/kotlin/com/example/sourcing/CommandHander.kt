package com.example.sourcing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.example.sourcing.Command.*
import org.springframework.stereotype.Service

sealed interface Command {
    data class CreateAccount(val accountNumber: String, val name: String, val balance: Int) : Command
    data class Deposit(val accountNumber: String, val amount: Int) : Command
    data class Withdraw(val accountNumber: String, val amount: Int) : Command
}


interface CommandHandler {
    operator fun invoke(command: Command): Either<Failure, Account>
}

@Service
class DefaultCommandHandler(
    private val accountRepository: AccountRepository,
) : CommandHandler {
    override fun invoke(command: Command): Either<Failure, Account> =
        when (command) {
            is CreateAccount -> handle(command)
            is Deposit -> handle(command)
            is Withdraw -> handle(command)
        }


    private fun handle(command: CreateAccount) =
        when {
            command.name.startsWith("Cindy") -> Failure("We are a bank without Cindy's.").left()
            command.accountNumber.exists() -> Failure("Account [${command.accountNumber}] already exists.").left()
            else -> accountRepository.save(Account(command)).right()
        }

    private fun handle(command: Deposit) =
        when {
            command.amount < 1_000 -> Failure("We are a bank for the big money.").left()
            else ->
                accountRepository.find(command.accountNumber)
                    ?.let { account -> accountRepository.save(account.deposit(command.amount)).right() } ?:
                    Failure("Account [${command.accountNumber}] does not exists.").left()
        }

    private fun handle(command: Withdraw) =
        when {
            command.amount < 1_000 -> Failure("We are a bank for the big money.").left()
            else ->
                accountRepository.find(command.accountNumber)
                    ?.let { account -> accountRepository.save(account.withdraw(command.amount)).right() } ?:
                    Failure("Account [${command.accountNumber}] does not exists.").left()
        }

    private fun String.exists() = accountRepository.find(this) != null
}

