package ca.unb.labline.integration;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.unb.labline.model.AuditLog;
import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.AuditLogRepository;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.CourseRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test of the TA page (ta.html): real controllers, security, services,
 * JPA and the in-memory database, calling the API in the same order the page does.
 * Each test builds its own lab, so the tests do not depend on each other.
 * The Flask grouping service is not running in tests, which also checks the fallback.
 */
@SpringBootTest
class TaPageFlowIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String TA = "tapage.ta@unb.ca";
    private static final String OTHER_TA = "tapage.ta2@unb.ca";
    private static final String PROF = "tapage.prof@unb.ca";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LabSessionRepository labSessionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mvc;
    private User ta;
    private User otherTa;
    private User prof;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        ta = findOrRegister(TA, "Page TA", Role.TA);
        otherTa = findOrRegister(OTHER_TA, "Other TA", Role.TA);
        prof = findOrRegister(PROF, "Page Prof", Role.INSTRUCTOR);
    }

    @Test
    void taWorksThroughTheQueueTheWayTheTaPageDoes() throws Exception {
        Lab lab = newLab("TAPG01");
        long first = submit(lab, "tapage.a1@unb.ca", "NullPointerException in Main", "Row 1");
        long second = submit(lab, "tapage.a2@unb.ca", "My loop never ends", "Row 2");

        // The page opens the lab by its join code (any case works)
        mvc.perform(get("/api/sessions/join/tapg01").header("Authorization", basic(TA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lab.session().getId().intValue()));

        // Queue: oldest first, nobody helping yet
        queue(lab, TA)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value((int) first))
                .andExpect(jsonPath("$[0].claimedById").value(nullValue()));

        // "Help next student" takes the oldest, and the page can tell it is this TA's claim
        claimNext(lab, TA)
                .andExpect(jsonPath("$.id").value((int) first))
                .andExpect(jsonPath("$.claimedById").value(ta.getId().intValue()));

        // Defer, then Return: back to WAITING with the claim cleared
        action(first, "defer", TA).andExpect(jsonPath("$.status").value("DEFERRED"));
        action(first, "return", TA)
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.claimedById").value(nullValue()));

        // Returning kept the original place, so "Help next" picks the same student again
        claimNext(lab, TA).andExpect(jsonPath("$.id").value((int) first));
        action(first, "resolve", TA).andExpect(jsonPath("$.status").value("RESOLVED"));

        // A resolved request leaves the queue; every step was audited
        queue(lab, TA)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value((int) second));
        assertEquals(5, auditLogRepository.findByRequestIdOrderByChangedAtAsc(first).size());
    }

    @Test
    void pageRulesMatchTheServerRules() throws Exception {
        Lab lab = newLab("TAPG02");
        long request = submit(lab, "tapage.b1@unb.ca", "Can't connect to the database", "Row 4");
        claimNext(lab, TA).andExpect(status().isOk());

        // Another TA gets no Resolve button on the page, and the server refuses it too
        mvc.perform(post("/api/ta/requests/" + request + "/resolve")
                        .header("Authorization", basic(OTHER_TA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the TA who claimed this request can do that"));

        // ...but any TA of the lab may return it to the queue
        action(request, "return", OTHER_TA).andExpect(jsonPath("$.status").value("WAITING"));

        // Students can never load the TA queue
        mvc.perform(get("/api/ta/sessions/" + lab.session().getId() + "/queue")
                        .header("Authorization", basic("tapage.b1@unb.ca")))
                .andExpect(status().isForbidden());
    }

    @Test
    void taAnswersSimilarQuestionsWithOneBroadcast() throws Exception {
        Lab lab = newLab("TAPG03");
        long first = submit(lab, "tapage.c1@unb.ca", "NullPointerException calling getName()", "Row 5");
        long second = submit(lab, "tapage.c2@unb.ca", "My object is null and it crashes", "Row 6");

        // Flask is not running in tests, so every question comes back on its own
        mvc.perform(get("/api/ta/sessions/" + lab.session().getId() + "/clusters")
                        .header("Authorization", basic(TA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requests.length()").value(1));

        String answer = "Create the object with new before you call its methods";
        mvc.perform(post("/api/ta/sessions/" + lab.session().getId() + "/broadcast")
                        .header("Authorization", basic(TA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\":[" + first + "," + second + "],\"answer\":\""
                                + answer + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[1].status").value("RESOLVED"));

        queue(lab, TA).andExpect(jsonPath("$.length()").value(0));
        for (long id : List.of(first, second)) {
            List<AuditLog> history = auditLogRepository.findByRequestIdOrderByChangedAtAsc(id);
            assertEquals("Broadcast answer: " + answer, history.get(history.size() - 1).getReason());
        }
    }

    // ---- helpers: each one is one call the TA page makes ----

    private record Lab(LabSession session, Category category) {
    }

    private Lab newLab(String joinCode) {
        Course course = courseRepository.save(new Course("CS2043", "Software Engineering I", prof));
        LabSession lab = new LabSession(course, "Lab " + joinCode, "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), joinCode);
        lab.openQueue();
        lab.assignTa(ta);
        lab.assignTa(otherTa);
        return new Lab(labSessionRepository.save(lab),
                categoryRepository.save(new Category(course, "Debugging")));
    }

    /** A new student signs up and joins the line; returns the new request's id. */
    private long submit(Lab lab, String studentEmail, String description, String seat)
            throws Exception {
        findOrRegister(studentEmail, "Student", Role.STUDENT);
        String json = mvc.perform(post("/api/requests").header("Authorization", basic(studentEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + lab.session().getId() + ",\"categoryId\":"
                                + lab.category().getId() + ",\"description\":\"" + description
                                + "\",\"seat\":\"" + seat + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions queue(Lab lab, String taEmail) throws Exception {
        return mvc.perform(get("/api/ta/sessions/" + lab.session().getId() + "/queue")
                        .header("Authorization", basic(taEmail)))
                .andExpect(status().isOk());
    }

    private ResultActions claimNext(Lab lab, String taEmail) throws Exception {
        return mvc.perform(post("/api/ta/sessions/" + lab.session().getId() + "/claim-next")
                        .header("Authorization", basic(taEmail)))
                .andExpect(status().isOk());
    }

    private ResultActions action(long requestId, String action, String taEmail) throws Exception {
        return mvc.perform(post("/api/ta/requests/" + requestId + "/" + action)
                        .header("Authorization", basic(taEmail)))
                .andExpect(status().isOk());
    }

    private User findOrRegister(String email, String name, Role role) throws Exception {
        if (!userRepository.existsByEmail(email)) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"name\":\"" + name
                                    + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isCreated());
        }
        User user = userRepository.findByEmail(email).orElseThrow();
        if (user.getRole() != role) {
            user.changeRole(role);
            user = userRepository.save(user);
        }
        return user;
    }

    private static String basic(String email) {
        String credentials = email + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
