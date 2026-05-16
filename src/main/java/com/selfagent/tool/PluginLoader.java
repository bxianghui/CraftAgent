package com.selfagent.tool;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class PluginLoader {
    public static List<ToolPlugin> loadFromDirectory(Path pluginDir) {
        List<ToolPlugin> loaded = new ArrayList<>();
        if (!Files.isDirectory(pluginDir)) return loaded;
        try {
            File[] jars = pluginDir.toFile().listFiles(f -> f.getName().endsWith(".jar"));
            if (jars == null) return loaded;
            for (File jar : jars) {
                URLClassLoader cl = new URLClassLoader(
                    new java.net.URL[]{jar.toURI().toURL()},
                    PluginLoader.class.getClassLoader());
                ServiceLoader<ToolPlugin> sl = ServiceLoader.load(ToolPlugin.class, cl);
                sl.forEach(loaded::add);
            }
        } catch (Exception e) {
            System.err.println("Plugin load error: " + e.getMessage());
        }
        return loaded;
    }
}
