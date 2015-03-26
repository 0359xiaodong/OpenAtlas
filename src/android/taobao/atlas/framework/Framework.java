package android.taobao.atlas.framework;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Build.VERSION;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.taobao.atlas.log.Logger;
import android.taobao.atlas.log.LoggerFactory;
import android.taobao.atlas.runtime.ClassNotFoundInterceptorCallback;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.AtlasFileLock;
import android.taobao.atlas.util.BundleLock;
import android.taobao.atlas.util.StringUtils;

public final class Framework {
    private static final AdminPermission ADMIN_PERMISSION;
    private static String BASEDIR = null;
    private static String BUNDLE_LOCATION = null;
    static int CLASSLOADER_BUFFER_SIZE = 0;
    static boolean DEBUG_BUNDLES = true;
    static boolean DEBUG_CLASSLOADING = true;
    static boolean DEBUG_PACKAGES = true;
    static boolean DEBUG_SERVICES = true;
    static final String FRAMEWORK_VERSION = "0.9.0";
    static int LOG_LEVEL;
    static String STORAGE_LOCATION;
    private static boolean STRICT_STARTUP;
    static List<BundleListener> bundleListeners;
    static Map<String, Bundle> bundles;
    private static ClassNotFoundInterceptorCallback classNotFoundCallback;
    static Map<String, List<ServiceReference>> classes_services;
    static Map<Package, Package> exportedPackages;
    static List<FrameworkListener> frameworkListeners;
    static boolean frameworkStartupShutdown;
    static int initStartlevel;
    static final Logger log;
    static boolean mIsEnableBundleInstallWhenFindClass;
    static Map<String, String> mMapForComAndBundles;
    static Properties properties;
    static boolean restart;
    static List<ServiceListenerEntry> serviceListeners;
    static List<ServiceReference> services;
    static int startlevel;
    static List<BundleListener> syncBundleListeners;
    static SystemBundle systemBundle;
    static ClassLoader systemClassLoader;
    static List<String> writeAheads;

    static final class ServiceListenerEntry implements EventListener {
        final Filter filter;
        final ServiceListener listener;

        ServiceListenerEntry(ServiceListener serviceListener, String str)
                throws InvalidSyntaxException {
            this.listener = serviceListener;
            this.filter = str == null ? null : RFC1960Filter.fromString(str);
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof ServiceListenerEntry)) {
                return false;
            }
            return this.listener.equals(((ServiceListenerEntry) obj).listener);
        }

        public int hashCode() {
            return (this.filter != null ? this.filter.hashCode() >> 8 : 0)
                    + this.listener.hashCode();
        }

        public String toString() {
            return this.listener + " " + this.filter;
        }
    }

    private static final class SystemBundle implements Bundle, PackageAdmin,
            StartLevel {
        private final Dictionary<String, String> props;
        private final ServiceReference[] registeredServices;
        int state;

        class AnonymousClass_1 extends Thread {
            final boolean restart;

            AnonymousClass_1(boolean z) {
                this.restart = z;
            }

            public void run() {
                Framework.shutdown(this.restart);
            }
        }

        class AnonymousClass_2 extends Thread {
            final int targetLevel;

            AnonymousClass_2(int i) {
                this.targetLevel = i;
            }

            public void run() {
                List bundles = Framework.getBundles();
                SystemBundle.this.setLevel(
                        (Bundle[]) bundles.toArray(new Bundle[bundles.size()]),
                        this.targetLevel, false);
                Framework.notifyFrameworkListeners(8, Framework.systemBundle,
                        null);
                Framework.storeMetadata();
            }
        }

        // TODO this is Component old version impl
        class AnonymousClass_3 extends Thread {
            final Bundle[] bundleArray;

            AnonymousClass_3(Bundle[] bundleArr) {
                this.bundleArray = bundleArr;
            }

            public void run() {
                synchronized (exportedPackages) {
                    try {
                        List bundles;
                        Bundle[] bundleArr;
                        int i;
                        BundleImpl bundleImpl;
                        if (this.bundleArray == null) {
                            bundles = Framework.getBundles();
                            bundleArr = (Bundle[]) bundles
                                    .toArray(new Bundle[bundles.size()]);
                        } else {
                            bundleArr = this.bundleArray;
                        }
                        List arrayList = new ArrayList(bundleArr.length);
                        for (i = 0; i < bundleArr.length; i++) {
                            if (bundleArr[i] != systemBundle) {
                                bundleImpl = (BundleImpl) bundleArr[i];
                                if (bundleImpl.classloader == null
                                        || bundleImpl.classloader.originalExporter != null) {
                                    arrayList.add(bundleArr[i]);
                                }
                            }
                        }
                        if (arrayList.isEmpty()) {
                            return;
                        }
                        int i2;
                        if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                            log.debug("REFRESHING PACKAGES FROM BUNDLES "
                                    + arrayList);
                        }
                        Set hashSet = new HashSet();
                        while (!arrayList.isEmpty()) {
                            bundleImpl = (BundleImpl) arrayList.remove(0);
                            if (!hashSet.contains(bundleImpl)) {
                                ExportedPackage[] access$100 = SystemBundle.this
                                        .getExportedPackages(bundleImpl, true);
                                if (access$100 != null) {
                                    for (ExportedPackage exportedPackage : access$100) {
                                        Package packageR = (Package) exportedPackage;
                                        if (packageR.importingBundles != null) {
                                            arrayList
                                                    .addAll(Arrays
                                                            .asList((Bundle[]) packageR.importingBundles
                                                                    .toArray(new Bundle[packageR.importingBundles
                                                                            .size()])));
                                        }
                                    }
                                }
                                if (bundleImpl.classloader != null) {
                                    hashSet.add(bundleImpl);
                                }
                            }
                        }
                        if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                            log.debug("UPDATE GRAPH IS " + hashSet);
                        }
                        Bundle[] bundleArr2 = new Bundle[hashSet.size()];
                        i = -1;
                        bundles = Framework.getBundles();
                        Bundle[] bundleArr3 = (Bundle[]) bundles
                                .toArray(new Bundle[bundles.size()]);
                        for (i2 = 0; i2 < bundleArr3.length; i2++) {
                            if (hashSet.contains(bundleArr3[i2])) {
                                i++;
                                bundleArr2[i] = bundleArr3[i2];
                            }
                        }
                        i2 = startlevel;
                        SystemBundle.this.setLevel(bundleArr2, 0, true);
                        for (i = 0; i < bundleArr2.length; i++) {
                            ((BundleImpl) bundleArr2[i]).classloader
                                    .cleanup(false);
                            ((BundleImpl) bundleArr2[i]).staleExportedPackages = null;
                        }
                        for (Bundle bundle : bundleArr2) {
                            BundleClassLoader bundleClassLoader = ((BundleImpl) bundle).classloader;
                            if (bundleClassLoader.exports.length > 0) {
                                Framework.export(bundleClassLoader,
                                        bundleClassLoader.exports, false);
                            }
                        }
                        for (Bundle bundle2 : bundleArr2) {
                            try {
                                ((BundleImpl) bundle2).classloader
                                        .resolveBundle(true, new HashSet());
                            } catch (BundleException e) {
                                e.printStackTrace();
                            }
                        }
                        SystemBundle.this.setLevel(bundleArr2, i2, true);
                        Framework.notifyFrameworkListeners(4, systemBundle,
                                null);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    } catch (Throwable th) {
                    }
                }
            }
        }

        SystemBundle() {
            this.props = new Hashtable();
            this.props.put(Constants.BUNDLE_NAME,
                    Constants.SYSTEM_BUNDLE_LOCATION);
            this.props.put(Constants.BUNDLE_VERSION,
                    Framework.FRAMEWORK_VERSION);
            this.props.put(Constants.BUNDLE_VENDOR, "Atlas");
            ServiceReferenceImpl serviceReferenceImpl = new ServiceReferenceImpl(
                    this, this, null, new String[] {
                            StartLevel.class.getName(),
                            PackageAdmin.class.getName() });
            Framework.addValue(Framework.classes_services,
                    StartLevel.class.getName(), serviceReferenceImpl);
            Framework.addValue(Framework.classes_services,
                    PackageAdmin.class.getName(), serviceReferenceImpl);
            Framework.services.add(serviceReferenceImpl);
            this.registeredServices = new ServiceReference[] { serviceReferenceImpl };
        }

        public long getBundleId() {
            return 0;
        }

        public Dictionary<String, String> getHeaders() {
            return this.props;
        }

        public String getLocation() {
            return Constants.SYSTEM_BUNDLE_LOCATION;
        }

        public ServiceReference[] getRegisteredServices() {
            return this.registeredServices;
        }

        public URL getResource(String str) {
            return getClass().getResource(str);
        }

        public ServiceReference[] getServicesInUse() {
            return null;
        }

        public int getState() {
            return this.state;
        }

        public boolean hasPermission(Object obj) {
            return true;
        }

        public void start() throws BundleException {
        }

        public void stop() throws BundleException {
            shutdownThread(false);
        }

        public void uninstall() throws BundleException {
            throw new BundleException("Cannot uninstall the System Bundle");
        }

        public void update() throws BundleException {
            shutdownThread(true);
        }

        private void shutdownThread(boolean z) {
            new AnonymousClass_1(z).start();
        }

        public void update(InputStream inputStream) throws BundleException {
            shutdownThread(true);
        }

        public void update(File file) throws BundleException {
            shutdownThread(true);
        }

        public int getBundleStartLevel(Bundle bundle) {
            if (bundle == this) {
                return 0;
            }
            BundleImpl bundleImpl = (BundleImpl) bundle;
            if (bundleImpl.state != 1) {
                return bundleImpl.currentStartlevel;
            }
            throw new IllegalArgumentException("Bundle " + bundle
                    + " has been uninstalled");
        }

        public int getInitialBundleStartLevel() {
            return Framework.initStartlevel;
        }

        public int getStartLevel() {
            return Framework.startlevel;
        }

        public boolean isBundlePersistentlyStarted(Bundle bundle) {
            if (bundle == this) {
                return true;
            }
            BundleImpl bundleImpl = (BundleImpl) bundle;
            if (bundleImpl.state != 1) {
                return bundleImpl.persistently;
            }
            throw new IllegalArgumentException("Bundle " + bundle
                    + " has been uninstalled");
        }

        public void setBundleStartLevel(Bundle bundle, int i) {
            if (bundle == this) {
                throw new IllegalArgumentException(
                        "Cannot set the start level for the system bundle.");
            }
            BundleImpl bundleImpl = (BundleImpl) bundle;
            if (bundleImpl.state == 1) {
                throw new IllegalArgumentException("Bundle " + bundle
                        + " has been uninstalled");
            } else if (i <= 0) {
                throw new IllegalArgumentException("Start level " + i
                        + " is not Component valid level");
            } else {
                bundleImpl.currentStartlevel = i;
                bundleImpl.updateMetadata();
                if (i <= Framework.startlevel && bundle.getState() != 32
                        && bundleImpl.persistently) {
                    try {
                        bundleImpl.startBundle();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        Framework.notifyFrameworkListeners(2, bundle, e);
                    }
                } else if (i <= Framework.startlevel) {
                } else {
                    if (bundle.getState() != 4 || bundle.getState() != 2) {
                        try {
                            bundleImpl.stopBundle();
                        } catch (Throwable e2) {
                            Framework.notifyFrameworkListeners(2, bundle, e2);
                        }
                    }
                }
            }
        }

        public void setInitialBundleStartLevel(int i) {
            if (i <= 0) {
                throw new IllegalArgumentException("Start level " + i
                        + " is not Component valid level");
            }
            Framework.initStartlevel = i;
        }

        public void setStartLevel(int i) {
            if (i <= 0) {
                throw new IllegalArgumentException("Start level " + i
                        + " is not Component valid level");
            }
            new AnonymousClass_2(i).start();
        }

        @SuppressLint({ "UseSparseArrays" })
        private void setLevel(Bundle[] bundleArr, int i, boolean z) {
            if (Framework.startlevel != i) {
                int i2 = i > Framework.startlevel ? 1 : 0;
                int i3 = i2 != 0 ? i - Framework.startlevel
                        : Framework.startlevel - i;
                Map hashMap = new HashMap(0);
                int i4 = 0;
                while (i4 < bundleArr.length) {
                    if (bundleArr[i4] != Framework.systemBundle
                            && (z || ((BundleImpl) bundleArr[i4]).persistently)) {
                        int i5;
                        BundleImpl bundleImpl = (BundleImpl) bundleArr[i4];
                        if (i2 != 0) {
                            i5 = (bundleImpl.currentStartlevel - Framework.startlevel) - 1;
                        } else {
                            i5 = Framework.startlevel
                                    - bundleImpl.currentStartlevel;
                        }
                        if (i5 >= 0 && i5 < i3) {
                            Framework.addValue(hashMap, Integer.valueOf(i5),
                                    bundleImpl);
                        }
                    }
                    i4++;
                }
                for (int i6 = 0; i6 < i3; i6++) {
                    if (i2 != 0) {
                        Framework.startlevel++;
                    } else {
                        Framework.startlevel--;
                    }
                    List list = (List) hashMap.get(Integer.valueOf(i6));
                    if (list != null) {
                        BundleImpl[] bundleImplArr = (BundleImpl[]) list
                                .toArray(new BundleImpl[list.size()]);
                        for (i4 = 0; i4 < bundleImplArr.length; i4++) {
                            if (i2 != 0) {
                                try {
                                    System.out.println("STARTING "
                                            + bundleImplArr[i4].location);
                                    bundleImplArr[i4].startBundle();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                    e.printStackTrace();
                                    Framework.notifyFrameworkListeners(2,
                                            Framework.systemBundle, e);
                                }
                            } else if (bundleImplArr[i4].getState() != 1) {
                                System.out.println("STOPPING "
                                        + bundleImplArr[i4].location);
                                try {
                                    bundleImplArr[(bundleImplArr.length - i4) - 1]
                                            .stopBundle();
                                } catch (BundleException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                Framework.startlevel = i;
            }
        }

        public ExportedPackage[] getExportedPackages(Bundle bundle) {
            return getExportedPackages(bundle, false);
        }

        private ExportedPackage[] getExportedPackages(Bundle bundle, boolean z) {
            synchronized (Framework.exportedPackages) {
                if (bundle != null) {
                    if (bundle != Framework.systemBundle) {
                        BundleImpl bundleImpl = (BundleImpl) bundle;
                        if (bundleImpl.state == 1) {
                            ExportedPackage[] exportedPackageArr;
                            if (z) {
                                exportedPackageArr = bundleImpl.staleExportedPackages;
                            } else {
                                exportedPackageArr = null;
                            }
                            return exportedPackageArr;
                        }
                        String[] strArr = bundleImpl.classloader.exports;
                        if (strArr == null) {
                            return null;
                        }
                        ArrayList arrayList = new ArrayList();
                        for (String str : strArr) {
                            Package packageR = (Package) Framework.exportedPackages
                                    .get(new Package(str, null, false));
                            if (packageR != null
                                    && packageR.classloader == bundleImpl.classloader) {
                                if (packageR.resolved) {
                                    arrayList.add(packageR);
                                } else {
                                    try {
                                        packageR.classloader.resolveBundle(
                                                true, new HashSet());
                                        arrayList.add(packageR);
                                    } catch (BundleException e) {
                                    }
                                }
                            }
                        }
                        if (bundleImpl.staleExportedPackages != null) {
                            arrayList.addAll(Arrays
                                    .asList(bundleImpl.staleExportedPackages));
                        }
                        System.out.println("\tBundle " + bundleImpl
                                + " has exported packages " + arrayList);
                        return arrayList.isEmpty() ? null
                                : (ExportedPackage[]) arrayList
                                        .toArray(new ExportedPackage[arrayList
                                                .size()]);
                    }
                }
                return (ExportedPackage[]) Framework.exportedPackages.keySet()
                        .toArray(
                                new ExportedPackage[Framework.exportedPackages
                                        .size()]);
            }
        }

        public ExportedPackage getExportedPackage(String str) {
            synchronized (exportedPackages) {
                try {
                    Package packageR = (Package) exportedPackages
                            .get(new Package(str, null, false));
                    if (packageR == null) {
                        return null;
                    }
                    if (!packageR.resolved) {
                        packageR.classloader.resolveBundle(true, new HashSet());
                    }
                    return packageR;
                } catch (BundleException e) {
                    return null;
                } catch (Throwable th) {
                }
            }
            return null;
        }

        public void refreshPackages(Bundle[] bundleArr) {
            new AnonymousClass_3(bundleArr).start();
        }

        public String toString() {
            return "SystemBundle";
        }
    }

    static BundleImpl installNewBundle(String arg7, File arg8)
            throws BundleException {
        Bundle v0_1;
        try {
            BundleLock.WriteLock(arg7);
            v0_1 = Framework.getBundle(arg7);
            if (v0_1 != null) {
            } else {
                v0_1 = new BundleImpl(
                        new File(Framework.STORAGE_LOCATION, arg7), arg7,
                        new BundleContextImpl(), null, arg8, true);
            }
        } catch (Throwable v0) {

            v0.printStackTrace();
            BundleLock.WriteUnLock(arg7);
            throw new BundleException(v0.getMessage());
        }

        return ((BundleImpl) v0_1);
    }

    static BundleImpl installNewBundle(String arg7, InputStream arg8)
            throws BundleException {
        Bundle v0_1 = null;
        try {
            BundleLock.WriteLock(arg7);
            v0_1 = Framework.getBundle(arg7);
            if (v0_1 != null) {
            } else {
                BundleImpl v0_2 = new BundleImpl(new File(
                        Framework.STORAGE_LOCATION, arg7), arg7,
                        new BundleContextImpl(), arg8, null, true);
                Framework.storeMetadata();
                return v0_2;
            }
        } catch (Throwable v0) {
            BundleLock.WriteUnLock(arg7);
        }

        return ((BundleImpl) v0_1);
    }

    static {
        log = LoggerFactory.getInstance("Framework");
        bundles = new ConcurrentHashMap();
        services = new ArrayList();
        classes_services = new HashMap();
        bundleListeners = new ArrayList();
        syncBundleListeners = new ArrayList();
        serviceListeners = new ArrayList();
        frameworkListeners = new ArrayList();
        exportedPackages = new ConcurrentHashMap();
        startlevel = 0;
        writeAheads = new ArrayList();
        initStartlevel = 1;
        frameworkStartupShutdown = false;
        restart = false;
        mMapForComAndBundles = new HashMap();
        mIsEnableBundleInstallWhenFindClass = false;
        ADMIN_PERMISSION = new AdminPermission();
    }

    private Framework() {
    }

    static void startup() throws BundleException {
        int i;
        int property;
        frameworkStartupShutdown = true;
        System.out
                .println("---------------------------------------------------------");
        System.out.println("  Atlas OSGi 0.9.0 on " + Build.MODEL + "/"
                + Build.CPU_ABI + "/" + VERSION.RELEASE + " starting ...");
        System.out
                .println("---------------------------------------------------------");
        long currentTimeMillis = System.currentTimeMillis();
        boolean property2 = getProperty("osgi.init", false);
        if (property2) {
            i = -1;
        } else {
            i = restoreProfile();
            restart = true;
        }
        if (i == -1) {
            restart = false;
            File file = new File(STORAGE_LOCATION);
            if (property2 && file.exists()) {
                System.out.println("Purging storage ...");
                try {
                    deleteDirectory(file);
                } catch (Throwable e) {
                    throw new RuntimeException("deleteDirectory failed", e);
                }
            }
            try {
                file.mkdirs();
                Integer.getInteger("osgi.maxLevel", Integer.valueOf(1))
                        .intValue();
                initStartlevel = getProperty("osgi.startlevel.bundle", 1);
                property = getProperty("osgi.startlevel.framework", 1);
            } catch (Throwable e2) {
                throw new RuntimeException("mkdirs failed", e2);
            }
        }
        property = i;
        systemBundle.setLevel(
                (Bundle[]) getBundles().toArray(new Bundle[bundles.size()]),
                property, false);
        frameworkStartupShutdown = false;
        if (!restart) {
            try {
                storeProfile();
            } catch (Throwable e22) {
                throw new RuntimeException("storeProfile failed", e22);
            }
        }
        long currentTimeMillis2 = System.currentTimeMillis()
                - currentTimeMillis;
        System.out
                .println("---------------------------------------------------------");
        System.out.println("  Framework " + (restart ? "restarted" : "started")
                + " in " + currentTimeMillis2 + " milliseconds.");
        System.out
                .println("---------------------------------------------------------");
        System.out.flush();
        systemBundle.state = 32;
        try {
            notifyFrameworkListeners(1, systemBundle, null);
        } catch (Throwable e222) {
            throw new RuntimeException("notifyFrameworkListeners failed", e222);
        }
    }

    public static ClassLoader getSystemClassLoader() {
        return systemClassLoader;
    }

    public static List<Bundle> getBundles() {
        List<Bundle> arrayList = new ArrayList(bundles.size());
        synchronized (bundles) {
            arrayList.addAll(bundles.values());
        }
        return arrayList;
    }

    public static Bundle getBundle(String str) {
        return (Bundle) bundles.get(str);
    }

    public static Bundle getBundle(long j) {
        return null;
    }

    static void shutdown(boolean z) {
        System.out
                .println("---------------------------------------------------------");
        System.out.println("  Atlas OSGi shutting down ...");
        System.out.println("  Bye !");
        System.out
                .println("---------------------------------------------------------");
        systemBundle.state = 16;
        systemBundle.setLevel(
                (Bundle[]) getBundles().toArray(new Bundle[bundles.size()]), 0,
                true);
        bundles.clear();
        systemBundle.state = 1;
        if (z) {
            try {
                startup();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    public static void initialize(Properties properties) {
        if (properties == null) {
            properties = new Properties();
        }
        properties = properties;
        File filesDir = RuntimeVariables.androidApplication.getFilesDir();
        if (filesDir == null || !filesDir.exists()) {
            filesDir = RuntimeVariables.androidApplication.getFilesDir();
        }
        BASEDIR = properties.getProperty("android.taobao.atlas.basedir",
                filesDir.getAbsolutePath());
        BUNDLE_LOCATION = properties.getProperty("android.taobao.atlas.jars",
                "file:" + BASEDIR);
        CLASSLOADER_BUFFER_SIZE = getProperty(
                "android.taobao.atlas.classloader.buffersize", 1024 * 10);
        LOG_LEVEL = getProperty("android.taobao.atlas.log.level", 6);
        DEBUG_BUNDLES = getProperty("android.taobao.atlas.debug.bundles", false);
        DEBUG_PACKAGES = getProperty("android.taobao.atlas.debug.packages",
                false);
        DEBUG_SERVICES = getProperty("android.taobao.atlas.debug.services",
                false);
        DEBUG_CLASSLOADING = getProperty(
                "android.taobao.atlas.debug.classloading", false);
        if (getProperty("android.taobao.atlas.debug", false)) {
            System.out.println("SETTING ALL DEBUG FLAGS");
            LOG_LEVEL = 3;
            DEBUG_BUNDLES = true;
            DEBUG_PACKAGES = true;
            DEBUG_SERVICES = true;
            DEBUG_CLASSLOADING = true;
        }
        STRICT_STARTUP = getProperty("android.taobao.atlas.strictStartup",
                false);
        String property = properties
                .getProperty("org.osgi.framework.system.packages");
        if (property != null) {
            StringTokenizer stringTokenizer = new StringTokenizer(property, ",");
            int countTokens = stringTokenizer.countTokens();
            for (int i = 0; i < countTokens; i++) {
                BundleClassLoader.FRAMEWORK_PACKAGES.add(stringTokenizer
                        .nextToken().trim());
            }
        }
        properties.put(
                Constants.FRAMEWORK_EXECUTIONENVIRONMENT,
                System.getProperty("java.specification.name") + "/"
                        + System.getProperty("java.specification.version"));
        Properties properties2 = properties;
        String str = Constants.FRAMEWORK_OS_NAME;
        Object property2 = System.getProperty("os.name");
        if (property2 == null) {
            property2 = "undefined";
        }
        properties2.put(str, property2);
        properties2 = properties;
        str = Constants.FRAMEWORK_OS_VERSION;
        property2 = System.getProperty("os.version");
        if (property2 == null) {
            property2 = "undefined";
        }
        properties2.put(str, property2);
        properties2 = properties;
        str = Constants.FRAMEWORK_PROCESSOR;
        property2 = System.getProperty("os.arch");
        if (property2 == null) {
            property2 = "undefined";
        }
        properties2.put(str, property2);
        properties.put(Constants.FRAMEWORK_VERSION, FRAMEWORK_VERSION);
        properties.put(Constants.FRAMEWORK_VENDOR, "Atlas");
        property2 = Locale.getDefault().getLanguage();
        properties2 = properties;
        str = Constants.FRAMEWORK_LANGUAGE;
        if (property2 == null) {
            property2 = "en";
        }
        properties2.put(str, property2);
        STORAGE_LOCATION = properties.getProperty(
                "android.taobao.atlas.storage",
                properties.getProperty("org.osgi.framework.dir", BASEDIR
                        + File.separatorChar + "storage"))
                + File.separatorChar;
        launch();
        notifyFrameworkListeners(0, systemBundle, null);
    }

    private static void launch() {
        systemBundle = new SystemBundle();
        systemBundle.state = 8;
    }

    public static boolean getProperty(String str, boolean z) {
        if (properties == null) {
            return z;
        }
        String str2 = (String) properties.get(str);
        return str2 != null ? Boolean.valueOf(str2).booleanValue() : z;
    }

    public static int getProperty(String str, int i) {
        if (properties == null) {
            return i;
        }
        String str2 = (String) properties.get(str);
        return str2 != null ? Integer.parseInt(str2) : i;
    }

    public static String getProperty(String str) {
        if (properties == null) {
            return null;
        }
        return (String) properties.get(str);
    }

    public static String getProperty(String str, String str2) {
        return properties == null ? str2 : (String) properties.get(str);
    }

    protected static void warning(String str) throws RuntimeException {
        if (getProperty("android.taobao.atlas.strictStartup", false)) {
            throw new RuntimeException(str);
        }
        System.err.println("WARNING: " + str);
    }

    private static void storeProfile() {
        BundleImpl[] bundleImplArr = (BundleImpl[]) getBundles().toArray(
                new BundleImpl[bundles.size()]);
        for (BundleImpl updateMetadata : bundleImplArr) {
            updateMetadata.updateMetadata();
        }
        storeMetadata();
    }

    static void storeMetadata() {
        File file;
        Throwable e;
        try {
            file = new File(STORAGE_LOCATION, "meta");
            try {
                if (!AtlasFileLock.getInstance().LockExclusive(file)) {
                    log.error("Failed to get fileLock for "
                            + file.getAbsolutePath());
                    AtlasFileLock.getInstance().unLock(file);
                } else if (file.length() > 0) {
                    AtlasFileLock.getInstance().unLock(file);
                } else {
                    DataOutputStream dataOutputStream = new DataOutputStream(
                            new FileOutputStream(file));
                    dataOutputStream.writeInt(startlevel);
                    String join = StringUtils.join(writeAheads.toArray(), ",");
                    if (join == null) {
                        join = "";
                    }
                    dataOutputStream.writeUTF(join);
                    dataOutputStream.flush();
                    dataOutputStream.close();
                    AtlasFileLock.getInstance().unLock(file);
                }
            } catch (IOException e2) {
                e = e2;
                try {
                    log.error("Could not save meta data.", e);
                    AtlasFileLock.getInstance().unLock(file);
                } catch (Throwable th) {
                    e = th;
                    AtlasFileLock.getInstance().unLock(file);
                    throw e;
                }
            }
        } catch (IOException e3) {
            e = e3;
            file = null;
            log.error("Could not save meta data.", e);
            AtlasFileLock.getInstance().unLock(file);
        } catch (Throwable th2) {
            e = th2;
            file = null;
            AtlasFileLock.getInstance().unLock(file);

        }
    }

    private static int restoreProfile() {
        try {
            System.out.println("Restoring profile");
            File file = new File(STORAGE_LOCATION, "meta");
            if (file.exists()) {
                DataInputStream dataInputStream = new DataInputStream(
                        new FileInputStream(file));
                int readInt = dataInputStream.readInt();
                String[] split = StringUtils.split(dataInputStream.readUTF(),
                        ",");
                if (split != null) {
                    writeAheads.addAll(Arrays.asList(split));
                }
                dataInputStream.close();
                if (!getProperty("android.taobao.atlas.auto.load", true)) {
                    return readInt;
                }
                File file2 = new File(STORAGE_LOCATION);
                mergeWalsDir(new File(STORAGE_LOCATION, "wal"), file2);
                File[] listFiles = file2.listFiles(new FilenameFilter() {
                    public boolean accept(File file, String str) {
                        if (str.matches("^[0-9]*")) {
                            return false;
                        }
                        return true;
                    }
                });
                int i = 0;
                while (i < listFiles.length) {
                    if (listFiles[i].isDirectory()
                            && new File(listFiles[i], "meta").exists()) {
                        try {
                            System.out.println("RESTORED BUNDLE "
                                    + new BundleImpl(listFiles[i],
                                            new BundleContextImpl()).location);
                        } catch (Exception e) {
                            log.error(e.getMessage(), e.getCause());
                        }
                    }
                    i++;
                }
                return readInt;
            }
            System.out.println("Profile not found, performing clean start ...");
            return -1;
        } catch (Exception e2) {
            e2.printStackTrace();
            return 0;
        }
    }

    private static void mergeWalsDir(File file, File file2) {
        if (writeAheads != null && writeAheads.size() > 0) {
            for (int i = 0; i < writeAheads.size(); i++) {
                if (writeAheads.get(i) != null) {
                    File file3 = new File(file, (String) writeAheads.get(i));
                    if (file3 != null) {
                        try {
                            if (file3.exists()) {
                                File[] listFiles = file3.listFiles();
                                if (listFiles != null) {
                                    for (File file4 : listFiles) {
                                        if (file4.isDirectory()) {
                                            File file5 = new File(file2,
                                                    file4.getName());
                                            if (file5.exists()) {
                                                File[] listFiles2 = file4
                                                        .listFiles(new FilenameFilter() {
                                                            public boolean accept(
                                                                    File file,
                                                                    String str) {
                                                                return str
                                                                        .startsWith(BundleArchive.REVISION_DIRECTORY);
                                                            }
                                                        });
                                                if (listFiles2 != null) {
                                                    for (File file6 : listFiles2) {
                                                        if (new File(file6,
                                                                "meta")
                                                                .exists()) {
                                                            file6.renameTo(new File(
                                                                    file5,
                                                                    file6.getName()));
                                                        }
                                                    }
                                                }
                                            } else {
                                                file4.renameTo(file5);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            log.error("Error while merge wal dir", e);
                        }
                    }
                    writeAheads.set(i, null);
                }
            }
        }
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteDirectory(File file) {
        File[] listFiles = file.listFiles();
        for (int i = 0; i < listFiles.length; i++) {
            if (listFiles[i].isDirectory()) {
                deleteDirectory(listFiles[i]);
            } else {
                listFiles[i].delete();
            }
        }
        file.delete();
    }

    static void checkAdminPermission() {
        AccessController.checkPermission(ADMIN_PERMISSION);
    }

    static BundleImpl installNewBundle(String str) throws BundleException {
        try {
            String str2 = str.indexOf(":") > -1 ? str : BUNDLE_LOCATION
                    + File.separatorChar + str;
            return installNewBundle(str2, new URL(str2).openConnection()
                    .getInputStream());
        } catch (Throwable e) {
            throw new BundleException("Cannot retrieve bundle from " + str, e);
        }
    }

    static void installOrUpdate(String[] strArr, File[] fileArr)
            throws BundleException {
        if (strArr == null || fileArr == null
                || strArr.length != fileArr.length) {
            throw new IllegalArgumentException(
                    "locations and files must not be null and must be same length");
        }
        String valueOf = String.valueOf(System.currentTimeMillis());
        File file = new File(new File(STORAGE_LOCATION, "wal"), valueOf);
        file.mkdirs();
        int i = 0;
        while (i < strArr.length) {
            if (!(strArr[i] == null || fileArr[i] == null)) {
                try {
                    BundleLock.WriteLock(strArr[i]);
                    Bundle bundle = getBundle(strArr[i]);
                    if (bundle != null) {
                        bundle.update(fileArr[i]);
                    } else {
                        BundleImpl bundleImpl = new BundleImpl(new File(file,
                                strArr[i]), strArr[i], new BundleContextImpl(),
                                null, fileArr[i], false);
                    }
                    BundleLock.WriteUnLock(strArr[i]);
                } catch (Throwable th) {
                    BundleLock.WriteUnLock(strArr[i]);
                }
            }
            i++;
        }
        writeAheads.add(valueOf);
        storeMetadata();
    }

    static void unregisterService(ServiceReference serviceReference) {
        services.remove(serviceReference);
        removeValue(classes_services,
                (String[]) serviceReference.getProperty(Constants.OBJECTCLASS),
                serviceReference);
        BundleImpl bundleImpl = (BundleImpl) serviceReference.getBundle();
        bundleImpl.registeredServices.remove(serviceReference);
        if (bundleImpl.registeredServices.isEmpty()) {
            bundleImpl.registeredServices = null;
        }
        notifyServiceListeners(4, serviceReference);
        if (DEBUG_SERVICES && log.isInfoEnabled()) {
            log.info("Framework: UNREGISTERED SERVICE " + serviceReference);
        }
    }

    static void notifyBundleListeners(int i, Bundle bundle) {
        int i2 = 0;
        if (!syncBundleListeners.isEmpty() || !bundleListeners.isEmpty()) {
            BundleEvent bundleEvent = new BundleEvent(i, bundle);
            BundleListener[] bundleListenerArr = (BundleListener[]) syncBundleListeners
                    .toArray(new BundleListener[syncBundleListeners.size()]);
            for (BundleListener bundleChanged : bundleListenerArr) {
                bundleChanged.bundleChanged(bundleEvent);
            }
            if (!bundleListeners.isEmpty()) {
                bundleListenerArr = (BundleListener[]) bundleListeners
                        .toArray(new BundleListener[bundleListeners.size()]);
                while (i2 < bundleListenerArr.length) {
                    bundleListenerArr[i2].bundleChanged(bundleEvent);
                    i2++;
                }
            }
        }
    }

    static void addFrameworkListener(FrameworkListener frameworkListener) {
        frameworkListeners.add(frameworkListener);
    }

    static void removeFrameworkListener(FrameworkListener frameworkListener) {
        frameworkListeners.remove(frameworkListener);
    }

    static void addBundleListener(BundleListener bundleListener) {
        bundleListeners.add(bundleListener);
    }

    static void removeBundleListener(BundleListener bundleListener) {
        bundleListeners.remove(bundleListener);
    }

    static void notifyFrameworkListeners(int i, Bundle bundle, Throwable th) {
        if (!frameworkListeners.isEmpty()) {
            FrameworkEvent frameworkEvent = new FrameworkEvent(i, bundle, th);
            FrameworkListener[] frameworkListenerArr = (FrameworkListener[]) frameworkListeners
                    .toArray(new FrameworkListener[frameworkListeners.size()]);
            for (FrameworkListener frameworkEvent2 : frameworkListenerArr) {
                frameworkEvent2.frameworkEvent(frameworkEvent);
            }
        }
    }

    static void notifyServiceListeners(int i, ServiceReference serviceReference) {
        if (!serviceListeners.isEmpty()) {
            ServiceEvent serviceEvent = new ServiceEvent(i, serviceReference);
            ServiceListenerEntry[] serviceListenerEntryArr = (ServiceListenerEntry[]) serviceListeners
                    .toArray(new ServiceListenerEntry[serviceListeners.size()]);
            int i2 = 0;
            while (i2 < serviceListenerEntryArr.length) {
                if (serviceListenerEntryArr[i2].filter == null
                        || serviceListenerEntryArr[i2].filter
                                .match(((ServiceReferenceImpl) serviceReference).properties)) {
                    serviceListenerEntryArr[i2].listener
                            .serviceChanged(serviceEvent);
                }
                i2++;
            }
        }
    }

    static void clearBundleTrace(BundleImpl bundleImpl) {
        int i = 0;
        if (bundleImpl.registeredFrameworkListeners != null) {
            frameworkListeners
                    .removeAll(bundleImpl.registeredFrameworkListeners);
            bundleImpl.registeredFrameworkListeners = null;
        }
        if (bundleImpl.registeredServiceListeners != null) {
            serviceListeners.removeAll(bundleImpl.registeredServiceListeners);
            bundleImpl.registeredServiceListeners = null;
        }
        if (bundleImpl.registeredBundleListeners != null) {
            bundleListeners.removeAll(bundleImpl.registeredBundleListeners);
            syncBundleListeners.removeAll(bundleImpl.registeredBundleListeners);
            bundleImpl.registeredBundleListeners = null;
        }
        ServiceReference[] registeredServices = bundleImpl
                .getRegisteredServices();
        if (registeredServices != null) {
            for (int i2 = 0; i2 < registeredServices.length; i2++) {
                unregisterService(registeredServices[i2]);
                ((ServiceReferenceImpl) registeredServices[i2]).invalidate();
            }
            bundleImpl.registeredServices = null;
        }
        ServiceReference[] servicesInUse = bundleImpl.getServicesInUse();
        while (i < servicesInUse.length) {
            ((ServiceReferenceImpl) servicesInUse[i]).ungetService(bundleImpl);
            i++;
        }
    }

    static void addValue(Map map, Object obj, Object obj2) {
        List list = (List) map.get(obj);
        if (list == null) {
            list = new ArrayList();
        }
        list.add(obj2);
        map.put(obj, list);
    }

    static void removeValue(Map map, Object[] objArr, Object obj) {
        for (int i = 0; i < objArr.length; i++) {
            List list = (List) map.get(objArr[i]);
            if (list != null) {
                list.remove(obj);
                if (list.isEmpty()) {
                    map.remove(objArr[i]);
                } else {
                    map.put(objArr[i], list);
                }
            }
        }
    }

    static void export(BundleClassLoader bundleClassLoader, String[] strArr,
            boolean z) {
        synchronized (exportedPackages) {
            if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                log.debug("Bundle " + bundleClassLoader.bundle + " registers "
                        + (z ? "resolved" : "unresolved") + " packages "
                        + Arrays.asList(strArr));
            }
            for (String str : strArr) {
                Package packageR = new Package(str, bundleClassLoader, z);
                Package packageR2 = (Package) exportedPackages.get(packageR);
                if (packageR2 == null) {
                    exportedPackages.put(packageR, packageR);
                    if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                        log.debug("REGISTERED PACKAGE " + packageR);
                    }
                } else if (packageR2.importingBundles == null
                        && packageR.updates(packageR2)) {
                    exportedPackages.remove(packageR2);
                    exportedPackages.put(packageR, packageR);
                    if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                        log.debug("REPLACED PACKAGE " + packageR2 + " WITH "
                                + packageR);
                    }
                }
            }
        }
    }

    static BundleClassLoader getImport(BundleImpl bundleImpl, String str,
            boolean z, HashSet<BundleClassLoader> hashSet) {
        if (DEBUG_PACKAGES && log.isDebugEnabled()) {
            log.debug("Bundle " + bundleImpl + " requests package " + str);
        }
        synchronized (exportedPackages) {
            try {
                Package packageR = (Package) exportedPackages.get(new Package(
                        str, null, false));
                if (packageR == null || !(packageR.resolved || z)) {
                    return null;
                }
                BundleClassLoader bundleClassLoader = packageR.classloader;
                if (bundleClassLoader == bundleImpl.classloader) {
                    return bundleClassLoader;
                }
                if (!(!z || packageR.resolved || hashSet
                        .contains(packageR.classloader))) {
                    hashSet.add(bundleImpl.classloader);
                    packageR.classloader.resolveBundle(true, hashSet);
                }
                if (packageR.importingBundles == null) {
                    packageR.importingBundles = new ArrayList();
                }
                if (!packageR.importingBundles.contains(bundleImpl)) {
                    packageR.importingBundles.add(bundleImpl);
                }
                if (DEBUG_PACKAGES && log.isDebugEnabled()) {
                    log.debug("REQUESTED PACKAGE " + str
                            + ", RETURNED DELEGATION TO "
                            + bundleClassLoader.bundle);
                }
                return bundleClassLoader;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } catch (Throwable th) {
            }
        }
        return null;
    }

    public static boolean isFrameworkStartupShutdown() {
        return frameworkStartupShutdown;
    }

    public static ClassNotFoundInterceptorCallback getClassNotFoundCallback() {
        return classNotFoundCallback;
    }

    public static void setClassNotFoundCallback(
            ClassNotFoundInterceptorCallback classNotFoundInterceptorCallback) {
        classNotFoundCallback = classNotFoundInterceptorCallback;
    }
}
