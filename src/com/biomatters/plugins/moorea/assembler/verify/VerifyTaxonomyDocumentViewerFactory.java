package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.ActionProvider;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.plugins.moorea.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.moorea.labbench.TableSorter;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyDocumentViewerFactory extends TableDocumentViewerFactory {

    private final VerifyBinningOptions overrideBinningOptions;

    public VerifyTaxonomyDocumentViewerFactory() {
        this.overrideBinningOptions = null;
    }

    public VerifyTaxonomyDocumentViewerFactory(VerifyBinningOptions overrideBinningOptions) {
        this.overrideBinningOptions = overrideBinningOptions;
    }

    public TableModel getTableModel(AnnotatedPluginDocument[] docs) {
        return new VerifyTaxonomyTableModel((VerifyTaxonomyResultsDocument)docs[0].getDocumentOrCrash(), overrideBinningOptions);
    }

    @Override
    public void messWithTheTable(JTable table) {
        table.setSelectionBackground(new Color(180, 180, 180));
        table.setSelectionForeground(Color.black);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getRowHeight() * 5);
        TableSorter sorter = (TableSorter) table.getModel();
        sorter.setSortingStatus(0, TableSorter.DESCENDING);
        final VerifyTaxonomyTableModel verifyTaxonomyModel = (VerifyTaxonomyTableModel) sorter.getTableModel();
        verifyTaxonomyModel.setTable(table);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    verifyTaxonomyModel.doDoubleClick();
                }
            }
        });

        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    if (!label.getText().startsWith("<html>")) {
                        label.setText("<html>" + label.getText() + "</html>");
                    }
                    label.setVerticalAlignment(JLabel.TOP);
                }
                return cellRendererComponent;
            }
        });
        table.setDefaultRenderer(Integer.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    label.setVerticalAlignment(JLabel.TOP);
                    label.setHorizontalAlignment(JLabel.LEFT);
                    if (value instanceof Integer) {
                        label.setForeground(verifyTaxonomyModel.getColorForHitLength((Integer)value, isSelected));
                    }
                }
                return cellRendererComponent;
            }
        });
        table.setDefaultRenderer(Percentage.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    label.setVerticalAlignment(JLabel.TOP);
                    label.setHorizontalAlignment(JLabel.LEFT);
                    if (value instanceof Percentage) {
                        label.setForeground(verifyTaxonomyModel.getColorForIdentity(((Percentage)value).doubleValue(), isSelected));
                    }
                }
                return cellRendererComponent;
            }
        });
        table.setDefaultRenderer(Icons.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, "", isSelected, false, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    label.setVerticalAlignment(JLabel.CENTER);
                    label.setHorizontalAlignment(JLabel.CENTER);
                    label.setIcon(((Icons)value).getOriginalIcon());
                }
                return cellRendererComponent;
            }
        });
    }

    @Override
    protected ActionProvider getActionProvider(JTable table) {
        TableModel model = ((TableSorter) table.getModel()).getTableModel();
        return ((VerifyTaxonomyTableModel)model).getActionProvider();
    }

    public String getName() {
        return "Verify Taxonomy";
    }

    public String getDescription() {
        return "Displays the results of verifying taxonomy";
    }

    public String getHelp() {
        return "It is not common sense to eat delicious dinner inside someone else's closet!";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(VerifyTaxonomyResultsDocument.class, 1, 1)
        };
    }
}
