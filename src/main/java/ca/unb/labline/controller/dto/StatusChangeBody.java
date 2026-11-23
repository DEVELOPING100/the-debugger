package ca.unb.labline.controller.dto;

import jakarta.validation.constraints.Size;

/** Optional reason a TA gives when deferring or returning a request. */
public record StatusChangeBody(@Size(max = 255) String reason) {
}
