package searchengine.repositoryes;

import searchengine.model.Lemma;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    long countBySite(Site site);
    Lemma findByLemmaAndSite(String lemma, Site site);
    List<Lemma> findAllBySite(Site site);
}