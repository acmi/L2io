/*
 * Copyright (c) 2021 acmi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package acmi.l2.clientmod.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.AccessControlException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static acmi.l2.clientmod.io.ByteUtil.*;
import static acmi.l2.clientmod.io.UnrealPackage.ObjectFlag.*;
import static acmi.l2.clientmod.util.CollectionsMethods.indexIf;

@SuppressWarnings("unused")
public class UnrealPackage implements AutoCloseable {
    private static Charset defaultCharset = Charset.forName("EUC-KR");

    static {
        try {
            defaultCharset = Charset.forName(System.getProperty("UnrealPackage.defaultCharset", "EUC-KR"));
        } catch (AccessControlException e) {
            System.err.println(e.getMessage());
        }
    }

    public static Charset getDefaultCharset() {
        return defaultCharset;
    }

    public static void setDefaultCharset(Charset defaultCharset) {
        UnrealPackage.defaultCharset = defaultCharset;
    }

    public static final int UNREAL_PACKAGE_MAGIC = 0x9E2A83C1;

    public static final int VERSION_OFFSET = 0x04;
    public static final int LICENSEE_OFFSET = 0x06;
    public static final int PACKAGE_FLAGS_OFFSET = 0x08;
    public static final int NAME_COUNT_OFFSET = 0x0c;
    public static final int NAME_OFFSET_OFFSET = 0x10;
    public static final int EXPORT_COUNT_OFFSET = 0x14;
    public static final int EXPORT_OFFSET_OFFSET = 0x18;
    public static final int IMPORT_COUNT_OFFSET = 0x1c;
    public static final int IMPORT_OFFSET_OFFSET = 0x20;
    public static final int GUID_OFFSET = 0x24;
    public static final int GENERATIONS_OFFSET = 0x34;

    protected RandomAccess file;

    protected int version;
    protected int license;
    protected int flags;

    protected List<NameEntry> names;
    protected List<ExportEntry> exports;
    protected List<ImportEntry> imports;

    protected UUID uuid;

    protected List<Generation> generations;

    protected int headerEndOffset;

    public UnrealPackage(String path, boolean readOnly) throws UncheckedIOException {
        this(new RandomAccessFile(path, readOnly, defaultCharset));
    }

    public UnrealPackage(File file, boolean readOnly) throws UncheckedIOException {
        this(new RandomAccessFile(file, readOnly, defaultCharset));
    }

    public UnrealPackage(String name, byte[] data) throws UncheckedIOException {
        this(new RandomAccessMemory(name, data, defaultCharset));
    }

    public UnrealPackage(RandomAccess file) throws UncheckedIOException {
        this.file = Objects.requireNonNull(file);

        readHeader();
    }

    protected void readHeader() throws UncheckedIOException {
        file.setPosition(0);

        if (file.readInt() != UNREAL_PACKAGE_MAGIC) {
            throw new UncheckedIOException(new IOException("Not a L2 package file."));
        }

        version = file.readUnsignedShort();
        license = file.readUnsignedShort();
        flags = file.readInt();

        readNameTable();
        readImportTable();
        readExportTable();

        file.setPosition(GUID_OFFSET);
        byte[] uuidBytes = new byte[16];
        file.readFully(uuidBytes);
        uuid = uuidFromBytes(uuidBytes);

        file.setPosition(GENERATIONS_OFFSET);
        int count = file.readInt();
        List<Generation> tmp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tmp.add(new Generation(this, i, file.readInt(), file.readInt()));
        }
        generations = Collections.unmodifiableList(tmp);

        headerEndOffset = file.getPosition();
    }

    public static UnrealPackage create(RandomAccess randomAccess, int version, int license) throws UncheckedIOException {
        byte[] data = new byte[56];

        ByteBuffer upData = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        upData.putInt(UNREAL_PACKAGE_MAGIC);
        upData.putShort((short) version);
        upData.putShort((short) license);
        upData.putInt(1);

        upData.position(GUID_OFFSET);
        upData.put(ByteUtil.uuidToBytes(UUID.randomUUID()));

        randomAccess.setPosition(0);
        randomAccess.writeBytes(data);
        randomAccess.trimToPosition();

        UnrealPackage up = new UnrealPackage(randomAccess);
        up.addNameEntries(Collections.singletonMap("None", 0x04070410));

        return up;
    }

    public RandomAccess getFile() {
        return file;
    }

    public String getPackageName() {
        return getFile().getName();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) throws UncheckedIOException {
        file.setPosition(VERSION_OFFSET);
        file.writeShort(version);

        this.version = version;
    }

    public int getLicense() {
        return license;
    }

    public void setLicense(int license) throws UncheckedIOException {
        file.setPosition(LICENSEE_OFFSET);
        file.writeShort(license);

        this.license = license;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) throws UncheckedIOException {
        file.setPosition(PACKAGE_FLAGS_OFFSET);
        file.writeInt(flags);

        this.flags = flags;
    }

    public UUID getGUID() {
        return uuid;
    }

    public void setGUID(UUID guid) throws UncheckedIOException {
        file.setPosition(GUID_OFFSET);
        file.writeBytes(uuidToBytes(uuid));

        this.uuid = guid;
    }

    public List<NameEntry> getNameTable() {
        return names;
    }

    protected void readNameTable() throws UncheckedIOException {
        file.setPosition(NAME_COUNT_OFFSET);
        int count = file.readInt();
        file.setPosition(getNameTableOffset());

        List<NameEntry> tmp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tmp.add(new NameEntry(this, i, file.readLine(), file.readInt()));
        }

        names = Collections.unmodifiableList(tmp);
    }

    public List<ExportEntry> getExportTable() {
        return exports;
    }

    protected void readExportTable() throws UncheckedIOException {
        file.setPosition(EXPORT_COUNT_OFFSET);
        int count = file.readInt();
        file.setPosition(getExportTableOffset());

        List<ExportEntry> tmp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tmp.add(new ExportEntry(this, i,
                    file.readCompactInt(),
                    file.readCompactInt(),
                    file.readInt(),
                    file.readCompactInt(),
                    file.readInt(),
                    file.readCompactInt(),
                    file.readCompactInt()));
        }

        exports = Collections.unmodifiableList(tmp);
    }

    public List<ImportEntry> getImportTable() {
        return imports;
    }

    protected void readImportTable() throws UncheckedIOException {
        file.setPosition(IMPORT_COUNT_OFFSET);
        int count = file.readInt();
        file.setPosition(getImportTableOffset());

        List<ImportEntry> tmp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tmp.add(new ImportEntry(this, i,
                    file.readCompactInt(),
                    file.readCompactInt(),
                    file.readInt(),
                    file.readCompactInt()));
        }

        imports = Collections.unmodifiableList(tmp);
    }

    @Deprecated
    public UUID getUUID() {
        return uuid;
    }

    @Deprecated
    public void setUUID(UUID uuid) throws UncheckedIOException {
        file.setPosition(GUID_OFFSET);
        file.writeBytes(uuidToBytes(uuid));

        this.uuid = uuid;
    }

    public List<Generation> getGenerations() {
        return generations;
    }

    public String toString() {
        return getPackageName();
    }

    public String nameReference(int index) {
        return getNameTable().get(index).getName();
    }

    public int nameReference(String name) {
        return getNameTable().parallelStream()
                .filter(e -> e.getName().equalsIgnoreCase(name))
                .findAny()
                .map(PackageEntry::getIndex)
                .orElse(-1);
    }

    public Entry objectReference(int ref) {
        if (ref > 0) {
            return getExportTable().get(ref - 1);
        } else if (ref < 0) {
            return getImportTable().get(-ref - 1);
        } else {
            return null;
        }
    }

    public Entry getAt(int index) {
        return objectReference(index);
    }

    public int objectReferenceByName(String name, Predicate<String> classFilter) {
        if (name == null || name.isEmpty()) {
            return 0;
        }

        int ref;

        if ((ref = importReferenceByName(name, classFilter)) != 0) {
            return ref;
        }

        if ((ref = exportReferenceByName(name, classFilter)) != 0) {
            return ref;
        }

        return 0;
    }

    public int importReferenceByName(String name, Predicate<String> classFilter) {
        return getImportTable().parallelStream()
                .filter(entry -> entry.getObjectFullName().equalsIgnoreCase(name))
                .filter(entry -> classFilter.test(entry.getFullClassName()))
                .findAny()
                .map(ImportEntry::getObjectReference)
                .orElse(0);
    }

    public int exportReferenceByName(String name, Predicate<String> classFilter) {
        Optional<Integer> ref = getExportTable().parallelStream()
                .filter(entry -> entry.getObjectFullName().equalsIgnoreCase(name))
                .filter(entry -> classFilter.test(entry.getFullClassName()))
                .findAny()
                .map(ExportEntry::getObjectReference);
        if (!ref.isPresent()) {
            ref = getExportTable().parallelStream()
                    .filter(entry -> entry.getObjectInnerFullName().equalsIgnoreCase(name))
                    .filter(entry -> classFilter.test(entry.getFullClassName()))
                    .findAny()
                    .map(ExportEntry::getObjectReference);
        }
        return ref.orElse(0);
    }

    public void updateNameTable(Consumer<List<UnrealPackage.NameEntry>> transformation) throws UncheckedIOException {
        List<UnrealPackage.NameEntry> nameTable = new ArrayList<>(getNameTable());

        transformation.accept(nameTable);

        int newNameTablePos = getDataEndOffset().orElse(headerEndOffset);
        file.setPosition(newNameTablePos);
        writeNameTable(nameTable);
        int newImportTablePos = file.getPosition();
        writeImportTable(getImportTable());
        int newExportTablePos = file.getPosition();
        writeExportTable(getExportTable());

        file.trimToPosition();

        file.setPosition(NAME_COUNT_OFFSET);
        file.writeInt(nameTable.size());
        file.setPosition(NAME_OFFSET_OFFSET);
        file.writeInt(newNameTablePos);

        file.setPosition(EXPORT_OFFSET_OFFSET);
        file.writeInt(newExportTablePos);

        file.setPosition(IMPORT_OFFSET_OFFSET);
        file.writeInt(newImportTablePos);

        readNameTable();
    }

    public void updateImportTable(Consumer<List<UnrealPackage.ImportEntry>> transformation) throws UncheckedIOException {
        List<UnrealPackage.ImportEntry> importTable = new ArrayList<>(getImportTable());

        transformation.accept(importTable);

        file.setPosition(getImportTableOffset());
        writeImportTable(importTable);
        int newExportTablePos = file.getPosition();
        writeExportTable(getExportTable());
        file.trimToPosition();

        file.setPosition(EXPORT_OFFSET_OFFSET);
        file.writeInt(newExportTablePos);
        file.setPosition(IMPORT_COUNT_OFFSET);
        file.writeInt(importTable.size());

        readImportTable();
    }

    /**
     * Note: transformation must set position to the end of data
     */
    public void updateExportTable(Consumer<List<ExportEntry>> transformation) throws UncheckedIOException {
        file.setPosition(getDataEndOffset().orElse(headerEndOffset));

        List<UnrealPackage.ExportEntry> exportTable = new ArrayList<>(getExportTable());

        transformation.accept(exportTable);

        int nameTablePosition = file.getPosition();
        writeNameTable(getNameTable());
        int importTablePosition = file.getPosition();
        writeImportTable(getImportTable());
        int exportTablePosition = file.getPosition();
        writeExportTable(exportTable);

        file.setPosition(NAME_OFFSET_OFFSET);
        file.writeInt(nameTablePosition);
        file.setPosition(EXPORT_COUNT_OFFSET);
        file.writeInt(exportTable.size());
        file.setPosition(EXPORT_OFFSET_OFFSET);
        file.writeInt(exportTablePosition);
        file.setPosition(IMPORT_OFFSET_OFFSET);
        file.writeInt(importTablePosition);

        readExportTable();
    }

    protected void writeNameTable(List<NameEntry> nameTable) throws UncheckedIOException {
        RandomAccessMemory buffer = new RandomAccessMemory(null, file.getCharset());
        for (NameEntry entry : nameTable) {
            buffer.writeLine(entry.getName());
            buffer.writeInt(entry.getFlags());
        }
        buffer.trimToPosition();
        buffer.writeTo(file);
    }

    protected void writeImportTable(List<ImportEntry> importTable) throws UncheckedIOException {
        RandomAccessMemory buffer = new RandomAccessMemory(null, file.getCharset());
        for (ImportEntry entry : importTable) {
            buffer.writeCompactInt(entry.classPackage);
            buffer.writeCompactInt(entry.className);
            buffer.writeInt(entry.objectPackage);
            buffer.writeCompactInt(entry.objectName);
        }
        buffer.trimToPosition();
        buffer.writeTo(file);
    }

    protected void writeExportTable(List<ExportEntry> exportTable) throws UncheckedIOException {
        RandomAccessMemory buffer = new RandomAccessMemory(null, file.getCharset());
        for (ExportEntry entry : exportTable) {
            buffer.writeCompactInt(entry.objectClass);
            buffer.writeCompactInt(entry.objectSuperClass);
            buffer.writeInt(entry.objectPackage);
            buffer.writeCompactInt(entry.objectName);
            buffer.writeInt(entry.objectFlags);
            buffer.writeCompactInt(entry.size);
            buffer.writeCompactInt(entry.offset);
        }
        buffer.trimToPosition();
        buffer.writeTo(file);
    }

    public int getNameTableOffset() throws UncheckedIOException {
        file.setPosition(NAME_OFFSET_OFFSET);
        return file.readInt();
    }

    public int getExportTableOffset() throws UncheckedIOException {
        file.setPosition(EXPORT_OFFSET_OFFSET);
        return file.readInt();
    }

    public int getImportTableOffset() throws UncheckedIOException {
        file.setPosition(IMPORT_OFFSET_OFFSET);
        return file.readInt();
    }

    public OptionalInt getDataStartOffset() {
        return getExportTable().parallelStream()
                .filter(entry -> entry.getSize() > 0)
                .mapToInt(ExportEntry::getOffset)
                .min();
    }

    public OptionalInt getDataEndOffset() {
        return getDataEndOffset(getExportTable());
    }

    protected static OptionalInt getDataEndOffset(List<ExportEntry> exportTable) {
        return exportTable.parallelStream()
                .filter(entry -> entry.getSize() > 0)
                .mapToInt(entry -> entry.getOffset() + entry.getSize())
                .max();
    }

    @Override
    public void close() throws UncheckedIOException {
        file.close();
    }

    public static final int DEFAULT_NAME_FLAGS = UnrealPackage.ObjectFlag.getFlags(
            TagExp,
            LoadForClient,
            LoadForServer,
            LoadForEdit);
    public static final int DEFAULT_OBJECT_FLAGS = UnrealPackage.ObjectFlag.getFlags(
            Public,
            LoadForClient,
            LoadForServer,
            LoadForEdit);

    public void addNameEntries(String... names) throws UncheckedIOException {
        addNameEntries(Arrays.asList(names));
    }

    public void addNameEntries(List<String> names) throws UncheckedIOException {
        addNameEntries(names.stream().collect(Collectors.toMap(name -> name, name -> DEFAULT_NAME_FLAGS)));
    }

    public void addNameEntries(Map<String, Integer> names) throws UncheckedIOException {
        updateNameTable(nameTable -> names.forEach((k, v) -> {
            UnrealPackage.NameEntry entry = new UnrealPackage.NameEntry(null, 0, k, v);
            if (!nameTable.contains(entry)) {
                nameTable.add(entry);
            }
        }));
    }

    public void updateNameEntry(int index, String newName, int newFlags) throws UncheckedIOException {
        updateNameTable(nameTable -> {
            nameTable.remove(index);
            nameTable.add(index, new UnrealPackage.NameEntry(this, index, newName, newFlags));
        });
    }

    public void addImportEntries(Map<String, String> imports) throws UncheckedIOException {
        Map<String, Integer> namesToAdd = new HashMap<>();
        if (nameReference("Core") == -1) {
            namesToAdd.put("Core", DEFAULT_NAME_FLAGS | Native.getMask());
        }
        if (nameReference("Package") == -1) {
            namesToAdd.put("Package", DEFAULT_NAME_FLAGS | HighlightedName.getMask() | Native.getMask());
        }
        imports.forEach((k, v) -> {
            String[] namePath = k.split("\\.");
            String[] classPath = v.split("\\.");

            Arrays.stream(namePath)
                    .filter(s -> nameReference(s) == -1)
                    .forEach(s -> namesToAdd.put(s, DEFAULT_NAME_FLAGS));
            Arrays.stream(classPath)
                    .filter(s -> nameReference(s) == -1)
                    .forEach(s -> namesToAdd.put(s, DEFAULT_NAME_FLAGS));
        });
        addNameEntries(namesToAdd);

        updateImportTable(importTable -> {
            for (Map.Entry<String, String> entry : imports.entrySet()) {
                String[] namePath = entry.getKey().split("\\.");
                String[] classPath = entry.getValue().split("\\.");

                int pckg = 0;
                UnrealPackage.ImportEntry importEntry;
                for (int i = 0; i < namePath.length - 1; i++) {
                    importEntry = new UnrealPackage.ImportEntry(this, 0,
                            nameReference("Core"),
                            nameReference("Package"),
                            pckg,
                            nameReference(namePath[i]));
                    UnrealPackage.ImportEntry toFind = importEntry;
                    if ((pckg = indexIf(importTable, ie -> toFind.objectPackage == ie.objectPackage &&
                            toFind.objectName == ie.objectName &&
                            toFind.classPackage == ie.classPackage &&
                            toFind.className == ie.className)) == -1) {
                        importTable.add(importEntry);
                        pckg = importTable.size() - 1;
                    }
                    pckg = -(pckg + 1);
                }

                importEntry = new UnrealPackage.ImportEntry(this, 0,
                        nameReference(classPath[0]),
                        nameReference(classPath[1]),
                        pckg,
                        nameReference(namePath[namePath.length - 1]));
                UnrealPackage.ImportEntry toFind = importEntry;
                if (indexIf(importTable, ie -> toFind.objectPackage == ie.objectPackage &&
                        toFind.objectName == ie.objectName &&
                        toFind.classPackage == ie.classPackage &&
                        toFind.className == ie.className) == -1) {
                    importTable.add(importEntry);
                }
            }
        });
    }

    public void renameImport(int index, String importDst) throws UncheckedIOException {
        addImportEntries(
                Collections.singletonMap(importDst, getImportTable().get(index).getFullClassName())
        );

        updateImportTable(importTable -> importTable.set(index, importTable.remove(importTable.size() - 1)));
    }

    public void changeImportClass(int index, String importDst) throws UncheckedIOException {
        String[] clazz = importDst.split("\\.");
        if (clazz.length != 2) {
            throw new IllegalArgumentException("Format: Package.Class");
        }

        addNameEntries(Arrays.stream(clazz)
                .filter(s -> nameReference(s) == -1)
                .collect(Collectors.toList()));

        updateImportTable(importTable -> {
            UnrealPackage.ImportEntry entry = importTable.get(index);
            entry.classPackage = nameReference(clazz[0]);
            entry.className = nameReference(clazz[1]);
        });
    }

    public void addExportEntry(String objectName, String objectClass, String objectSuperClass, byte[] data, int flags) throws UncheckedIOException {
        Map<String, String> classes = new HashMap<>();
        if (objectClass != null && objectReferenceByName(objectClass, IS_CLASS) == 0) {
            classes.put(objectClass, "Core.Class");
        }
        if (objectSuperClass != null && objectReferenceByName(objectSuperClass, IS_CLASS) == 0) {
            classes.put(objectClass, "Core.Class");
        }
        if (!classes.isEmpty()) {
            addImportEntries(classes);
        }

        Map<String, Integer> namesToAdd = new HashMap<>();
        String[] namePath = objectName.split("\\.");
        addNameEntries(Arrays.stream(namePath)
                .filter(s -> nameReference(s) == -1)
                .collect(Collectors.toList()));

        updateExportTable(exportTable -> {
            int pckgInd = importReferenceByName("Core.Package", IS_CLASS);
            byte[] pckgData = compactIntToByteArray(nameReference("None"));
            int pckg = 0;
            UnrealPackage.ExportEntry exportEntry;
            for (int i = 0; i < namePath.length - 1; i++) {
                file.setPosition(findPositionForNewExportEntryData(exportTable, pckgData.length).orElse(headerEndOffset));
                exportEntry = new UnrealPackage.ExportEntry(this,
                        0,
                        pckgInd,
                        0,
                        pckg,
                        nameReference(namePath[i]),
                        DEFAULT_OBJECT_FLAGS,
                        pckgData.length,
                        file.getPosition());
                ExportEntry toFind = exportEntry;
                if ((pckg = indexIf(exportTable, ee ->
                        ee.objectPackage == toFind.objectPackage &&
                                ee.objectName == toFind.objectName)) == -1) {
                    exportTable.add(exportEntry);
                    pckg = exportTable.size() - 1;
                    file.writeBytes(pckgData);
                }
                pckg++;
            }

            file.setPosition(findPositionForNewExportEntryData(exportTable, data.length).orElse(headerEndOffset));
            exportEntry = new UnrealPackage.ExportEntry(this,
                    0,
                    objectReferenceByName(objectClass, IS_CLASS),
                    objectReferenceByName(objectSuperClass, IS_CLASS),
                    pckg,
                    nameReference(namePath[namePath.length - 1]),
                    flags,
                    data.length,
                    file.getPosition());
            UnrealPackage.ExportEntry toFind = exportEntry;
            if (indexIf(exportTable, ee -> ee.objectPackage == toFind.objectPackage &&
                    ee.objectName == toFind.objectName &&
                    ee.objectClass == toFind.objectClass &&
                    ee.objectSuperClass == toFind.objectSuperClass) == -1) {
                exportTable.add(exportEntry);
                file.writeBytes(data);
            }

            file.setPosition(getDataEndOffset(exportTable).orElseThrow(IllegalStateException::new));
        });
    }

    public void renameExport(int index, String nameDst) throws UncheckedIOException {
        String[] namePath = nameDst.split("\\.");
        if (namePath.length > 1 && objectReferenceByName("Core.Package", IS_CLASS) == 0) {
            addImportEntries(Collections.singletonMap("Core.Package", "Core.Class"));
        }
        addNameEntries(Arrays.stream(namePath)
                .filter(s -> nameReference(s) == -1)
                .collect(Collectors.toList()));

        updateExportTable(exportTable -> {
            int pckgInd = objectReferenceByName("Core.Package", IS_CLASS);
            byte[] pckgData = ByteUtil.compactIntToByteArray(nameReference("None"));
            int pckg = 0;
            UnrealPackage.ExportEntry exportEntry;
            for (int i = 0; i < namePath.length - 1; i++) {
                exportEntry = new UnrealPackage.ExportEntry(this,
                        0,
                        pckgInd,
                        0,
                        pckg,
                        nameReference(namePath[i]),
                        Public.getMask() | LoadForServer.getMask() | LoadForEdit.getMask(),
                        pckgData.length, file.getPosition());
                ExportEntry toFind = exportEntry;
                if ((pckg = indexIf(exportTable, ee ->
                        ee.objectPackage == toFind.objectPackage &&
                                ee.objectName == toFind.objectName)) == -1) {
                    exportTable.add(exportEntry);
                    pckg = exportTable.size() - 1;
                    file.writeBytes(pckgData);
                }
                pckg++;
            }
            ExportEntry oldEntry = exportTable.remove(index);
            exportTable.add(index, new ExportEntry(this,
                    0,
                    oldEntry.objectClass,
                    oldEntry.objectSuperClass,
                    pckg,
                    nameReference(namePath[namePath.length - 1]),
                    oldEntry.objectFlags,
                    oldEntry.offset,
                    oldEntry.size
            ));
        });
    }

    public void removeExport(int index) throws UncheckedIOException {
        if (objectReferenceByName("Core.Package", IS_CLASS) == 0) {
            addImportEntries(Collections.singletonMap("Core.Package", "Core.Class"));
        }

        int pckgInd = objectReferenceByName("Core.Package", IS_CLASS);
        byte[] pckgData = compactIntToByteArray(nameReference("None"));

        updateExportTable(exportTable -> {
            ExportEntry entry = exportTable.get(index);
            entry.objectClass = pckgInd;
            entry.objectSuperClass = 0;
            entry.objectFlags = ObjectFlag.getFlags(Public, LoadForClient, LoadForServer, LoadForEdit);
            entry.setObjectRawData(pckgData, false);

            file.setPosition(getDataEndOffset().orElseThrow(IllegalStateException::new));
        });
    }

    protected static OptionalInt findPositionForNewExportEntryData(List<ExportEntry> exportTable, int size) {
        List<ExportEntry> list = new ArrayList<>(exportTable);
        list.sort(Comparator.comparingInt(ExportEntry::getOffset));
        for (int i = 1; i < exportTable.size(); i++) {
            int prevEnd = list.get(i - 1).getOffset() + list.get(i - 1).getSize();
            if (list.get(i).getOffset() - prevEnd >= size) {
                return OptionalInt.of(prevEnd);
            }
        }
        return getDataEndOffset(exportTable);
    }

    protected static final Predicate<String> IS_CLASS = clazz -> clazz.equalsIgnoreCase("Core.Class");

    protected static abstract class PackageEntry<T extends PackageEntry<T>> {
        private final UnrealPackage unrealPackage;
        private final int index;

        protected PackageEntry(UnrealPackage unrealPackage, int index) {
            this.unrealPackage = unrealPackage;
            this.index = index;
        }

        public UnrealPackage getUnrealPackage() {
            return unrealPackage;
        }

        public abstract List<T> getTable();

        public int getIndex() {
            return index;
        }

        public T previous() throws IndexOutOfBoundsException {
            return getTable().get(getIndex() - 1);
        }

        public T next() throws IndexOutOfBoundsException {
            return getTable().get(getIndex() + 1);
        }
    }

    public static final class Generation extends PackageEntry<Generation> {
        private final int exportCount;
        private final int importCount;

        public Generation(UnrealPackage unrealPackage, int index, int exportCount, int importCount) {
            super(unrealPackage, index);
            this.exportCount = exportCount;
            this.importCount = importCount;
        }

        public int getExportCount() {
            return exportCount;
        }

        public int getNameCount() {
            return importCount;
        }

        @Override
        public List<Generation> getTable() {
            return getUnrealPackage().getGenerations();
        }

        @Override
        public String toString() {
            return "Generation[" +
                    "exportCount=" + exportCount +
                    ", importCount=" + importCount +
                    ']';
        }
    }

    public static final class NameEntry extends PackageEntry<NameEntry> {
        private final String name;
        private final int flags;

        public NameEntry(UnrealPackage unrealPackage, int index, String name, int flags) {
            super(unrealPackage, index);
            this.name = Objects.requireNonNull(name);
            this.flags = flags;
        }

        public String getName() {
            return name;
        }

        public int getFlags() {
            return flags;
        }

        @Override
        public List<NameEntry> getTable() {
            return getUnrealPackage().getNameTable();
        }

        public String toString() {
            return name;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if ((o == null) || (getClass() != o.getClass())) {
                return false;
            }
            NameEntry nameEntry = (NameEntry) o;

            return name.equalsIgnoreCase(nameEntry.name);
        }

        public int hashCode() {
            return name.hashCode();
        }

        public NameEntry previous() {
            return getUnrealPackage().getNameTable().get(getIndex() - 1);
        }

        public NameEntry next() {
            return getUnrealPackage().getNameTable().get(getIndex() + 1);
        }
    }

    public static abstract class Entry<T extends Entry<T>> extends PackageEntry<T> {
        protected final int objectPackage;
        protected final int objectName;

        private Reference<String> innerName = new SoftReference<>(null);

        protected Entry(UnrealPackage unrealPackage, int index, int objectPackage, int objectName) {
            super(unrealPackage, index);
            this.objectPackage = objectPackage;
            this.objectName = objectName;
        }

        public Entry getObjectPackage() {
            return getUnrealPackage().objectReference(objectPackage);
        }

        public NameEntry getObjectName() {
            return getUnrealPackage().getNameTable().get(objectName);
        }

        public String getObjectInnerFullName() {
            String str = innerName.get();
            if (str == null) {
                Entry pckg = getObjectPackage();
                str = pckg == null ? getObjectName().getName() : pckg.getObjectInnerFullName() + '.' + getObjectName().getName();
                innerName = new SoftReference<>(str);
            }
            return str;
        }

        public String getObjectFullName() {
            return getObjectInnerFullName();
        }

        public abstract String getFullClassName();

        public abstract int getObjectReference();

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Entry)) {
                return false;
            }

            Entry entry = (Entry) o;

            return getObjectFullName().equalsIgnoreCase(entry.getObjectFullName()) &&
                    getFullClassName().equalsIgnoreCase(entry.getFullClassName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getObjectFullName(), getFullClassName());
        }

        public String toString() {
            return getObjectFullName();
        }
    }

    public static final class ExportEntry extends Entry<ExportEntry> {
        private static boolean eraseUnusedSpace = false;

        static {
            try {
                eraseUnusedSpace = Boolean.parseBoolean(System.getProperty("UnrealPackage.ExportEntry.eraseUnusedSpace", "false"));
            } catch (AccessControlException e) {
                System.err.println(e.getMessage());
            }
        }

        private int objectClass;
        private int objectSuperClass;
        private int objectFlags;
        private int size;
        private int offset;

        private Reference<String> fullName = new SoftReference<>(null);

        public ExportEntry(UnrealPackage unrealPackage, int index, int objectClass, int objectSuperClass, int objectPackage, int objectName, int objectFlags, int size, int offset) {
            super(unrealPackage, index, objectPackage, objectName);
            this.objectClass = objectClass;
            this.objectSuperClass = objectSuperClass;
            this.objectFlags = objectFlags;
            this.size = size;
            this.offset = offset;
        }

        @Override
        public int getObjectReference() {
            return getIndex() + 1;
        }

        public Entry getObjectClass() {
            return getUnrealPackage().objectReference(objectClass);
        }

        public Entry getObjectSuperClass() {
            return getUnrealPackage().objectReference(objectSuperClass);
        }

        @Override
        public String getFullClassName() {
            return getObjectClass() != null ? getObjectClass().getObjectFullName() : "Core.Class";
        }

        public int getObjectFlags() {
            return objectFlags;
        }

        public int getSize() {
            return size;
        }

        public int getOffset() {
            return offset;
        }

        public byte[] getObjectRawData() throws UncheckedIOException {
            if (getSize() == 0) {
                return new byte[0];
            }

            byte[] raw = new byte[size];
            getUnrealPackage().file.setPosition(offset);
            getUnrealPackage().file.readFully(raw);
            return raw;
        }

        public byte[] getObjectRawDataExternally() throws UncheckedIOException {
            if (getSize() == 0) {
                return new byte[0];
            }

            try (RandomAccess ra = getUnrealPackage().getFile().openNewSession(true)) {
                byte[] data = new byte[getSize()];
                ra.setPosition(getOffset());
                ra.readFully(data);
                return data;
            }
        }

        public void setObjectRawData(byte[] data) throws UncheckedIOException {
            setObjectRawData(data, true);
        }

        public void setObjectRawData(byte[] data, boolean writeExportTable) throws UncheckedIOException {
            if (data.length <= getSize()) {
                getUnrealPackage().file.setPosition(getOffset());
                getUnrealPackage().file.writeBytes(data);
                if (data.length != getSize()) {
                    size = data.length;

                    if (writeExportTable) {
                        getUnrealPackage().file.setPosition(getUnrealPackage().getExportTableOffset());
                        getUnrealPackage().writeExportTable(getUnrealPackage().getExportTable());
                    }
                }
            } else {
                if (eraseUnusedSpace) {
                    getUnrealPackage().file.setPosition(getOffset());
                    getUnrealPackage().file.writeBytes(new byte[getSize()]);
                }

                boolean isLast = getUnrealPackage().getExportTable()
                        .parallelStream()
                        .filter(entry -> entry.getSize() > 0)
                        .mapToInt(ExportEntry::getOffset)
                        .max()
                        .orElseThrow(IllegalStateException::new) == offset;
                int newOffset = isLast ? offset :
                        findPositionForNewExportEntryData(getUnrealPackage().getExportTable(), data.length).orElseThrow(IllegalStateException::new);
                getUnrealPackage().file.setPosition(newOffset);
                offset = getUnrealPackage().file.getPosition();
                size = data.length;
                getUnrealPackage().file.writeBytes(data);

                getUnrealPackage().file.setPosition(getUnrealPackage().getDataEndOffset().orElseThrow(IllegalStateException::new));
                int nameTablePosition = getUnrealPackage().file.getPosition();
                getUnrealPackage().writeNameTable(getUnrealPackage().getNameTable());
                int importTablePosition = getUnrealPackage().file.getPosition();
                getUnrealPackage().writeImportTable(getUnrealPackage().getImportTable());
                int exportTablePosition = getUnrealPackage().file.getPosition();
                getUnrealPackage().writeExportTable(getUnrealPackage().getExportTable());

                getUnrealPackage().file.setPosition(NAME_OFFSET_OFFSET);
                getUnrealPackage().file.writeInt(nameTablePosition);
                getUnrealPackage().file.setPosition(EXPORT_OFFSET_OFFSET);
                getUnrealPackage().file.writeInt(exportTablePosition);
                getUnrealPackage().file.setPosition(IMPORT_OFFSET_OFFSET);
                getUnrealPackage().file.writeInt(importTablePosition);
            }
        }

        @Override
        public String getObjectFullName() {
            String str = fullName.get();
            if (str == null) {
                str = getUnrealPackage().getPackageName() + "." + getObjectInnerFullName();
                fullName = new SoftReference<>(str);
            }
            return str;
        }

        @Override
        public List<ExportEntry> getTable() {
            return getUnrealPackage().getExportTable();
        }

        @Override
        public String toString() {
            return getObjectInnerFullName();
        }
    }

    public static final class ImportEntry extends Entry<ImportEntry> {
        private int classPackage;
        private int className;

        private Reference<String> fullClassName = new SoftReference<>(null);

        public ImportEntry(UnrealPackage unrealPackage, int index, int classPackage, int className, int objectPackage, int objectName) {
            super(unrealPackage, index, objectPackage, objectName);
            this.classPackage = classPackage;
            this.className = className;
        }

        @Override
        public int getObjectReference() {
            return -(getIndex() + 1);
        }

        public NameEntry getClassPackage() {
            return getUnrealPackage().getNameTable().get(classPackage);
        }

        public NameEntry getClassName() {
            return getUnrealPackage().getNameTable().get(className);
        }

        @Override
        public String getFullClassName() {
            String str = fullClassName.get();
            if (str == null) {
                NameEntry pckg = getClassPackage();
                str = pckg == null ? getClassName().getName() : pckg.getName() + '.' + getClassName().getName();
                fullClassName = new SoftReference<>(str);
            }
            return str;
        }

        @Override
        public List<ImportEntry> getTable() {
            return getUnrealPackage().getImportTable();
        }
    }

    public enum ObjectFlag {
        /**
         * Object is transactional.
         */
        Transactional,
        /**
         * Object is not reachable on the object graph.
         */
        Unreachable,
        /**
         * Object is visible outside its package.
         */
        Public,
        /**
         * Temporary import tag in load/save.
         */
        TagImp,
        /**
         * Temporary export tag in load/save.
         */
        TagExp,
        /**
         * Modified relative to source files.
         */
        SourceModified,
        /**
         * Check during garbage collection.
         */
        TagGarbage,
        Private,
        Flag8,
        /**
         * During load, indicates object needs loading.
         */
        NeedLoad,
        /**
         * A hardcoded name which should be syntaxhighlighted.
         */
        HighlightedName,
        /**
         * In a singular function.
         */
        InSingularFunc,
        /**
         * Suppressed log name.
         */
        Suppress,
        /**
         * Within an EndState call.
         */
        InEndState,
        /**
         * Don't save object.
         */
        Transient,
        /**
         * Data is being preloaded from file.
         */
        PreLoading,
        /**
         * In-file load for client.
         */
        LoadForClient,
        /**
         * In-file load for client.
         */
        LoadForServer,
        /**
         * In-file load for client.
         */
        LoadForEdit,
        /**
         * Keep object around for editing even if unreferenced.
         */
        Standalone,
        /**
         * Don't load this object for the game client.
         */
        NotForClient,
        /**
         * Don't load this object for the game server.
         */
        NotForServer,
        /**
         * Don't load this object for the editor.
         */
        NotForEdit,
        /**
         * Object Destroy has already been called.
         */
        Destroyed,
        /**
         * Object needs to be postloaded.
         */
        NeedPostLoad,
        /**
         * Has execution stack.
         */
        HasStack,
        /**
         * Native (UClass only).
         */
        Native,
        /**
         * Marked (for debugging).
         */
        Marked,
        /**
         * ShutdownAfterError called.
         */
        ErrorShutdown,
        /**
         * For debugging Serialize calls.
         */
        DebugPostLoad,
        /**
         * For debugging Serialize calls.
         */
        DebugSerialize,
        /**
         * For debugging Destroy calls.
         */
        DebugDestroy;

        private final int mask;

        ObjectFlag() {
            this.mask = 1 << ordinal();
        }

        public int getMask() {
            return mask;
        }

        @Override
        public String toString() {
            return "RF_" + name();
        }

        public static Collection<ObjectFlag> getFlags(int flags) {
            return Arrays.stream(values())
                    .filter(e -> (e.getMask() & flags) != 0)
                    .collect(Collectors.toList());
        }

        public static int getFlags(ObjectFlag... flags) {
            int v = 0;
            for (ObjectFlag flag : flags) {
                v |= flag.getMask();
            }
            return v;
        }
    }

    public enum PackageFlag {
        /**
         * Allow downloading package
         */
        AllowDownload(0),
        /**
         * Purely optional for clients
         */
        ClientOptional(1),
        /**
         * Only needed on the server side
         */
        ServerSideOnly(2),
        /**
         * Loaded from linker with broken import links
         */
        BrokenLinks(3),
        /**
         * Not trusted
         */
        Unsecure(4),
        /**
         * Client needs to download this package
         */
        Need(15);

        private final int mask;

        PackageFlag(int bit) {
            this.mask = 1 << bit;
        }

        public int getMask() {
            return mask;
        }

        @Override
        public String toString() {
            return "PKG_" + name();
        }

        public static Collection<PackageFlag> getFlags(int flags) {
            return Arrays.stream(values())
                    .filter(e -> (e.getMask() & flags) != 0)
                    .collect(Collectors.toList());
        }

        public static int getFlags(PackageFlag... flags) {
            int v = 0;
            for (PackageFlag flag : flags) {
                v |= flag.getMask();
            }
            return v;
        }
    }
}
