package cloud.muer.autoconfigure.web;

import cloud.muer.core.model.ScopeAccess;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares an IAM permission and scope access requirement for an MVC handler. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {
    String value();

    ScopeAccess access() default ScopeAccess.READ;
}
