package cloud.muer.authorization;

/** Result of an explicit first-administrator bootstrap invocation. */
public record AdministrationBootstrapResult(long templateId, long templateVersionId, long profileId,
                                            boolean created) {
}
