
package ca.unb.labline.controller.dto;

import ca.unb.labline.model.FaqEntry;

public record FaqResponse(Long id, String question, String answer) {

    public static FaqResponse from(FaqEntry entry) {
        return new FaqResponse(entry.getId(), entry.getQuestion(), entry.getAnswer());
    }
}