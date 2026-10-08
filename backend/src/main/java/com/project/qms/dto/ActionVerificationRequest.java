package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Inspector's verdict on a completed corrective action (FR-08.9).
 * Accepts EFFECTIVE or NOT_EFFECTIVE - never PENDING, which is where every
 * action starts.
 *
 * The remark is mandatory and has no "optional" wording, because the whole
 * value of a verification is knowing WHY it worked or did not.
 */
public record ActionVerificationRequest(
        @NotBlank(message = "Verification outcome is required") String verificationStatus,

        @NotBlank(message = "A verification remark is required")
        @Size(max = 500, message = "Verification remark must be at most 500 characters")
        String verificationRemark) {
}
