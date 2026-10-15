package io.github.alexc.keycloak.passwords.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HibpResponseParserTest {

    private static final String SAMPLE_RESPONSE_CRLF =
            "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" +
            "00D6441148D6C44F01C53B30C29CAD01407:42\r\n" +
            "1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n";

    private static final String SAMPLE_RESPONSE_LF =
            "0018A45C4D1DEF81644B54AB7F969B88D65:1\n" +
            "00D6441148D6C44F01C53B30C29CAD01407:42\n" +
            "1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\n";

    @Test
    @DisplayName("Parse standard CRLF response correctly into suffix and count map")
    void testParseCrlfResponse() {
        Map<String, Integer> map = HibpResponseParser.parse(SAMPLE_RESPONSE_CRLF);
        assertEquals(3, map.size());
        assertEquals(1, map.get("0018A45C4D1DEF81644B54AB7F969B88D65"));
        assertEquals(42, map.get("00D6441148D6C44F01C53B30C29CAD01407"));
        assertEquals(3861493, map.get("1E4C9B93F3F0682250B6CF8331B7EE68FD8"));
    }

    @Test
    @DisplayName("Parse Unix LF response correctly")
    void testParseLfResponse() {
        Map<String, Integer> map = HibpResponseParser.parse(SAMPLE_RESPONSE_LF);
        assertEquals(3, map.size());
        assertEquals(1, map.get("0018A45C4D1DEF81644B54AB7F969B88D65"));
    }

    @Test
    @DisplayName("Direct lookup via findCount handles case insensitivity")
    void testFindCountCaseInsensitive() {
        int countUpper = HibpResponseParser.findCount(SAMPLE_RESPONSE_CRLF, "00D6441148D6C44F01C53B30C29CAD01407");
        int countLower = HibpResponseParser.findCount(SAMPLE_RESPONSE_CRLF, "00d6441148d6c44f01c53b30c29cad01407");
        int notFound = HibpResponseParser.findCount(SAMPLE_RESPONSE_CRLF, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

        assertEquals(42, countUpper);
        assertEquals(42, countLower);
        assertEquals(0, notFound);
    }

    @Test
    @DisplayName("Parse response with padding zeroes and malformed lines gracefully")
    void testParseWithPaddingAndMalformedLines() {
        String responseWithPaddingAndNoise =
                "0018A45C4D1DEF81644B54AB7F969B88D65:0\n" + // padding hash
                "INVALID_NO_COLON\n" +
                ":123\n" +
                "TRAILING_COLON:\n" +
                "00D6441148D6C44F01C53B30C29CAD01407:NOT_A_NUMBER\n" +
                "\n" +
                "1E4C9B93F3F0682250B6CF8331B7EE68FD8:5\n";

        Map<String, Integer> map = HibpResponseParser.parse(responseWithPaddingAndNoise);
        assertEquals(2, map.size());
        assertEquals(0, map.get("0018A45C4D1DEF81644B54AB7F969B88D65"));
        assertEquals(5, map.get("1E4C9B93F3F0682250B6CF8331B7EE68FD8"));
    }

    @Test
    @DisplayName("Empty or null responses return empty map and zero count")
    void testEmptyAndNullResponses() {
        assertTrue(HibpResponseParser.parse(null).isEmpty());
        assertTrue(HibpResponseParser.parse("   ").isEmpty());
        assertEquals(0, HibpResponseParser.findCount(null, "ANY"));
        assertEquals(0, HibpResponseParser.findCount("ANY", null));
        assertEquals(0, HibpResponseParser.findCount("", "   "));
    }
}
