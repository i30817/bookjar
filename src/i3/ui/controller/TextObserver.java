package i3.ui.controller;

import java.awt.Rectangle;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;



final class TextObserver {

    public int first;
    public int last;
    private JTextComponent text;

    public TextObserver(JTextComponent text) {
        reset();
        this.text = text;
    }

    private void setFirst(int in) {
        this.first = in;
    }

    private void setLast(int in) {
        this.last = in;
    }

    public JTextComponent getText() {
        return text;
    }

    /**
     * Resets the state of the observed values
     */
    public void reset() {
//        System.out.println("reset");
        first = Integer.MAX_VALUE;
        last = Integer.MIN_VALUE;
    }
    /**
     * Gets the result of the computation
     */
    public ObservedResult getResult() {
        return new ObservedResult(
                (first == Integer.MAX_VALUE) ? 0 : first,
                (last == Integer.MIN_VALUE) ? 0 : last);
    }

    public void observe(View view, Rectangle lineOfText) {
        int startOffSet = view.getStartOffset();
        int endOffSet = view.getEndOffset();

        if (startOffSet < first) {
            setFirst(startOffSet);
        }

        if (endOffSet > last) {
            if (endOffSet == view.getDocument().getLength() + 1) {
                //avoid abstract document stupidity
                setLast(endOffSet - 1);
            } else {
                setLast(endOffSet);
            }

        }
    }
}
