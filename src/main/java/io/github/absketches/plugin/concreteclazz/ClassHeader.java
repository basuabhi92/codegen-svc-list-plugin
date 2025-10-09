package io.github.absketches.plugin.concreteclazz;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Header: access flags and super internal name.
 */
record ClassHeader(int accessFlags, String superInternalName) {
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ABSTRACT = 0x0400;

    boolean isInterface() {
        return (accessFlags & ACC_INTERFACE) != 0;
    }

    boolean isAbstract() {
        return (accessFlags & ACC_ABSTRACT) != 0;
    }

    static ClassHeader read(InputStream raw) throws IOException {

        final DataInputStream in = (raw instanceof DataInputStream dis) ? dis : new DataInputStream(raw);

        if (in.readInt() != 0xCAFEBABE)
            throw new IOException("Corrupt stream - magic number missing");

        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major

        final int cpCount = in.readUnsignedShort();
        final String[] utf8 = new String[cpCount];     // tag=1
        final int[] classNameIndex = new int[cpCount]; // tag=7 -> name_index

        for (int i = 1; i < cpCount; i++) {
            final int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();                                // Utf8
                case 3, 4 -> in.skipNBytes(4);                                // int/float
                case 5, 6 -> {                                                   // long/double (2 slots)
                    in.skipNBytes(8);
                    i++;
                }
                case 7 -> classNameIndex[i] = in.readUnsignedShort();         // Class -> name_index (u2)
                case 8 -> in.readUnsignedShort();                             // String -> string_index
                case 9, 10, 11, 12, 18 -> {
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                } // refs / name&type / indy
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                } // MethodHandle
                case 16, 19, 20 -> in.readUnsignedShort();                    // MethodType / Module / Package
                default -> throw new IOException("Unknown cp tag: " + tag);
            }
        }

        final int access = in.readUnsignedShort();   // access_flags
        in.readUnsignedShort();                      // this_class
        final int superIdx = in.readUnsignedShort(); // super_class

        String superName = null;
        if (superIdx != 0) {
            final int nameIdx = classNameIndex[superIdx]; // must point to a Utf8
            if (nameIdx != 0) {
                superName = utf8[nameIdx];  // internal name like "java/lang/Object"
            }
        }
        return new ClassHeader(access, superName);
    }
}
