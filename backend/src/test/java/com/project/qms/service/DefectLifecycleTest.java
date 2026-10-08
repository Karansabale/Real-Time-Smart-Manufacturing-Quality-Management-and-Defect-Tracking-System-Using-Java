package com.project.qms.service;

import com.project.qms.entity.DefectStatus;
import com.project.qms.entity.Role;
import com.project.qms.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.project.qms.entity.DefectStatus.CORRECTIVE_ACTION;
import static com.project.qms.entity.DefectStatus.CLOSED;
import static com.project.qms.entity.DefectStatus.OPEN;
import static com.project.qms.entity.DefectStatus.UNDER_INVESTIGATION;
import static com.project.qms.entity.DefectStatus.VERIFIED;
import static com.project.qms.entity.Role.ADMIN;
import static com.project.qms.entity.Role.INSPECTOR;
import static com.project.qms.entity.Role.SUPERVISOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the defect lifecycle's ROLE TABLE — the rules that decide which
 * role may make each move (Phase 4 §6.2, Phase 5 §7.4, FR-07).
 *
 * <p>These are the project's first unit tests, and they are unit tests for a
 * reason: {@code DefectService.isPermitted} is a pure function — four statuses,
 * a role, no database, no Spring context. That makes it possible to test every
 * combination in a few milliseconds, which is the whole truth table, rather
 * than the three or four combinations an end-to-end test could afford.
 *
 * <p>They complement, and do not replace, the API test cases in
 * {@code backend/tests/api-test-cases.sh}: those prove that a refused move
 * really answers 400 with a sentence a person can act on, and that the
 * lifecycle table is consulted before the role table.
 *
 * <p>Run:  cd backend && mvn test      (or mvn package, which runs them too)
 */
class DefectLifecycleTest {

    /* The three signed-in users, as the service sees them: only the role matters
       to this rule, so only the role is set. */
    private static final User AS_ADMIN = signedInAs(ADMIN);
    private static final User AS_INSPECTOR = signedInAs(INSPECTOR);
    private static final User AS_SUPERVISOR = signedInAs(SUPERVISOR);

    private static User signedInAs(Role role) {
        User user = new User();
        user.setRole(role);
        return user;
    }

    /* ==================================================================
       TC-UNIT-01 … TC-UNIT-06 : the six moves the lifecycle offers, each
       with the role that owns it and the roles that do not.
       ================================================================== */

    @Test
    @DisplayName("TC-UNIT-01  OPEN -> UNDER_INVESTIGATION is the supervisor's step")
    void open_to_under_investigation_is_the_supervisors_step() {
        assertTrue(DefectService.isPermitted(OPEN, UNDER_INVESTIGATION, AS_SUPERVISOR));
        assertFalse(DefectService.isPermitted(OPEN, UNDER_INVESTIGATION, AS_INSPECTOR));
        assertFalse(DefectService.isPermitted(OPEN, UNDER_INVESTIGATION, AS_ADMIN));
    }

    @Test
    @DisplayName("TC-UNIT-02  the two moves out of UNDER_INVESTIGATION belong to the supervisor")
    void under_investigation_moves_belong_to_the_supervisor() {
        for (DefectStatus to : List.of(CORRECTIVE_ACTION, OPEN)) {
            assertTrue(DefectService.isPermitted(UNDER_INVESTIGATION, to, AS_SUPERVISOR), "supervisor -> " + to);
            assertFalse(DefectService.isPermitted(UNDER_INVESTIGATION, to, AS_INSPECTOR), "inspector -> " + to);
            assertFalse(DefectService.isPermitted(UNDER_INVESTIGATION, to, AS_ADMIN), "admin -> " + to);
        }
    }

    @Test
    @DisplayName("TC-UNIT-03  only the inspector may verify a corrective action")
    void only_the_inspector_may_verify() {
        assertTrue(DefectService.isPermitted(CORRECTIVE_ACTION, VERIFIED, AS_INSPECTOR));
        assertFalse(DefectService.isPermitted(CORRECTIVE_ACTION, VERIFIED, AS_SUPERVISOR));
        assertFalse(DefectService.isPermitted(CORRECTIVE_ACTION, VERIFIED, AS_ADMIN));
    }

    @Test
    @DisplayName("TC-UNIT-04  a verified defect is closed by the inspector or the admin")
    void a_verified_defect_is_closed_by_the_inspector_or_the_admin() {
        assertTrue(DefectService.isPermitted(VERIFIED, CLOSED, AS_INSPECTOR));
        assertTrue(DefectService.isPermitted(VERIFIED, CLOSED, AS_ADMIN));
        assertFalse(DefectService.isPermitted(VERIFIED, CLOSED, AS_SUPERVISOR));
    }

    @Test
    @DisplayName("TC-UNIT-05  sending a verified defect back for more work is the inspector's alone")
    void sending_a_verified_defect_back_is_the_inspectors_alone() {
        assertTrue(DefectService.isPermitted(VERIFIED, CORRECTIVE_ACTION, AS_INSPECTOR));
        assertFalse(DefectService.isPermitted(VERIFIED, CORRECTIVE_ACTION, AS_SUPERVISOR));
        assertFalse(DefectService.isPermitted(VERIFIED, CORRECTIVE_ACTION, AS_ADMIN));
    }

    @Test
    @DisplayName("TC-UNIT-06  stepping a corrective-action defect back is the supervisor's")
    void stepping_a_corrective_action_back_is_the_supervisors() {
        assertTrue(DefectService.isPermitted(CORRECTIVE_ACTION, UNDER_INVESTIGATION, AS_SUPERVISOR));
        assertFalse(DefectService.isPermitted(CORRECTIVE_ACTION, UNDER_INVESTIGATION, AS_INSPECTOR));
        assertFalse(DefectService.isPermitted(CORRECTIVE_ACTION, UNDER_INVESTIGATION, AS_ADMIN));
    }

    /* ==================================================================
       TC-UNIT-07 : the lifecycle's end, and the completeness of the table
       ================================================================== */

    @Test
    @DisplayName("TC-UNIT-07  a closed defect cannot be moved to anything, by anybody")
    void a_closed_defect_cannot_be_moved_by_anyone() {
        for (DefectStatus to : DefectStatus.values()) {
            assertFalse(DefectService.isPermitted(CLOSED, to, AS_ADMIN), "admin: CLOSED -> " + to);
            assertFalse(DefectService.isPermitted(CLOSED, to, AS_INSPECTOR), "inspector: CLOSED -> " + to);
            assertFalse(DefectService.isPermitted(CLOSED, to, AS_SUPERVISOR), "supervisor: CLOSED -> " + to);
        }
    }

    @Test
    @DisplayName("TC-UNIT-08  every move the lifecycle offers has at least one role that may make it")
    void every_offered_move_is_owned_by_somebody() {
        /* The pairs below are the ones ALLOWED_TRANSITIONS offers (DefectService,
           lines 89-98). A rule table that forbids everything would still pass
           every test above; this one would catch it. */
        List<DefectStatus[]> offered = List.of(
                new DefectStatus[]{OPEN, UNDER_INVESTIGATION},
                new DefectStatus[]{UNDER_INVESTIGATION, CORRECTIVE_ACTION},
                new DefectStatus[]{UNDER_INVESTIGATION, OPEN},
                new DefectStatus[]{CORRECTIVE_ACTION, VERIFIED},
                new DefectStatus[]{CORRECTIVE_ACTION, UNDER_INVESTIGATION},
                new DefectStatus[]{VERIFIED, CLOSED},
                new DefectStatus[]{VERIFIED, CORRECTIVE_ACTION});

        for (DefectStatus[] pair : offered) {
            boolean somebodyMay = DefectService.isPermitted(pair[0], pair[1], AS_ADMIN)
                    || DefectService.isPermitted(pair[0], pair[1], AS_INSPECTOR)
                    || DefectService.isPermitted(pair[0], pair[1], AS_SUPERVISOR);
            assertTrue(somebodyMay, "nobody may make the move " + pair[0] + " -> " + pair[1]);
        }
    }

    /* ==================================================================
       TC-UNIT-09 : a property of this function that the next person
       should know about (it is not a rule anybody wants, but it is true,
       and it is safe only because of the order of the checks)
       ================================================================== */

    @Test
    @DisplayName("TC-UNIT-09  the role table also answers for pairs the lifecycle never offers")
    void the_role_table_is_permissive_for_pairs_the_lifecycle_forbids() {
        /* For a pair the lifecycle does not offer, isPermitted() is not a
           statement about the move - it is only ever consulted for a pair the
           lifecycle has already accepted. It happens to answer "true" for some
           of those unreachable pairs, and these assertions state exactly which,
           so that a future change which starts calling isPermitted() on its own
           fails here instead of shipping.

           The safety net is the order of the checks in changeStatus(): the
           lifecycle table is consulted first and refuses the pair with
           "A defect cannot move from X to Y ...", before the role is examined.
           That is proved at the API level, not here - see TC-API-31/32 in
           backend/tests/api-test-cases.sh. */
        assertTrue(DefectService.isPermitted(UNDER_INVESTIGATION, VERIFIED, AS_SUPERVISOR),
                "a supervisor asking about a pair the lifecycle forbids");
        assertTrue(DefectService.isPermitted(UNDER_INVESTIGATION, UNDER_INVESTIGATION, AS_SUPERVISOR),
                "the same status to itself, which changeStatus refuses as 'already X'");
        assertTrue(DefectService.isPermitted(CORRECTIVE_ACTION, CLOSED, AS_SUPERVISOR));
        assertTrue(DefectService.isPermitted(VERIFIED, OPEN, AS_INSPECTOR));

        /* and the one place where it is strict beyond the offered moves:
           nothing at all leaves CLOSED, which TC-UNIT-07 asserts above. */
    }
}
