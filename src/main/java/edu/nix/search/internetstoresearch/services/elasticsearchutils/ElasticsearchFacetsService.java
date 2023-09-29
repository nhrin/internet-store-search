package edu.nix.search.internetstoresearch.services.elasticsearchutils;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ElasticsearchFacetsService {

    Map<String, Aggregation> buildTermFacets(Set<String> termFacets);

    RangeQuery buildRangeFilters(String key, String fromTo);

    Optional<Map<String, String>> matchFilterQuery(Optional<Set<String>> filters);

    List<Query> buildFilterQueries(Map<String, String> filters);
    Set<String> getTermFacets();
}
