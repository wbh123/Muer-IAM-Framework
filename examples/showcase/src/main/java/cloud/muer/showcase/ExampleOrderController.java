package cloud.muer.showcase;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationRequest;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ScopeAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/example/orders")
public final class ExampleOrderController {
    private final AuthorizationEngine authorization;
    private final Map<String, ExampleOrder> orders = new ConcurrentHashMap<>(Map.of(
            "9001", new ExampleOrder("9001", "501", "PENDING"),
            "9002", new ExampleOrder("9002", "502", "PENDING")));

    public ExampleOrderController(AuthorizationEngine authorization) {
        this.authorization = authorization;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ExampleOrder> getOrder(@PathVariable("orderId") String orderId) {
        return authorize(orderId, "order.read", ScopeAccess.READ, false);
    }

    @PostMapping("/{orderId}/approve")
    public ResponseEntity<ExampleOrder> approveOrder(@PathVariable("orderId") String orderId) {
        return authorize(orderId, "order.approve", ScopeAccess.WRITE, true);
    }

    private ResponseEntity<ExampleOrder> authorize(String orderId, String permission,
                                                   ScopeAccess access, boolean approve) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var order = orders.get(orderId);
        if (order == null) return ResponseEntity.notFound().build();
        var resource = new ResourceDescriptor(
                "ORDER", order.orderId(), List.of("DEPARTMENT:" + order.departmentId()), Map.of());
        var request = new AuthorizationRequest(
                permission, principal.identityDomain(), principal.clientType(), resource, access);
        if (!authorization.decide(principal, request).allowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (approve) {
            order = order.approve();
            orders.put(orderId, order);
        }
        return ResponseEntity.ok(order);
    }

    private static IamPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
