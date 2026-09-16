package net.yaycraft.yunit.exception

import java.math.BigDecimal
import java.util.UUID

sealed class YunitException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InsufficientBalanceException(
    val uuid: UUID, val required: BigDecimal, val available: BigDecimal
) : YunitException("Yetersiz bakiye: gerekli=$required, mevcut=$available")

class AccountNotFoundException(val uuid: UUID) : YunitException("Hesap bulunamadı: $uuid")

class DatabaseException(message: String, cause: Throwable? = null) : YunitException(message, cause)

class DatabaseUnavailableException(cause: Throwable? = null) : YunitException("Veritabanı şu an erişilemez durumda", cause)

class DuplicateTransactionException(val idempotencyKey: String) : YunitException("Duplicate işlem: $idempotencyKey")

class InvalidAmountException(val amount: BigDecimal) : YunitException("Geçersiz miktar: $amount")

class RateLimitedException(val uuid: UUID) : YunitException("İşlem çok hızlı, lütfen bekleyin")
