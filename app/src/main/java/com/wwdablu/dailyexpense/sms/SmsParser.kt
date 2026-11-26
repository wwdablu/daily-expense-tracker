package com.wwdablu.dailyexpense.sms

import java.math.BigDecimal

/** A debit or credit found in a bank SMS. */
data class ParsedSms(
    val amountMinor: Long,
    val isIncome: Boolean,
    /** Merchant, payee or payer, if one could be found. */
    val merchant: String?,
)

/**
 * Finds debits and credits in Indian bank / UPI / card SMS. Pure Kotlin so it can be unit-tested.
 * It is deliberately cautious: anything that looks like an OTP, a bill reminder, a payment
 * request, a failed transaction or an offer is ignored, as are messages from phone numbers.
 */
object SmsParser {

    private val IGNORE = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "will be debited", "will be credited", "is due", "due on", "due date", "due by", "minimum amount due",
        "has requested", "requested money", "collect request", "payment request",
        "failed", "declined", "unsuccessful", "could not be processed",
        "offer", "pre-approved", "preapproved", "apply now", "eligible for", "win ", "reward points",
    )

    private const val AMOUNT = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val CURRENCY_AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*$AMOUNT""", RegexOption.IGNORE_CASE)
    // e.g. SBI: "debited by 250.0", "credited with 1,000"
    private val VERB_AMOUNT = Regex("""(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*$AMOUNT""", RegexOption.IGNORE_CASE)

    private val DEBIT = Regex("""\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase|debit(?!\s*card))\b""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(credited|received|deposited|refund(?:ed)?)\b""", RegexOption.IGNORE_CASE)

    private val VPA = Regex("""\b([a-z0-9][a-z0-9.\-_]{1,40})@[a-z]{2,}\b""", RegexOption.IGNORE_CASE)
    // Words that end a merchant name in a sentence.
    private const val STOP = """(?=\s+(?:on|via|ref|refno|upi|txn|using|from|by|avl|avbl|a/c|ac|info|not|if)\b|[.,;:\n(]|\s*$)"""
    private val AT = Regex("""\bat\s+([A-Za-z0-9][A-Za-z0-9 &'.\-*/]{1,40}?)$STOP""", RegexOption.IGNORE_CASE)
    private val TO = Regex("""\b(?:to|trf to|towards)\s+([A-Za-z][A-Za-z0-9 &'.\-]{1,40}?)$STOP""", RegexOption.IGNORE_CASE)
    private val FROM = Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9 &'.\-]{1,40}?)$STOP""", RegexOption.IGNORE_CASE)
    // Words that mean the match is our own account, not a merchant.
    private val NOT_MERCHANT = Regex("""\b(a/c|ac|account|acct|card|bank|your|you|xx\d+|\*+\d+)\b""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String): ParsedSms? {
        if (!looksLikeBankSender(sender)) return null
        val lower = body.lowercase()
        if (IGNORE.any { it in lower }) return null

        val debit = DEBIT.find(body)
        val credit = CREDIT.find(body)
        val isIncome = when {
            debit != null && credit != null -> credit.range.first < debit.range.first
            debit != null -> false
            credit != null -> true
            else -> return null
        }

        val amountText = (VERB_AMOUNT.find(body) ?: CURRENCY_AMOUNT.find(body))?.groupValues?.get(1) ?: return null
        val amountMinor = runCatching {
            BigDecimal(amountText.replace(",", "")).setScale(2, java.math.RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull() ?: return null
        if (amountMinor <= 0) return null

        return ParsedSms(amountMinor, isIncome, findMerchant(body, isIncome))
    }

    /** Bank and payment SMS come from alphanumeric sender IDs (e.g. "VM-HDFCBK"), not phone numbers. */
    fun looksLikeBankSender(sender: String): Boolean {
        val s = sender.trim()
        return s.any { it.isLetter() } && !s.startsWith("+")
    }

    private fun findMerchant(body: String, isIncome: Boolean): String? {
        val candidates = buildList {
            if (!isIncome) {
                AT.find(body)?.let { add(it.groupValues[1]) }
                TO.findAll(body).forEach { add(it.groupValues[1]) }
            } else {
                FROM.findAll(body).forEach { add(it.groupValues[1]) }
            }
            VPA.find(body)?.let { add(it.groupValues[1]) }
        }
        candidates.map(::clean).firstOrNull { it.length >= 2 && !NOT_MERCHANT.containsMatchIn(it) }?.let { return it }
        // Fall back to a well-known brand named anywhere in the message.
        val lower = body.lowercase()
        return TypeGuess.KNOWN_BRANDS.firstOrNull { it in lower }?.replaceFirstChar { it.uppercase() }
    }

    private fun clean(s: String): String =
        s.trim().trim('.', '-', '*', '/', '\'').replace(Regex("""\s+"""), " ").take(30)
}

/** Guesses an expense type name from a merchant or the SMS text. */
object TypeGuess {
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf("swiggy", "zomato", "dominos", "domino's", "mcdonald", "kfc", "pizza", "restaurant", "cafe", "eatsure") to "Food",
        listOf("bigbasket", "blinkit", "zepto", "dmart", "jiomart", "instamart", "grofers", "more retail", "grocery") to "Grocery",
        listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fastag", "petrol", "fuel", "hpcl", "iocl", "bpcl") to "Transport",
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq") to "Shopping",
        listOf("airtel", "jio", "vodafone", "vi ", "bsnl", "recharge") to "Telephone",
        listOf("electricity", "bescom", "tneb", "msedcl", "water bill", "gas bill", "broadband", "act fibernet", "bill") to "Bills",
        listOf("apollo", "pharmeasy", "1mg", "netmeds", "hospital", "clinic", "pharmacy", "medplus") to "Health",
        listOf("netflix", "hotstar", "prime video", "spotify", "bookmyshow", "pvr", "inox") to "Entertainment",
        listOf("school", "tuition") to "School",
        listOf("udemy", "coursera", "byju", "college", "university") to "Education",
        listOf("salary", "sal cr", "payroll") to "Salary",
        listOf("refund", "cashback", "reversal") to "Refunds",
    )

    /** Brand words that are useful as a merchant name when nothing better is found. */
    val KNOWN_BRANDS: List<String> = listOf(
        "swiggy", "zomato", "amazon", "flipkart", "myntra", "uber", "ola", "rapido", "irctc", "airtel", "jio",
        "netflix", "bigbasket", "blinkit", "zepto", "dmart", "bookmyshow", "apollo", "pharmeasy",
    )

    /** The type name to suggest, or null if nothing matches. */
    fun typeName(text: String): String? {
        val lower = " ${text.lowercase()} "
        return RULES.firstOrNull { (words, _) -> words.any { it in lower } }?.second
    }
}
