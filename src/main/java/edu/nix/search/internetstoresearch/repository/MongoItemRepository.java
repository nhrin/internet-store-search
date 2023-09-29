package edu.nix.search.internetstoresearch.repository;

import edu.nix.search.internetstoresearch.models.Item;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MongoItemRepository extends MongoRepository<Item, Long> {

    @Query(value = "{'id': {$in: ?0}}")
    Optional<List<Item>> findByItemId(List<Long> id);
}
