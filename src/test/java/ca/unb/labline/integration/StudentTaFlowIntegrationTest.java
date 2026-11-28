package ca.unb.labline.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.AuditLogRepository;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.CourseRepository;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration (system) test of the whole application: real controllers,
 * security, services, JPA and an in-memory database. It walks one help
 * request from sign-up to resolution, the way the web pages use the API.
 */
@SpringBootTest
class StudentTaFlowIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String STUDENT = "flow.student@unb.ca";
    private static final String TA = "flow.ta@unb.ca";
    private static final String PROF = "flow.prof@unb.ca";

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
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mvc;
    private LabSession session;
    private Category debugging;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        if (userRepository.existsByEmail(STUDENT)) {
            session = labSessionRepository.findByJoinCode("FLOW01").orElseThrow();
            debugging = categoryRepository.findByCourseIdAndActiveTrue(
                    session.getCourse().getId()).get(0);
            return;
        }
        register(STUDENT, "Flow Student");
        User ta = withRole(register(TA, "Flow TA"), Role.TA);
        User prof = withRole(register(PROF, "Flow Prof"), Role.INSTRUCTOR);

        Course course = courseRepository.save(new Course("CS2043", "Software Engineering I", prof));
        LabSession lab = new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "FLOW01");
        lab.openQueue();
        lab.assignTa(ta);
        session = labSessionRepository.save(lab);
        debugging = categoryRepository.save(new Category(course, "Debugging"));
    }

    @Test
    void requestGoesFromSubmittedToResolvedAndEveryStepIsAudited() throws Exception {
        mvc.perform(get("/api/sessions/join/flow01").header("Authorization", basic(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueOpen").value(true))
                .andExpect(jsonPath("$.categories[0].name").value("Debugging"));

        mvc.perform(post("/api/requests").header("Authorization", basic(STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + session.getId() + ",\"categoryId\":"
                                + debugging.getId()
                                + ",\"description\":\"NullPointerException\",\"seat\":\"Row 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mvc.perform(get("/api/requests/mine").param("sessionId", session.getId().toString())
                        .header("Authorization", basic(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));

        mvc.perform(post("/api/ta/sessions/" + session.getId() + "/claim-next")
                        .header("Authorization", basic(TA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"));

        HelpRequest request = helpRequestRepository.findAll().get(0);
        mvc.perform(post("/api/ta/requests/" + request.getId() + "/resolve")
                        .header("Authorization", basic(TA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(get("/api/requests/mine").param("sessionId", session.getId().toString())
                        .header("Authorization", basic(STUDENT)))
                .andExpect(status().isNoContent());

        assertEquals(2, auditLogRepository.findByRequestIdOrderByChangedAtAsc(request.getId()).size());
    }

    @Test
    void securityRulesAreEnforced() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/ta/sessions/" + session.getId() + "/claim-next")
                        .header("Authorization", basic(STUDENT)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    private User register(String email, String name) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"" + name
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private User withRole(User user, Role role) {
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(String email) {
        String credentials = email + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
