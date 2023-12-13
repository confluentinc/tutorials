package io.confluent.developer;

import static org.junit.Assert.*;
import io.confluent.developer.RegexReplace;
import org.junit.Test;

public class RegexReplaceTest {

    @Test
    public void testRegexReplace() {
        RegexReplace udf = new RegexReplace();
        String regEx = "\\(?(\\d{3}).*";
        assertEquals("206", udf.regexReplace("206-555-1272", regEx, "$1"));
        assertEquals("425", udf.regexReplace("425.555.6940", regEx, "$1"));
        assertEquals("360", udf.regexReplace("360 555 6952", regEx, "$1"));
        assertEquals("253", udf.regexReplace("(253) 555-7050", regEx, "$1"));
        assertEquals("425", udf.regexReplace("425-555-7926", regEx, "$1"));

        // test null parameters return null
        assertNull(udf.regexReplace(null, regEx, "$1"));
        assertNull(udf.regexReplace("425-555-7926", null, "$1"));
        assertNull(udf.regexReplace("425-555-7926", regEx, null));
    }
}
