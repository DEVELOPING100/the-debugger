import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SCRUM-18 spike: call the Flask grouping service from Java (Java 17, no extra libraries).
 * Run the Flask service first, then:  javac GroupingClientSpike.java && java GroupingClientSpike
 * Stop the Flask service and run again to see the fallback when the service is down.
 */
public class GroupingClientSpike {

    private static final String URL = "http://localhost:5000/group";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    public static void main(String[] args) {
        String json = """
            {"requests": [
              {"id": "r1", "description": "NullPointerException when I call getName() on my Student object"},
              {"id": "r2", "description": "My program crashes with NullPointerException, my object is null"},
              {"id": "r3", "description": "My while loop never stops and the program just hangs"},
              {"id": "r4", "description": "The loop keeps running forever, the condition never becomes false"}
            ]}""";

        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            long start = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode()
                    + " (" + (System.currentTimeMillis() - start) + " ms)");
            System.out.println("Clusters: " + response.body());
        } catch (Exception e) {
            // Fallback: the queue must keep working without grouping
            System.out.println("Grouping service unavailable (" + e.getClass().getSimpleName()
                    + "). Showing the normal ungrouped queue instead.");
        }
    }
}
