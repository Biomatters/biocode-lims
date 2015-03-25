package com.biomatters.plugins.biocode.server.utilities;

import java.util.*;

/**
 * Routines for verifying Strings. Non-instantiable.
 *
 * @author Gen Li
 *         Created on 15/10/14 9:23 AM
 */
public class StringUtilities {
    private StringUtilities() {
    }

    public static String generateCommaSeparatedQuestionMarks(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }

        StringBuilder commaSeparatedQuestionMarksBuilder = new StringBuilder();

        for (int i = 0; i < count; i++) {
            commaSeparatedQuestionMarksBuilder.append("?,");
        }

        if (count > 0) {
            commaSeparatedQuestionMarksBuilder.deleteCharAt(commaSeparatedQuestionMarksBuilder.length() - 1);
        }

        return commaSeparatedQuestionMarksBuilder.toString();
    }

    public static List<String> getListFromString(String stringList) {
        if (stringList == null) {
            return null;
        }
        List<String> strings = new ArrayList<String>();
        for (String item : Arrays.asList(stringList.split(","))) {
            String toAdd = item.trim();
            if(!toAdd.isEmpty()) {
                strings.add(item);
            }
        }
        return strings;
    }
}