package searchengine.repositoryes;

import searchengine.model.Index;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    Index findByLemmaAndPage(Lemma lemma, Page page);
    List<Index> findAllByPage(Page page);
    List<Index> findAllByLemma(Lemma lemma);
}