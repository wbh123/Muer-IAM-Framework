package io.github.iamstarter.example;

public record ExampleOrder(String orderId, String departmentId, String status) {
    ExampleOrder approve() {
        return new ExampleOrder(orderId, departmentId, "APPROVED");
    }
}
