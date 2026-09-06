package io.github.iamstarter.autoconfigure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/** Writes the starter's safe default problem-detail response. */
public final class ProblemDetailIamAuthorizationFailureHandler implements IamAuthorizationFailureHandler {
    private static final String GENERIC_DETAIL = "The request could not be authorized.";

    private final ObjectMapper json;

    public ProblemDetailIamAuthorizationFailureHandler(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    @Override
    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, IamAuthorizationFailure failure) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), GENERIC_DETAIL);
        problem.setProperty("code", failure.code());
        problem.setProperty("path", request.getRequestURI());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getWriter(), problem);
    }
}
