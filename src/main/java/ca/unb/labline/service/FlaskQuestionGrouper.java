package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Groups questions by calling the Flask TF-IDF service (contract from spike SCRUM-18):
 *   POST /group  {"requests": [{"id": 1, "description": "..."}]}
 *   returns      {"clusters": [{"cluster_id": "c1", "label": "...", "request_ids": [1, 2]}]}
 * If Flask is down or answers badly, every request is shown on its own, so the
 * queue keeps working without grouping.
 */
@Component
public class FlaskQuestionGrouper implements QuestionGrouper {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    @Autowired
    public FlaskQuestionGrouper(@Value("${labline.grouping.url}") String baseUrl) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactoryWithTimeout())
                .build());
    }

    /** Lets tests pass in a RestClient that talks to a fake server. */
    FlaskQuestionGrouper(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<QuestionCluster> group(List<HelpRequest> requests) {
        if (requests.size() < 2) {
            return eachOnItsOwn(requests);
        }
        try {
            GroupResponse response = restClient.post()
                    .uri("/group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GroupBody.of(requests))
                    .retrieve()
                    .body(GroupResponse.class);
            return toClusters(requests, response);
        } catch (RestClientException e) {
            return eachOnItsOwn(requests);
        }
    }

    /** Maps Flask's ids back to requests; any request Flask left out is shown on its own. */
    static List<QuestionCluster> toClusters(List<HelpRequest> requests, GroupResponse response) {
        if (response == null || response.clusters() == null) {
            return eachOnItsOwn(requests);
        }
        Map<Long, HelpRequest> notYetGrouped = new LinkedHashMap<>();
        requests.forEach(request -> notYetGrouped.put(request.getId(), request));

        List<QuestionCluster> clusters = new ArrayList<>();
        for (FlaskCluster flaskCluster : response.clusters()) {
            List<HelpRequest> members = new ArrayList<>();
            for (Long id : flaskCluster.requestIdsOrEmpty()) {
                HelpRequest member = notYetGrouped.remove(id);
                if (member != null) {
                    members.add(member);
                }
            }
            if (!members.isEmpty()) {
                clusters.add(new QuestionCluster(flaskCluster.label(), members));
            }
        }
        notYetGrouped.values().forEach(request -> clusters.add(QuestionCluster.single(request)));
        return clusters;
    }

    private static List<QuestionCluster> eachOnItsOwn(List<HelpRequest> requests) {
        return requests.stream().map(QuestionCluster::single).toList();
    }

    private static SimpleClientHttpRequestFactory requestFactoryWithTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    // ---- JSON shapes of the Flask contract ----

    public record GroupBody(List<Item> requests) {
        static GroupBody of(List<HelpRequest> requests) {
            return new GroupBody(requests.stream()
                    .map(request -> new Item(request.getId(), request.getDescription()))
                    .toList());
        }
    }

    public record Item(Long id, String description) {
    }

    public record GroupResponse(List<FlaskCluster> clusters) {
    }

    public record FlaskCluster(@JsonProperty("cluster_id") String clusterId, String label,
            @JsonProperty("request_ids") List<Long> requestIds) {

        List<Long> requestIdsOrEmpty() {
            return requestIds == null ? List.of() : requestIds;
        }
    }
}
