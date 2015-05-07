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
package com.openAtlas.dexopt;

import com.openAtlas.log.Logger;
import com.openAtlas.log.LoggerFactory;

import android.os.Build.VERSION;

public class InitExecutor {
    static final Logger log;
    private static boolean sDexOptLoaded;

    private static native void dexopt(String str, String str2, String str3);

    static {
        log = LoggerFactory.getInstance("InitExecutor");
        sDexOptLoaded = false;
        try {
            System.loadLibrary("dexopt");
            sDexOptLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    public static boolean optDexFile(String str, String str2) {
        try {
            if (sDexOptLoaded && VERSION.SDK_INT <= 18) {
                dexopt(str, str2, "v=n,o=v");
                return true;
            }
        } catch (Throwable e) {
            log.error("Exception while try to call native dexopt >>>", e);
        }
        return false;
    }
}
