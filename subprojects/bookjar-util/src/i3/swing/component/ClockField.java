package i3.swing.component;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.Timer;

public class ClockField extends JLabel {

    private int currentFormat = ClockField.HH_MM_SS_AMPM;
    private boolean activated = true;
    public static final int HH_MM = 0;
    public static final int HH_MM_AMPM = 1;
    public static final int HH_MM_SS_AMPM = 2;
    public static final int HH_MM_SS = 3;
    public static final int HH_MM_SS_AMPM_LOCALE = 4;
    protected Timer timer = new Timer(1000, new TimeTicker());
    protected DateFormat formater = DateFormat.getTimeInstance();

    public ClockField() {
        super();
        setFont(new Font("Monospaced", java.awt.Font.BOLD, getFont().getSize() + 2));
    }

    public ClockField(int format) {
        super();
        setDateFormatImpl(format);
        setFont(new Font("Monospaced", java.awt.Font.BOLD, getFont().getSize() + 2));
    }

    private void activate(boolean on) {
        if (!activated && on) {
            start();
            activated = on;
        } else if (activated && !on) {
            stop();
            activated = on;
        }
    }

    @Override
    public void setVisible(boolean v) {
        super.setVisible(v);
        activate(v);
    }

    @Override
    public void setEnabled(boolean e) {
        super.setEnabled(e);
        activate(e);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (activated) {
            start();
        }
    }

    @Override
    public void removeNotify() {
        if (activated) {
            stop();
        }
        super.removeNotify();
    }

    public void setDateFormat(int format) {
        setDateFormatImpl(format);
    }

    private void setDateFormatImpl(int format) {
        if (format < HH_MM || format > HH_MM_SS_AMPM_LOCALE) {
            throw new IllegalArgumentException("invalid format");
        }
        if (format == ClockField.HH_MM_SS_AMPM) {
            formater = DateFormat.getTimeInstance();
            timer.setDelay(1000);
        } else if (format == ClockField.HH_MM_AMPM) {
            formater = DateFormat.getTimeInstance(DateFormat.SHORT);
            timer.setDelay(1000 * 60);
        } else if (format == ClockField.HH_MM_SS_AMPM_LOCALE) {
            formater = DateFormat.getTimeInstance(DateFormat.LONG);
            timer.setDelay(1000);
        } else if (format == ClockField.HH_MM) {
            formater = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timer.setDelay(1000 * 60);
        } else if (format == ClockField.HH_MM_SS) {
            formater = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            timer.setDelay(1000);
        }

        currentFormat = format;
    }

    private void stop() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }

    private void start() {


        if (currentFormat <= HH_MM_AMPM) {//Minute based
            Calendar now = Calendar.getInstance();
            //In the next minute
            int max = 1000 * (1 + now.getActualMaximum(Calendar.SECOND) - now.get(Calendar.SECOND));
            timer.setInitialDelay(max);
        } else {//Second based
            //In the next second
            timer.setInitialDelay(1001);
        }

        setText(formater.format(new Date()));
        timer.restart();
    }

    class TimeTicker implements ActionListener {

        public void actionPerformed(ActionEvent arg0) {
            ClockField.this.setText(formater.format(new Date()));
        }
    }
}
