# Class Implementation Index Maven Plugin

Generate a **`.properties`** index of all **concrete implementations** of one or more abstract base types (interfaces/abstract classes) found in **your project and its dependencies**.

By default, it writes to:
```
classes/META-INF/io/github/absketches/plugin/services.properties
```
Each **key** is a dotted base class; each **value** is a comma‑separated list of dotted implementation classes.

Example output:
```properties
org.nanonative.nano.core.model.Service=org.nanonative.devconsole.service.DevConsoleService,org.nanonative.nano.services.http.HttpServer,org.ab.sentinel.service.PostgreSqlService
```
---

## Features

- ⚡  **Fast**: parses only .class headers (no ASM, no classloading).
- 📦 **Works across dependencies**: current module + resolved JARs.
- 🧹 **Memoization**: stores traversed superclass paths in memory to avoid rework.
- 🔒 **Zero intrusion**: developers don’t add annotations or code.

---

## Requirements

- **Java** 21+
- **Maven** 3.9.11+

---

## Quickstart

```xml
<plugin>
  <groupId>io.github.absketches</groupId>
  <artifactId>codegen-concrete-classes-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <id>impl-index</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <baseServices>org.nanonative.nano.core.model.Service,io.github.absketches.sentinel.Notification</baseServices>
        <outputDir>META-INF/io/github/absketches/plugin/services.properties</outputDir>
        <usePrecompiled>true</usePrecompiled>
        <verbose>false</verbose>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Invoke:
```
mvn codegen-concrete-classes:generate
```

---

## Configuration properties (prefix: `codegenConcreteClass.*`)

> Use **dot-notation** for class names in the configuration.

### `codegenConcreteClass.baseServices` (String) — **required**
Comma‑separated list of abstract base types (interfaces/abstract classes) whose concrete implementations you want to index?
- **Default:** `org.nanonative.nano.core.model.Service`
- **Example:** `io.github.absketches.sentinel.Notification`

### `codegenConcreteClass.outputDir` (String)
Path (relative to `${project.build.outputDirectory}`) of the generated file.
- **Default:** `META-INF/io/github/absketches/plugin/services.properties`

### `codegenConcreteClass.usePrecompiled` (boolean)
If `true`, when a dependency JAR already contains a properties file at the same `outputDir`, those entries are **merged** in. If a JAR’s precomputed file is **missing** some configured bases, that JAR is **scanned** to fill gaps.
- **Default:** `true`

### `codegenConcreteClass.verbose` (boolean)
Enable extra logging.
- **Default:** `false`

---

## How it works
1. Parses `baseClasses` (dotted) and converts to JVM internal form.
2. Scans your module’s class files and builds a header map.
3. For each dependency JAR:
    - If `usePrecompiled=true` and a properties file exists at `outputDir`, it is read and filtered to your configured base types.
    - If the file is absent or **incomplete**, the JAR is scanned to populate headers.
4. For each base type, the plugin unions **precomputed** + **discovered** implementations and writes the final properties file atomically.

---

## Troubleshooting

**No `services.properties` in the JAR**

- The plugin isn’t bound to that module.
- The module didn’t produce classes (empty sources or skipped compile).

**Empty `services.properties`**

- `baseClasses` Check if baseClasses location has changed or does the module have any implementations:
- Get verbose logging output using -DcodegenConcreteClass.verbose=true

**Step-through debug**
Run this in the IDE of your project which includes this plugin:

```bash
mvnDebug process-classes -DcodegenConcreteClass.verbose=true
# Attach the debugger of the plugin project (this) to localhost:8000
```

---

## Contract

- Only **concrete** subclasses are listed.
- Scans **compile + runtime** classpath.

---

