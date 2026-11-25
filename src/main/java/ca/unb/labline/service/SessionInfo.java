package ca.unb.labline.service;

import java.util.List;

/** What a student sees after joining a lab session with its code. */
public record SessionInfo(Long id, String name, String room, String courseCode,
        boolean queueOpen, List<CategoryInfo> categories) {

    /** One category the student can choose for a request. */
    public record CategoryInfo(Long id, String name) {
    }
}
