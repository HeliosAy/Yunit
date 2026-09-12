package net.yaycraft.yunit.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

fun BigDecimal.format(): String {
    return df.format(this.setScale(2, RoundingMode.DOWN))
}

fun String.toBigDecimalSafe(): BigDecimal? {
    return try {
        val bd = BigDecimal(this)
        if (bd < BigDecimal.ZERO) null else bd
    } catch (e: Exception) {
        null
    }
}
