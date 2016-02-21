package acmi.l2.clientmod.io;

import java.io.IOException;

public interface IO<T> {
    void writeObject(T obj, DataOutput output) throws IOException;

    void readObject(T obj, DataInput input) throws IOException;
}
