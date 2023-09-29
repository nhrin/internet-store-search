package edu.nix.search.internetstoresearch.services;

import edu.nix.search.internetstoresearch.models.Item;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SearchService {
    Map<String, Object> findByDescriptionAndItemTitle(String q, Optional<Set<String>> fq);

    Map<String, Object> findByCategory(String query, Optional<Set<String>> filters);

    Item addItem(Item item);
}
