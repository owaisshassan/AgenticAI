package part2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WeatherConnector {

    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String getWeather(String location) {
        if (location == null || location.isBlank()) {
            FileLogger.warn("get_weather called with blank/missing location argument - rejecting without a network call");
            return "Error: no location was provided, so the weather could not be looked up.";
        }

        try {
            double[] coordinates = geocode(location);
            if (coordinates == null) {
                FileLogger.warn("Geocoding returned no results for location: " + location);
                return "Error: could not find a location matching \"" + location + "\".";
            }

            String forecastUri = FORECAST_URL
                    + "?latitude=" + coordinates[0]
                    + "&longitude=" + coordinates[1]
                    + "&current_weather=true";

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(forecastUri)).GET().build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            FileLogger.info("Forecast API response (" + response.statusCode() + ") for \"" + location + "\": " + response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Error: weather service returned status " + response.statusCode() + ".";
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode current = root.get("current_weather");
            if (current == null) {
                return "Error: weather service response did not contain current weather data.";
            }

            String summary = "Current weather near \"" + location + "\": "
                    + current.get("temperature").asDouble() + "°C, "
                    + "wind speed " + current.get("windspeed").asDouble() + " km/h.";
            FileLogger.info("Weather summary: " + summary);
            return summary;
        } catch (IOException | InterruptedException e) {
            FileLogger.error("get_weather failed for location \"" + location + "\"", e);
            return "Error: failed to retrieve weather data (" + e.getMessage() + ").";
        }
    }

    private static double[] geocode(String location) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String uri = GEOCODE_URL + "?name=" + encoded + "&count=1";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        FileLogger.info("Geocode API response (" + response.statusCode() + ") for \"" + location + "\": " + response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }

        JsonNode first = results.get(0);
        return new double[]{first.get("latitude").asDouble(), first.get("longitude").asDouble()};
    }
}
