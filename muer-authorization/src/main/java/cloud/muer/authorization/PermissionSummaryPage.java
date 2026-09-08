package cloud.muer.authorization;

import java.util.List;
import java.util.Objects;

/**
 * One page of permission registry entries plus the opaque numeric cursor of the
 * last returned entry.
 */
public record PermissionSummaryPage(List<PermissionSummary> items, long lastPermissionId) {
    public PermissionSummaryPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (lastPermissionId < 0) throw new IllegalArgumentException("lastPermissionId must not be negative");
    }
}
