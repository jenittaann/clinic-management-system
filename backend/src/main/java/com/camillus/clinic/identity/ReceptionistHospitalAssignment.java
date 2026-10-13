package com.camillus.clinic.identity;

import com.camillus.clinic.hospital.Hospital;
import jakarta.persistence.*;

@Entity
@Table(name = "receptionist_hospital_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_receptionist_hospital", columnNames = {"receptionist_user_id", "hospital_id"}))
public class ReceptionistHospitalAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receptionist_user_id", nullable = false) private UserAccount receptionist;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id", nullable = false) private Hospital hospital;

    protected ReceptionistHospitalAssignment() {}
    public ReceptionistHospitalAssignment(UserAccount receptionist, Hospital hospital) {
        this.receptionist = receptionist;
        this.hospital = hospital;
    }
    public UserAccount getReceptionist() { return receptionist; }
    public Hospital getHospital() { return hospital; }
}
