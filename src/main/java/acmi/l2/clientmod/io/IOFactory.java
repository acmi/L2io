/*
 * Copyright (c) 2016 acmi
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

import acmi.l2.clientmod.io.annotation.*;
import acmi.l2.clientmod.util.function.IOBiConsumer;
import acmi.l2.clientmod.util.function.IOFunction;
import acmi.l2.clientmod.util.function.IOSupplier;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static acmi.l2.clientmod.util.ReflectionUtil.*;

public class IOFactory<C extends Context> {
    protected Logger log = Logger.getLogger(toString());

    protected final Map<Class, IO> cache = new HashMap<>();

    public IO<Object, C> forClass(Class<?> clazz) {
        if (!cache.containsKey(clazz)) {
            createForClass(clazz);
        }
        return cache.get(clazz);
    }

    protected void createForClass(Class<?> clazz) {
        List<IOBiConsumer<Object, ObjectInput<C>>> read = new ArrayList<>();
        List<IOBiConsumer<Object, ObjectOutput<C>>> write = new ArrayList<>();

        IO<Object, C> io = createIO(clazz, read, write);

        log.fine("Create io for " + clazz);

        cache.put(clazz, io);

        forClass(clazz, read, write);
    }

    protected IO<Object, C> createIO(Class<?> clazz, List<IOBiConsumer<Object, ObjectInput<C>>> read, List<IOBiConsumer<Object, ObjectOutput<C>>> write) {
        return new IO<Object, C>() {
            private IOFunction<ObjectInput<C>, Object> instantiator;
            private IOBiConsumer<Object, ObjectInput<C>> reader;
            private IOBiConsumer<Object, ObjectOutput<C>> writer;

            @Override
            public Object instantiate(ObjectInput<C> input) throws IOException {
                if (instantiator == null) {
                    instantiator = createInstantiator(clazz);
                    log.fine("Create instantiator for " + clazz);
                }
                Object obj = instantiator.apply(input);
                log.fine(this + ".instantiate->" + obj);
                return obj;
            }

            @Override
            public <S> void readObject(S obj, ObjectInput<C> input) throws IOException {
                if (obj == null)
                    return;
                if (reader == null) {
                    reader = createReader(clazz, read);
                    log.fine("Create reader for " + clazz);
                }
                reader.accept(obj, input);
                log.fine(this + ".read->" + obj);
            }

            @Override
            public <S> void writeObject(S obj, ObjectOutput<C> output) throws IOException {
                if (writer == null) {
                    writer = createWriter(clazz, write);
                    log.fine("Create writer for " + clazz);
                }
                writer.accept(obj, output);
                log.fine(this + ".write->" + obj);
            }

            @Override
            public String toString() {
                return "IO[" + clazz + "]";
            }
        };
    }

    protected IOFunction<ObjectInput<C>, Object> createInstantiator(Class<?> clazz) {
        return input -> {
            try {
                Object obj = clazz.newInstance();
                log.fine("IO[" + clazz + "].newInstance->" + obj);
                return obj;
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    protected IOBiConsumer<Object, ObjectInput<C>> createReader(Class<?> clazz, List<IOBiConsumer<Object, ObjectInput<C>>> read) {
        return (obj, input) -> {
            for (IOBiConsumer<Object, ObjectInput<C>> readAction : read) {
                readAction.accept(obj, input);
            }
        };
    }

    protected IOBiConsumer<Object, ObjectOutput<C>> createWriter(Class<?> clazz, List<IOBiConsumer<Object, ObjectOutput<C>>> write) {
        return (obj, output) -> {
            for (IOBiConsumer<Object, ObjectOutput<C>> writeAction : write)
                writeAction.accept(obj, output);
        };
    }

    private <T, U extends T> void forClass(Class<U> clazz, List<IOBiConsumer<T, ObjectInput<C>>> read, List<IOBiConsumer<T, ObjectOutput<C>>> write) {
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            IO<? super T, C> superIO = forClass(clazz.getSuperclass());
            read.add(superIO::readObject);
            write.add(superIO::writeObject);
        }

        List<IOBiConsumer<T, ObjectInput<C>>> read1 = new ArrayList<>();
        List<IOBiConsumer<T, ObjectOutput<C>>> write1 = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!inspectField(field))
                continue;

            handleField(field, read1, write1);
        }

        boolean readMethod = false;
        boolean writeMethod = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ReadMethod.class)) {
                read.add((o, dataInput) -> invokeMethod(method, o, dataInput));
                readMethod = true;
            }

            if (method.isAnnotationPresent(WriteMethod.class)) {
                write.add((o, dataOutput) -> invokeMethod(method, o, dataOutput));
                writeMethod = true;
            }
        }
        if (!readMethod) read.addAll(read1);
        if (!writeMethod) write.addAll(write1);
    }

    protected boolean inspectField(Field field) {
        return !Modifier.isStatic(field.getModifiers()) &&
                !Modifier.isTransient(field.getModifiers()) &&
                !field.isSynthetic();
    }

    protected <T> void handleField(Field field, List<IOBiConsumer<T, ObjectInput<C>>> read1, List<IOBiConsumer<T, ObjectOutput<C>>> write1) {
        field.setAccessible(true);

        Custom custom = field.getAnnotation(Custom.class);
        if (custom != null) {
            if (!cache.containsKey(custom.value())) {
                try {
                    cache.put(custom.value(), custom.value().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            IO<T, C> io = cache.get(custom.value());
            read1.add(io::readObject);
            write1.add(io::writeObject);
        } else {
            io(field.getType(),
                    object -> fieldGet(field, object), (obj, val) -> fieldSet(field, obj, val.get()),
                    field::getAnnotation,
                    read1, write1);
        }
    }

    protected <T> void io(Class type,
                          IOFunction<T, Object> getter, IOBiConsumer<T, IOSupplier> setter,
                          Function<Class<? extends Annotation>, Annotation> getAnnotation,
                          List<IOBiConsumer<T, ObjectInput<C>>> read,
                          List<IOBiConsumer<T, ObjectOutput<C>>> write) {
        if (type == Byte.TYPE || type == Byte.class) {
            read.add((object, dataInput) -> setter.accept(object, () -> (byte) dataInput.readUnsignedByte()));
            write.add((object, dataOutput) -> dataOutput.writeByte(((Byte) getter.apply(object))));
        } else if (type == Short.TYPE || type == Short.class) {
            read.add((object, dataInput) -> setter.accept(object, () -> (short) dataInput.readUnsignedShort()));
            write.add((object, dataOutput) -> dataOutput.writeShort(((Short) getter.apply(object))));
        } else if (type == Integer.TYPE || type == Integer.class) {
            if (getAnnotation.apply(Compact.class) != null) {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readCompactInt));
                write.add((object, dataOutput) -> dataOutput.writeCompactInt(((Integer) getter.apply(object))));
            } else if (getAnnotation.apply(UShort.class) != null) {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readUnsignedShort));
                write.add((object, dataOutput) -> dataOutput.writeShort(((Integer) getter.apply(object))));
            } else if (getAnnotation.apply(UByte.class) != null) {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readUnsignedByte));
                write.add((object, dataOutput) -> dataOutput.writeByte(((Integer) getter.apply(object))));
            } else {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readInt));
                write.add((object, dataOutput) -> dataOutput.writeInt(((Integer) getter.apply(object))));
            }
        } else if (type == Long.TYPE || type == Long.class) {
            read.add((object, dataInput) -> setter.accept(object, dataInput::readLong));
            write.add((object, dataOutput) -> dataOutput.writeLong(((Long) getter.apply(object))));
        } else if (type == Float.TYPE || type == Float.class) {
            read.add((object, dataInput) -> setter.accept(object, dataInput::readFloat));
            write.add((object, dataOutput) -> dataOutput.writeFloat(((Float) getter.apply(object))));
        } else if (type == String.class) {
            if (getAnnotation.apply(UTF.class) != null) {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readUTF));
                write.add((object, dataOutput) -> dataOutput.writeUTF(((String) getter.apply(object))));
            } else {
                read.add((object, dataInput) -> setter.accept(object, dataInput::readLine));
                write.add((object, dataOutput) -> dataOutput.writeLine(((String) getter.apply(object))));
            }
        } else if (type.isArray()) {
            Class componentType = type.getComponentType();
            Length length = (Length) getAnnotation.apply(Length.class);
            IOFunction<DataInput, Integer> lenReader;
            if (length != null) {
                if (length.value() == Length.Type.BYTE)
                    lenReader = DataInput::readUnsignedByte;
                else if (length.value() == Length.Type.INT)
                    lenReader = DataInput::readInt;
                else
                    lenReader = DataInput::readCompactInt;
            } else {
                lenReader = DataInput::readCompactInt;
            }
            IO io = forClass(componentType);
            read.add((object, dataInput) -> {
                Object array = Array.newInstance(componentType, lenReader.apply(dataInput));
                for (int i = 0; i < Array.getLength(array); i++) {
                    int ind = i;
                    List<IOBiConsumer<Object, ObjectInput<C>>> arrayRead = new ArrayList<>();
                    List<IOBiConsumer<Object, ObjectOutput<C>>> arrayWrite = new ArrayList<>();
                    io(componentType, arr -> Array.get(arr, ind), (arr, val) -> Array.set(arr, ind, val.get()), getAnnotation, arrayRead, arrayWrite);
                    for (IOBiConsumer<Object, ObjectInput<C>> ra : arrayRead)
                        ra.accept(array, dataInput);
                }
                setter.accept(object, () -> array);
            });
            write.add((object, dataOutput) -> {
                Object array = getter.apply(object);
                dataOutput.writeCompactInt(Array.getLength(array));
                for (int i = 0; i < Array.getLength(array); i++) {
                    int ind = i;
                    List<IOBiConsumer<Object, ObjectInput<C>>> arrayRead = new ArrayList<>();
                    List<IOBiConsumer<Object, ObjectOutput<C>>> arrayWrite = new ArrayList<>();
                    io(componentType, arr -> Array.get(arr, ind), (arr, val) -> Array.set(arr, ind, val.get()), getAnnotation, arrayRead, arrayWrite);
                    for (IOBiConsumer<Object, ObjectOutput<C>> wa : arrayWrite)
                        wa.accept(array, dataOutput);
                }
            });
        } else {
            IO io = forClass(type);
            read.add((object, dataInput) -> {
                Object obj = io.instantiate(dataInput);
                forClass(obj.getClass()).readObject(obj, dataInput);
                setter.accept(object, () -> obj);
            });
            write.add((object, dataOutput) -> io.writeObject(getter.apply(object), dataOutput));
        }
    }

    public interface IO<T, C extends Context> {
        T instantiate(ObjectInput<C> input) throws IOException;

        <S extends T> void readObject(S obj, ObjectInput<C> input) throws IOException;

        <S extends T> void writeObject(S obj, ObjectOutput<C> output) throws IOException;
    }
}
