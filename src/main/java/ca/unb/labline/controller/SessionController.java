package ca.unb.labline.controller;

import ca.unb.labline.service.SessionInfo;
import ca.unb.labline.service.StudentQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR4: a logged-in student joins a lab session by typing its join code. */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final StudentQueueService studentQueueService;

    public SessionController(StudentQueueService studentQueueService) {
        this.studentQueueService = studentQueueService;
    }

    @GetMapping("/join/{joinCode}")
    public SessionInfo join(@PathVariable String joinCode) {
        return studentQueueService.joinSession(joinCode);
    }
}
