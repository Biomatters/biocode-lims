package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.io.File;
import java.io.IOException;
import java.awt.*;

import jebl.util.ProgressListener;

import javax.swing.table.TableModel;

import jxl.write.*;
import jxl.Workbook;
import jxl.format.Colour;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 19/05/2010
 * Time: 8:38:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class WorkflowSummaryExporter extends DocumentFileExporter{

    private static TableDocumentViewerFactory[] factoriesToExport = new TableDocumentViewerFactory[] {
        new MultiWorkflowDocumentViewerFactory(),
        new MultiPrimerDocumentViewerFactory(Reaction.Type.PCR),
        new MultiPrimerDocumentViewerFactory(Reaction.Type.CycleSequencing)
    };

    public String getDefaultExtension() {
        return "xls";
    }

    public String getFileTypeDescription() {
        return "Workflow Summary (Excel)";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public void export(File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);

            for (int i = 0; i < factoriesToExport.length; i++) {
                TableDocumentViewerFactory factory = factoriesToExport[i];
                TableModel tableModel = factory.getTableModel(documents);
                WritableSheet sheet = workbook.createSheet(factory.getName(), i);
                ExcelUtilities.exportTable(sheet, tableModel, progressListener, options);
            }

            workbook.write();
            workbook.close();
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        }
    }

}