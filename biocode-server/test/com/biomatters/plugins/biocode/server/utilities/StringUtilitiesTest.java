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