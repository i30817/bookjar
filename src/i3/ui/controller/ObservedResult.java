package i3.ui.controller;

class ObservedResult {

    private int first, last;

    public ObservedResult(int first, int last) {
        this.first = first;
        this.last = last;
    }

    /**
     * @return the observed interval
     */
    public int getInterval() {
        return last - first;
    }

    public int getFirst() {
        return first;
    }

    public int getLast() {
        return last;
    }
}
