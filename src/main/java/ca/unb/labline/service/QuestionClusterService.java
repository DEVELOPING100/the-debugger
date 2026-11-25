package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * SCRUM-10: shows a TA the waiting questions of a session grouped by topic,
 * so a common problem can be answered once (see broadcastAnswer, SCRUM-11).
 */
@Service
public class QuestionClusterService {

    private final TaQueueService taQueueService;
    private final QuestionGrouper questionGrouper;

    public QuestionClusterService(TaQueueService taQueueService, QuestionGrouper questionGrouper) {
        this.taQueueService = taQueueService;
        this.questionGrouper = questionGrouper;
    }

    /**
     * Clusters of WAITING requests, the cluster with the longest-waiting student first.
     * viewQueue already checks that the user is staff of this session.
     */
    public List<QuestionCluster> clustersFor(Long sessionId, String taEmail) {
        List<HelpRequest> waiting = taQueueService.viewQueue(sessionId, taEmail).stream()
                .filter(HelpRequest::isWaiting)
                .toList();
        return questionGrouper.group(waiting).stream()
                .sorted(Comparator.comparing(QuestionClusterService::oldestJoinTime))
                .toList();
    }

    private static LocalDateTime oldestJoinTime(QuestionCluster cluster) {
        return cluster.requests().stream()
                .map(HelpRequest::getTimeJoined)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.MAX);
    }
}
