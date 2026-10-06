package net.momirealms.sparrow.proxy;

public final class MinecraftVersionParser {

    private MinecraftVersionParser() {
    }

    public static int parseVersionToInteger(String versionString) {
        int[] parts = new int[3];
        int currentNumber = 0;
        int part = 0;
        for (int i = 0; i < versionString.length(); i++) {
            char c = versionString.charAt(i);
            if (c >= '0' && c <= '9') {
                currentNumber = currentNumber * 10 + (c - '0');
            } else if (c == '.') {
                if (part == parts.length - 1) {
                    parts[part] = currentNumber;
                    break;
                }
                parts[part++] = currentNumber;
                currentNumber = 0;
            }
        }
        if (part < parts.length) parts[part] = currentNumber;
        return parts[0] * 10000 + parts[1] * 100 + parts[2];
    }
}
