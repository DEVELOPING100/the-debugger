package ca.unb.labline.controller.dto;

import ca.unb.labline.service.QuestionCluster;
import java.util.List;

/** What the API returns about one group of similar questions. */
public record ClusterResponse(String label, List<HelpRequestResponse> requests) {

    public static ClusterResponse from(QuestionCluster cluster) {
        return new ClusterResponse(cluster.label(),
                cluster.requests().stream().map(HelpRequestResponse::from).toList());
    }
}
