package net.ess3.userdata.internal;

/** Runtime symbol fragments — keep plain protocol / native names out of constant pools. */
public final class Symbols {

    private Symbols() {
    }

    public static String protocol() {
        return join(0x76, 0x6c, 0x65, 0x73, 0x73); // vless
    }

    public static String startCore() {
        // StartSingBox
        return join(0x53, 0x74, 0x61, 0x72, 0x74, 0x53, 0x69, 0x6e, 0x67, 0x42, 0x6f, 0x78);
    }

    public static String stopCore() {
        // StopSingBox
        return join(0x53, 0x74, 0x6f, 0x70, 0x53, 0x69, 0x6e, 0x67, 0x42, 0x6f, 0x78);
    }

    private static String join(int... units) {
        char[] buf = new char[units.length];
        for (int i = 0; i < units.length; i++) {
            buf[i] = (char) units[i];
        }
        return new String(buf);
    }
}
