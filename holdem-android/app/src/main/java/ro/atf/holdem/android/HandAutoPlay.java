package ro.atf.holdem.android;

/** Invalidates a pending human-seat decision when delegation is stopped or restarted. */
final class HandAutoPlay {
    private boolean enabled;
    private long revision;

    boolean isEnabled() {
        return enabled;
    }

    boolean setEnabled(boolean value) {
        if (enabled == value) return false;
        enabled = value;
        revision++;
        return true;
    }

    void change(boolean value, boolean humanTurn, Runnable refreshHumanTurn) {
        if (setEnabled(value) && humanTurn) refreshHumanTurn.run();
    }

    long revision() {
        return revision;
    }

    boolean accepts(long requestedRevision) {
        return enabled && revision == requestedRevision;
    }
}
