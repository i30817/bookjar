package i3.ui;

import javax.swing.undo.AbstractUndoableEdit;
import i3.ui.controller.MovingPane;

final class MovementUndoableEdit extends AbstractUndoableEdit {

    private int lastIndex;
    private int currentIndex;
    private final MovingPane pane;

    public MovementUndoableEdit(int lastIndex, int currentIndex, MovingPane outer) {
        super();
        this.pane = outer;
        this.lastIndex = lastIndex;
        this.currentIndex = currentIndex;
    }

    @Override
    public void redo() {
        super.redo();
        lastIndex = pane.getIndex();
        pane.setIndex(currentIndex);
    }

    @Override
    public void undo() {
        super.undo();
        currentIndex = pane.getIndex();
        pane.setIndex(lastIndex);
    }
}
