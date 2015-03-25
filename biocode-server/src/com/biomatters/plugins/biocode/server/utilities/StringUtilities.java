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

    public static void throwExceptionIfExistsNULLOrEmptyStrings(Map<String, String> stringIdentifierToString) throws IllegalArgumentException {
        if (existsNULLOrEmptyStrings(stringIdentifierToString.values())) {
            throw new IllegalArgumentException(createStringsAreNULLOrEmptyMessage(getIdentifiersOfNULLStrings(stringIdentifierToString), getIdentifiersOfEmptyStrings(stringIdentifierToString)));
        }
    }

    public static void throwExceptionIfExistsNULLStrings(Map<String, String> stringIdentifierToString) throws IllegalArgumentException {
        if (existsNULLStrings(stringIdentifierToString.values())) {
            throw new IllegalArgumentException(createStringsAreNULLMessage(getIdentifiersOfNULLStrings(stringIdentifierToString)));
        }
    }

    public static void throwExceptionIfExistsEmptyStrings(Map<String, String> stringIdentifierToString) throws IllegalArgumentException {
        if (existsEmptyStrings(stringIdentifierToString.values())) {
            throw new IllegalArgumentException(createStringsAreEmptyMessage(getIdentifiersOfEmptyStrings(stringIdentifierToString)));
        }
    }

    public static void throwExceptionIfAllNULLOrEmptyStrings(Map<String, String> stringIdentifierToString) throws IllegalArgumentException {
        if (allNULLOrEmptyStrings(stringIdentifierToString.values())) {
            throw new IllegalArgumentException(createStringsAreNULLOrEmptyMessage(getIdentifiersOfNULLStrings(stringIdentifierToString), getIdentifiersOfEmptyStrings(stringIdentifierToString)));
        }
    }

    public static boolean allNULLOrEmptyStrings(Collection<String> strings) {
        for (String string : strings) {
            if (!isStringNULLOrEmpty(string)) {
                return false;
            }
        }
        return true;
    }

    public static boolean existsNULLStrings(Collection<String> strings) {
        for (String string : strings) {
            if (isStringNULL(string)) {
                return true;
            }
        }
        return false;
    }

    public static boolean existsEmptyStrings(Collection<String> strings) {
        for (String string : strings) {
            if (string.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean existsNULLOrEmptyStrings(Collection<String> strings) {
        for (String string : strings) {
            if (isStringNULLOrEmpty(string)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStringNULLOrEmpty(String string) {
        return isStringNULL(string) ||string.isEmpty();
    }

    public static boolean isStringNULL(String string) {
        return string == null;
    }

    private static List<String> getIdentifiersOfNULLStrings(Map<String, String> stringIdentifierToString) {
        List<String> identifiersOfNULLStrings = new ArrayList<String>();

        for (Map.Entry<String, String> stringIdentifierAndString : stringIdentifierToString.entrySet()) {
            if (isStringNULL(stringIdentifierAndString.getValue())) {
                identifiersOfNULLStrings.add(stringIdentifierAndString.getKey());
            }
        }

        return identifiersOfNULLStrings;
    }

    private static List<String> getIdentifiersOfEmptyStrings(Map<String, String> stringIdentifierToString) {
        List<String> identifiersOfEmptyStrings = new ArrayList<String>();

        for (Map.Entry<String, String> stringIdentifierAndString : stringIdentifierToString.entrySet()) {
            String string = stringIdentifierAndString.getValue();
            if (string != null && string.isEmpty()) {
                identifiersOfEmptyStrings.add(stringIdentifierAndString.getKey());
            }
        }

        return identifiersOfEmptyStrings;
    }

    private static String createStringsAreNULLMessage(Collection<String> identifiersOfNULLStrings) {
        if (identifiersOfNULLStrings.isEmpty()) {
            return "";
        }

        return "NULL Strings: " + com.biomatters.geneious.publicapi.utilities.StringUtilities.join(", ", identifiersOfNULLStrings) + ".";
    }

    private static String createStringsAreEmptyMessage(Collection<String> identifiersOfEmptyStrings) {
        if (identifiersOfEmptyStrings.isEmpty()) {
            return "";
        }

        return "Empty Strings: " + com.biomatters.geneious.publicapi.utilities.StringUtilities.join(", ", identifiersOfEmptyStrings) + ".";
    }

    private static String createStringsAreNULLOrEmptyMessage(Collection<String> identifiersOfNULLStrings, Collection<String> identifiersOfEmptyStrings) {
        String message = createStringsAreNULLMessage(identifiersOfNULLStrings) + "\n" + createStringsAreEmptyMessage(identifiersOfEmptyStrings);

        return message.length() > 1 ? message : "";
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