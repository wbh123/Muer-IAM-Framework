package cloud.muer.persistence.mapper;

import cloud.muer.persistence.OverviewRow;

/**
 * Read-only operational overview counters.
 */
public interface IamOverviewMapper {
    OverviewRow load();
}
