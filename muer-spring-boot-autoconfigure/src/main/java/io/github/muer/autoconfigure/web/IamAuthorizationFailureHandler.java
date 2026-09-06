package io.github.muer.autoconfigure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Writes a host-controlled response for a declarative MVC IAM failure. */
@FunctionalInterface
public interface IamAuthorizationFailureHandler {
    void write(HttpServletRequest request, HttpServletResponse response,
               int status, IamAuthorizationFailure failure) throws IOException;
}
