package org.nanonative.plugin.nanosvc;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Generates META-INF/plugin/nano/services.index (module + dependencies) containing all concrete subclasses of the configured base Service type.
 * Stores subclass in memory and checks to avoid re-walking super chains.
 * Skips writing the index if content didn't change.
 * Bails early if the base type isn't present on the classpath.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class CodegenNanoSvcList extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "codegenSvcList.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * The base Service location. Default matches the path it is on today.
     * If Nano Service relocates, we can override with -DcodegenSvcList.nanoService=org/nanonative/nano/newPackage/Service
     */
    @Parameter(property = "codegenSvcList.baseService", defaultValue = "org/nanonative/nano/core/model/Service")
    private String baseNanoService;

    private static final String OUTPUT_PATH = "META-INF/plugin/nano/services.index";
    private static final String BASE_JAVA_CLASS = "java/lang/Object";

    @Override
    public void execute() throws MojoExecutionException {
        final Map<String, Boolean> cache = new HashMap<>();       // Cache already iterated paths
        final Set<String> services = new HashSet<>();             // services matched
        final Map<String, ClassHeader> headers = new HashMap<>(); // Headers for each class

        try {
            final Path classesDir = Path.of(project.getBuild().getOutputDirectory());
            if (!Files.isDirectory(classesDir)) {
                log("[codegen-svc-list] No classes dir (skipping): " + classesDir, 'I');
                return;
            }

            scanDirectory(classesDir, headers);
            for (Artifact artifact : project.getArtifacts()) {
                File jar = artifact.getFile();
                if (jar != null && jar.isFile() && "jar".equals(artifact.getType())) {
                    scanJar(jar, headers);
                }
            }

            log("[codegen-svc-list] headers size = " + headers.size(), 'I');
            log("[codegen-svc-list] headers = " + headers, 'I');

            // Early bail if the Nano service is not on the classpath
            if (!headers.containsKey(baseNanoService)) {
                log("[codegen-svc-list] Base type not found on classpath: " + baseNanoService, 'I');
                writeServiceList(classesDir, List.of());
                return;
            }

            for (var e : headers.entrySet()) {
                String className = e.getKey();
                ClassHeader header = e.getValue();
                if (header.isInterface() || header.isAbstract())
                    continue;

                if (isSubclassOfBase(className, headers, cache, baseNanoService)) {
                    services.add(className.replace('/', '.'));
                }
            }
            log("[codegen-svc-list] services found = " + services.size(), 'I');
            writeServiceList(classesDir, services);
        } catch (Exception ex) {
            log("[codegen-svc-list] Exception occurred: " + ex, 'E');
            throw new MojoExecutionException("codegen-svc-list failed", ex);
        }
    }

    private void scanDirectory(Path root, Map<String, ClassHeader> out) throws IOException {

        try (var stream = Files.walk(root)) {
            var it = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .iterator();
            while (it.hasNext()) {
                Path p = it.next();
                String internal = root.relativize(p).toString().replace('\\', '/');
                try (InputStream in = Files.newInputStream(p)) {
                    out.put(formatKey(internal), ClassHeader.read(in));
                }
            }
        }
    }

    private void scanJar(File jar, Map<String, ClassHeader> out) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class"))
                    continue;
                try (InputStream in = jf.getInputStream(e)) {
                    out.putIfAbsent(formatKey(name), ClassHeader.read(in));
                }
            }
        }
    }

    private boolean isSubclassOfBase(String internal, final Map<String, ClassHeader> headers,
                                     final Map<String, Boolean> cache, final String baseInternal) {
        // Check if parsed before
        Boolean cachedResult = cache.get(internal);
        if (null != cachedResult)
            return cachedResult;

        // get header and fail fast
        ClassHeader h = headers.get(internal);
        if (null == h || h.isInterface() || h.isAbstract()) {
            cache.put(internal, false);
            return false;
        }
        String cur = h.superInternalName();

        // walk up the path
        final List<String> visited = new ArrayList<>();
        visited.add(internal);

        for (int hops = 0; null != cur && hops <= 256; hops++) {
            if (baseInternal.equals(cur)) {
                markVisited(visited, cache, true);
                return true;
            }
            if (BASE_JAVA_CLASS.equals(cur)) {
                markVisited(visited, cache, false);
                return false;
            }

            Boolean cacheCur = cache.get(cur);
            if (null != cacheCur) {
                markVisited(visited, cache, cacheCur);
                return cacheCur;
            }

            // advance one step
            ClassHeader sup = headers.get(cur);
            if (null == sup || sup.isInterface() || sup.isAbstract()) {
                markVisited(visited, cache, false);
                return false;
            }
            visited.add(cur);
            cur = sup.superInternalName();
        }
        markVisited(visited, cache, false);
        return false;
    }

    private void writeServiceList(Path classesDir, Collection<String> services) throws IOException {
        Path outputPath = classesDir.resolve(OUTPUT_PATH);
        Path parent = outputPath.getParent();
        if (null == parent)
            return;

        Files.createDirectories(parent);
        String newContent = String.join("\n", services);
        String oldContent = Files.exists(outputPath) ? Files.readString(outputPath, StandardCharsets.UTF_8) : null;
        if (newContent.equals(oldContent)) {
            log("[codegen-svc-list] unchanged - skipping", 'I');
            return;
        }

        Path tmp = Files.createTempFile(parent, "services", ".tmp");
        try {
            Files.writeString(tmp, newContent, StandardCharsets.UTF_8);
            log("[codegen-svc-list] Copying tmp file to actual destination", 'I');
            Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log("[codegen-svc-list] output service count = " + services.size(), 'I');
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void markVisited(final List<String> visited, final Map<String, Boolean> cache, final boolean isNanoService) {
        for (String v : visited)
            cache.put(v, isNanoService);
    }

    private void log(String msg, char level) {
        if ('E' == level) {
            getLog().error(msg);
        } else if (verbose) {
            switch (level) {
                case 'I' -> getLog().info(msg);
                case 'W' -> getLog().warn(msg);
                case 'D' -> getLog().debug(msg);
            }
        }
    }

    // remove .class from key names in header
    private String formatKey(String className) {
        return className.substring(0, className.length() - 6);
    }
}
