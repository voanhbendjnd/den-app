package com.denhub.web.rest.errors;

import java.net.URI;

public final class ErrorConstants {
    public static final String ERR_CONCURRENCY_FAILURE = "error.concurrencyFailure";
    public static final String ERR_VALIDATION = "error.validation";
    public static final String PROBLEM_BASE_URL = "https://www.denhub.com/problem";
    public static final URI DEFAULT_TYPE = URI.create(PROBLEM_BASE_URL + "/problem-with-message");
    public static final URI CONSTRAINT_VIOLATION_TYPE = URI.create(PROBLEM_BASE_URL + "/constraint-violation");
    public static final URI INVALID_PASSWORD_TYPE = URI.create(PROBLEM_BASE_URL + "/invalid-password");
    public static final URI EMAIL_ALREADY_USED_TYPE = URI.create(PROBLEM_BASE_URL + "/email-already-used");
    public static final URI LOGIN_ALREADY_USED_TYPE = URI.create(PROBLEM_BASE_URL + "/login-already-used");
    public static final URI NOT_FOUND = URI.create(PROBLEM_BASE_URL + "/not-found");
    public static final URI NOT_AUTHORIZED = URI.create(PROBLEM_BASE_URL + "/authorize");
    public static final URI ACCESS_DENIED = URI.create(PROBLEM_BASE_URL + "/access-denied");
    public static final URI INVALID_SESSION_TYPE = URI.create(PROBLEM_BASE_URL + "/invalid-session-type");
    public static final URI BAD_REQUEST_TYPE = URI.create(PROBLEM_BASE_URL + "/bad-request");
    public static final URI CONFLICT = URI.create(PROBLEM_BASE_URL + "/conflict");
    public static final URI FRIEND_BLOCKED_ALREADY = URI.create(PROBLEM_BASE_URL + "/friend-blocked-already");
    public static final URI FRIEND_ACCEPT_ALREADY = URI.create(PROBLEM_BASE_URL + "/friend-accept-already");

    public static final URI FRIEND_REQUEST_ALREADY_SENT = URI.create(PROBLEM_BASE_URL + "/friend-request-already-sent");
    private ErrorConstants() {}
}