package com.camillus.clinic.hospital;

import java.util.List;

public record HospitalResponse(String id, String name, String type, String area, String address,
                               Integer beds, String description, List<String> departmentIds) {
    static HospitalResponse from(Hospital hospital) {
        return new HospitalResponse(hospital.getPublicId(), hospital.getName(), hospital.getType(),
                hospital.getArea(), hospital.getAddress(), hospital.getBeds(), hospital.getDescription(),
                hospital.getDepartments().stream().map(Department::getPublicId).toList());
    }
}
