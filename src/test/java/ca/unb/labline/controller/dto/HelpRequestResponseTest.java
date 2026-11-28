package ca.unb.labline.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The TA page needs to know who is helping each request (claimedById). */
class HelpRequestResponseTest {

    private User ta;
    private HelpRequest request;

    @BeforeEach
    void setUp() {
        User instructor = withId(new User("test.prof@unb.ca", "Test Prof", "HASH"), 9L);
        ta = withId(new User("test.ta@unb.ca", "Test TA", "HASH"), 5L);
        User student = withId(new User("test.student@unb.ca", "Test Student", "HASH"), 1L);
        Course course = withId(new Course("CS2043", "Software Engineering I", instructor), 10L);
        LabSession session = withId(new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "TEST01"), 20L);
        Category category = withId(new Category(course, "Debugging"), 30L);
        request = withId(new HelpRequest(session, student, category,
                "NullPointerException in my loop", "Row 3 Seat 4"), 100L);
    }

    @Test
    void waitingRequestHasNobodyHelping() {
        HelpRequestResponse response = HelpRequestResponse.from(request);

        assertEquals(RequestStatus.WAITING, response.status());
        assertNull(response.claimedById());
    }

    @Test
    void claimedRequestShowsWhichTaIsHelping() {
        request.markClaimed(ta);
        request.changeStatus(RequestStatus.CLAIMED);

        HelpRequestResponse response = HelpRequestResponse.from(request);

        assertEquals(5L, response.claimedById());
        assertEquals(100L, response.id());
        assertEquals("Row 3 Seat 4", response.seat());
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
