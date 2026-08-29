package io.github.iamstarter.persistence;

import io.github.iamstarter.authorization.TemplateVersionStatus;

public record PermissionTemplateVersionRow(long versionId, long templateId, int versionNumber,
                                           TemplateVersionStatus status) {
}
