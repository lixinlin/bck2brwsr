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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apidesign.bck2brwsr.aot.Bck2BrwsrJars;
import org.apidesign.vm4brwsr.Bck2Brwsr;
import org.apidesign.vm4brwsr.ObfuscationLevel;

/**
 *
 * @author Jaroslav Tulach
 * @since 0.9
 */
@Mojo(name = "aot",
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    defaultPhase = LifecyclePhase.PACKAGE
)
public class AheadOfTime extends AbstractMojo {
    @Parameter(defaultValue = "${project}")
    private MavenProject prj;
    
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File mainJar;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.js")
    private File mainJavaScript;
    
    @Parameter
    private String[] exports;
    
    /**
     * Directory where to generate ahead-of-time JavaScript files for
     * required libraries.
     */
    @Parameter(defaultValue = "lib")
    private String classPathPrefix;

    /** Root JavaScript file to generate */
    @Parameter(defaultValue="${project.build.directory}/bck2brwsr.js")
    private File vm;
    
    @Parameter(defaultValue = "true")
    private boolean generateAotLibraries;
    
    @Parameter(defaultValue = "true")
    private boolean ignoreBootClassPath;
    
    /**
     * The obfuscation level for the generated JavaScript file.
     *
     * @since 0.5
     */
    @Parameter(defaultValue = "NONE")
    private ObfuscationLevel obfuscation;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        URLClassLoader loader;
        try {
            loader = buildClassLoader(mainJar, prj.getArtifacts());
        } catch (MalformedURLException ex) {
            throw new MojoFailureException("Can't initialize classloader");
        }
        for (Artifact a : prj.getArtifacts()) {
            if (a.getFile() == null) {
                continue;
            }
            String n = a.getFile().getName();
            if (!n.endsWith(".jar")) {
                continue;
            }
            if ("provided".equals(a.getScope())) {
                continue;
            }
            File aot = new File(mainJavaScript.getParent(), classPathPrefix);
            aot.mkdirs();
            File js = new File(aot, n.substring(0, n.length() - 4) + ".js");
            if (js.lastModified() > a.getFile().lastModified()) {
                getLog().info("Skipping " + js + " as it already exists.");
                continue;
            }
            try {
                aotLibrary(a, js , loader);
            } catch (IOException ex) {
                throw new MojoFailureException("Can't compile " + a.getFile(), ex);
            }
        }
        
        try {
            if (mainJavaScript.lastModified() > mainJar.lastModified()) {
                getLog().info("Skipping " + mainJavaScript + " as it already exists.");
            } else {
                getLog().info("Generating " + mainJavaScript);
                Bck2Brwsr c = Bck2BrwsrJars.configureFrom(null, mainJar, loader, ignoreBootClassPath);
                if (exports != null) {
                    for (String e : exports) {
                        c = c.addExported(e.replace('.', '/'));
                    }
                }
                Writer w = new OutputStreamWriter(new FileOutputStream(mainJavaScript), "UTF-8");
                c.
                        obfuscation(obfuscation).
                        generate(w);
                w.close();
            }
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot generate script for " + mainJar, ex);
        }
            
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(vm), "UTF-8");
            Bck2Brwsr.newCompiler().
                    obfuscation(obfuscation).
                    standalone(false).
                    resources(new Bck2Brwsr.Resources() {

                @Override
                public InputStream get(String resource) throws IOException {
                    return null;
                }
            }).
                    generate(w);
            w.close();
            
        } catch (IOException ex) {
            throw new MojoExecutionException("Can't compile", ex);
        }
    }

    private void aotLibrary(Artifact a, File js, URLClassLoader loader) throws IOException, MojoExecutionException {
        for (Artifact b : prj.getArtifacts()) {
            if ("bck2brwsr".equals(b.getClassifier())) { // NOI18N
                getLog().debug("Inspecting " + b.getFile());
                JarFile jf = new JarFile(b.getFile());
                Manifest man = jf.getManifest();
                for (Map.Entry<String, Attributes> entrySet : man.getEntries().entrySet()) {
                    String entryName = entrySet.getKey();
                    Attributes attr = entrySet.getValue();
                    
                    if (
                        a.getArtifactId().equals(attr.getValue("Bck2BrwsrArtifactId")) &&
                        a.getGroupId().equals(attr.getValue("Bck2BrwsrGroupId")) &&
                        a.getVersion().equals(attr.getValue("Bck2BrwsrVersion")) &&
                        (
                            obfuscation == ObfuscationLevel.FULL && "true".equals(attr.getValue("Bck2BrwsrMinified"))
                            ||
                            obfuscation != ObfuscationLevel.FULL && "true".equals(attr.getValue("Bck2BrwsrDebug"))
                        )
                    ) {
                        getLog().info("Extracting " + js + " from " + b.getFile());
                        InputStream is = jf.getInputStream(new ZipEntry(entryName));
                        Files.copy(is, js.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        is.close();
                        return;
                    }
                }
            }
        }
        if (!generateAotLibraries) {
            throw new MojoExecutionException("Not generating " + js + " and no precompiled version found!");
        }
        getLog().info("Generating " + js);
        Writer w = new OutputStreamWriter(new FileOutputStream(js), "UTF-8");
        Bck2Brwsr c = Bck2BrwsrJars.configureFrom(null, a.getFile(), loader, ignoreBootClassPath);
        if (exports != null) {
            c = c.addExported(exports);
        }
        c.
            obfuscation(obfuscation).
            generate(w);
        w.close();
    }
    private static URLClassLoader buildClassLoader(File root, Collection<Artifact> deps) throws MalformedURLException {
        List<URL> arr = new ArrayList<URL>();
        if (root != null) {
            arr.add(root.toURI().toURL());
        }
        for (Artifact a : deps) {
            if (a.getFile() != null) {
                arr.add(a.getFile().toURI().toURL());
            }
        }
        return new URLClassLoader(arr.toArray(new URL[0]), Java2JavaScript.class.getClassLoader());
    }
}
