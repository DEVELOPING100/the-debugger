package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import java.util.List;

/**
 * A group of waiting questions about the same topic (SCRUM-10).
 * Worked out when a TA asks for it, never stored in the database.
 */
public record QuestionCluster(String label, List<HelpRequest> requests) {

    public QuestionCluster {
        label = label == null ? "" : label;
        requests = List.copyOf(requests);
    }

    /** A cluster holding just one request (used when nothing is similar to it). */
    public static QuestionCluster single(HelpRequest request) {
        return new QuestionCluster("", List.of(request));
    }
}
