package cloud.muer.authentication;

public record LoginRequest(String username, String password, String clientType, String clientInstance,
                           String ipAddress, String userAgent, String deviceType, String osName,
                           String browserName, String appVersion, String requestId) {
    public LoginRequest {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username must not be blank");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("password must not be blank");
        if (clientType == null || clientType.isBlank()) throw new IllegalArgumentException("clientType must not be blank");
    }

    public LoginRequest(String username, String password, String clientType) {
        this(username, password, clientType, null);
    }

    public LoginRequest(String username, String password, String clientType, String clientInstance) {
        this(username, password, clientType, clientInstance, null, null, null, null, null, null, null);
    }
}
