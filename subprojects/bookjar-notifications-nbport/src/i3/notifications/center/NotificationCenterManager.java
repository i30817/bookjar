/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package i3.notifications.center;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import i3.notifications.NotificationImpl;
import i3.notifications.NotificationDisplayer.Category;

/**
 *
 * @author jpeska
 */
public class NotificationCenterManager {

    public static final String PROP_NOTIFICATIONS_CHANGED = "notificationsChanged"; //NOI18N
    public static final String PROP_NOTIFICATION_ADDED = "notificationAdded"; //NOI18N
    public static final String PROP_NOTIFICATION_READ = "notificationRead"; //NOI18N
    private static final int NOTIFICATIONS_CAPACITY = 100;
    private static final PropertyChangeSupport propSupport = new PropertyChangeSupport(NotificationCenterManager.class);
    private static NotificationCenterManager instance = null;
    private final List<NotificationImpl> notifications = new ArrayList<>();
    private final List<NotificationImpl> filteredNotifications = new ArrayList<>();
    private final NotificationTableModel model = new NotificationTableModel();

    private NotificationCenterManager() {
    }

    public static NotificationCenterManager getInstance() {
        if (instance == null) {
            instance = new NotificationCenterManager();
        }
        return instance;
    }

    public void add(NotificationImpl notification) {
        boolean capacityFull = false;
        synchronized (notifications) {
            capacityFull = notifications.size() == NOTIFICATIONS_CAPACITY;
            if (capacityFull) {
                notifications.remove(0).clear();
            }
            notifications.add(notification);
        }
        if (isEnabled(notification)) {
            filteredNotifications.add(notification);
            firePropertyChange(PROP_NOTIFICATION_ADDED, notification);
        }
        updateTable(capacityFull);
    }

    public void delete(NotificationImpl notification) {
        synchronized (notifications) {
            if (!notifications.remove(notification)) {
                return;
            }
        }
        if (isEnabled(notification)) {
            filteredNotifications.remove(notification);
            if (!notification.isRead()) {
                firePropertyChange(PROP_NOTIFICATION_READ, notification);
            }
        }
        updateTable(false);
    }

    public void updateTable(boolean filter) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                NotificationTableModel model = getModel();
                synchronized (notifications) {
                    model.setEntries(filteredNotifications);
                }
            }
        });
    }

    public void update(NotificationImpl n) {
        final int index;
        synchronized (notifications) {
            index = filteredNotifications.indexOf(n);
        }
        if (index != -1) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    NotificationTableModel model = getModel();
                    model.updateIndex(index);
                }
            });
        }
    }

    public void deleteAll() {
        synchronized (notifications) {
            notifications.clear();
            filteredNotifications.clear();
            firePropertyChange(PROP_NOTIFICATIONS_CHANGED, null);
            updateTable(false);
        }
    }

    public void markAllRead() {
        synchronized (notifications) {
            for (NotificationImpl n : notifications) {
                n.markAsRead(true);
            }
        }
    }

    public List<Category> getCategories() {
        return Category.getCategories();
    }

    public void wasRead(NotificationImpl notification) {
        firePropertyChange(PROP_NOTIFICATION_READ, notification);
        update(notification);
    }

    public void wasChanged(NotificationImpl notification) {
        firePropertyChange(PROP_NOTIFICATIONS_CHANGED, notification);
        update(notification);
    }

    private NotificationTableModel getModel() {
        return model;
    }

    public int getUnreadCount() {
        int count = 0;
        synchronized (notifications) {
            for (NotificationImpl notification : notifications) {
                if (!notification.isRead()) {
                    count++;
                }
            }
        }
        return count;
    }

    public NotificationImpl getLastUnreadNotification() {
        synchronized (notifications) {
            for (int i = filteredNotifications.size() - 1; i >= 0; i--) {
                NotificationImpl n = filteredNotifications.get(i);
                if (!n.isRead()) {
                    return n;
                }
            }
        }
        return null;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        propSupport.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        propSupport.removePropertyChangeListener(l);
    }

    private void firePropertyChange(final String propName, final NotificationImpl notification) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (PROP_NOTIFICATION_ADDED.equals(propName)) {
                    notification.initDecorations();
                }
                propSupport.firePropertyChange(propName, null, notification);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    static Logger getLogger() {
        return Logger.getLogger(NotificationCenterManager.class.getName());
    }

    public boolean isEnabled(NotificationImpl notification) {
        return true;
    }

    /**
     * for testing
     */
    int getTotalCount() {
        int count = 0;
        synchronized (notifications) {
            count = notifications.size();
        }
        return count;
    }

    /**
     * for testing
     */
    int getFilteredCount() {
        int count = 0;
        synchronized (notifications) {
            count = filteredNotifications.size();
        }
        return count;
    }

    /**
     * for testing
     */
    List<NotificationImpl> getFilteredNotifications() {
        return filteredNotifications;
    }
}
