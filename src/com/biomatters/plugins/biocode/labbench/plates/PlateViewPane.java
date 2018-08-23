package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.util.List;

public interface PlateViewPane<T extends JComponent> {
    void setPlate(Plate plate);

    Plate getPlate();

    void decreaseZoom();

    void increaseZoom();

    void setDefaultZoom();

    boolean isColorBackground();

    void setColorBackground(boolean colorBackground);

    boolean isEditted();

    boolean checkForPlateSpecificErrors();

    List<Reaction> getSelectedReactions();

    void addSelectionListener(ListSelectionListener lsl);

    void addEditListener(SimpleListener listener);

    T getComponent();
}
