package com.wwdablu.dailyexpense.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvWriterTest {
    @Test fun plainFieldUnchanged() = assertEquals("Lunch", CsvWriter.field("Lunch"))
    @Test fun commaIsQuoted() = assertEquals("\"Tea, snacks\"", CsvWriter.field("Tea, snacks"))
    @Test fun quotesAreDoubled() = assertEquals("\"5\"\" pizza\"", CsvWriter.field("5\" pizza"))
    @Test fun newlineIsQuoted() = assertEquals("\"a\nb\"", CsvWriter.field("a\nb"))
}
