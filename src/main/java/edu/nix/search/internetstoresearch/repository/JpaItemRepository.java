package edu.nix.search.internetstoresearch.repository;

import edu.nix.search.internetstoresearch.models.Item;
import org.springframework.data.repository.CrudRepository;

public interface JpaItemRepository extends CrudRepository<Item, Long> {

}
