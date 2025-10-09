package io.github.absketches.plugin.concreteclazz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class ClassFileUtils {
    private static final String BASE_JAVA_CLASS = "java/lang/Object";

    private ClassFileUtils() {}

    static String toDotted(String internalName) {
        return internalName.replace('/', '.');
    }

    static String toInternal(String dottedName) {
        return dottedName.replace('.', '/');
    }

    static boolean isConcrete(ClassHeader h) {
        return h != null && !h.isInterface() && !h.isAbstract();
    }

    // strip .class from name
    static String formatKey(String classFilePath) {
        return classFilePath.substring(0, classFilePath.length() - 6);
    }

    // Accept comma-separated dot-notation base classes -> INTERNAL names
    static List<String> parseBaseClasses(String input) {
        if (input == null || input.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : input.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(toInternal(s));
        }
        return out;
    }

    // Read precomputed properties (keys/values are dotted in file)
    // Returns true if all allowedBases were present in the jar file (so caller may skip class scan)
    static boolean readAllPropertiesFromJar(JarFile jf, JarEntry indexFileEntry,
                                            Map<String, Set<String>> precomputed, Set<String> allowedBases)
        throws IOException {
        try (InputStream in = jf.getInputStream(indexFileEntry);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Properties props = new Properties();
            props.load(br);

            Set<String> matched = new HashSet<>();
            for (String key : props.stringPropertyNames()) {
                String keyInternal = toInternal(key);
                if (!allowedBases.contains(keyInternal)) continue;

                matched.add(keyInternal);
                String val = props.getProperty(key);
                Set<String> set = precomputed.computeIfAbsent(keyInternal, k -> new TreeSet<>());
                if (val != null) {
                    for (String clazz : val.split(",", -1)) {
                        String c = clazz.strip();
                        if (!c.isEmpty()) set.add(toInternal(c));
                    }
                }
            }
            return matched.containsAll(allowedBases);
        }
    }

    // Class hierarchy walk
    static boolean isSubclassOfBase(String internal,
                                    Map<String, ClassHeader> headers,
                                    Map<String, Boolean> cache,
                                    String baseInternal) {
        Boolean cached = cache.get(internal);
        if (cached != null) return cached;

        ClassHeader h = headers.get(internal);
        if (!isConcrete(h)) {
            cache.put(internal, false);
            return false;
        }

        String cur = h.superInternalName();
        List<String> visited = new ArrayList<>();
        visited.add(internal);

        for (int hops = 0; cur != null && hops <= 256; hops++) {
            if (baseInternal.equals(cur)) {
                markVisited(visited, cache, true);
                return true;
            }
            if (BASE_JAVA_CLASS.equals(cur)) {
                markVisited(visited, cache, false);
                return false;
            }

            Boolean c2 = cache.get(cur);
            if (c2 != null) {
                markVisited(visited, cache, c2);
                return c2;
            }

            ClassHeader sup = headers.get(cur);
            if (!isConcrete(sup)) {
                markVisited(visited, cache, false);
                return false;
            }

            visited.add(cur);
            cur = sup.superInternalName();
        }
        markVisited(visited, cache, false);
        return false;
    }

    private static void markVisited(List<String> visited, Map<String, Boolean> cache, boolean v) {
        for (String s : visited) cache.putIfAbsent(s, v);
    }
}
