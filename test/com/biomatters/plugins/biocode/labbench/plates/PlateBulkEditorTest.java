package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 18/05/15 7:39 AM
 */
public class PlateBulkEditorTest extends LimsTestCase {
    // TODO: figure out Unable to XML deserialize multiple options. This is most likely because the child multiple options do not fulfil the XMLSerializable contract.

    @Test
    public void testDocumentFieldEditorGetChanges() {
        PlateBulkEditor.DocumentFieldEditor documentFieldEditor = new PlateBulkEditor.DocumentFieldEditor(PlateBulkEditor.WORKFLOW_ID_FIELD, new Plate(Plate.Size.w16, Reaction.Type.PCR), PlateBulkEditor.Direction.ACROSS_AND_DOWN, null);

        documentFieldEditor.setText(getNewLineSeparatedSequenceOfNumbers(0, 15));
        documentFieldEditor.valuesFromTextView();

        assertTrue(documentFieldEditor.getChanges().isEmpty());

        documentFieldEditor.setText(getNewLineSeparatedSequenceOfNumbers(1, 16));

        List<String> changes = documentFieldEditor.getChanges();
        assertEquals(16, changes.size());
        assertEquals("A1 : 0 => 1", changes.get(0));
        assertEquals("A2 : 8 => 9", changes.get(1));
        assertEquals("B1 : 1 => 2", changes.get(2));
        assertEquals("B2 : 9 => 10", changes.get(3));
        assertEquals("C1 : 2 => 3", changes.get(4));
        assertEquals("C2 : 10 => 11", changes.get(5));
        assertEquals("D1 : 3 => 4", changes.get(6));
        assertEquals("D2 : 11 => 12", changes.get(7));
        assertEquals("E1 : 4 => 5", changes.get(8));
        assertEquals("E2 : 12 => 13", changes.get(9));
        assertEquals("F1 : 5 => 6", changes.get(10));
        assertEquals("F2 : 13 => 14", changes.get(11));
        assertEquals("G1 : 6 => 7", changes.get(12));
        assertEquals("G2 : 14 => 15", changes.get(13));
        assertEquals("H1 : 7 => 8", changes.get(14));
        assertEquals("H2 : 15 => 16", changes.get(15));

        documentFieldEditor = new PlateBulkEditor.DocumentFieldEditor(PlateBulkEditor.WORKFLOW_ID_FIELD, new Plate(Plate.Size.w16, Reaction.Type.PCR), PlateBulkEditor.Direction.DOWN_AND_ACROSS, null);;

        documentFieldEditor.setText(getNewLineSeparatedSequenceOfNumbers(0, 15));
        documentFieldEditor.valuesFromTextView();
        documentFieldEditor.setText(getNewLineSeparatedSequenceOfNumbers(1, 16));

        changes = documentFieldEditor.getChanges();
        assertEquals(16, changes.size());
        assertEquals("A1 : 0 => 1", changes.get(0));
        assertEquals("A2 : 1 => 2", changes.get(1));
        assertEquals("B1 : 2 => 3", changes.get(2));
        assertEquals("B2 : 3 => 4", changes.get(3));
        assertEquals("C1 : 4 => 5", changes.get(4));
        assertEquals("C2 : 5 => 6", changes.get(5));
        assertEquals("D1 : 6 => 7", changes.get(6));
        assertEquals("D2 : 7 => 8", changes.get(7));
        assertEquals("E1 : 8 => 9", changes.get(8));
        assertEquals("E2 : 9 => 10", changes.get(9));
        assertEquals("F1 : 10 => 11", changes.get(10));
        assertEquals("F2 : 11 => 12", changes.get(11));
        assertEquals("G1 : 12 => 13", changes.get(12));
        assertEquals("G2 : 13 => 14", changes.get(13));
        assertEquals("H1 : 14 => 15", changes.get(14));
        assertEquals("H2 : 15 => 16", changes.get(15));
    }


    // TODO: figure out Unable to XML deserialize multiple options. This is most likely because the child multiple options do not fulfil the XMLSerializable contract.
    @Test
    public void testBuldEdit_AddBarcodesFromFile_splitByDelimiters() {
        PlateBulkEditor.DocumentFieldEditor barcodeEditor = new PlateBulkEditor.DocumentFieldEditor(PlateBulkEditor.EXTRACTION_BARCODE_FIELD, new Plate(Plate.Size.w1, Reaction.Type.PCR), PlateBulkEditor.Direction.ACROSS_AND_DOWN, null);
        BiocodeUtilities.Well well = new BiocodeUtilities.Well("A1");

        List<String> delimiters = Arrays.asList("\t", ";", ",", " ", "-", "_", /*Try some combinations:*/ ";\t", ",\t", "-;");
        for (int i = 0; i < delimiters.size(); i++) {
            PlateBulkEditor.setWellAndBarcode(String.format("A1%s%d", delimiters.get(i), i+2), barcodeEditor);
            Object value = barcodeEditor.getValue(well.row(), well.col());
            assertEquals(""+(i+2), value);
        }
        barcodeEditor.setValue(well.row(), well.col(), "0");
        // test invalid lines:
        List<String> invalidLines = Arrays.asList("Header", "Two words", "Three words header", "\n", "What's this?", "A1:A2:1234", "A1:x\nA1;y");
        for (String invalidLine : invalidLines) {
            PlateBulkEditor.setWellAndBarcode(invalidLine, barcodeEditor);
            Object value = barcodeEditor.getValue(well.row(), well.col());
            assertEquals("0", value);
        }
    }


    private static String getNewLineSeparatedSequenceOfNumbers(int start, int end) {
        if (start >= end) {
            throw new IllegalArgumentException("The supplied start argument (" + start + ") must be smaller than the supplied end (" + end + ") argument.");
        }

        StringBuilder sequenceBuilder = new StringBuilder();

        while (start <= end) {
            sequenceBuilder.append(start++).append("\n");
        }

        sequenceBuilder.deleteCharAt(sequenceBuilder.length() - 1);

        return sequenceBuilder.toString();
    }
}