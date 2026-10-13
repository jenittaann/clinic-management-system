package com.camillus.clinic.hospital;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(columnNames = {"hospital_id", "name"}))
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hospital_id", nullable = false) private Hospital hospital;
    @Column(nullable = false, length = 160) private String name;
    @Column(columnDefinition = "text") private String description;
    protected Department() {}
    public Department(Hospital hospital, String name, String description) { this.hospital = hospital; this.name = name; this.description = description; }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Hospital getHospital() { return hospital; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
