package cloud.muer.autoconfigure.web;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/** Registers the IAM annotation interceptor without owning host route security matchers. */
public final class IamAuthorizationWebMvcConfiguration implements WebMvcConfigurer {
    private final IamAuthorizationInterceptor interceptor;

    public IamAuthorizationWebMvcConfiguration(IamAuthorizationInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor must not be null");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
