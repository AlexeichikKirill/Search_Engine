package searchengine.repositoryes;

import searchengine.model.Page;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    List<Page> findAllBySite(Site site);
    Page findByPath(String path);
    long countBySite(Site site);
}