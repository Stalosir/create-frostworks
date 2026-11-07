package com.frostworks.room.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The saved form of the rooms of one dimension. Pure Java, so that the exact bytes written into the
 * world are tested by JUnit.
 * <p>
 * Format (version {@value #VERSION}), big-endian: int version, int count, then per room: int id, UTF status,
 * float temperature (deg C), int cold target (deg C, version 3), int n + n longs (cells, {@link PosKey} packed),
 * int m + m longs (vents). 20 000 cells take 160 kB before the world file's own compression. Read back: version 2 had a
 * boolean for the cold liquid (before its rename to Frosium), read as -20 deg C, else -10; version 1 had nothing,
 * read as -10.
 */
public final class RoomRecords {
    private RoomRecords() {}

    public static final int VERSION = 3;

    /** The target of a COLD room on cold gas, as the versions before 1.1 had it (deg C). */
    public static final int DEFAULT_COLD_TARGET = -10;
    /** The target of a room on Frosium alone, as version 2 saved it (its Frosium flag). */
    public static final int FROSIUM_COLD_TARGET = -20;

    /** One saved room; {@code coldTarget}: its target while COLD (deg C), from the cold fluids of its vents. */
    public record Record(int id, RoomStatus status, float temperature, int coldTarget, long[] cells, long[] vents) {
        public Record(int id, RoomStatus status, float temperature, long[] cells, long[] vents) {
            this(id, status, temperature, DEFAULT_COLD_TARGET, cells, vents);
        }
    }

    public static byte[] encode(List<Record> records) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(records.size());
            for (Record r : records) {
                out.writeInt(r.id());
                out.writeUTF(r.status().name());
                out.writeFloat(r.temperature());
                out.writeInt(r.coldTarget());
                writeLongs(out, r.cells());
                writeLongs(out, r.vents());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("cannot encode rooms", e);
        }
    }

    /** The rooms saved in {@code data}; an empty list for no data or an unknown version (the scan rebuilds them). */
    public static List<Record> decode(byte[] data) {
        List<Record> records = new ArrayList<>();
        if (data == null || data.length == 0)
            return records;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version < 1 || version > VERSION)
                return records;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                RoomStatus status = RoomStatus.valueOf(in.readUTF());
                float temperature = in.readFloat();
                int coldTarget = version >= 3 ? in.readInt()
                        : version == 2 && in.readBoolean() ? FROSIUM_COLD_TARGET : DEFAULT_COLD_TARGET;
                long[] cells = readLongs(in);
                long[] vents = readLongs(in);
                records.add(new Record(id, status, temperature, coldTarget, cells, vents));
            }
            return records;
        } catch (IOException | IllegalArgumentException e) {
            return new ArrayList<>(); // damaged data: the rooms are searched again from scratch
        }
    }

    private static void writeLongs(DataOutputStream out, long[] values) throws IOException {
        out.writeInt(values.length);
        for (long v : values)
            out.writeLong(v);
    }

    private static long[] readLongs(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 10_000_000)
            throw new IOException("bad length " + n);
        long[] values = new long[n];
        for (int i = 0; i < n; i++)
            values[i] = in.readLong();
        return values;
    }
}
