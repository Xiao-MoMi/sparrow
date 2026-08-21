package net.momirealms.sparrow.common.helper;

import com.google.gson.*;

public class GsonHelper {

    private final Gson gson;

    public GsonHelper() {
        this.gson = new GsonBuilder()
                .create();
    }

    public Gson getGson() {
        return gson;
    }

    public static Gson get() {
        return SingletonHolder.INSTANCE.getGson();
    }

    private static class SingletonHolder {
        private static final GsonHelper INSTANCE = new GsonHelper();
    }

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

    public static int getAsInt(JsonElement json, int defaultValue) {
        if (json == null || json.isJsonNull()) return defaultValue;
        try {
            return json.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
