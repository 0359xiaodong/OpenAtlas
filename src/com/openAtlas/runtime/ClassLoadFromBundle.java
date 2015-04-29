/**
 *  OpenAtlasForAndroid Project
The MIT License (MIT) Copyright (OpenAtlasForAndroid) 2015 Bunny Blue,achellies

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software 
without restriction, including without limitation the rights to use, copy, modify, 
merge, publish, distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies 
or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
@author BunnyBlue
 * **/
package com.openAtlas.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;

import android.content.pm.PackageInfo;
import android.text.TextUtils;
import android.util.Log;
import blue.stack.openAtlas.PlatformConfigure;

import com.openAtlas.bundleInfo.BundleInfoList;
import com.openAtlas.framework.Atlas;
import com.openAtlas.framework.BundleImpl;
import com.openAtlas.framework.Framework;
import com.openAtlas.framework.bundlestorage.BundleArchiveRevision.DexLoadException;

public class ClassLoadFromBundle {
    private static final String TAG = "ClassLoadFromBundle";
    private static Hashtable<Integer, String> classNotFoundReason;
    private static int reasonCnt;
    public static List<String> sInternalBundles;

    static {
        classNotFoundReason = new Hashtable<Integer, String>();
        reasonCnt = 0;
    }

    public static String getClassNotFoundReason(String str) {
        for (int i = 0; i < classNotFoundReason.size(); i++) {
            if ((classNotFoundReason.get(Integer.valueOf(i)) + "").contains(str
                    + "")) {
                return classNotFoundReason.get(Integer.valueOf(i)) + "";
            }
        }
        return "";
    }

    private static void insertToReasonList(String str, String str2) {
        classNotFoundReason.put(Integer.valueOf(reasonCnt), " Not found class "
                + str + " because " + str2);
        int i = reasonCnt + 1;
        reasonCnt = i;
        reasonCnt = i % 10;
    }

    public static String getPackageNameFromEntryName(String str) {
        return str.substring(
                str.indexOf("lib/armeabi/lib") + "lib/armeabi/lib".length(),
                str.indexOf(".so")).replace("_", ".");
    }

    public static synchronized void resolveInternalBundles() {
        synchronized (ClassLoadFromBundle.class) {
            if (sInternalBundles == null || sInternalBundles.size() == 0) {
                String str = "lib/armeabi/libcom_";
                String str2 = ".so";
                List<String> arrayList = new ArrayList<String>();
                try {
                	ZipFile apkFile=new ZipFile(
                            RuntimeVariables.androidApplication
                            .getApplicationInfo().sourceDir);
                    Enumeration<?> entries = apkFile.entries();
                    while (entries.hasMoreElements()) {
                        String name = ((ZipEntry) entries.nextElement())
                                .getName();
                        if (name.startsWith(str) && name.endsWith(str2)) {
                            arrayList.add(getPackageNameFromEntryName(name));
                        }
                    }
                    apkFile.close();
                    sInternalBundles = arrayList;
                } catch (Throwable e) {
                    Log.e(TAG, "Exception while get bundles in assets or lib",
                            e);
                }
            }
        }
    }

    public static Class<?> loadFromUninstalledBundles(String str)
            throws ClassNotFoundException {
        if (sInternalBundles == null) {
            resolveInternalBundles();
        }
        BundleInfoList instance = BundleInfoList.getInstance();
        String bundleForComponet = instance.getBundleForComponet(str);
        if (bundleForComponet == null) {
            Log.e("me",
                    "Failed to find the bundle in BundleInfoList for component "
                            + str);
            insertToReasonList(str, "not found in BundleInfoList!");
            return null;
        } else if (sInternalBundles != null
                && !sInternalBundles.contains(bundleForComponet)) {
            return null;
        } else {
            Bundle installBundle;
            List<String> linkedList = new LinkedList<String>();
            if (instance.getDependencyForBundle(bundleForComponet) != null) {
                linkedList.addAll(instance
                        .getDependencyForBundle(bundleForComponet));
            }
            linkedList.add(bundleForComponet);
            for (String str2 : linkedList) {
                File file = new File(
                        new File(
                                Framework
                                        .getProperty("com.openAtlas.AppDirectory"),
                                "lib"), "lib".concat(str2.replace(".", "_"))
                                .concat(".so"));
                if (Atlas.getInstance().getBundle(str2) == null) {
                    try {
                        if (!file.exists()) {
                            return null;
                        }
                        installBundle = Atlas.getInstance().installBundle(str2,
                                file);
                        if (installBundle != null) {
                            Log.e("me", "Succeed to install bundle " + str2);
                            try {
                                long currentTimeMillis = System
                                        .currentTimeMillis();
                                ((BundleImpl) installBundle).optDexFile();
                                Log.e("me",
                                        "Succeed to dexopt bundle "
                                                + str2
                                                + " cost time = "
                                                + (System.currentTimeMillis() - currentTimeMillis)
                                                + " ms");
                            } catch (Throwable e) {
                                Log.e(TAG, "Error while dexopt >>>", e);
                                insertToReasonList(str, "dexopt failed!");
                                if (!(e instanceof DexLoadException)) {
                                    return null;
                                }
                                throw ((RuntimeException) e);
                            }
                        }
                    } catch (Throwable e2) {
                        Log.e(TAG, "Could not install bundle.", e2);
                        insertToReasonList(str, "bundle installation failed");
                        return null;
                    }
                }
            }
            installBundle = Atlas.getInstance().getBundle(bundleForComponet);
            ClassLoader classLoader = ((BundleImpl) installBundle)
                    .getClassLoader();
            if (classLoader != null) {
                try {
                    Class<?> loadClass = classLoader.loadClass(str);
                    if (loadClass != null) {
                        return loadClass;
                    }
                } catch (ClassNotFoundException e3) {
                }
            }
            throw new ClassNotFoundException("Can't find class " + str
                    + " in BundleClassLoader: " + installBundle.getLocation());
        }
    }

    static Class<?> loadFromInstalledBundles(String str)
            throws ClassNotFoundException {
        BundleImpl bundleImpl;
        int i = 0;
        Class<?> cls = null;
        List<Bundle> bundles = Framework.getBundles();
        if (!(bundles == null || bundles.isEmpty())) {
            for (Bundle bundle : bundles) {
                bundleImpl = (BundleImpl) bundle;
                PackageLite packageLite = DelegateComponent
                        .getPackage(bundleImpl.getLocation());
                if (packageLite != null && packageLite.components.contains(str)) {
                    bundleImpl.getArchive().optDexFile();
                    ClassLoader classLoader = bundleImpl.getClassLoader();
                    if (classLoader != null) {
                        try {
                            cls = classLoader.loadClass(str);
                            if (cls != null) {
                                return cls;
                            }
                        } catch (ClassNotFoundException e) {
                            throw new ClassNotFoundException(
                                    "Can't find class "
                                            + str
                                            + " in BundleClassLoader: "
                                            + bundleImpl.getLocation()
                                            + " ["
                                            + (bundles == null ? 0
                                                    : bundles.size())
                                            + "]"
                                            + "classloader is: "
                                            + (classLoader == null ? "null"
                                                    : "not null")
                                            + " packageversion "
                                            + getPackageVersion()
                                            + " exception:" + e.getMessage());
                        }
                    }
                    StringBuilder append = new StringBuilder()
                            .append("Can't find class ").append(str)
                            .append(" in BundleClassLoader: ")
                            .append(bundleImpl.getLocation()).append(" [");
                    if (bundles != null) {
                        i = bundles.size();
                    }
                    throw new ClassNotFoundException(append
                            .append(i)
                            .append("]")
                            .append(classLoader == null ? "classloader is null"
                                    : "classloader not null")
                            .append(" packageversion ")
                            .append(getPackageVersion()).toString());
                }
            }
        }
        if (!(bundles == null || bundles.isEmpty())) {
            Class<?> cls2 = null;
            for (Bundle bundle2 : Framework.getBundles()) {
                bundleImpl = (BundleImpl) bundle2;
                if (bundleImpl.getArchive().isDexOpted()) {
                    Class<?> loadClass = null;
                    ClassLoader classLoader2 = bundleImpl.getClassLoader();
                    if (classLoader2 != null) {
                        try {
                            loadClass = classLoader2.loadClass(str);
                            if (loadClass != null) {
                                return loadClass;
                            }
                        } catch (ClassNotFoundException e2) {
                        }
                    } else {
                        loadClass = cls2;
                    }
                    cls2 = loadClass;
                }
            }
            cls = cls2;
        }
        return cls;
    }
    public static void checkInstallBundleAndDependency(String str) {
        List dependencyForBundle = BundleInfoList.getInstance().getDependencyForBundle(str);
        if (dependencyForBundle != null && dependencyForBundle.size() > 0) {
            for (int i = 0; i < dependencyForBundle.size(); i++) {
                checkInstallBundleAndDependency((String) dependencyForBundle.get(i));
            }
        }
        if (Atlas.getInstance().getBundle(str) == null) {
            File file = new File(new File(Framework.getProperty(PlatformConfigure.ATLAS_APP_DIRECTORY), "lib"), "lib".concat(str.replace(".", "_")).concat(".so"));
            if (file.exists()) {
                try {
                    Atlas.getInstance().installBundle(str, file);
                } catch (Throwable e) {
                    throw new RuntimeException("failed to install bundle " + str, e);
                }
            }
        }
    }
    public static void checkInstallBundleIfNeed(String str) {
        synchronized (str) {
            if (sInternalBundles == null) {
                resolveInternalBundles();
            }
            String bundleForComponet = BundleInfoList.getInstance().getBundleForComponet(str);
            if (TextUtils.isEmpty(bundleForComponet)) {
               // "Failed to find the bundle in BundleInfoList for component " + str;
                insertToReasonList(str, "not found in BundleInfoList!");
            }
            if (sInternalBundles == null || sInternalBundles.contains(bundleForComponet)) {
                checkInstallBundleAndDependency(bundleForComponet);
                return;
            }
        }
    }

    private static int getPackageVersion() {
        PackageInfo packageInfo;
        try {
            packageInfo = RuntimeVariables.androidApplication
                    .getPackageManager()
                    .getPackageInfo(
                            RuntimeVariables.androidApplication
                                    .getPackageName(),
                            0);
        } catch (Throwable e) {
            Log.e(TAG, "Error to get PackageInfo >>>", e);
            packageInfo = new PackageInfo();
        }
        return packageInfo.versionCode;
    }
}
