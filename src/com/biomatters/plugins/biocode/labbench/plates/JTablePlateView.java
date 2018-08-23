package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.biomatters.plugins.biocode.labbench.reaction.Reaction.GEL_IMAGE_DOCUMENT_FIELD;

public class JTablePlateView extends JTable implements PlateViewPane<JTable> {

    private Plate plate;
    private boolean selectAll = false;
    boolean creating = false;
    private boolean editted = false;

    final Font DEFAULT_FONT = new JLabel().getFont();

    private Point mousePos = new Point(0,0);
    private Boolean[] wasSelected;

    private float zoom = 1.0f;


    private boolean colorBackground = true;

    private AbstractTableModel model = new AbstractTableModel(){
        @Override
        public int getRowCount() {
            return plate.getRows();
        }

        @Override
        public int getColumnCount() {
            return plate.getCols();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(rowIndex < 0 || columnIndex < 0) {
                return null;
            }
            else {
                return plate.getReactions()[getColumnCount() * rowIndex + columnIndex];
            }
        }

        @Override
        public String getColumnName(int column) {
            return Character.valueOf((char)(65+column)).toString();
        }

    };

    private TableCellRenderer reactionRenderer = new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Reaction reaction = (Reaction)value;
            List<DocumentField> fields = reaction.getFieldsToDisplay();
            StringBuilder builder = new StringBuilder("<html><div style=\"color:#888888;width:100%\">"+reaction.getLocationString()+"</div><div style='text-align: center;'>");
            for(int i = 0; i < fields.size(); i++) {
                if(!fields.get(i).equals(GEL_IMAGE_DOCUMENT_FIELD)) {
                    if(i == 0) {
                        builder.append("<b>").append(reaction.getFieldValue(""+fields.get(i).getCode())).append("</b>");
                    }
                    else {
                        builder.append(reaction.getFieldValue(""+fields.get(i).getCode()));
                    }
                    if(i < fields.size() - 1) {
                        builder.append("<br>");
                    }
                }

            }
            builder.append("</div></html>");
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if(component instanceof JLabel) {
                JLabel label = (JLabel) component;
                label.setHorizontalAlignment(JLabel.CENTER);
                label.setVerticalAlignment(JLabel.CENTER);
                label.setHorizontalTextPosition(JLabel.CENTER);
                label.setVerticalTextPosition(JLabel.TOP);
                label.setText(builder.toString());
                label.setBackground(colorBackground && !plate.isDeleted() ? reaction.isSelected() ? reaction.getBackgroundColor().darker() : reaction.getBackgroundColor() : Color.white);
                label.setForeground(plate.isDeleted() ? Color.gray : Color.black);
                Font font = new Font(DEFAULT_FONT.getAttributes());
                label.setFont(DEFAULT_FONT.deriveFont(DEFAULT_FONT.getSize() * zoom));

                if(fields.contains(GEL_IMAGE_DOCUMENT_FIELD)) {
                    label.setIcon(new ImageIcon(reaction.getGelImage().getImage()));
                }
                else {
                    label.setIcon(null);
                }
            }
            return component;
        }
    };
    private List<ListSelectionListener> selectionListeners = new ArrayList<>();
    private List<SimpleListener> editListeners = new ArrayList<>();


    public JTablePlateView(int numberOfWells, Reaction.Type type, boolean creating) {
        this.creating = creating;
        plate = new Plate(numberOfWells, type);
        init();
    }

    public JTablePlateView(Plate.Size size, Reaction.Type type, boolean creating) {
        this.creating = creating;
        plate = new Plate(size, type);


        init();
    }

    public JTablePlateView(Plate plate, boolean creating) {
        this.creating = creating;
        this.plate = plate;
        setModel(model);
        init();
    }

    @Override
    public JTable getComponent() {
        return this;
    }

    private void init() {
        setModel(model);
        setDefaultRenderer(Object.class, reactionRenderer);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowSelectionAllowed(false);
        setCellSelectionEnabled(false);
        setColumnSelectionAllowed(false);


        addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if (plate.isDeleted()) {
                    return;
                }
                requestFocus();
                boolean ctrlIsDown = (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;

                if (mousePos != null && shiftIsDown) {
                    if (wasSelected == null) {
                        initWasSelected(plate.getReactions());
                    }
                    selectRectangle(e);
                    repaint();
                    return;
                }

                if (e.getClickCount() == 1) {
                    //select just the cell the user clicked on
                    if (!ctrlIsDown) {
                        for (Reaction reaction : plate.getReactions()) {
                            reaction.setSelected(false);
                        }
                    }
                    Reaction reactionUnderMouse = (Reaction)getValueAt(rowAtPoint(e.getPoint()), columnAtPoint(e.getPoint()));
                    reactionUnderMouse.setSelected(!ctrlIsDown || !reactionUnderMouse.isSelected());
                }
                if (e.getClickCount() == 2) {
                    final AtomicBoolean editResult = new AtomicBoolean(false);
                    final ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Making changes...", JTablePlateView.this);
                    new Thread() {
                        public void run() {
                            progressFrame.setIndeterminateProgress();

                            final List<Reaction> selectedReactions = getSelectedReactions();

                            if (!selectedReactions.isEmpty()) {
                                if (ReactionUtilities.editReactions(selectedReactions, JTablePlateView.this, creating, true)) {
                                    checkForPlateSpecificErrors();

                                    editResult.set(true);

                                    if (!editted) {
                                        editted = true;
                                    }
                                }


                                ThreadUtilities.invokeNowOrLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (editResult.get()) {
                                            fireEditListeners();
                                        }

                                        ReactionUtilities.invalidateFieldWidthCacheOfReactions(selectedReactions);

                                        repaint();
                                        revalidate();
                                        repaint();
                                    }
                                });
                            }
                            progressFrame.setComplete();
                        }
                    }.start();
                }
                fireSelectionListeners();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (plate.isDeleted()) {
                    return;
                }
                selectAll = false;
                Reaction[] reactions = plate.getReactions();
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;
                if (!shiftIsDown) {
                    mousePos = e.getPoint();
                }

                initWasSelected(reactions);

            }

            @Override
            public void mouseReleased(MouseEvent e) {
                wasSelected = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter(){
            @Override
            public void mouseDragged(MouseEvent e) {
                if (plate.isDeleted()) {
                    return;
                }
                selectRectangle(e);
                repaint();
            }
        });

        addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e) {
                if (plate.isDeleted()) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_A && (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0) {
                    selectAll = !selectAll;
                    for (Reaction r : plate.getReactions()) {
                        r.setSelected(selectAll);
                    }
                    repaint();
                }
                repaint();
            }
        });
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, GuiUtilities.MENU_MASK), "select-all");
    }

    @Override
    public void setPlate(Plate plate) {
        this.plate = plate;
        model.fireTableDataChanged();
    }

    @Override
    public Plate getPlate() {
        return plate;
    }

    @Override
    public void decreaseZoom() {
        zoom -= 0.1f;
        if(zoom < 0.1) {
            zoom = 0.1f;
        }
        revalidate();
    }

    @Override
    public void increaseZoom() {
        zoom += 0.1;
        if(zoom > 3.0) {
            zoom = 3.0f;
        }
        revalidate();
    }

    @Override
    public void setDefaultZoom() {
        zoom = 1.0f;
        revalidate();
    }

    @Override
    public boolean isColorBackground() {
        return colorBackground;
    }

    @Override
    public void setColorBackground(boolean colorBackground) {
        this.colorBackground = colorBackground;
    }

    @Override
    public boolean isEditted() {
        return editted;
    }

    @Override
    public boolean checkForPlateSpecificErrors() {
        boolean detectedError = false;
        Collection<Reaction> reactions = Arrays.asList(getPlate().getReactions());

        if (plate.getReactionType().equals(Reaction.Type.Extraction)) {
            detectedError |= checkForDuplicateAttributesAmongReactions(reactions, new ExtractionIDGetter(), this);
        }


        detectedError |= checkForDuplicateAttributesAmongReactions(reactions, new ExtractionBarcodeGetter(), this);

        return detectedError;
    }

    private static boolean checkForDuplicateAttributesAmongReactions(Collection<Reaction> reactions, ReactionAttributeGetter<String> reactionAttributeGetter, JComponent dialogParent) {
        Map<String, List<Reaction>> attributeToReactions = ReactionUtilities.buildAttributeToReactionsMap(reactions, reactionAttributeGetter);
        List<String> duplications = new ArrayList<String>();

        for (Map.Entry<String, List<Reaction>> attributeAndReactions : attributeToReactions.entrySet()) {
            List<Reaction> reactionsAssociatedWithSameAttribute = attributeAndReactions.getValue();
            if (reactionsAssociatedWithSameAttribute.size() > 1) {
                duplications.add(
                        reactionAttributeGetter.getAttributeName() + ": " + attributeAndReactions.getKey()
                                + "<br>Well Numbers: " + StringUtilities.join(", ", ReactionUtilities.getWellNumbers(reactionsAssociatedWithSameAttribute))
                );

                ReactionUtilities.setReactionErrorStates(reactionsAssociatedWithSameAttribute, true);
            }
        }

        if (!duplications.isEmpty()) {
            Dialogs.showMessageDialog(
                    "Reactions that are associated with the same " + reactionAttributeGetter.getAttributeName().toLowerCase() + " were detected on the plate:<br><br>" + StringUtilities.join("<br>", duplications),
                    "Multiple Reactions With Same " + reactionAttributeGetter.getAttributeName() + " Detected",
                    dialogParent,
                    Dialogs.DialogIcon.INFORMATION
            );

            return true;
        }

        return false;
    }

    private void initWasSelected(Reaction[] reactions) {
        wasSelected = new Boolean[reactions.length];
        for (int i=0; i < reactions.length; i++) {
            wasSelected[i] = reactions[i].isSelected();
        }
    }

    @Override
    public List<Reaction> getSelectedReactions() {
        List<Reaction> selReactions = new ArrayList<>();
        for(Reaction r : plate.getReactions()) {
            if(r.isSelected()) {
                selReactions.add(r);
            }
        }
        return selReactions;
    }

    @Override
    public void addSelectionListener(ListSelectionListener lsl) {
        selectionListeners.add(lsl);
    }

    private void fireSelectionListeners() {
        for (ListSelectionListener listener : selectionListeners) {
            listener.valueChanged(new ListSelectionEvent(this, 0, plate.getReactions().length - 1, false));
        }

    }

    void fireEditListeners() {
        for (SimpleListener listener : editListeners) {
            listener.objectChanged();
        }
    }

    /**
     * adds a listener that's fired when one or more of the wells in this plate are edited
     * @param listener
     */
    @Override
    public void addEditListener(SimpleListener listener) {
        editListeners.add(listener);
    }

    @Override
    public void invalidate() {
        updateRowHeight();
        super.invalidate();
    }

    @Override
    public void revalidate() {
        updateRowHeight();
        super.revalidate();
    }

    private void updateRowHeight() {
        if (model != null) {
            int height = 32; //min row height
            for (int col = 0; col < model.getColumnCount(); col++) {
                for (int row = 0; row < model.getRowCount(); row++) {
                    height = Math.max(height, reactionRenderer.getTableCellRendererComponent(this, model.getValueAt(row, col), false, false, row, col).getPreferredSize().height + 10);
                }
            }
            if(getRowHeight() != height) {
                setRowHeight(height);
            }
        }
    }

    private void selectRectangle(MouseEvent e) {
        Rectangle selectionRect = createRect(new Point(rowAtPoint(mousePos), columnAtPoint(mousePos)), new Point(rowAtPoint(e.getPoint()), columnAtPoint(e.getPoint())));
        boolean ctrlIsDown = (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
        Reaction[] reactions = plate.getReactions();

        Reaction[] reactions1 = plate.getReactions();
        for (int i = 0; i < reactions1.length; i++) {
            Reaction r = reactions1[i];
            r.setSelected(ctrlIsDown && wasSelected != null ? wasSelected.length >= reactions.length && wasSelected[i] : false);
            assert wasSelected.length == reactions.length;
        }

        //select all wells within the selection rectangle
        for (int row = selectionRect.x; row <= selectionRect.width + selectionRect.x; row++) {
            for(int col = selectionRect.y; col <= selectionRect.height + selectionRect.y; col++) {
                Reaction reactionUnderMouse = (Reaction) getValueAt(row, col);
                if(reactionUnderMouse != null) {
                    reactionUnderMouse.setSelected(true);
                }
            }
        }
        fireSelectionListeners();
    }

    /**
     * creates a rectangle between the two points
     * @param p1 a point representing one corner of the rectangle
     * @param p2 a point representing the opposite corner of the rectangle
     * @return the finished rectangle
     */
    private Rectangle createRect(Point p1, Point p2) {
        int x, y, w, h;
        if (p1.x < p2.x) {
            x = p1.x;
            w = p2.x - p1.x;
        }
        else {
            x = p2.x;
            w = p1.x - p2.x;
        }

        if (p1.y < p2.y) {
            y = p1.y;
            h = p2.y - p1.y;
        }
        else {
            y = p2.y;
            h = p1.y - p2.y;
        }
        return new Rectangle(x, y, w, h);
    }
}
