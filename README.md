# Maven plugin for generating available Nano services in consumer projects

Generates a compile-time index of all **concrete subclasses** of your Nano `Service` — from **the current module and its dependencies** — and package it into your JAR at:
```
META-INF/plugin/nano/services.index
```

Each line in the file is a fully-qualified class name. No reflection, no runtime scanning — just a fast header scan at build time.

---

## Features

- ⚡  **Fast**: parses only .class headers (no ASM, no classloading).
- 📦 **Works across dependencies**: current module + resolved JARs.
- 🧹 **Deterministic**: runs at `process-classes`, writes only if content changed.
- 🔒 **Zero intrusion**: developers don’t add annotations or code.

---

## Requirements

- **Java** 21+
- **Maven** 3.9.11+

---

## Quick start (consumer project)

Add the plugin to any module that produces a JAR:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.nanonative</groupId>
      <artifactId>codegen-svc-list-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <id>nano-service-index</id>
          <phase>process-classes</phase>
          <goals>
            <goal>generate</goal>
          </goals>
          <!-- Optional: If Nano's service class moves from the default add the below config to point to the new location -->
          <configuration>
            <baseService>org/nanonative/nano/core/model/Service</baseService>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Build and verify (for verbose logging set the flag -DcodegenSvcList.verbose=true):

```bash
mvn clean package -DcodegenSvcList.verbose=true
```

---

## Configuration

| Parameter       | Type    | Default                                    | How to set                                   |
|-----------------|---------|--------------------------------------------|-----------------------------------------------|
| `baseService`   | String  | `org.nanonative.nano.core.model.Service`   | `<configuration>` or `-DcodegenSvcList.baseService=...` |
| `verbose`       | boolean | `false`                                    | `-DcodegenSvcList.verbose=true`               |


See goal details:

```bash
mvn help:describe   -Dplugin=org.nanonative:codegen-svc-list-maven-plugin:1.0.0   -Dgoal=generate -Ddetail
```

---

## How it works

- Walks `target/classes` and enumerates dependency JARs.
- Reads only the **classfile header** (`0xCAFEBABE`, version, constant pool, `access_flags`, `super_class`).  
  Implementation uses `DataInputStream` for reads.
- Follows superclass chains with memoization.
- Writes `META-INF/plugin/nano/services.index` **only if content changed**.

---

## Multi-module builds

Bind the plugin in **every module that produces a JAR** and needs an index.

Build a specific module (and its dependencies):

```bash
mvn -pl :devconsole -am clean package -DcodegenSvcList.verbose=true
```

---

## Troubleshooting

**No `services.index` in the JAR**
- The plugin isn’t bound to that module.
- The module didn’t produce classes (empty sources or skipped compile).

**Empty `services.index`**
- `baseService` Check if baseService location has changed or does the module have any Nano services:
- Get verbose logging output using -DcodegenSvcList.verbose=true

**Step-through debug**
Run this in the IDE of your project which includes this plugin:
```bash
mvnDebug process-classes -DcodegenSvcList.verbose=true
# Attach the debugger of the plugin project (this) to localhost:8000
```

---

## Contract

- Output path is **fixed**: `META-INF/plugin/nano/services.index` (consumers rely on this).
- Only **concrete** subclasses are listed.
- Scans **compile + runtime** classpath.

---

