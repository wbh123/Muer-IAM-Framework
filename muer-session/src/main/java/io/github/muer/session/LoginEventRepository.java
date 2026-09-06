package io.github.iamstarter.session;

@FunctionalInterface
public interface LoginEventRepository {
    void append(LoginEvent event);
}
