package net.momirealms.sparrow.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Gson 工具类, 封装了常用的 JSON 处理操作, 如读取, 写入, 解析, 合并以及安全取值等.
 */
public final class GsonHelper {
    private static final Gson GSON;
    public static final Gson DEFAULT_GSON = new Gson(); // 默认配置, 转义 HTML 字符
    public static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create(); // 缩进输出, 保留默认 HTML 转义

    private GsonHelper() {}

    static {
        GSON = new GsonBuilder()
                .disableHtmlEscaping()
                .create();
    }

    /**
     * 获取全局的 Gson 实例, 不转义 HTML 字符.
     *
     * @return 配置好的 Gson 对象
     */
    public static Gson get() {
        return GSON;
    }

    /**
     * 将 JsonElement 写入到指定路径的文件中.
     *
     * @param json 要写入的 JSON 元素
     * @param path 目标文件路径
     * @throws IOException 当发生文件 I/O 错误时抛出
     */
    public static void writeJsonFile(JsonElement json, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            get().toJson(json, writer);
        }
    }

    /**
     * 从指定路径的文件中读取并解析为 JsonElement.
     *
     * @param path 要读取的文件路径
     * @return 解析后的 JSON 元素
     * @throws IOException 当读取文件时发生 I/O 错误时抛出
     * @throws JsonParseException 当文件内容不是合法的 JSON 格式时抛出
     */
    public static JsonElement readJsonFile(Path path) throws IOException, JsonParseException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        }
    }

    /**
     * 将 JSON 字符串解析为 JsonElement.
     *
     * @param json 需要解析的 JSON 格式字符串
     * @return 解析后得到的 JSON 元素
     */
    public static JsonElement parseJson(String json) {
        return GSON.fromJson(json, JsonElement.class);
    }

    /**
     * 将 JsonElement 转换为 JSON 格式的字符串.
     *
     * @param json 需要转换的 JSON 元素
     * @return 转换得到的 JSON 字符串
     */
    public static String toString(JsonElement json) {
        return GSON.toJson(json);
    }

    /**
     * 判断给定的 JSON 字符串是否为紧凑格式 (即不包含换行符和回车符).
     *
     * @param content 需要判断的 JSON 字符串
     * @return 如果字符串是紧凑格式则返回 true, 否则返回 false
     */
    public static boolean isCompactJson(String content) {
        String trimmed = content.trim();
        return !trimmed.contains("\n") && !trimmed.contains("\r") &&
                content.length() == trimmed.length();
    }

    /**
     * 浅合并两个 JsonObject, 如果存在同名键, 第二个对象的键值会覆盖第一个对象的键值.
     *
     * @param obj1 第一个 JSON 对象
     * @param obj2 第二个 JSON 对象
     * @return 合并后的新 JSON 对象
     */
    public static JsonObject shallowMerge(JsonObject obj1, JsonObject obj2) {
        JsonObject merged = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : obj1.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, JsonElement> entry : obj2.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    /**
     * 深合并两个 JsonObject, 递归合并所有嵌套的 JsonObject, 如果非 JsonObject 节点存在同名键, 目标对象的键值将覆盖源对象的键值.
     *
     * @param source 源 JSON 对象
     * @param target 目标 JSON 对象
     * @return 合并后的新 JSON 对象
     */
    public static JsonObject deepMerge(JsonObject source, JsonObject target) {
        JsonObject merged = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, JsonElement> entry : target.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (merged.has(key)) {
                JsonElement existingValue = merged.get(key);
                if (existingValue.isJsonObject() && value.isJsonObject()) {
                    JsonObject mergedChild = deepMerge(
                            existingValue.getAsJsonObject(),
                            value.getAsJsonObject()
                    );
                    merged.add(key, mergedChild);
                } else {
                    merged.add(key, value);
                }
            } else {
                merged.add(key, value);
            }
        }
        return merged;
    }

    /**
     * 将 JSON 字符串解析为 JsonObject.
     *
     * @param json 需要解析的 JSON 字符串
     * @return 解析后的 JSON 对象
     * @throws RuntimeException 当 JSON 字符串格式无效无法解析为 JsonObject 时抛出
     */
    public static JsonObject parseJsonToJsonObject(String json) {
        try {
            return get().fromJson(
                    json,
                    JsonObject.class
            );
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Invalid JSON response: " + json, e);
        }
    }

    /**
     * 将 JSON 字符串解析为 Map 对象.
     *
     * @param json 需要解析的 JSON 字符串
     * @return 解析得到的 Map 对象
     * @throws RuntimeException 当 JSON 字符串格式无效无法解析为 Map 时抛出
     */
    public static Map<String, Object> parseJsonToMap(String json) {
        try {
            return GsonHelper.get().fromJson(
                    json,
                    new TypeToken<Map<String, Object>>() {}.getType()
            );
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Invalid JSON response: " + json, e);
        }
    }

    /**
     * 从 Reader 中读取并解析 JSON 为 Map 对象.
     *
     * @param json 包含 JSON 数据的字符读取器
     * @return 解析得到的 Map 对象
     */
    public static Map<String, Object> parseJsonToMap(Reader json) {
        return GsonHelper.get().fromJson(
                json,
                new TypeToken<Map<String, Object>>() {}.getType()
        );
    }

    /**
     * 安全地从 JsonElement 获取浮点数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 float 值或默认值
     */
    public static float getAsFloat(JsonElement json, float defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsFloat();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取双精度浮点数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 double 值或默认值
     */
    public static double getAsDouble(JsonElement json, double defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取整型数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 int 值或默认值
     */
    public static int getAsInt(JsonElement json, int defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取长整型数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 long 值或默认值
     */
    public static long getAsLong(JsonElement json, long defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsLong();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取短整型数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 short 值或默认值
     */
    public static short getAsShort(JsonElement json, short defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsShort();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取字节数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 byte 值或默认值
     */
    public static byte getAsByte(JsonElement json, byte defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsByte();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取布尔数值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 boolean 值或默认值
     */
    public static boolean getAsBoolean(JsonElement json, boolean defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonElement 获取字符串值, 如果元素为空或发生异常则返回默认值.
     *
     * @param json JSON 元素
     * @param defaultValue 发生异常或为空时返回的默认值
     * @return 提取到的 String 值或默认值
     */
    public static String getAsString(JsonElement json, String defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsString();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
