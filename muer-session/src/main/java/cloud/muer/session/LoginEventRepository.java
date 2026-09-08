package cloud.muer.session;

@FunctionalInterface
public interface LoginEventRepository {
    void append(LoginEvent event);
}
