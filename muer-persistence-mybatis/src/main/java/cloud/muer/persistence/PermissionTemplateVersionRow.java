package cloud.muer.persistence;

import cloud.muer.authorization.TemplateVersionStatus;

public record PermissionTemplateVersionRow(long versionId, long templateId, int versionNumber,
                                           TemplateVersionStatus status) {
}
