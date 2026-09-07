package io.github.muer.persistence.mapper;

import io.github.muer.persistence.OverviewRow;

/**
 * Read-only operational overview counters.
 */
public interface IamOverviewMapper {
    OverviewRow load();
}
