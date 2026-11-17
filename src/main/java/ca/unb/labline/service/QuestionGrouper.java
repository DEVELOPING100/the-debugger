package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import java.util.List;

/**
 * Groups waiting questions into clusters of similar topics (SCRUM-10).
 * Services depend on this interface, not on the Flask service (Dependency
 * Inversion), so the grouping method can change and tests can use a fake.
 * Every request passed in must appear in exactly one returned cluster.
 */
public interface QuestionGrouper {

    List<QuestionCluster> group(List<HelpRequest> requests);
}
