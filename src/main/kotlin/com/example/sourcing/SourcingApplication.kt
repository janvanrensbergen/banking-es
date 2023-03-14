package com.example.sourcing

import arrow.core.Either
import arrow.core.right
import com.example.sourcing.Command.*
import com.example.sourcing.Query.AccountBalance
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.body
import org.springframework.web.servlet.function.router

@SpringBootApplication
class SourcingApplication

fun main(args: Array<String>) {
	runApplication<SourcingApplication>(*args)
}

@Configuration
class RoutesConfig(
    private val commandHandler: CommandHandler,
    private val queryHandler: SimpleQueryHandler
) {
    @Bean
    fun routes() = router {
        accept(MediaType.APPLICATION_JSON).nest {

            GET("/") { _ -> ServerResponse.ok().body("Ok")}
            GET("/{accountNumber}") { request ->
                val accountNumber = request.pathVariable("accountNumber")

                handleInternal({ it.value }) { queryHandler(AccountBalance(accountNumber)).right()  }
            }

            PUT("/{accountNumber}") { request ->
                val accountNumber = request.pathVariable("accountNumber")
                val body = request.body<Map<String, String>>()

                handleInternal({ it.asJson() }) { commandHandler(CreateAccount(accountNumber, body["name"]?: "Jos", body["balance"]?.toInt() ?: 0))}
            }

            PUT("/{accountNumber}/deposit/{amount}") { request ->
                val accountNumber = request.pathVariable("accountNumber")
                val amount = request.pathVariable("amount").toInt()

                handleInternal({ it.asJson() }) { commandHandler(Deposit(accountNumber, amount))}
            }

            PUT("/{accountNumber}/withdraw/{amount}") { request ->
                val accountNumber = request.pathVariable("accountNumber")
                val amount = request.pathVariable("amount").toInt()

                handleInternal({ it.asJson() }) { commandHandler(Withdraw(accountNumber, amount)) }
            }
        }
    }

    private fun <T> handleInternal(
        map: (T) -> Map<String, Any>,
        f: () -> Either<Failure, T>,
    ) =
        Either.resolve(
            f = f,
            success = { account -> ServerResponse.ok().body(map(account)).right() },
            error = { failure -> ServerResponse.status(HttpStatus.BAD_REQUEST).body(failure.message).right() },
            throwable = { _ -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build().right()},
            unrecoverableState = { _ -> Unit.right()}
        )

    private fun Account.asJson() = mapOf("account" to this.number, "balance" to this.balance)
}


data class Failure(val message: String)

@Component
class Initializer(
    private val commandHandler: CommandHandler
): ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {

        commandHandler(CreateAccount("BE00 0000 0000 0001", "Jan Van Rensbergen", 10_000)).also { logger.info("{}", it) }
        commandHandler(Deposit("BE00 0000 0000 0001",  10_000)).also { logger.info("{}", it) }
        commandHandler(Deposit("BE00 0000 0000 0001",  20_000)).also { logger.info("{}", it) }
        commandHandler(Deposit("BE00 0000 0000 0001",  30_000)).also { logger.info("{}", it) }

    }

}