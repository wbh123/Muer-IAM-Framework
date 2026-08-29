package com.wust.iam.persistence;

import com.wust.iam.authorization.TemplateVersionStatus;

public record PermissionTemplateVersionRow(long versionId, long templateId, int versionNumber,
                                           TemplateVersionStatus status) {
}
