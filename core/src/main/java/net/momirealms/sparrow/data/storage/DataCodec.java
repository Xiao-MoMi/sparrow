package net.momirealms.sparrow.data.storage;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/** 业务空值不会进入编码器. 键的编码必须稳定, 解码后的值供调用方只读使用. */
public interface DataCodec<T> {

    byte @NotNull [] encode(@NotNull T value);

    @NotNull
    T decode(byte @NotNull [] bytes);

    @NotNull
    static <T> DataCodec<T> of(@NotNull Function<T, byte[]> encoder, @NotNull Function<byte[], T> decoder) {
        return new DataCodec<>() {
            @Override
            public byte @NotNull [] encode(@NotNull T value) {
                return encoder.apply(value);
            }

            @NotNull
            @Override
            public T decode(byte @NotNull [] bytes) {
                return decoder.apply(bytes);
            }
        };
    }
}