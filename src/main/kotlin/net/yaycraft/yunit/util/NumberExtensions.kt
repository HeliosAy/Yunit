package net.yaycraft.yunit.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** DECIMAL(18,2) sütununa sığabilecek en büyük değer */
val MAX_AMOUNT: BigDecimal = BigDecimal("9999999999999999.99")

private val decimalFormat: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    DecimalFormat("#,##0.00", symbols)
}

/** Örn: 1500.5 -> "1.500,50" */
fun BigDecimal.format(): String {
    return decimalFormat.get().format(this.setScale(2, RoundingMode.DOWN))
}

/** En fazla 2 ondalık basamak ve sütun sınırı içinde mi? */
private fun BigDecimal.fitsColumn(): Boolean {
    return this.stripTrailingZeros().scale() <= 2 && this <= MAX_AMOUNT
}

/** İşlem tutarı olarak geçerli mi? (pozitif) */
fun BigDecimal.isValidAmount(): Boolean = this > BigDecimal.ZERO && fitsColumn()

/** Bakiye değeri olarak geçerli mi? (sıfır veya pozitif) */
fun BigDecimal.isValidBalance(): Boolean = this >= BigDecimal.ZERO && fitsColumn()

/** Komut argümanını güvenli şekilde sayıya çevirir. Negatif, 2'den fazla ondalıklı veya çok büyük değerler null döner. */
fun String.toBigDecimalSafe(): BigDecimal? {
    return try {
        val bd = BigDecimal(this)
        if (bd.isValidBalance()) bd else null
    } catch (e: NumberFormatException) {
        null
    }
}
