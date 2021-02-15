l2io
====
![GitHub](https://img.shields.io/github/license/acmi/l2io)
[![](https://jitpack.io/v/acmi/l2io.svg)](https://jitpack.io/#acmi/l2io)

Lineage 2 client files I/O library.

Usage
-----
```java
import acmi.l2.clientmod.io.RandomAccess;
import acmi.l2.clientmod.io.UnrealPackage;
import java.io.File;
import java.nio.ByteBuffer;


File l2Folder = new File("C:\\Lineage 2");
File pckg = new File(new File(l2Folder, "system"), "Engine.u");
String entryName = "Actor.ScriptText";

try (UnrealPackage up = new UnrealPackage(pckg, true)) {
    UnrealPackage.ExportEntry entry = up.getExportTable()
            .stream()
            .filter(e -> e.getObjectInnerFullName().equalsIgnoreCase(entryName))
            .findAny()
            .orElseThrow(() -> new IllegalStateException("Entry not found"));
    byte[] raw = entry.getObjectRawData();
    RandomAccess buffer = RandomAccess.randomAccess(ByteBuffer.wrap(raw), null, up.getFile().getCharset(), entry.getOffset());
    buffer.readCompactInt(); //empty properties
    buffer.readInt();        //pos
    buffer.readInt();        //top
    String text = buffer.readLine();
    System.out.println(text);
}
```

Build
-----
```
gradlew build
```
Append `-x test` to skip tests.

Install to local maven repository
---------------------------------
```
gradlew install
```

Maven
-----
```maven
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.acmi</groupId>
    <artifactId>l2io</artifactId>
    <version>2.2.6</version>
</dependency>
```

Gradle
------
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compile group:'com.github.acmi', name:'l2io', version: '2.2.6'
}
```