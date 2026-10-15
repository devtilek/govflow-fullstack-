package kz.govflow;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
@Entity @Table(name="service_requests") public class Request {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @NotBlank @Size(max=120) public String title;
 @NotBlank @Size(max=2000) @Column(length=2000) public String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status=Status.NEW;
 @Column(nullable=false,updatable=false) public Instant createdAt=Instant.now();
 public enum Status {NEW,IN_PROGRESS,RESOLVED}
}