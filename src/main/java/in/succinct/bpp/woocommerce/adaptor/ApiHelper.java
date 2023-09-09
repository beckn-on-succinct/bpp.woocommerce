package in.succinct.bpp.woocommerce.adaptor;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.util.Bucket;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.application.ApplicationUtil;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jose4j.base64url.Base64;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


public class ApiHelper {
    final ECommerceAdaptor adaptor;
    final Application application;

    public ApiHelper(ECommerceAdaptor adaptor) {
        this.adaptor = adaptor;
        this.application = adaptor == null ? null : ApplicationUtil.find(adaptor.getSubscriber().getAppId());
    }

    public ApiHelper() {
        this(null);
    }

    /**
     * Returns the configuration prefix for the WooCommerce API.
     *
     * @return The configuration prefix.
     */
    public String getConfigPrefix() {
        return "in.succinct.bpp.woocommerce";
    }

    /**
     * Retrieve the store URL.
     *
     * @return the store URL
     */
    public String getStoreUrl() {
        // Get the configuration
        Map<String, String> configuration = adaptor.getConfiguration();

        // Format the configuration prefix
        String configPrefix = getConfigPrefix();

        // Build the key for the store URL
        String key = String.format("%s.storeUrl", configPrefix);

        // Retrieve the store URL from the configuration & Return the store URL
        return configuration.get(key);
    }

    /**
     * Returns the Consumer secret configuration value.
     *
     * @return the secret configuration value
     */
    public String getConsumerSecret() {
        return adaptor.getConfiguration().get(String.format("%s.secret", getConfigPrefix()));
    }

    /**
     * Retrieves the client ID from the configuration.
     *
     * @return The client ID.
     */
    public String getClientId() {
        return adaptor.getConfiguration().get(String.format("%s.clientId", getConfigPrefix()));
    }

    /**
     * Returns the authentication string for the API.
     *
     * @return the authentication string
     */
    public String getAuth() {
        // Encode the client ID and consumer secret in Base64
        String credentials = String.format("%s:%s", getClientId(), getConsumerSecret());
        byte[] encodedCredentials = Base64.encode(credentials.getBytes(StandardCharsets.UTF_8)).getBytes();
        // Format the authentication string with the encoded credentials
        return String.format("Basic %s", new String(encodedCredentials));
    }

    /**
     * Initializes the Call object with the provided parameters.
     *
     * @param relativeUrl  The relative URL endpoint.
     * @param parameter    The JSON parameters to be sent in the request.
     * @param addonHeaders Additional headers for the request.
     * @param reqMethod    The HTTP request method (e.g., GET, POST).
     * @return Configured Call object.
     */
    private Call<JSONObject> initializeCall(String relativeUrl, JSONObject parameter,
            Map<String, String> addonHeaders, @NotNull HttpMethod reqMethod) {

        Call<JSONObject> call = new Call<JSONObject>()
                .url(getStoreUrl(), relativeUrl)
                .header("content-type", MimeType.APPLICATION_JSON.toString())
                .header("Authorization", getAuth())
                .headers(addonHeaders)
                .input(parameter);

        switch (reqMethod) {
            case GET:
                return call.method(HttpMethod.GET).getResponseAsJson();
            case POST:
                return call.method(HttpMethod.POST).getResponseAsJson();
            // Handle other HTTP methods as needed.
            default:
                throw new IllegalArgumentException("Unsupported HTTP method");
        }

    }

    /**
     * Determine if a retry is required based on call status and retry count.
     */
    private boolean shouldRetry(@NotNull Call<JSONObject> call, Bucket tries) {
        return call.getStatus() > 200 && call.getStatus() < 299 && tries.intValue() > 0;
    }

    /**
     * Handles the retry logic for the request.
     */
    private Call<JSONObject> handleRetry(@NotNull Call<JSONObject> call, Bucket tries) {
        List<String> locations = call.getResponseHeaders().get("location");

        // Exit if no locations are found.
        if (locations.isEmpty()) {
            return call;
        }

        List<String> after = call.getResponseHeaders().get("retry-after");

        // Exit if no retry-after headers are found.
        if (after == null || after.isEmpty()) {
            return call;
        }

        // Calculate sleep duration.
        long millis = Long.parseLong(after.get(0)) * 1000 + 500;
        try {
            Thread.sleep(millis);
        } catch (Exception ex) {
            // Handle exception if needed.
        }
        tries.decrement();

        return call; // return the new retry call
    }

    /**
     * Parse the error response.
     */
    @SuppressWarnings("unchecked")
    private <T extends JSONAware> T parseJsonError(Call<JSONObject> call) {
        return (T) JSONValue.parse(new InputStreamReader(call.getErrorStream()));
    }

    /**
     * Sends a POST request and handles retries for certain conditions.
     *
     * @param relativeUrl  The relative URL endpoint.
     * @param parameter    The JSON parameters to be sent in the request.
     * @param addonHeaders Additional headers for the request.
     * @param <T>          The type of object to return. Must extend JSONAware.
     * @return A JSON object representing the response.
     */
    public <T extends JSONAware> T post(String relativeUrl, JSONObject parameter, Map<String, String> addonHeaders) {
        // Initialize the call with the provided parameters.
        Call<JSONObject> initialCall = initializeCall(relativeUrl, parameter, addonHeaders, HttpMethod.POST);
        T responseObject = initialCall.getResponseAsJson();

        // Create a bucket for retry attempts.
        Bucket tries = new Bucket(3);

        // Retry logic based on call status and response headers.
        Call<JSONObject> currentCall = initialCall;
        while (shouldRetry(currentCall, tries)) {
            currentCall = handleRetry(currentCall, tries);
            responseObject = currentCall.getResponseAsJson();
        }

        // Process errors if present.
        if (currentCall.hasErrors()) {
            responseObject = parseJsonError(currentCall);
        }

        return responseObject;
    }

    /**
     * Sends a GET request with the given parameters, URL, and additional headers.
     *
     * @param relativeUrl  The relative URL endpoint.
     * @param parameter    The JSON parameters to be sent in the request.
     * @param addonHeaders Additional headers for the request.
     * @param <T>          The type of object to return. Must extend JSONAware.
     * @return A JSON object representing the response.
     */
    public <T extends JSONAware> T get(String relativeUrl, JSONObject parameter, Map<String, String> addonHeaders) {
        // Initialize and configure the call object
        Call<JSONObject> call = initializeCall(relativeUrl, parameter, addonHeaders, HttpMethod.GET);

        // Fetch the response as a JSON object
        T response = call.getResponseAsJson();

        // If the response is null and there are errors, parse the error stream
        if (response == null && call.hasErrors()) {
            response = parseJsonError(call);
        }

        return response;
    }



    /**
     * Sends a POST request to the specified relative URL with the given parameters.
     * Uses a default empty map for headers.
     *
     * @param relativeUrl The relative URL of the API endpoint.
     * @param parameter   The JSON object containing the request parameters.
     * @param <T>         The type of JSON response expected.
     * @return The response of type T from the API.
     */
    public <T extends JSONAware> T post(String relativeUrl, JSONObject parameter) {
        return post(relativeUrl, parameter, new IgnoreCaseMap<>());
    }

    /**
     * Sends a GET request with the given parameters and URL.
     *
     * @param relativeUrl The relative URL endpoint.
     * @param parameter   The JSON parameters to be sent in the request.
     * @param <T>         The type of object to return. Must extend JSONAware.
     * @return A JSON object representing the response.
     */
    public <T extends JSONAware> T get(String relativeUrl, JSONObject parameter) {
        return get(relativeUrl, parameter, new IgnoreCaseMap<>());
    }
    
    /**
     * Sends a POST request to the specified relative URL with the given parameter
     * and sets the X-HTTP-Method-Override header to "PUT".
     *
     * @param relativeUrl the relative URL to send the request to
     * @param parameter   the parameter to include in the request body
     * @param <T>         the type of the response, which must implement JSONAware
     * @return the response object of type T
     */
    public <T extends JSONAware> T putViaPost(String relativeUrl, JSONObject parameter) {
        return post(relativeUrl, parameter, new IgnoreCaseMap<>() {
            {
                put("X-HTTP-Method-Override", "PUT");
            }
        });
    }

    /**
     * Sends a PUT request to the specified relative URL with the given parameters
     * using the GET method.
     *
     * @param relativeUrl the relative URL to send the request to
     * @param parameter   the JSON object containing the request parameters
     * @param headers     the headers to include in the request
     * @param <T>         the type of the response object
     * @return the response object of type T
     */
    public <T extends JSONAware> T putViaGet(String relativeUrl, JSONObject parameter,
            @NotNull Map<String, String> headers) {
        // Set the X-HTTP-Method-Override header to "PUT"
        headers.put("X-HTTP-Method-Override", "PUT");
        // Send the GET request with the specified relative URL, parameters, and headers
        return get(relativeUrl, parameter, headers);
    }

    /**
     * Sends a DELETE request to the specified relative URL with the given
     * parameters.
     *
     * @param relativeUrl The relative URL to send the request to.
     * @param parameter   The JSON object containing the parameters for the request.
     * @param <T>         The type of the response object.
     * @return The response object of type T.
     */
    public <T extends JSONAware> T delete(String relativeUrl, JSONObject parameter) {
        // Set the X-HTTP-Method-Override header to "DELETE"
        Map<String, String> headers = new IgnoreCaseMap<>();
        headers.put("X-HTTP-Method-Override", "DELETE");

        // Make a POST request with the specified URL, parameters, and headers
        return post(relativeUrl, parameter, headers);
    }

    public static class Page<T extends JSONAware> {
        T data;
        String next = null;
        String previous = null;
    }
}
