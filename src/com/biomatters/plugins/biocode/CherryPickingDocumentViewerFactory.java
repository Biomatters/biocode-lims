package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.CherryPickingDocument;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 21/04/2010
 * Time: 7:35:00 AM
 * To change this template use File | Settings | File Templates.
 */
public class CherryPickingDocumentViewerFactory extends TableDocumentViewerFactory {

    @Override
    public String getName() {
        return "Reactions";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(CherryPickingDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public TableModel getTableModel(AnnotatedPluginDocument[] docs) {
        final List<Reaction> allReactions = new ArrayList<Reaction>();
        for(AnnotatedPluginDocument doc : docs) {
            CherryPickingDocument cDoc = (CherryPickingDocument)doc.getDocumentOrCrash();
            allReactions.addAll(cDoc.getReactions());
        }

        return new AbstractTableModel(){
            public int getRowCount() {
                return allReactions.size();
            }

            public int getColumnCount() {
                return 3;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                Reaction reaction = allReactions.get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return reaction.getPlateName();
                    case 1:
                        return reaction.getLocationString();
                    case 2:
                        return reaction.getExtractionId();

                }
                return null;
            }
            @Override
            public String getColumnName(int column) {
                return new String[] {
                        "Plate",
                        "Well",
                        "Extraction ID"
                }[column];
            }
        };
    }
}