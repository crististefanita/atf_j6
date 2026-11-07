package ro.atf.holdem.android;

import ro.cardtable.ui.table.TableSeatLayout;

/** Projects shared perimeter anchors into two rows with room for a full-width board between them. */
record CompactSeatRows(int[] top, int[] bottom) {
    static final int EMPTY = -1;

    static CompactSeatRows forSeats(int count) {
        if (count == 2) return new CompactSeatRows(new int[]{1}, new int[]{0});
        int[] top = {EMPTY, EMPTY, EMPTY};
        int[] bottom = {EMPTY, EMPTY, EMPTY};
        var anchors = TableSeatLayout.positions(count);
        for (int seat = 0; seat < anchors.length; seat++) {
            switch (anchors[seat]) {
                case NORTH_WEST -> top[0] = seat;
                case NORTH -> top[1] = seat;
                case NORTH_EAST -> top[2] = seat;
                case SOUTH_WEST -> bottom[0] = seat;
                case SOUTH -> bottom[1] = seat;
                case SOUTH_EAST -> bottom[2] = seat;
                default -> throw new IllegalArgumentException("Anchor has no compact row: " + anchors[seat]);
            }
        }
        return new CompactSeatRows(top, bottom);
    }
}
