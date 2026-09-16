package net.yaycraft.yunit.model

sealed class TransactionResult {
    data class Success(
        val account: YunitAccount,
        val transaction: Transaction
    ) : TransactionResult()
    
    data class Failure(
        val reason: FailureReason,
        val message: String
    ) : TransactionResult()
}

enum class FailureReason {
    INSUFFICIENT_BALANCE,
    ACCOUNT_NOT_FOUND,
    INVALID_AMOUNT,
    DATABASE_ERROR,
    DATABASE_UNAVAILABLE,
    DUPLICATE_TRANSACTION,
    RATE_LIMITED,
    INVALID_REQUEST
}
