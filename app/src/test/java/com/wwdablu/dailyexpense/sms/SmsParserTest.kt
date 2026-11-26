package com.wwdablu.dailyexpense.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private fun parse(body: String, sender: String = "VM-HDFCBK") = SmsParser.parse(sender, body)

    @Test fun hdfcUpiSent() {
        val r = parse("Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 26/11/26\nRef 123456789012\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(45000L, r.amountMinor); assertEquals(false, r.isIncome); assertEquals("SWIGGY", r.merchant)
    }

    @Test fun sbiDebitedByWithoutCurrency() {
        val r = parse("Dear UPI user A/C X1234 debited by 250.0 on date 26Nov26 trf to ZOMATO Refno 433012345678. If not u? call 1800111109. -SBI", "AD-SBIINB")!!
        assertEquals(25000L, r.amountMinor); assertEquals(false, r.isIncome); assertEquals("ZOMATO", r.merchant)
    }

    @Test fun iciciCardSpentAtMerchant() {
        val r = parse("INR 1,299.00 spent on ICICI Bank Card XX5678 on 26-Nov-26 at AMAZON PAY IN. Avl Lmt: INR 45,000.00. If not you, call 1800 2662", "JX-ICICIT")!!
        assertEquals(129900L, r.amountMinor); assertEquals(false, r.isIncome); assertEquals("AMAZON PAY IN", r.merchant)
    }

    @Test fun salaryCredited() {
        val r = parse("Rs.50,000.00 credited to HDFC Bank A/c XX1234 on 26-11-26 from ACME TECHNOLOGIES (UPI Ref No 123456789012).")!!
        assertEquals(5000000L, r.amountMinor); assertEquals(true, r.isIncome); assertEquals("ACME TECHNOLOGIES", r.merchant)
    }

    @Test fun kotakDebitToVpa() {
        val r = parse("Rs 120.00 debited from your Kotak Bank a/c XX9012 to rapido@ybl on 26-11-26. UPI Ref 123456789012", "BZ-KOTAKB")!!
        assertEquals(12000L, r.amountMinor); assertEquals(false, r.isIncome); assertEquals("rapido", r.merchant)
    }

    @Test fun atmWithdrawal() {
        val r = parse("Rs.2000 withdrawn from A/c XX1234 at ATM SBI MG ROAD on 26-11-26. Avl Bal Rs.10,000", "AD-SBIINB")!!
        assertEquals(200000L, r.amountMinor); assertEquals(false, r.isIncome); assertEquals("ATM SBI MG ROAD", r.merchant)
    }

    @Test fun refundCreditedUsesBrandFallback() {
        val r = parse("Refund of Rs.299.00 for your Myntra order has been credited to your A/c XX1234")!!
        assertEquals(29900L, r.amountMinor); assertEquals(true, r.isIncome); assertEquals("Myntra", r.merchant)
    }

    @Test fun creditCardSpendIsNotIncome() {
        val r = parse("Rs.799 spent on your HDFC Bank Credit Card ending 4321 at NETFLIX on 26-11-26")!!
        assertEquals(false, r.isIncome); assertEquals("NETFLIX", r.merchant)
    }

    @Test fun ignoresOtp() = assertNull(parse("123456 is your OTP for txn of Rs 500 at Flipkart. Do not share."))
    @Test fun ignoresBillDue() = assertNull(parse("Your credit card bill of Rs 5,000 is due on 30-Nov-26. Pay now."))
    @Test fun ignoresCollectRequest() = assertNull(parse("Rahul has requested money of Rs 300 from you on Google Pay"))
    @Test fun ignoresFailed() = assertNull(parse("Your transaction of Rs.450 to SWIGGY has failed. Amount will be refunded."))
    @Test fun ignoresPersonalNumber() = assertNull(parse("Sent Rs.450.00 to you", sender = "+919876543210"))
    @Test fun ignoresMessagesWithoutAmount() = assertNull(parse("Your account statement has been sent to your email"))

    @Test fun typeGuess() {
        assertEquals("Food", TypeGuess.typeName("SWIGGY"))
        assertEquals("Shopping", TypeGuess.typeName("AMAZON PAY IN"))
        assertEquals("Transport", TypeGuess.typeName("rapido"))
        assertEquals("Salary", TypeGuess.typeName("Salary for Nov"))
        assertNull(TypeGuess.typeName("ACME TECHNOLOGIES"))
    }
}
