package io.github.muer.core.port;

import io.github.muer.core.model.IamUser;

import java.util.List;
import java.util.Optional;

/**
 * Read-side user query surface used by the IAM administration console.
 *
 * <p>This SPI is intentionally separate from {@link IamUserRepository} so that
 * existing consumer persistence implementations keep working without change.
 */
public interface UserQueryRepository {

    Optional<IamUser> findById(long userId);

    /**
     * Cursor-paginated user search.
     *
     * <p>All filter arguments are optional; a {@code null} filter is ignored.
     * {@code username} is matched as a case-insensitive substring. Ordering is by
     * ascending user id strictly after {@code afterUserId}.
     */
    List<IamUser> search(long afterUserId, int limit, String username, String userType, Boolean enabled);
}
