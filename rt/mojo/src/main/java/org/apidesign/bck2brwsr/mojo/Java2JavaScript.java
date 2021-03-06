/**
 * Back 2 Browser Bytecode Translator
 * Copyright (C) 2012-2017 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
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
package org.apidesign.bck2brwsr.mojo;

import org.apache.maven.plugin.AbstractMojo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apidesign.vm4brwsr.Bck2Brwsr;
import org.apidesign.vm4brwsr.ObfuscationLevel;

/** Compiles classes into JavaScript. */
@Mojo(name="j2js", 
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase=LifecyclePhase.PROCESS_CLASSES
)
public class Java2JavaScript extends AbstractMojo {
    public Java2JavaScript() {
    }
    /** Root of the class files */
    @Parameter(defaultValue="${project.build.directory}/classes")
    private File classes;
    /** JavaScript file to generate */
    @Parameter(defaultValue="${project.build.directory}/bck2brwsr.js")
    private File javascript;

    /** Additional classes that should be pre-compiled into the javascript 
     * file. By default compiles all classes found under <code>classes</code>
     * directory and their transitive closure.
     */
    @Parameter
    private List<String> compileclasses;
    
    @Parameter(defaultValue="${project}")
    private MavenProject prj;

    /**
     * The obfuscation level for the generated JavaScript file.
     *
     * @since 0.5
     */
    @Parameter(defaultValue="NONE")
    private ObfuscationLevel obfuscation;
    
    /** Should classes from rt.jar be included? */
    @Parameter(defaultValue = "false")
    private boolean ignoreBootClassPath;

    /**
     * Indicates whether to create an extension library 
     * module instead of a standalone JavaScript VM.
     *
     * @since 0.9
     */
    @Parameter(defaultValue="false")
    private boolean library;

    @Override
    public void execute() throws MojoExecutionException {
        if (!classes.isDirectory()) {
            throw new MojoExecutionException("Can't find " + classes);
        }
        if (javascript == null) {
            throw new MojoExecutionException("Need to define 'javascript' attribute with a path to file to generate");
        }

        List<String> arr = new ArrayList<String>();
        long newest = collectAllClasses("", classes, arr);
        
        if (compileclasses != null) {
            arr.retainAll(compileclasses);
            arr.addAll(compileclasses);
        }
        
        if (javascript.lastModified() > newest) {
            return;
        }

        try {
            URLClassLoader url = buildClassLoader(classes, prj.getArtifacts());
            Writer w = new OutputStreamWriter(new FileOutputStream(javascript), "UTF-8");
            Bck2Brwsr c = Bck2Brwsr.newCompiler().
                obfuscation(obfuscation).
                resources(url, ignoreBootClassPath).
                addRootClasses(arr.toArray(new String[0]));
            if (library) {
                c = c.library();
            }
            c.generate(w);
            w.close();
        } catch (IOException ex) {
            throw new MojoExecutionException("Can't compile", ex);
        }
    }

    private static long collectAllClasses(String prefix, File toCheck, List<String> arr) {
        File[] files = toCheck.listFiles();
        if (files != null) {
            long newest = 0L;
            for (File f : files) {
                long lastModified = collectAllClasses(prefix + f.getName() + "/", f, arr);
                if (newest < lastModified) {
                    newest = lastModified;
                }
            }
            return newest;
        } else if (toCheck.getName().endsWith(".class")) {
            final String cls = prefix.substring(0, prefix.length() - 7);
            arr.add(cls);
            return toCheck.lastModified();
        } else {
            return 0L;
        }
    }

    private static URLClassLoader buildClassLoader(File root, Collection<Artifact> deps) throws MalformedURLException {
        List<URL> arr = new ArrayList<URL>();
        arr.add(root.toURI().toURL());
        for (Artifact a : deps) {
            if (a.getFile() != null) {
                arr.add(a.getFile().toURI().toURL());
            }
        }
        return new URLClassLoader(arr.toArray(new URL[0]), Java2JavaScript.class.getClassLoader());
    }
}
