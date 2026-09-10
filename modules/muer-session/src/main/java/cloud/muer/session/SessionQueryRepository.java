package cloud.muer.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-side session query surface used by the IAM administration console.
 *
 * <p>This SPI is intentionally separate from {@link SessionRepository} so that
 * existing consumer persistence implementations keep working without change.
 * Returned sessions never contain token material.
 */
public interface SessionQueryRepository {

    Optional<AuthSession> findById(String sessionId);

    List<AuthSession> findByUserId(long userId);

    /**
     * Cursor-paginated session search across all users.
     *
     * <p>Filter arguments ({@code userId}, {@code clientType}, {@code active}) are
     * optional; {@code null} disables the predicate. When {@code active} is
     * {@code true} only sessions that are not revoked and not expired are returned;
     * when {@code false} only sessions that are revoked or expired are returned.
     *
     * <p>The cursor is the composite {@code (loginAt, sessionId)} of the last
     * visible row: rows are ordered by {@code loginAt DESC, sessionId DESC} and the
     * next page must start strictly after the composite key. A {@code null}
     * {@code afterSessionId} (or a {@code null} {@code afterLoginAt}) means "from
     * the beginning".
     */
    List<AuthSession> search(Instant afterLoginAt, String afterSessionId, int limit,
                             Long userId, String clientType, Boolean active);
}
