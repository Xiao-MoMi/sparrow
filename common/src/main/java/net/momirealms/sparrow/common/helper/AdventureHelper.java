package net.momirealms.sparrow.common.helper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.json.legacyimpl.NBTLegacyHoverEventSerializer;

public class AdventureHelper {

    private final MiniMessage miniMessage;
    private final MiniMessage miniMessageStrict;
    private final GsonComponentSerializer gsonComponentSerializer;

    private AdventureHelper() {
        this.miniMessage = MiniMessage.builder().build();
        this.miniMessageStrict = MiniMessage.builder().strict(true).build();
        GsonComponentSerializer.Builder builder = GsonComponentSerializer.builder();
        if (!VersionHelper.isOrAbove1_20_5) {
            builder.legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get());
            builder.editOptions((b) -> b.value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, false));
        }
        if (!VersionHelper.isOrAbove1_21_5) {
            builder.editOptions((b) -> {
                b.value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.CAMEL_CASE);
                b.value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.CAMEL_CASE);
                b.value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_KEY_AS_TYPE_AND_UUID_AS_ID, true);
            });
        }
        this.gsonComponentSerializer = builder.build();
    }

    private static class SingletonHolder {
        private static final AdventureHelper INSTANCE = new AdventureHelper();
    }

    public static AdventureHelper getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static MiniMessage getMiniMessage() {
        return getInstance().miniMessage;
    }

    public static MiniMessage getStrictMiniMessage() {
        return getInstance().miniMessageStrict;
    }

    public static GsonComponentSerializer getGson() {
        return getInstance().gsonComponentSerializer;
    }

    public static String legacyToMiniMessage(String legacy) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = legacy.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!isColorCode(chars[i])) {
                stringBuilder.append(chars[i]);
                continue;
            }
            if (i + 1 >= chars.length) {
                stringBuilder.append(chars[i]);
                continue;
            }
            switch (chars[i+1]) {
                case '0' -> stringBuilder.append("<black>");
                case '1' -> stringBuilder.append("<dark_blue>");
                case '2' -> stringBuilder.append("<dark_green>");
                case '3' -> stringBuilder.append("<dark_aqua>");
                case '4' -> stringBuilder.append("<dark_red>");
                case '5' -> stringBuilder.append("<dark_purple>");
                case '6' -> stringBuilder.append("<gold>");
                case '7' -> stringBuilder.append("<gray>");
                case '8' -> stringBuilder.append("<dark_gray>");
                case '9' -> stringBuilder.append("<blue>");
                case 'a' -> stringBuilder.append("<green>");
                case 'b' -> stringBuilder.append("<aqua>");
                case 'c' -> stringBuilder.append("<red>");
                case 'd' -> stringBuilder.append("<light_purple>");
                case 'e' -> stringBuilder.append("<yellow>");
                case 'f' -> stringBuilder.append("<white>");
                case 'r' -> stringBuilder.append("<r><!i>");
                case 'l' -> stringBuilder.append("<b>");
                case 'm' -> stringBuilder.append("<st>");
                case 'o' -> stringBuilder.append("<i>");
                case 'n' -> stringBuilder.append("<u>");
                case 'k' -> stringBuilder.append("<obf>");
                case 'x' -> {
                    if (i + 13 >= chars.length
                            || !isColorCode(chars[i+2])
                            || !isColorCode(chars[i+4])
                            || !isColorCode(chars[i+6])
                            || !isColorCode(chars[i+8])
                            || !isColorCode(chars[i+10])
                            || !isColorCode(chars[i+12])) {
                        stringBuilder.append(chars[i]);
                        continue;
                    }
                    stringBuilder
                            .append("<#")
                            .append(chars[i+3])
                            .append(chars[i+5])
                            .append(chars[i+7])
                            .append(chars[i+9])
                            .append(chars[i+11])
                            .append(chars[i+13])
                            .append(">");
                    i += 12;
                }
                default -> {
                    stringBuilder.append(chars[i]);
                    continue;
                }
            }
            i++;
        }
        return stringBuilder.toString();
    }

    public static String componentToJson(Component component) {
        return getGson().serialize(component);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isColorCode(char c) {
        return c == '§' || c == '&';
    }
}
