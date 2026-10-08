package com.project.qms.dto;

import jakarta.validation.constraints.Size;

/**
 * Change the remarks of an inspection that is already saved (FR-05.12).
 *
 * WHY A SEPARATE RECORD. Only the remarks may change once an inspection
 * exists - the product, the type and the two quantities are settled. The
 * remarks endpoint therefore cannot take InspectionRequest, because that
 * record requires all four of those fields and a screen correcting a typo
 * in a note should not have to re-send the whole inspection.
 *
 * It had no validation at all before (D-03, Phase 12): a remark longer than
 * the 500 characters the column allows travelled down to the database, was
 * refused there, and came back as "That change would break a link to another
 * record" - a sentence about foreign keys, which is not what went wrong.
 * This record gives the endpoint its own small, validated body, and the
 * message is deliberately the same one InspectionRequest uses so the same
 * mistake is described in the same words wherever it is made.
 */
public record InspectionRemarksRequest(

        @Size(max = 500, message = "Remarks must be at most 500 characters")
        String remarks) {
}
