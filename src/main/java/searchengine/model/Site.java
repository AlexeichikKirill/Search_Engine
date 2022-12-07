package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "site", uniqueConstraints = {
        @UniqueConstraint(name = "uc_site_url", columnNames = {"url"})
})
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String url;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;
}