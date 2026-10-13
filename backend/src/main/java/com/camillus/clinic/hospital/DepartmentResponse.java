package com.camillus.clinic.hospital;

public record DepartmentResponse(String id, String name, String description) {
    static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getPublicId(),
                department.getName(),
                department.getDescription()
        );
    }
}
