/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */

package org.openide.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.WeakHashMap;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Convenience class permitting easy loading of localized resources of various sorts.
* Extends the functionality of the default Java resource support, and interacts
* better with class loaders in a multiple-loader system.
* <p>Example usage:
* <p><code><pre>
* package com.mycom;
* public class Foo {
*   // Search for tag Foo_theMessage in /com/mycom/Bundle.properties:
*   private static String theMessage = {@link NbBundle#getMessage(Class, String) NbBundle.getMessage} (Foo.class, "Foo_theMessage");
*   // Might also look in /com/mycom/Bundle_de.properties, etc.
* }
* </pre></code>
*/
public class NbBundle extends Object {

    private static final Logger LOG = Logger.getLogger(NbBundle.class.getName());

    private static final boolean USE_DEBUG_LOADER = Boolean.getBoolean("org.openide.util.NbBundle.DEBUG"); // NOI18N
    private static String brandingToken = null;

    /**
     * Cache of URLs for localized files.
     * Keeps only weak references to the class loaders.
     * @see "#9275"
     */
    static final Map<ClassLoader,Map<String,URL>> localizedFileCache = new WeakHashMap<ClassLoader,Map<String,URL>>();

    /**
     * Cache of resource bundles.
     */
    static final Map<ClassLoader,Map<String,Reference<ResourceBundle>>> bundleCache = new WeakHashMap<ClassLoader,Map<String,Reference<ResourceBundle>>>();

    private NbBundle() {
    }

    /** Get the current branding token.
     * @return the branding, or <code>null</code> for none
     */
    public static String getBranding() {
        return brandingToken;
    }

    /** Set the current branding token.
     * The permitted format, as a regular expression:
     * <pre>[a-z][a-z0-9]*(_[a-z][a-z0-9]*)*</pre>
     * <p class="nonnormative">
     * This is normally only called by NetBeans startup code and unit tests.
     * Currently the branding may be specified by passing the <code>--branding</code>
     * command-line option to the launcher.
     * </p>
     * @param bt the new branding, or <code>null</code> to clear
     * @throws IllegalArgumentException if in an incorrect format
     */
    public static void setBranding(String bt) throws IllegalArgumentException {
        if (bt != null && !bt.matches("[a-z][a-z0-9]*(_[a-z][a-z0-9]*)*")) { // NOI18N
            throw new IllegalArgumentException("Malformed branding token: " + bt); // NOI18N
        }
        brandingToken = bt;
    }

    /** Necessary because Attributes implements Map; however this is dangerous!
    * The keys are Attributes.Name's, not Strings.
    * Also manifest lookups should not be case-sensitive.
    * (Though the locale suffix still will be!)
    */
    private static Map<String,String> attr2Map(Attributes attr) {
        return new AttributesMap(attr);
    }

    // ---- LOADING RESOURCE BUNDLES ----

    /**
    * Get a resource bundle with the default class loader and locale/branding.
    * <strong>Caution:</strong> {@link #getBundle(Class)} is generally
    * safer when used from a module as this method relies on the module's
    * classloader to currently be part of the system classloader. NetBeans
    * does add enabled modules to this classloader, however calls to
    * this variant of the method made in <a href="@org-openide-modules@/org/openide/modules/ModuleInstall.html#validate()">ModuleInstall.validate</a>,
    * or made soon after a module is uninstalled (due to background threads)
    * could fail unexpectedly.
    * @param baseName bundle basename
    * @return the resource bundle
    * @exception MissingResourceException if the bundle does not exist
    */
    public static final ResourceBundle getBundle(String baseName)
    throws MissingResourceException {
        return getBundle(baseName, Locale.getDefault(), getLoader());
    }

    /** Get a resource bundle in the same package as the provided class,
    * with the default locale/branding and the class' own classloader.
    * This is the usual style of invocation.
    *
    * @param clazz the class to take the package name from
    * @return the resource bundle
    * @exception MissingResourceException if the bundle does not exist
    */
    public static ResourceBundle getBundle(Class clazz)
    throws MissingResourceException {
        String name = findName(clazz);

        return getBundle(name, Locale.getDefault(), clazz.getClassLoader());
    }

    /** Finds package name for given class */
    private static String findName(Class clazz) {
        String pref = clazz.getName();
        int last = pref.lastIndexOf('.');

        if (last >= 0) {
            pref = pref.substring(0, last + 1);

            return pref + "Bundle"; // NOI18N
        } else {
            // base package, search for bundle
            return "Bundle"; // NOI18N
        }
    }

    /**
    * Get a resource bundle with the default class loader and branding.
    * @param baseName bundle basename
    * @param locale the locale to use (but still uses {@link #getBranding default branding})
    * @return the resource bundle
    * @exception MissingResourceException if the bundle does not exist
    */
    public static final ResourceBundle getBundle(String baseName, Locale locale)
    throws MissingResourceException {
        return getBundle(baseName, locale, getLoader());
    }

    /** Get a resource bundle the hard way.
    * @param baseName bundle basename
    * @param locale the locale to use (but still uses {@link #getBranding default branding})
    * @param loader the class loader to use
    * @return the resource bundle
    * @exception MissingResourceException if the bundle does not exist
    */
    public static final ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader)
    throws MissingResourceException {
        if (USE_DEBUG_LOADER) {
            loader = DebugLoader.get(loader);
        }

        // Could more simply use ResourceBundle.getBundle (plus some special logic
        // with MergedBundle to handle branding) instead of manually finding bundles.
        // However this code is faster and has some other desirable properties.
        // Cf. #13847.
        ResourceBundle b = getBundleFast(baseName, locale, loader);

        if (b != null) {
            return b;
        } else {
            MissingResourceException e = new MissingResourceException("No such bundle " + baseName, baseName, null); // NOI18N
            throw e;
        }
    }

    /**
     * Get a resource bundle by name.
     * Like {@link ResourceBundle#getBundle(String,Locale,ClassLoader)} but faster,
     * and also understands branding.
     * First looks for <samp>.properties</samp>-based bundles, then <samp>.class</samp>-based.
     * @param name the base name of the bundle, e.g. <samp>org.netbeans.modules.foo.Bundle</samp>
     * @param locale the locale to use
     * @param loader a class loader to search in
     * @return a resource bundle (locale- and branding-merged), or null if not found
     */
    private static ResourceBundle getBundleFast(String name, Locale locale, ClassLoader loader) {
        Map<String,Reference<ResourceBundle>> m;

        synchronized (bundleCache) {
            m = bundleCache.get(loader); 

            if (m == null) {
                bundleCache.put(loader, m = new HashMap<String,Reference<ResourceBundle>>());
            }
        }

        //A minor optimization to cut down on StringBuffer allocations - OptimizeIt
        //showed the commented out code below was a major source of them.  This
        //just does the same thing with a char array - Tim
        String localeStr = locale.toString();
        char[] k = new char[name.length() + ((brandingToken != null) ? brandingToken.length() : 1) + 2 +
            localeStr.length()];
        name.getChars(0, name.length(), k, 0);
        k[name.length()] = '/'; //NOI18N

        int pos = name.length() + 1;

        if (brandingToken == null) {
            k[pos] = '-'; //NOI18N
            pos++;
        } else {
            brandingToken.getChars(0, brandingToken.length(), k, pos);
            pos += brandingToken.length();
        }

        k[pos] = '/'; //NOI18N
        pos++;
        localeStr.getChars(0, localeStr.length(), k, pos);

        String key = new String(k);

        synchronized (m) {
            Reference<ResourceBundle> o = m.get(key);
            ResourceBundle b = o != null ? o.get() : null;

            if (b != null) {
                return b;
            } else {
                b = loadBundle(name, locale, loader);

                if (b != null) {
                    m.put(key, new SoftReference<ResourceBundle>(b));
                } else {
                    // Used to cache misses as well, to make the negative test faster.
                    // However this caused problems: see #31578.
                }

                return b;
            }
        }
    }

    /**
     * Load a resource bundle (without caching).
     * @param name the base name of the bundle, e.g. <samp>org.netbeans.modules.foo.Bundle</samp>
     * @param locale the locale to use
     * @param loader a class loader to search in
     * @return a resource bundle (locale- and branding-merged), or null if not found
     */
    private static ResourceBundle loadBundle(String name, Locale locale, ClassLoader loader) {
        String sname = name.replace('.', '/');
        Iterator<String> it = new LocaleIterator(locale);
        LinkedList<String> l = new LinkedList<String>();

        while (it.hasNext()) {
            l.addFirst(it.next());
        }

        Properties p = new Properties();

        for (String suffix : l) {
            String res = sname + suffix + ".properties";

            // #49961: don't use getResourceAsStream; catch all errors opening it
            URL u = loader != null ? loader.getResource(res) : ClassLoader.getSystemResource(res);

            if (u != null) {
                //System.err.println("Loading " + res);
                try {
                    // #51667: but in case we are in USE_DEBUG_LOADER mode, use gRAS (since getResource is not overridden)
                    InputStream is = USE_DEBUG_LOADER ?
                        (loader != null ? loader.getResourceAsStream(res) : ClassLoader.getSystemResourceAsStream(res)) :
                            u.openStream();

                    try {
                        p.load(is);
                    } finally {
                        is.close();
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, null, e);

                    return null;
                }
            } else if (suffix.length() == 0) {
                // No base *.properties. Try *.class.
                // Note that you may not mix *.properties w/ *.class this way.
                return loadBundleClass(name, sname, locale, l, loader);
            }
        }

        return new PBundle(new PropsWrapperMap(p) , locale);
    }

    /**
     * Load a class-based resource bundle.
     * @param name the base name of the bundle, e.g. <samp>org.netbeans.modules.foo.Bundle</samp>
     * @param sname the name with slashes, e.g. <samp>org/netbeans/modules/foo/Bundle</samp>
     * @param locale the locale to use
     * @param suffixes a list of suffixes to apply to the bundle name, in <em>increasing</em> order of specificity
     * @param loader a class loader to search in
     * @return a resource bundle (merged according to the suffixes), or null if not found
     */
    private static ResourceBundle loadBundleClass(
        String name, String sname, Locale locale, List<String> suffixes, ClassLoader l
    ) {
        if (l != null && l.getResource(sname + ".class") == null) { // NOI18N

            // No chance - no base bundle. Don't waste time catching CNFE.
            return null;
        }

        ResourceBundle master = null;

        for (String suffix : suffixes) {
            try {
                Class<? extends ResourceBundle> c = Class.forName(name + suffix, true, l).asSubclass(ResourceBundle.class);
                ResourceBundle b = c.newInstance();

                if (master == null) {
                    master = b;
                } else {
                    master = new MergedBundle(locale, b, master);
                }
            } catch (ClassNotFoundException cnfe) {
                // fine - ignore
            } catch (Exception e) {
                LOG.log(Level.WARNING, null, e);
            } catch (LinkageError e) {
                LOG.log(Level.WARNING, null, e);
            }
        }

        return master;
    }

    //
    // Helper methods to simplify localization of messages
    //

    /**
     * Finds a localized and/or branded string in a bundle.
    * @param clazz the class to use to locate the bundle
    * @param resName name of the resource to look for
    * @return the string associated with the resource
    * @throws MissingResourceException if either the bundle or the string cannot be found
    */
    public static String getMessage(Class clazz, String resName)
    throws MissingResourceException {
        return getBundle(clazz).getString(resName);
    }

    /**
     * Finds a localized and/or branded string in a bundle and formats the message
    * by passing requested parameters.
    *
    * @param clazz the class to use to locate the bundle
    * @param resName name of the resource to look for
    * @param param1 the argument to use when formatting the message
    * @return the string associated with the resource
    * @throws MissingResourceException if either the bundle or the string cannot be found
    * @see java.text.MessageFormat#format(String,Object[])
    */
    public static String getMessage(Class clazz, String resName, Object param1)
    throws MissingResourceException {
        return getMessage(clazz, resName, new Object[] { param1 });
    }

    /**
     * Finds a localized and/or branded string in a bundle and formats the message
    * by passing requested parameters.
    *
    * @param clazz the class to use to locate the bundle
    * @param resName name of the resource to look for
    * @param param1 the argument to use when formatting the message
    * @param param2 the second argument to use for formatting
    * @return the string associated with the resource
    * @throws MissingResourceException if either the bundle or the string cannot be found
    * @see java.text.MessageFormat#format(String,Object[])
    */
    public static String getMessage(Class clazz, String resName, Object param1, Object param2)
    throws MissingResourceException {
        return getMessage(clazz, resName, new Object[] { param1, param2 });
    }

    /**
     * Finds a localized and/or branded string in a bundle and formats the message
    * by passing requested parameters.
    *
    * @param clazz the class to use to locate the bundle
    * @param resName name of the resource to look for
    * @param param1 the argument to use when formatting the message
    * @param param2 the second argument to use for formatting
    * @param param3 the third argument to use for formatting
    * @return the string associated with the resource
    * @throws MissingResourceException if either the bundle or the string cannot be found
    * @see java.text.MessageFormat#format(String,Object[])
    */
    public static String getMessage(Class clazz, String resName, Object param1, Object param2, Object param3)
    throws MissingResourceException {
        return getMessage(clazz, resName, new Object[] { param1, param2, param3 });
    }

    /**
     * Finds a localized and/or branded string in a bundle and formats the message
    * by passing requested parameters.
    *
    * @param clazz the class to use to locate the bundle
    * @param resName name of the resource to look for
    * @param arr array of parameters to use for formatting the message
    * @return the string associated with the resource
    * @throws MissingResourceException if either the bundle or the string cannot be found
    * @see java.text.MessageFormat#format(String,Object[])
    */
    public static String getMessage(Class clazz, String resName, Object[] arr)
    throws MissingResourceException {
        return java.text.MessageFormat.format(getMessage(clazz, resName), arr);
    }

    /** @return default class loader which is used, when we don't have
    * any other class loader. (in function getBundle(String), getLocalizedFile(String),
    * and so on...
    */
    private static ClassLoader getLoader() {
        ClassLoader c = NbBundle.class.getClassLoader();
        return (c != null) ? c : ClassLoader.getSystemClassLoader();
    }

    /**
     * Get a list of all suffixes used to search for localized/branded resources.
     * Based on the default locale and branding, returns the list of suffixes
     * which various <code>NbBundle</code> methods use as the search order.
     * For example, when {@link #getBranding} returns <code>branding</code>
     * and the default locale is German, you might get a sequence such as:
     * <ol>
     * <li><samp>"_branding_de"</samp>
     * <li><samp>"_branding"</samp>
     * <li><samp>"_de"</samp>
     * <li><samp>""</samp>
     * </ol>
     * @return a read-only iterator of type <code>String</code>
     * @since 1.1.5
     */
    public static Iterator<String> getLocalizingSuffixes() {
        return new LocaleIterator(Locale.getDefault());
    }

    private static class AttributesMap extends HashMap<String,String> {
        private Attributes attrs;

        public AttributesMap(Attributes attrs) {
            super(7);
            this.attrs = attrs;
        }

        public String get(Object _k) {
            if (!(_k instanceof String)) {
                return null;
            }
            String k = (String) _k;

            Attributes.Name an;

            try {
                an = new Attributes.Name(k);
            } catch (IllegalArgumentException iae) {
                // Robustness, and workaround for reported MRJ locale bug:
                LOG.log(Level.FINE, null, iae);
                return null;
            }

            return attrs.getValue(an);
        }
    }

    /**
     * A resource bundle based on <samp>.properties</samp> files (or any map).
     */
    private static final class PBundle extends ResourceBundle {
        private final Map<String,String> m;
        private final Locale locale;

        /**
         * Create a new bundle based on a map.
         * @param m a map from resources keys to values (typically both strings)
         * @param locale the locale it represents <em>(informational)</em>
         */
        public PBundle(Map<String,String> m, Locale locale) {
            this.m = m;
            this.locale = locale;
        }

        public Enumeration<String> getKeys() {
            return Collections.enumeration(m.keySet());
        }

        protected Object handleGetObject(String key) {
            return m.get(key);
        }

        public Locale getLocale() {
            return locale;
        }
    }

    /** Special resource bundle which delegates to two others.
     * Ideally could just set the parent on the first, but this is protected, so...
     */
    private static class MergedBundle extends ResourceBundle {
        private Locale loc;
        private ResourceBundle sub1;
        private ResourceBundle sub2;

        /**
         * Create a new bundle delegating to two others.
         * @param loc the locale it represents <em>(informational)</em>
         * @param sub1 one delegate (taking precedence over the other in case of overlap)
         * @param sub2 the other (weaker) delegate
         */
        public MergedBundle(Locale loc, ResourceBundle sub1, ResourceBundle sub2) {
            this.loc = loc;
            this.sub1 = sub1;
            this.sub2 = sub2;
        }

        public Locale getLocale() {
            return loc;
        }

        public Enumeration<String> getKeys() {
            return Enumerations.removeDuplicates(Enumerations.concat(sub1.getKeys(), sub2.getKeys()));
        }

        protected Object handleGetObject(String key) throws MissingResourceException {
            try {
                return sub1.getObject(key);
            } catch (MissingResourceException mre) {
                // Ignore exception, and...
                return sub2.getObject(key);
            }
        }
    }

    /** This class (enumeration) gives all localized sufixes using nextElement
    * method. It goes through given Locale and continues through Locale.getDefault()
    * Example 1:
    *   Locale.getDefault().toString() -> "_en_US"
    *   you call new LocaleIterator(new Locale("cs", "CZ"));
    *  ==> You will gets: "_cs_CZ", "_cs", "", "_en_US", "_en"
    *
    * Example 2:
    *   Locale.getDefault().toString() -> "_cs_CZ"
    *   you call new LocaleIterator(new Locale("cs", "CZ"));
    *  ==> You will gets: "_cs_CZ", "_cs", ""
    *
    * If there is a branding token in effect, you will get it too as an extra
    * prefix, taking precedence, e.g. for the token "f4jce":
    *
    * "_f4jce_cs_CZ", "_f4jce_cs", "_f4jce", "_f4jce_en_US", "_f4jce_en", "_cs_CZ", "_cs", "", "_en_US", "_en"
    *
    * Branding tokens with underscores are broken apart naturally: so e.g.
    * branding "f4j_ce" looks first for "f4j_ce" branding, then "f4j" branding, then none.
    */
    private static class LocaleIterator extends Object implements Iterator<String> {
        /** this flag means, if default locale is in progress */
        private boolean defaultInProgress = false;

        /** this flag means, if empty suffix was exported yet */
        private boolean empty = false;

        /** current locale, and initial locale */
        private Locale locale;

        /** current locale, and initial locale */
        private Locale initLocale;

        /** current suffix which will be returned in next calling nextElement */
        private String current;

        /** the branding string in use */
        private String branding;

        /** Creates new LocaleIterator for given locale.
        * @param locale given Locale
        */
        public LocaleIterator(Locale locale) {
            this.locale = this.initLocale = locale;

            if (locale.equals(Locale.getDefault())) {
                defaultInProgress = true;
            }

            current = '_' + locale.toString();

            if (brandingToken == null) {
                branding = null;
            } else {
                branding = "_" + brandingToken; // NOI18N
            }

            //System.err.println("Constructed: " + this);
        }

        /** @return next suffix.
        * @exception NoSuchElementException if there is no more locale suffix.
        */
        public String next() throws NoSuchElementException {
            if (current == null) {
                throw new NoSuchElementException();
            }

            final String ret;

            if (branding == null) {
                ret = current;
            } else {
                ret = branding + current;
            }

            int lastUnderbar = current.lastIndexOf('_');

            if (lastUnderbar == 0) {
                if (empty) {
                    reset();
                } else {
                    current = ""; // NOI18N
                    empty = true;
                }
            } else {
                if (lastUnderbar == -1) {
                    if (defaultInProgress) {
                        reset();
                    } else {
                        // [PENDING] stuff with trying the default locale
                        // after the real one does not actually seem to work...
                        locale = Locale.getDefault();
                        current = '_' + locale.toString();
                        defaultInProgress = true;
                    }
                } else {
                    current = current.substring(0, lastUnderbar);
                }
            }

            //System.err.println("Returning: `" + ret + "' from: " + this);
            return ret;
        }

        /** Finish a series.
         * If there was a branding prefix, restart without that prefix
         * (or with a shorter prefix); else finish.
         */
        private void reset() {
            if (branding != null) {
                current = '_' + initLocale.toString();

                int idx = branding.lastIndexOf('_');

                if (idx == 0) {
                    branding = null;
                } else {
                    branding = branding.substring(0, idx);
                }

                empty = false;
            } else {
                current = null;
            }
        }

        /** Tests if there is any sufix.*/
        public boolean hasNext() {
            return (current != null);
        }

        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }
    }
     // end of LocaleIterator

    /** Classloader whose special trick is inserting debug information
     * into any *.properties files it loads.
     */
    static final class DebugLoader extends ClassLoader {
        /** global bundle index, each loaded bundle gets its own */
        private static int count = 0;

        /** indices of known bundles; needed since DebugLoader's can be collected
         * when softly reachable, but this should be transparent to the user
         */
        private static final Map<String,Integer> knownIDs = new HashMap<String,Integer>();

        /** cache of existing debug loaders for regular loaders */
        private static final Map<ClassLoader,Reference<ClassLoader>> existing = new WeakHashMap<ClassLoader,Reference<ClassLoader>>();

        private DebugLoader(ClassLoader cl) {
            super(cl);

            //System.err.println ("new DebugLoader: cl=" + cl);
        }

        private static int getID(String name) {
            synchronized (knownIDs) {
                Integer i = knownIDs.get(name);

                if (i == null) {
                    i = ++count;
                    knownIDs.put(name, i);
                    System.err.println("NbBundle trace: #" + i + " = " + name); // NOI18N
                }

                return i;
            }
        }

        public static ClassLoader get(ClassLoader normal) {
            //System.err.println("Lookup: normal=" + normal);
            synchronized (existing) {
                Reference<ClassLoader> r = existing.get(normal);

                if (r != null) {
                    ClassLoader dl = r.get();

                    if (dl != null) {
                        //System.err.println("\tcache hit");
                        return dl;
                    } else {
                        //System.err.println("\tcollected ref");
                    }
                } else {
                    //System.err.println("\tnot in cache");
                }

                ClassLoader dl = new DebugLoader(normal);
                existing.put(normal, new WeakReference<ClassLoader>(dl));

                return dl;
            }
        }

        public InputStream getResourceAsStream(String name) {
            InputStream base = super.getResourceAsStream(name);

            if (base == null) {
                return null;
            }

            if (name.endsWith(".properties")) { // NOI18N

                int id = getID(name);

                //System.err.println ("\tthis=" + this + " parent=" + getParent ());
                boolean loc = name.indexOf("Bundle") != -1; // NOI18N

                return new DebugInputStream(base, id, loc);
            } else {
                return base;
            }
        }

        // [PENDING] getResource not overridden; but ResourceBundle uses getResourceAsStream anyhow

        /** Wrapper input stream which parses the text as it goes and adds annotations.
         * Resource-bundle values are annotated with their current line number and also
         * the supplied it, so e.g. if in the original input stream on line 50 we have:
         *   somekey=somevalue
         * so in the wrapper stream (id 123) this line will read:
         *   somekey=somevalue (123:50)
         * Since you see on stderr what #123 is, you can then pinpoint where any bundle key
         * originally came from, assuming NbBundle loaded it from a *.properties file.
         * @see {@link Properties#load} for details on the syntax of *.properties files.
         */
        static final class DebugInputStream extends InputStream {
            /** state transition diagram constants */
            private static final int WAITING_FOR_KEY = 0;

            /** state transition diagram constants */
            private static final int IN_COMMENT = 1;

            /** state transition diagram constants */
            private static final int IN_KEY = 2;

            /** state transition diagram constants */
            private static final int IN_KEY_BACKSLASH = 3;

            /** state transition diagram constants */
            private static final int AFTER_KEY = 4;

            /** state transition diagram constants */
            private static final int WAITING_FOR_VALUE = 5;

            /** state transition diagram constants */
            private static final int IN_VALUE = 6;

            /** state transition diagram constants */
            private static final int IN_VALUE_BACKSLASH = 7;
            private final InputStream base;
            private final int id;
            private final boolean localizable;

            /** current line number */
            private int line = 0;
            
            /** line number in effect for last-encountered key */
            private int keyLine = 0;

            /** current state in state machine */
            private int state = WAITING_FOR_KEY;

            /** if true, the last char was a CR, waiting to see if we get a NL too */
            private boolean twixtCrAndNl = false;

            /** if non-null, a string to serve up before continuing (length must be > 0) */
            private String toInsert = null;

            /** if true, the next value encountered should be localizable if normally it would not be, or vice-versa */
            private boolean reverseLocalizable = false;

            /** text of currently read comment, including leading comment character */
            private StringBuffer lastComment = null;

            /** Create a new InputStream which will annotate resource bundles.
             * Bundles named Bundle*.properties will be treated as localizable by default,
             * and so annotated; other bundles will be treated as nonlocalizable and not annotated.
             * Messages can be individually marked as localizable or not to override this default,
             * in accordance with some I18N conventions for NetBeans.
             * @param base the unannotated stream
             * @param id an identifying number to use in annotations
             * @param localizable if true, this bundle is expected to be localizable
             * @see http://www.netbeans.org/i18n/
             */
            public DebugInputStream(InputStream base, int id, boolean localizable) {
                this.base = base;
                this.id = id;
                this.localizable = localizable;
            }

            public int read() throws IOException {
                if (toInsert != null) {
                    char result = toInsert.charAt(0);

                    if (toInsert.length() > 1) {
                        toInsert = toInsert.substring(1);
                    } else {
                        toInsert = null;
                    }

                    return result;
                }

                int next = base.read();

                if (next == '\n') {
                    twixtCrAndNl = false;
                    line++;
                } else if (next == '\r') {
                    if (twixtCrAndNl) {
                        line++;
                    } else {
                        twixtCrAndNl = true;
                    }
                } else {
                    twixtCrAndNl = false;
                }

                switch (state) {
                case WAITING_FOR_KEY:

                    switch (next) {
                    case '#':
                    case '!':
                        state = IN_COMMENT;
                        lastComment = new StringBuffer();
                        lastComment.append((char) next);

                        return next;

                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case -1:
                        return next;

                    case '\\':
                        state = IN_KEY_BACKSLASH;

                        return next;

                    default:
                        state = IN_KEY;
                        keyLine = line + 1;

                        return next;
                    }

                case IN_COMMENT:

                    switch (next) {
                    case '\n':
                    case '\r':

                        String comment = lastComment.toString();
                        lastComment = null;

                        if (localizable && comment.equals("#NOI18N")) { // NOI18N
                            reverseLocalizable = true;
                        } else if (localizable && comment.equals("#PARTNOI18N")) { // NOI18N
                            System.err.println(
                                "NbBundle WARNING (" + id + ":" + line +
                                "): #PARTNOI18N encountered, will not annotate I18N parts"
                            ); // NOI18N
                            reverseLocalizable = true;
                        } else if (!localizable && comment.equals("#I18N")) { // NOI18N
                            reverseLocalizable = true;
                        } else if (!localizable && comment.equals("#PARTI18N")) { // NOI18N
                            System.err.println(
                                "NbBundle WARNING (" + id + ":" + line +
                                "): #PARTI18N encountered, will not annotate I18N parts"
                            ); // NOI18N
                            reverseLocalizable = false;
                        } else if (
                            (localizable && (comment.equals("#I18N") || comment.equals("#PARTI18N"))) || // NOI18N
                                (!localizable && (comment.equals("#NOI18N") || comment.equals("#PARTNOI18N")))
                        ) { // NOI18N
                            System.err.println(
                                "NbBundle WARNING (" + id + ":" + line + "): incongruous comment " + comment +
                                " found for bundle"
                            ); // NOI18N
                            reverseLocalizable = false;
                        }

                        state = WAITING_FOR_KEY;

                        return next;

                    default:
                        lastComment.append((char) next);

                        return next;
                    }

                case IN_KEY:

                    switch (next) {
                    case '\\':
                        state = IN_KEY_BACKSLASH;

                        return next;

                    case ' ':
                    case '\t':
                        state = AFTER_KEY;

                        return next;

                    case '=':
                    case ':':
                        state = WAITING_FOR_VALUE;

                        return next;

                    case '\r':
                    case '\n':
                        state = WAITING_FOR_KEY;

                        return next;

                    default:
                        return next;
                    }

                case IN_KEY_BACKSLASH:
                    state = IN_KEY;

                    return next;

                case AFTER_KEY:

                    switch (next) {
                    case '=':
                    case ':':
                        state = WAITING_FOR_VALUE;

                        return next;

                    case '\r':
                    case '\n':
                        state = WAITING_FOR_KEY;

                        return next;

                    default:
                        return next;
                    }

                case WAITING_FOR_VALUE:

                    switch (next) {
                    case '\r':
                    case '\n':
                        state = WAITING_FOR_KEY;

                        return next;

                    case ' ':
                    case '\t':
                        return next;

                    case '\\':
                        state = IN_VALUE_BACKSLASH;

                        return next;

                    default:
                        state = IN_VALUE;

                        return next;
                    }

                case IN_VALUE:

                    switch (next) {
                    case '\\':

                        // Gloss over distinction between simple escapes and \u1234, which is not important for us.
                        // Also no need to deal specially with continuation lines; for us, there is an escaped
                        // newline, after which will be more value, and that is all that is important.
                        state = IN_VALUE_BACKSLASH;

                        return next;

                    case '\n':
                    case '\r':
                    case -1:

                        // End of value. This is the tricky part.
                        boolean revLoc = reverseLocalizable;
                        reverseLocalizable = false;
                        state = WAITING_FOR_KEY;

                        if (localizable ^ revLoc) {
                            // This value is intended to be localizable. Annotate it.
                            assert keyLine > 0;
                            toInsert = "(" + id + ":" + keyLine + ")"; // NOI18N
                            if (next != -1) {
                                toInsert += new Character((char) next);
                            }
                            keyLine = 0;

                            // Now return the space before the rest of the string explicitly.
                            return ' ';
                        } else {
                            // This is not supposed to be a localizable value, leave it alone.
                            return next;
                        }

                    default:
                        return next;
                    }

                case IN_VALUE_BACKSLASH:
                    state = IN_VALUE;

                    return next;

                default:
                    throw new IOException("should never happen"); // NOI18N
                }
            }

        }
    }
}
