package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.ActionProvider;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyTableModel implements TableModel {

    private final List<VerifyResult> rows = new ArrayList<VerifyResult>();
    private int selectedRow = -1;
    private JComponent dialogParent = null;

    private final VerifyBinningOptions binningOptions;
    private final AnnotatedPluginDocument resultsDocument;

    private static final Color HTML_GREEN = new Color(0, 128, 0);
    private static final Color ORANGEY = new Color(233, 160, 45);

    public VerifyTaxonomyTableModel(AnnotatedPluginDocument results, VerifyBinningOptions overrideBinningOptions) {
        resultsDocument = results;
        VerifyTaxonomyResultsDocument resultsPluginDocument = (VerifyTaxonomyResultsDocument) results.getDocumentOrCrash();
        for (VerifyResult entry : resultsPluginDocument.getResults()) {
            rows.add(entry);
        }
        if (overrideBinningOptions != null) {
            binningOptions = overrideBinningOptions;
        } else {
            binningOptions = new VerifyBinningOptions();
            if (resultsPluginDocument.getBinningOptionsValues() != null) {
                binningOptions.valuesFromXML(resultsPluginDocument.getBinningOptionsValues());
            }
        }
        saveAction.setEnabled(false);
        goToAssemblyAction.setEnabled(false);
        showOtherHitsAction.setEnabled(false);
    }

    /**
     *
     * @param i selected row or -1 for none
     */
    public void setSelectedRow(int i) {
        selectedRow = i;
        goToAssemblyAction.setEnabled(i != -1);
        showOtherHitsAction.setEnabled(i != -1 && rows.get(selectedRow).hitDocuments.size() > 1);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columns.length;
    }

    public String getColumnName(int columnIndex) {
        return columns[columnIndex].name;
    }

    public Class<?> getColumnClass(int columnIndex) {
        return columns[columnIndex].columnClass;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns[columnIndex].getValue(rows.get(rowIndex));
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new UnsupportedOperationException("Edit this value, you shall not!");
    }

    private final List<TableModelListener> listeners = new ArrayList<TableModelListener>();

    public void addTableModelListener(TableModelListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeTableModelListener(TableModelListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    private void fireListeners() {
        List<TableModelListener> listenersCopy;
        synchronized (listeners) {
            listenersCopy = new ArrayList<TableModelListener>(listeners);
        }
        for (TableModelListener listener : listenersCopy) {
            listener.tableChanged(new TableModelEvent(this));
        }
    }

    public void setColumnWidths(TableColumnModel columnModel) {
        for (int i = 0; i < columns.length; i++) {
            VerifyColumn column = columns[i];
            columnModel.getColumn(i).setPreferredWidth(column.getPreferredWidth());
            if (column.fixedWidth) {
                columnModel.getColumn(i).setMinWidth(column.getPreferredWidth());
            }
            if (column.fixedWidth) {
                columnModel.getColumn(i).setMaxWidth(column.getPreferredWidth());
            }
        }
    }

    public Color getColorForHitLength(int length, boolean isSelected) {
        if (length >= binningOptions.getHighBinOptions().getMinLength()) {
            return HTML_GREEN;
        }
        if (length >= binningOptions.getMediumBinOptions().getMinLength()) {
            return isSelected ? ORANGEY.darker() : ORANGEY;
        }
        return Color.red; 
    }

    public Color getColorForIdentity(double identity, boolean isSelected) {
        if (identity >= binningOptions.getHighBinOptions().getMinIdentity()) {
            return HTML_GREEN;
        }
        if (identity >= binningOptions.getMediumBinOptions().getMinIdentity()) {
            return isSelected ? ORANGEY.darker() : ORANGEY;
        }
        return Color.red;
    }

    private final GeneiousAction goToAssemblyAction = new GeneiousAction("Go To Query",
            "Select the query document for the selected result", IconUtilities.getIcons("sequenceSearch16.png")) {
        public void actionPerformed(ActionEvent e) {
            URN urn = rows.get(selectedRow).queryDocument.getURN();
            DocumentUtilities.selectDocument(urn);
        }
    };

    private static final Preferences PREFS = Preferences.userNodeForPackage(VerifyTaxonomyTableModel.class);

    private final GeneiousAction showOtherHitsAction = new GeneiousAction("Show Other Hits",
            "Show the other hits that were downloaded for this query (SuperTip: Double click in table for quick access)", IconUtilities.getIcons("nucleotideList16.png")) {
        public void actionPerformed(ActionEvent e) {
            List<VerifyResult> dummyResults = new ArrayList<VerifyResult>();
            for (AnnotatedPluginDocument hitDocument : rows.get(selectedRow).hitDocuments) {
                dummyResults.add(new VerifyResult(Collections.singletonList(hitDocument), rows.get(selectedRow).queryDocument));
            }
            JComponent tableComponent = new VerifyTaxonomyDocumentViewerFactory(binningOptions).createViewer(new AnnotatedPluginDocument[]{
                    DocumentUtilities.createAnnotatedPluginDocument(new VerifyTaxonomyResultsDocument(dummyResults, null))
            }).getComponent();

            Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"Close"}, "Other Hits", dialogParent, Dialogs.DialogIcon.NO_ICON);
            int width = PREFS.getInt("popupWidth4", 1024);
            int height = PREFS.getInt("popupHeight4", 500);
            tableComponent.setPreferredSize(new Dimension(width, height));
            dialogOptions.setMaxWidth(width + 50);
            dialogOptions.setMaxHeight(height + 100);
            tableComponent.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    PREFS.putInt("popupWidth4", e.getComponent().getWidth());
                    PREFS.putInt("popupHeight4", e.getComponent().getHeight());
                }
            });
            Dialogs.showDialog(dialogOptions, tableComponent);
        }
    };

    private final GeneiousAction binningParametersAction = new GeneiousAction("Binning Parameters",
            "Set the binning parameters for this taxonomy verification",
            new Icons(new ImageIcon(VerifyTaxonomyTableModel.class.getResource("happy.png")))) {
        public void actionPerformed(ActionEvent e) {
            Element oldValues = binningOptions.valuesToXML("flyingMonkey");
            boolean choice = Dialogs.showOptionsDialog(binningOptions, "Binning Parameters", false, dialogParent);
            if (!choice) {
                binningOptions.valuesFromXML(oldValues);
            } else {
                Element currentDocumentBinning = ((VerifyTaxonomyResultsDocument) resultsDocument.getDocumentOrCrash()).getBinningOptionsValues();
                saveAction.setEnabled(currentDocumentBinning == null || !currentDocumentBinning.equals(binningOptions.valuesToXML(VerifyTaxonomyResultsDocument.BINNING_ELEMENT_NAME)));
            }
            fireListeners();
        }
    };

    private final GeneiousAction saveAction = new GeneiousAction("Save",
            "Save the current binning parameters", IconUtilities.getIcons("save16.png")) {
        public void actionPerformed(ActionEvent e) {
            ((VerifyTaxonomyResultsDocument) resultsDocument.getDocumentOrCrash()).setBinningOptionsValues(binningOptions.valuesToXML(VerifyTaxonomyResultsDocument.BINNING_ELEMENT_NAME));
            resultsDocument.saveDocument();
            setEnabled(false);
        }
    };

    public ActionProvider getActionProvider() {
        return new ActionProvider() {
            @Override
            public List<GeneiousAction> getOtherActions() {
                return Arrays.asList(goToAssemblyAction, showOtherHitsAction, new GeneiousAction.Divider(), binningParametersAction);
            }

            @Override
            public GeneiousAction getSaveAction() {
                return saveAction;
            }
        };
    }

    public void setTable(final JTable table) {
        setColumnWidths(table.getColumnModel());
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                setSelectedRow(table.getSelectedRow());
            }
        });
        dialogParent = table;
    }

    public void doDoubleClick() {
        if (showOtherHitsAction.isEnabled()) {
            showOtherHitsAction.actionPerformed(null);
        }
    }

    private abstract static class VerifyColumn {

        final String name;
        final Class columnClass;
        final boolean fixedWidth;

        protected VerifyColumn(String name, Class columnClass) {
            this(name, columnClass, false);
        }

        VerifyColumn(String name, Class columnClass, boolean fixedWidth) {
            this.name = name;
            this.columnClass = columnClass;
            this.fixedWidth = fixedWidth;
        }

        int getPreferredWidth() {
            return new JLabel(name).getPreferredSize().width;
        }

        abstract Object getValue(VerifyResult row);
    }

    private final VerifyColumn[] columns = new VerifyColumn[] {
            new VerifyColumn("Bin", Icons.class, true) {
                @Override
                Object getValue(VerifyResult row) {
                    if (binningOptions.getHighBinOptions().isMetBy(row, binningOptions.getKeywords())) {
                        return VerifyBinOptions.Bin.High.getIcons();
                    }
                    if (binningOptions.getMediumBinOptions().isMetBy(row, binningOptions.getKeywords())) {
                        return VerifyBinOptions.Bin.Medium.getIcons();
                    }
                    return VerifyBinOptions.Bin.Low.getIcons();
                }

                @Override
                int getPreferredWidth() {
                    return 40;
                }
            },
            new VerifyColumn("Query", String.class) {
                @Override
                Object getValue(VerifyResult row) {
                    return row.queryDocument.getName();
                }

                @Override
                int getPreferredWidth() {
                    return 30;
                }
            },
            new VerifyColumn("Query Taxon", String.class) {
                @Override
                Object getValue(VerifyResult row) {
                    Object fimsTaxonomy = row.queryDocument.getFieldValue(DocumentField.TAXONOMY_FIELD);
                    AtomicReference<String> taxonomy = new AtomicReference<String>(fimsTaxonomy == null ? "" : fimsTaxonomy.toString());
                    Object taxObject = row.hitDocuments.get(0).getFieldValue(DocumentField.TAXONOMY_FIELD);
                    AtomicReference<String> blastTaxonomy = new AtomicReference<String>(taxObject == null ? "" : taxObject.toString());
                    highlight(taxonomy, ";", blastTaxonomy);
                    return taxonomy.get();
                }

                @Override
                int getPreferredWidth() {
                    return 70;
                }
            },
            new VerifyColumn("Hit Taxon", String.class) {
                @Override
                Object getValue(VerifyResult row) {
                    Object fimsTaxonomy = row.queryDocument.getFieldValue(DocumentField.TAXONOMY_FIELD);
                    AtomicReference<String> taxonomy = new AtomicReference<String>(fimsTaxonomy == null ? "" : fimsTaxonomy.toString());
                    Object taxObject = row.hitDocuments.get(0).getFieldValue(DocumentField.TAXONOMY_FIELD);
                    AtomicReference<String> blastTaxonomy = new AtomicReference<String>(taxObject == null ? "" : taxObject.toString());
                    highlight(taxonomy, ";", blastTaxonomy);
                    return blastTaxonomy.get();
                }

                @Override
                int getPreferredWidth() {
                    return 180;
                }
            },
            new VerifyColumn("Keywords", String.class) {
                @Override
                Object getValue(VerifyResult row) {
                    AtomicReference<String> keys = new AtomicReference<String>(binningOptions.getKeywords());
                    AtomicReference<String> definition = new AtomicReference<String>(row.hitDocuments.get(0).getFieldValue(DocumentField.DESCRIPTION_FIELD).toString());
                    highlight(keys, ",", definition);
                    return keys.get();
                }

                @Override
                int getPreferredWidth() {
                    return 50;
                }
            },
            new VerifyColumn("Hit Definition", String.class) {
                @Override
                Object getValue(VerifyResult row) {
                    AtomicReference<String> keys = new AtomicReference<String>(binningOptions.getKeywords());
                    AtomicReference<String> definition = new AtomicReference<String>(row.hitDocuments.get(0).getFieldValue(DocumentField.DESCRIPTION_FIELD).toString());
                    highlight(keys, ",", definition);
                    return definition.get();
                }

                @Override
                int getPreferredWidth() {
                    return 120;
                }
            },
            new VerifyColumn("Hit Length", Integer.class, true) {
                @Override
                Object getValue(VerifyResult row) {
                    return row.hitDocuments.get(0).getFieldValue(DocumentField.SEQUENCE_LENGTH);
                }
            },
            new VerifyColumn("Hit Identity", Percentage.class, true) {
                @Override
                Object getValue(VerifyResult row) {
                    return row.hitDocuments.get(0).getFieldValue(DocumentField.ALIGNMENT_PERCENTAGE_IDENTICAL);
                }
            },
            new VerifyColumn("Assembly Bin", String.class, true) {
                @Override
                Object getValue(VerifyResult row) {
                    return row.queryDocument.getFieldValue(DocumentField.BIN).toString();
                }
            }
    };

    /**
     *
     * @param keywords keywords separated by delimiter, used to return value too
     * @param delimiter
     * @param s string to check for keywords, used to return value too
     * @return true iff all keywords were found in s, false otherwise
     */
    private static void highlight(AtomicReference<String> keywords, String delimiter, AtomicReference<String> s) {
        String[] keys = keywords.get().split(delimiter);
        String keys2 = keywords.get();
        String s2 = s.get();
        for (String key : keys) {
            key = key.trim();
            int index = s2.toLowerCase().indexOf(key.toLowerCase());
            if (index != -1) {
                keys2 = keys2.replace(key, "<font color=\"green\">" + key + "</font>");
                s2 = s2.substring(0, index) + "<font color=\"green\">" + s2.substring(index, index + key.length()) + "</font>" + s2.substring(index + key.length());
            } else {
                keys2 = keys2.replace(key, "<font color=\"red\">" + key + "</font>");
            }
        }
        keywords.set(keys2);
        s.set(s2);
    }
}
