package io.github.muer.persistence;

import io.github.muer.authorization.TemplateVersionStatus;

public record PermissionTemplateVersionRow(long versionId, long templateId, int versionNumber,
                                           TemplateVersionStatus status) {
}
