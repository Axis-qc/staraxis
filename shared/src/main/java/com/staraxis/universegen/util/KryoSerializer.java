package com.staraxis.universegen.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 简易 Kryo 序列化封装，注册常用类后再读写。
 */
public final class KryoSerializer {
    private final Kryo kryo;

    public KryoSerializer() {
        kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 简化示例
    }

    public <T> void write(Path path, T obj) throws IOException {
        try (Output out = new Output(new FileOutputStream(path.toFile()))) {
            kryo.writeClassAndObject(out, obj);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T read(Path path, Class<T> type) throws IOException {
        try (Input in = new Input(new FileInputStream(path.toFile()))) {
            Object o = kryo.readClassAndObject(in);
            return type.cast(o);
        }
    }
}
