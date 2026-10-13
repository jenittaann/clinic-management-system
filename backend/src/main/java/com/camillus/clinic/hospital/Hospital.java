package com.camillus.clinic.hospital;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "hospitals")
public class Hospital {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId = UUID.randomUUID().toString();
    @Column(nullable = false) private String name;
    private String type;
    private String area;
    private String address;
    private Integer beds;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "source_name") private String sourceName;
    @Column(name = "source_url", length = 1000) private String sourceUrl;
    @Column(name = "source_record_id") private String sourceRecordId;
    @OneToMany(mappedBy = "hospital", fetch = FetchType.LAZY)
    @OrderBy("name ASC") private List<Department> departments = new ArrayList<>();

    protected Hospital() {}
    public Hospital(String name, String type, String area, String address, Integer beds, String description) {
        this.name = name; this.type = type; this.area = area; this.address = address; this.beds = beds; this.description = description;
    }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getArea() { return area; }
    public String getAddress() { return address; }
    public Integer getBeds() { return beds; }
    public String getDescription() { return description; }
    public List<Department> getDepartments() { return departments; }
}
