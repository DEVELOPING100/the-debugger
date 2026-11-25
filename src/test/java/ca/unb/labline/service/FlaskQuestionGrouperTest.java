package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.User;
import ca.unb.labline.service.FlaskQuestionGrouper.FlaskCluster;
import ca.unb.labline.service.FlaskQuestionGrouper.GroupResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** SCRUM-10 unit tests. A fake server stands in for the Flask service. */
class FlaskQuestionGrouperTest {

    private static final String GROUP_URL = "http://flask.test/group";

    private MockRestServiceServer flask;
    private FlaskQuestionGrouper grouper;
    private HelpRequest nullPointer1;
    private HelpRequest nullPointer2;
    private HelpRequest endlessLoop;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://flask.test");
        flask = MockRestServiceServer.bindTo(builder).build();
        grouper = new FlaskQuestionGrouper(builder.build());

        nullPointer1 = request(101L, "NullPointerException when I call getName()");
        nullPointer2 = request(102L, "My program crashes, my object is null");
        endlessLoop = request(103L, "My while loop never stops");
    }

    @Test
    void similarQuestionsComeBackInOneCluster() {
        flask.expect(requestTo(GROUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.requests[0].id").value(101))
                .andExpect(jsonPath("$.requests[0].description").value(nullPointer1.getDescription()))
                .andRespond(withSuccess("""
                        {"clusters": [
                          {"cluster_id": "c1", "label": "null object", "request_ids": [101, 102]},
                          {"cluster_id": "c2", "label": "loop", "request_ids": [103]}
                        ]}""", MediaType.APPLICATION_JSON));

        List<QuestionCluster> clusters = grouper.group(List.of(nullPointer1, nullPointer2, endlessLoop));

        assertEquals(2, clusters.size());
        assertEquals("null object", clusters.get(0).label());
        assertEquals(List.of(nullPointer1, nullPointer2), clusters.get(0).requests());
        assertEquals(List.of(endlessLoop), clusters.get(1).requests());
        flask.verify();
    }

    @Test
    void whenFlaskFailsEveryRequestIsShownOnItsOwn() {
        flask.expect(requestTo(GROUP_URL)).andRespond(withServerError());

        List<QuestionCluster> clusters = grouper.group(List.of(nullPointer1, nullPointer2, endlessLoop));

        assertEquals(3, clusters.size());
        assertEquals(List.of(nullPointer1), clusters.get(0).requests());
        assertEquals(List.of(endlessLoop), clusters.get(2).requests());
    }

    @Test
    void requestsFlaskLeavesOutAreStillShownAndUnknownIdsAreIgnored() {
        GroupResponse response = new GroupResponse(List.of(
                new FlaskCluster("c1", "null object", List.of(101L, 999L))));

        List<QuestionCluster> clusters = FlaskQuestionGrouper.toClusters(
                List.of(nullPointer1, nullPointer2, endlessLoop), response);

        assertEquals(3, clusters.size());
        assertEquals(List.of(nullPointer1), clusters.get(0).requests());
        assertEquals(List.of(nullPointer2), clusters.get(1).requests());
        assertEquals(List.of(endlessLoop), clusters.get(2).requests());
    }

    @Test
    void oneRequestIsNotSentToFlask() {
        List<QuestionCluster> clusters = grouper.group(List.of(endlessLoop));

        assertEquals(List.of(QuestionCluster.single(endlessLoop)), clusters);
        flask.verify(); // no call was expected, so none may have happened
    }

    // ---- helpers ----

    private static HelpRequest request(Long id, String description) {
        User instructor = new User("test.prof@unb.ca", "Test Prof", "HASH");
        User student = new User("student" + id + "@unb.ca", "Student " + id, "HASH");
        Course course = new Course("CS2043", "Software Engineering I", instructor);
        LabSession session = new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "TEST01");
        Category category = new Category(course, "Debugging");
        HelpRequest request = new HelpRequest(session, student, category, description, "Row 1");
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}
