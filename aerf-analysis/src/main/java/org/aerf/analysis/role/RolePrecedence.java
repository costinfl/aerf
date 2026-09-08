package org.aerf.analysis.role;

import org.aerf.model.Role;

import java.util.List;

/**
 * The conflict-resolution order from AERF v0.4 section 3.4:
 *
 * <pre>External &gt; Persistence &gt; Infrastructure &gt; Application &gt; Domain &gt; Presentation</pre>
 *
 * <p><b>This is not a validated law.</b> The specification itself calls it
 * "a hypothesis for v0.4 implementation [that] must be tested empirically"
 * and notes "a later confidence model may supersede simple precedence."
 * It is implemented exactly as stated, unmodified, so that it can be
 * tested against real evidence rather than silently adjusted here.
 */
public final class RolePrecedence {

    private static final List<Role> ORDER = List.of(
            Role.EXTERNAL,
            Role.PERSISTENCE,
            Role.INFRASTRUCTURE,
            Role.APPLICATION,
            Role.DOMAIN,
            Role.PRESENTATION);

    private RolePrecedence() {
    }

    /**
     * Lower rank wins a conflict. {@link Role#UNKNOWN}, and any role not
     * part of the stated precedence order, ranks last: it must never be
     * chosen over an actual signal.
     */
    public static int rank(Role role) {
        int index = ORDER.indexOf(role);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * Resolves a set of conflicting signals to the single winning role,
     * per this precedence order. {@link Role#UNKNOWN} if {@code signals}
     * is empty.
     */
    public static Role winner(List<RoleSignal> signals) {
        Role winner = Role.UNKNOWN;
        boolean hasWinner = false;
        for (RoleSignal signal : signals) {
            if (!hasWinner || rank(signal.role()) < rank(winner)) {
                winner = signal.role();
                hasWinner = true;
            }
        }
        return winner;
    }
}
