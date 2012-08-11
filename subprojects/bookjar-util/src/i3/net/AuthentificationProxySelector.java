package i3.net;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.GroupLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import i3.swing.SwingUtils;

/**
 * A proxy selector prepared to configure proxies.
 * Insert this as the default proxySelector
 * before any code that attempts to connect to the
 * internet. Attempts to set default networking properties
 * in the constructor if they are not null.
 */
public class AuthentificationProxySelector extends java.net.ProxySelector {

    static {
        System.setProperty("java.net.useSystemProxies", "true");
    }
    //used for piggy backing on google DNS load balancing to find out
    //if there is a proxy between us and the internet.
    //(proxy firewalls are too diverse)
    private static final String GOOGLE_MAIN = "http://www.google.com";
    private static final long serialVersionUID = -7828571585756309513L;
    //The system default proxyselector can deal with system defaults
    private static ProxySelector defaultSelector = ProxySelector.getDefault();
    //Probing, done once after each set of probing to true and a request.
    private transient boolean probing;
    private transient final Object probingLock = new Object();
    //bean members
    private transient boolean isTrying;
    private ProxyMode proxyMode = ProxyMode.SYSTEM_PROXY;
    private String httpProxy = "";
    private int httpPort = 3128;
    private String httpsProxy = "";
    private int httpsPort = 443;
    private String socksProxy = "";
    private int socksPort = 1080;
    private String ftpProxy = "";
    private int ftpPort = 21;
    private String noProxyFor = "127.0.0.1, localhost";
    private String userName = "";
    private char[] password = new char[0];

    /**
     * @return if there is at least one network that is not local and up
     * @throws SocketException if there is no network
     */
    public static boolean nonLocalNetworkUp() throws SocketException {
        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        boolean upAndNotLocal = false;
        while (e.hasMoreElements()) {
            NetworkInterface n = e.nextElement();
            if (!n.isLoopback() && n.isUp()) {
                upAndNotLocal = true;
                break;
            }
        }
        return upAndNotLocal;
    }

    public enum ProxyMode {

        /**
         * Use configurated properties.
         */
        MANUAL_PROXY,
        /**
         * Uses the networking system properties (If they are null
         * they are set in the constructor)
         */
        SYSTEM_PROXY
    }

    private class SimpleAuthenticator extends Authenticator {

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(getUserName(), getPassword());
        }
    }

    /**
     * Defaults to a System proxy mode.
     * @param asks for networking values if needed.
     */
    public AuthentificationProxySelector() {
        this(true);
    }

    /**
     * Defaults to a System proxy mode.
     * @param if true ask for networking values if needed.
     */
    public AuthentificationProxySelector(boolean probe) {
        super();
        probing = probe;
        Authenticator.setDefault(new SimpleAuthenticator());
        if (System.getProperty("http.proxyHost") != null) {
            httpProxy = System.getProperty("http.proxyHost");
        }
        if (System.getProperty("http.proxyPort") != null) {
            httpPort = Integer.parseInt(System.getProperty("http.proxyPort"));
        }
        httpsProxy = httpProxy;
        if (System.getProperty("https.proxyPort") != null) {
            httpsPort = Integer.parseInt(System.getProperty("https.proxyPort"));
        }
        if (System.getProperty("socksProxyHost") != null) {
            socksProxy = System.getProperty("socksProxyHost");
        }
        if (System.getProperty("socksProxyPort") != null) {
            socksPort = Integer.parseInt(System.getProperty("socksProxyPort"));
        }
        if (System.getProperty("ftp.proxyHost") != null) {
            ftpProxy = System.getProperty("ftp.proxyHost");
        }
        if (System.getProperty("ftp.proxyPort") != null) {
            ftpPort = Integer.parseInt(System.getProperty("ftp.proxyPort"));
        }
        try {
            noProxyFor += ", " + InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
        }
        if (System.getProperty("http.nonProxyHosts") != null) {
            noProxyFor += System.getProperty("http.nonProxyHosts");
        }
        if (System.getProperty("java.net.socks.password") != null) {
            password = System.getProperty("java.net.socks.password").toCharArray();
        }
        if (System.getProperty("java.net.socks.username") != null) {
            userName = System.getProperty("java.net.socks.username");
        }
        if (userName == null && System.getProperty("user.name") != null) {
            userName = System.getProperty("user.name");
        }
    }

    public boolean isProbing() {
        synchronized (probingLock) {
            return probing;
        }
    }

    public void setProbing(boolean probing) {
        synchronized (probingLock) {
            this.probing = probing;
        }
    }

    /**
     * Probe for a working network, and show a input dialog
     * for proxy config if needed
     */
    private void probeForProxy() {
        if (isTrying) {
            return;
        }

        try {
            isTrying = true;
            if (nonLocalNetworkUp() && failConnect(GOOGLE_MAIN)) {
                showRequestDialog();
            }
        } catch (IOException e) {
            //if getNetworkInterfaces() threw a exception there is NO network
        } finally {
            isTrying = false;
        }
    }

    private boolean failConnect(String url) {
        try {
            URL u = new URL(url);
            //url.openConnection(Proxy.NO_PROXY) has a bug
            //that it still uses the current proxy selector
            //the is trying variable is to break the recursion caused
            //by this.
            URLConnection c = u.openConnection();
            c.setUseCaches(false);
            c.setConnectTimeout(c.getConnectTimeout() / 2);
            ((HttpURLConnection) c).setInstanceFollowRedirects(false);
            c.connect();
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    public void setProxyMode(final ProxyMode p) {
        if (p == null) {
            throw new IllegalArgumentException("null proxymode");
        }
        proxyMode = p;
    }

    public ProxyMode getProxyMode() {
        return proxyMode;
    }

    public void useGlobalProxy(final String s) {
        setHttpProxy(s);
        setSocksProxy(s);
        setFtpProxy(s);
        setHttpsProxy(s);
    }

    public void useGlobalPort(final int s) {
        setHttpPort(s);
        setSocksPort(s);
        setFtpPort(s);
        setHttpsPort(s);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        if (userName != null) {
            this.userName = userName.trim();
        }
    }

    public int getFtpPort() {
        return ftpPort;
    }

    public void setFtpPort(final int ftpPort) {
        this.ftpPort = ftpPort;
    }

    public String getFtpProxy() {
        return ftpProxy;
    }

    public void setFtpProxy(final String ftpProxy) {
        if (ftpProxy != null) {
            this.ftpProxy = ftpProxy.trim();
        }
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(final int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public String getHttpsProxy() {
        return httpsProxy;
    }

    public void setHttpsProxy(final String httpsProxy) {
        if (httpsProxy != null) {
            this.httpsProxy = httpsProxy.trim();
        }
    }

    public int getSocksPort() {
        return socksPort;
    }

    public void setSocksPort(final int socksPort) {
        this.socksPort = socksPort;
    }

    public String getSocksProxy() {
        return socksProxy;
    }

    public void setSocksProxy(final String socksProxy) {
        if (socksProxy != null) {
            this.socksProxy = socksProxy.trim();
        }
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(final int httpPort) {
        this.httpPort = httpPort;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    public void setHttpProxy(final String httpProxy) {
        if (httpProxy != null) {
            this.httpProxy = httpProxy.trim();
        }
    }

    public String getNoProxyFor() {
        return noProxyFor;
    }

    public void setNoProxyFor(final String noProxyFor) {
        if (noProxyFor != null) {
            this.noProxyFor = noProxyFor.trim();
        }
    }

    public char[] getPassword() {
        return password.clone();
    }

    public void setPassword(final char[] password) {
        if (password != null) {
            this.password = new char[password.length];
            System.arraycopy(password, 0, this.password, 0, password.length);
        }
    }

    @Override
    public List<Proxy> select(final URI uri) {
        List<Proxy> l = new ArrayList<>();


        if (uri == null) {
            throw new IllegalArgumentException("URI can't be null.");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Host can't be null.");
        }

        if (getNoProxyFor().contains(host)) {
            l.add(Proxy.NO_PROXY);
            return l;
        }


        synchronized (probingLock) {
            //only do this once.
            if (probing) {
                probeForProxy();
                probing = false;
            }
        }

        switch (proxyMode) {
            case SYSTEM_PROXY:
                l = defaultSelector.select(uri);
                break;
            case MANUAL_PROXY:
                addManualProxy(l, uri.getScheme());
                break;
            default:
                throw new AssertionError("Impossible");
        }
        return l;
    }

    private void addManualProxy(List<Proxy> l, String scheme) {
        InetSocketAddress addr;
        if ("http".equalsIgnoreCase(scheme)) {
            addr = new InetSocketAddress(getHttpProxy(), getHttpPort());
            l.add(new Proxy(Proxy.Type.HTTP, addr));
        } else if ("https".equalsIgnoreCase(scheme)) {
            addr = new InetSocketAddress(getHttpsProxy(), getHttpsPort());
            l.add(new Proxy(Proxy.Type.HTTP, addr));
        } else if ("socket".equalsIgnoreCase(scheme)) {
            //disable SOCKS?
            addr = new InetSocketAddress(getSocksProxy(), getSocksPort());
            l.add(new Proxy(Proxy.Type.SOCKS, addr));
        } else if ("ftp".equalsIgnoreCase(scheme)) {
            addr = new InetSocketAddress(getFtpProxy(), getFtpPort());
            l.add(new Proxy(Proxy.Type.HTTP, addr));
        } else {
            throw new IllegalArgumentException("unknown scheme: " + scheme);
        }
    }

    @Override
    public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
        defaultSelector.connectFailed(uri, sa, ioe);
    }

    private void showRequestDialog() {
        UserInterface userInterface = new UserInterface();
        //while the user presses ok, and sets the wrong info
        //try again. If he presses cancel, stop trying.
        //(the largerProbeForProxy method is never called again)
        boolean wrongConfig = true;
        while (wrongConfig) {
            SwingUtils.runInEDTAndWait(userInterface);
            wrongConfig = userInterface.pressedOk
                    && failConnect(GOOGLE_MAIN);
        }
    }

    private class UserInterface implements Runnable {

        volatile boolean pressedOk;

        @Override
        public void run() {
            JLabel msg = new JLabel("Need authentication for proxy (unsaved)");
            JLabel user = new JLabel("Username");
            JLabel pass = new JLabel("Password");
            JLabel http = new JLabel("Proxy");
            JLabel port = new JLabel("Port");
            user.setDisplayedMnemonic('u');
            pass.setDisplayedMnemonic('p');
            http.setDisplayedMnemonic('r');
            port.setDisplayedMnemonic('o');

            FocusListener listener = new FocusAdapter() {

                @Override
                public void focusGained(final FocusEvent e) {
                    //bug #4740914 for Formatted text fields grrr.
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            ((JTextComponent) e.getComponent()).selectAll();
                        }
                    });
                }
            };

            JPasswordField passField = new JPasswordField(new String(getPassword()));
            passField.addFocusListener(listener);
            JTextField userField = new JTextField(getUserName());
            userField.addFocusListener(listener);
            JTextField proxyField = new JTextField(getHttpProxy(), 30);
            proxyField.addFocusListener(listener);
            NumberFormat f = NumberFormat.getIntegerInstance();
            f.setGroupingUsed(false);
            JFormattedTextField portField = new JFormattedTextField(f);
            portField.addFocusListener(listener);
            portField.setValue(getHttpPort());
            try {
                portField.commitEdit();
            } catch (ParseException ex) {
                throw new IllegalStateException(ex);
            }
            portField.setColumns(4);
            user.setLabelFor(userField);
            pass.setLabelFor(passField);
            port.setLabelFor(portField);
            http.setLabelFor(proxyField);
            JPanel passwordPanel = new JPanel();

            GroupLayout l = new GroupLayout(passwordPanel);
            passwordPanel.setLayout(l);
            l.setAutoCreateContainerGaps(true);
            l.setAutoCreateGaps(true);
            GroupLayout.ParallelGroup fullX = l.createParallelGroup();


            GroupLayout.ParallelGroup leftX = l.createParallelGroup();
            leftX = leftX.addComponent(user).addComponent(pass).addComponent(http);

            GroupLayout.ParallelGroup rightX = l.createParallelGroup();
            rightX = rightX.addComponent(userField).addComponent(passField).addGroup(
                    l.createSequentialGroup().addComponent(proxyField).addComponent(port).addComponent(portField));

            GroupLayout.SequentialGroup mainX = l.createSequentialGroup();
            mainX = mainX.addGroup(leftX).addGroup(rightX);

            fullX = fullX.addComponent(msg).addGroup(mainX);

            GroupLayout.ParallelGroup topY = l.createBaselineGroup(true, true);
            GroupLayout.ParallelGroup middleY = l.createBaselineGroup(true, true);
            GroupLayout.ParallelGroup bottomY = l.createBaselineGroup(true, true);
            GroupLayout.ParallelGroup lastY = l.createBaselineGroup(true, true);
            GroupLayout.SequentialGroup groupY = l.createSequentialGroup();

            topY = topY.addComponent(msg);
            middleY = middleY.addComponent(user).addComponent(userField);
            bottomY = bottomY.addComponent(pass).addComponent(passField);
            lastY = lastY.addComponent(http).addComponent(proxyField).addComponent(port).addComponent(portField);
            groupY = groupY.addGroup(topY).addGroup(middleY).addGroup(bottomY).addGroup(lastY);
            l.setHorizontalGroup(fullX);
            l.setVerticalGroup(groupY);

            userField.requestFocusInWindow();
            String[] buttons = {"Ok", "Cancel"};
            Component active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

            int result = JOptionPane.showOptionDialog(active, passwordPanel, "Proxy authentication",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0]);

            if (result == JOptionPane.OK_OPTION) {
                setUserName(userField.getText());
                setPassword(passField.getPassword());
                useGlobalProxy(proxyField.getText());
                Number n = (Number) portField.getValue();
                useGlobalPort(n.intValue());
                setProxyMode(ProxyMode.MANUAL_PROXY);

            }
            pressedOk = result == JOptionPane.OK_OPTION;
        }
    }
}
