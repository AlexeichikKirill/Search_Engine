package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "lemma")
public class Lemma implements Comparable<Lemma> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional=false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "lemma", columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @Override
    public int compareTo(Lemma lemma) {
        if (frequency > lemma.getFrequency()) {
            return 1;
        } else if (frequency < lemma.getFrequency()) {
            return -1;
        }
        return 0;
    }
}