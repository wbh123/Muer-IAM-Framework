package cloud.muer.acceptance;

import cloud.muer.autoconfigure.web.RequirePermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/acceptance/resources")
public class AcceptanceResourceController {
    @GetMapping("/{id}")
    @RequirePermission("acceptance:read")
    public ResponseEntity<String> read(@PathVariable String id) {
        return ResponseEntity.ok("resource-" + id);
    }
}
