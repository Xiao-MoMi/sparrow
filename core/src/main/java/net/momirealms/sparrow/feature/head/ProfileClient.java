package net.momirealms.sparrow.feature.head;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.util.UUIDUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

final class ProfileClient {
    private final HttpClient client;
    private final HeadSettings.ApiOptions settings;
    private final Duration timeout;

    ProfileClient(HttpClient client, HeadSettings.ApiOptions settings) {
        this.client = client;
        this.settings = settings;
        this.timeout = DurationUtils.parsePositive(settings.requestTimeout());
    }

    HeadData fetchByName(String name) throws IOException, InterruptedException {
        JsonObject result = this.get(endpoint(this.settings.nameUrl().replace("{name}", URLEncoder.encode(name, StandardCharsets.UTF_8))));
        return result == null ? null : this.fetchByUuid(uuid(string(result, "id")));
    }

    HeadData fetchByUuid(UUID uuid) throws IOException, InterruptedException {
        String url = this.settings.profileUrl().replace("{uuid}", uuid.toString().replace("-", "")).replace("{uuid-dashed}", uuid.toString());
        JsonObject result = this.get(endpoint(url));
        if (result == null) return null;
        UUID returned = uuid(string(result, "id"));
        if (!returned.equals(uuid)) throw invalid("Profile UUID does not match the request");
        String name = string(result, "name");
        JsonElement properties = result.get("properties");
        if (properties == null) return null;
        if (!properties.isJsonArray()) throw invalid("Profile properties must be an array");
        JsonArray array = properties.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) throw invalid("Invalid profile property");
            JsonObject property = element.getAsJsonObject();
            if (string(property, "name").equals("textures")) {
                return new HeadData(returned, name, string(property, "value"), optionalString(property, "signature"));
            }
        }
        return null;
    }

    private JsonObject get(URI uri) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(this.timeout).header("User-Agent", "Sparrow/Head");
        this.settings.headers().forEach(builder::header);
        HttpResponse<String> response = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code == 204 || code == 404) return null;
        if (code == 429) throw new HeadFetchException(HeadFetchException.Reason.THROTTLED, "Head service rate limited the request");
        if (code != 200) throw new HeadFetchException(HeadFetchException.Reason.SERVICE_ERROR, "Head service returned HTTP " + code);
        try {
            JsonElement value = JsonParser.parseString(response.body());
            if (!value.isJsonObject()) throw invalid("Expected a JSON object");
            return value.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw invalid("Head service returned invalid JSON");
        }
    }

    static URI endpoint(String url) {
        URI uri = URI.create(url);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
            throw new HeadFetchException(HeadFetchException.Reason.INVALID_INPUT, "Expected an HTTP(S) URL");
        }
        return uri;
    }

    private static UUID uuid(String value) {
        try {
            return UUIDUtils.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid profile UUID");
        }
    }

    private static String string(JsonObject parent, String key) {
        String value = optionalString(parent, key);
        if (value == null || value.isEmpty()) throw invalid("Missing string: " + key);
        return value;
    }

    private static String optionalString(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Invalid string: " + key);
        return value.getAsString();
    }

    private static HeadFetchException invalid(String message) {
        return new HeadFetchException(HeadFetchException.Reason.INVALID_RESPONSE, message);
    }
}
