/**
 * Back 2 Browser Bytecode Translator
 * Copyright (C) 2012 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://opensource.org/licenses/GPL-2.0.
 */
package org.apidesign.vm4brwsr;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import org.apidesign.bck2brwsr.core.JavaScriptBody;

/**
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
@ClassesMarker(number = 10)
@ClassesNamer(name = "my text")
public class Classes {
    public static String nameOfIO() {
        return nameFor(IOException.class);
    }
    
    private static String nameFor(Class<?> c) {
        return c.getName();
    }
    
    public static boolean equalsClassesOfExceptions() {
        return MalformedURLException.class.getSuperclass() == IOException.class;
    }
    public static boolean differenceInClasses() {
        Class<?> c1 = MalformedURLException.class;
        Class<?> c2 = IOException.class;
        return c1 != c2;
    }
    
    public static String classForInstance() {
        return new IOException().getClass().getName().toString();
    }
    
    @ClassesMarker(number = 1)
    public static String name() {
        return IOException.class.getName().toString();
    }
    public static String simpleName() {
        return IOException.class.getSimpleName();
    }
    public static String canonicalName() {
        return IOException.class.getCanonicalName();
    }
    public static boolean newInstance() throws Exception {
        IOException ioe = IOException.class.newInstance();
        if (ioe instanceof IOException) {
            return ioe.getClass() == IOException.class;
        }
        throw new IllegalStateException("Not a subtype: " + ioe);
    }
    public static int getMarker() {
        if (!Classes.class.isAnnotationPresent(ClassesMarker.class)) {
            return -2;
        }
        ClassesMarker cm = Classes.class.getAnnotation(ClassesMarker.class);
        return cm == null ? -1 : cm.number();
    }
    public static String getNamer(boolean direct) {
        if (direct) {
            ClassesNamer cm = Classes.class.getAnnotation(ClassesNamer.class);
            return cm == null ? null : cm.name();
        }
        for (Annotation a : Classes.class.getAnnotations()) {
            if (a instanceof ClassesNamer) {
                return ((ClassesNamer)a).name();
            }
        }
        return null;
    }
    
    @JavaScriptBody(args = "msg", body = "throw msg;")
    private static native void thrw(String msg);
    
    public static Object reflectiveMethodCall(boolean direct, String mn) throws Exception {
        Method find = null;
        StringBuilder sb = new StringBuilder();
        if (!direct) {
            final Class<? extends Annotation> v = ClassesMarker.class;
            for (Method m : Classes.class.getMethods()) {
                sb.append("\n").append(m.getName());
                if (mn != null) {
                    if (m.getName().equals(mn)) {
                        find = m;
                        break;
                    }
                } else {
                    if (m.getAnnotation(v) != null) {
                        find = m;
                        break;
                    }
                }
            }
        } else {
            find = Classes.class.getMethod(mn);
        }
        if (find == null) {
            thrw(sb.toString());
            throw new NullPointerException(sb.toString());
        }
        return find.invoke(null);
    }
}