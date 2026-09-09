package cloud.muer.showcase;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleResourceHierarchyAdapterTest {
    private final ExampleResourceHierarchyAdapter hierarchy = new ExampleResourceHierarchyAdapter();

    @Test
    void maps_an_order_parent_path_to_its_department_scope() {
        var order = new ResourceDescriptor("ORDER", "9001", List.of("DEPARTMENT:501"), Map.of());

        assertTrue(hierarchy.isWithinScope(order,
                new ResourceScope("DEPARTMENT", "501", ScopeAccess.READ)));
        assertFalse(hierarchy.isWithinScope(order,
                new ResourceScope("DEPARTMENT", "999", ScopeAccess.READ)));
    }
}
