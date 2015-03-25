package com.biomatters.plugins.biocode.server.utilities;

import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 21/10/14 7:34 AM
 */
public class StringUtilitiesTest extends Assert {
    private static final String REGULAR_STRING = "regular";
    private static final String EMPTY_STRING   = "";
    private static final String NULL_STRING    = null;

    @Test
    public void testIsStringNULL() {
        assertTrue(StringUtilities.isStringNULL(NULL_STRING));
        assertFalse(StringUtilities.isStringNULL(REGULAR_STRING));
    }

    @Test
    public void testIsStringNULLOrEmpty() {
        assertTrue(StringUtilities.isStringNULLOrEmpty(NULL_STRING));
        assertTrue(StringUtilities.isStringNULLOrEmpty(EMPTY_STRING));
        assertFalse(StringUtilities.isStringNULLOrEmpty(REGULAR_STRING));
    }

    @Test
    public void testCheckExistsNULLStringsBoolean() {
        assertTrue(StringUtilities.existsNULLStrings(Arrays.asList(NULL_STRING)));
        assertFalse(StringUtilities.existsNULLStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringUtilities.existsNULLStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckExistsEmptyStringsBoolean() {
        assertTrue(StringUtilities.existsEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringUtilities.existsEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringUtilities.existsEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckExistsNULLOrEmptyStringsBoolean() {
        assertTrue(StringUtilities.existsNULLOrEmptyStrings(Arrays.asList(NULL_STRING)));
        assertTrue(StringUtilities.existsNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringUtilities.existsNULLOrEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringUtilities.existsNULLOrEmptyStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
        assertTrue(StringUtilities.existsNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckAllAreNULLOrEmptyStringsBoolean() {
        assertTrue(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING)));
        assertTrue(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING, EMPTY_STRING)));
        assertFalse(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
        assertFalse(StringUtilities.allNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLStringsOnNULLString() {
        StringUtilities.throwExceptionIfExistsNULLStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfExistsNULLStringsOnNonNULLString() {
        StringUtilities.throwExceptionIfExistsNULLStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsEmptyStringsOnEmptyString() {
        StringUtilities.throwExceptionIfExistsEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfExistsEmptyStringsOnNonEmptyString() {
        StringUtilities.throwExceptionIfExistsEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNULLString() {
        StringUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnEmptyString() {
        StringUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }

    @Test
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNULLStringAndNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnEmptyStringAndNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnEmptyString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }

    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLStringAndEmptyString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }

    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLStringAndNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnEmptyStringAndNonNULLNorEmptyString() {
        StringUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test
    public void testGenerateCommaSeparatedQuestionMarks() {
        assertEquals("?,?,?,?,?", StringUtilities.generateCommaSeparatedQuestionMarks(5));
    }

    @Test
    public void testGenerateCommaSeparatedQuestionMarksWithZeroArgument() {
        assertEquals("", StringUtilities.generateCommaSeparatedQuestionMarks(0));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGenerateCommaSeparatedQuestionMarksWithNegativeArgument() {
        StringUtilities.generateCommaSeparatedQuestionMarks(-5);
    }
}