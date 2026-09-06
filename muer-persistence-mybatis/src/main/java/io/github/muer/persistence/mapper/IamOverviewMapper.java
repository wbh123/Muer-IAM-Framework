package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.persistence.OverviewRow;

/**
 * Read-only operational overview counters.
 */
public interface IamOverviewMapper {
    OverviewRow load();
}
