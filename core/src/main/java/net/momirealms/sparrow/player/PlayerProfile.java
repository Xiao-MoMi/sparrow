package net.momirealms.sparrow.player;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.NBT;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.util.ServerLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public record PlayerProfile(
        @NotNull String name,
        long loginAt,
        long logoutAt,
        @Nullable InetAddress ip,
        @Nullable ServerLocation logoutLocation
) {
    public static final String ENTRY = "profile";

    @ApiStatus.Internal
    public static final class Codec implements DataCodec<PlayerProfile> {
        public static final Codec INSTANCE = new Codec();

        @Override
        public byte @NotNull [] encode(@NotNull PlayerProfile profile) {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", profile.name());
            tag.putLong("login_at", profile.loginAt());
            tag.putLong("logout_at", profile.logoutAt());
            if (profile.ip() != null) {
                tag.putByteArray("ip", profile.ip().getAddress());
            }
            if (profile.logoutLocation() != null) {
                tag.put("logout_location", profile.logoutLocation().toNbt());
            }
            return encodeCompound(tag);
        }

        @NotNull
        @Override
        public PlayerProfile decode(byte @NotNull [] bytes) {
            CompoundTag tag = decodeCompound(bytes);
            if (tag.getTagType("name") != Tag.TAG_STRING_ID || tag.getTagType("login_at") != Tag.TAG_LONG_ID || tag.getTagType("logout_at") != Tag.TAG_LONG_ID) {
                throw new IllegalArgumentException("Player profile requires a name and login/logout timestamps");
            }
            if (tag.containsKey("ip") && tag.getTagType("ip") != Tag.TAG_BYTE_ARRAY_ID) {
                throw new IllegalArgumentException("Player profile IP must be a byte array");
            }
            if (tag.containsKey("logout_location") && tag.getTagType("logout_location") != Tag.TAG_COMPOUND_ID) {
                throw new IllegalArgumentException("Player profile logout location must be a compound");
            }
            CompoundTag location = tag.getCompound("logout_location");
            return new PlayerProfile(
                    tag.getString("name"),
                    tag.getLong("login_at"),
                    tag.getLong("logout_at"),
                    decodeAddress(tag.getByteArray("ip")),
                    location == null ? null : ServerLocation.fromNbt(location)
            );
        }

        @Nullable
        public static InetAddress decodeAddress(byte @Nullable [] bytes) {
            if (bytes == null) return null;
            try {
                return InetAddress.getByAddress(bytes);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("Player profile IP requires 4 or 16 bytes", exception);
            }
        }

        public static byte @Nullable [] encodeLocation(@Nullable ServerLocation location) {
            return location == null ? null : encodeCompound(location.toNbt());
        }

        @Nullable
        public static ServerLocation decodeLocation(byte @Nullable [] bytes) {
            return bytes == null ? null : ServerLocation.fromNbt(decodeCompound(bytes));
        }

        private static byte[] encodeCompound(CompoundTag tag) {
            try {
                return NBT.toBytes(tag);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static CompoundTag decodeCompound(byte[] bytes) {
            try {
                CompoundTag tag = NBT.fromBytes(bytes);
                if (tag == null) throw new IllegalArgumentException("Player profile NBT must contain a compound");
                return tag;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}