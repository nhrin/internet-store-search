package edu.nix.search.internetstoresearch.services.elasticsearchutils;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;


@Component
public final class ElasticsearchFacetsServiceImpl implements ElasticsearchFacetsService {

    @Getter
    @Value("${search.term-facets}")
    private Set<String> termFacets;

    @Value("${search.range-facets}")
    private Set<String> rangeFacet;

    @Override
    public Map<String, Aggregation> buildTermFacets(Set<String> termFacets) {
        Map<String, Aggregation> aggregations = new HashMap<>();
        for (String field : termFacets) {
            Aggregation aggregation = AggregationBuilders.terms(f -> f.field(field));
            aggregations.put(field, aggregation);
        }
        return aggregations;
    }

    @Override
    public RangeQuery buildRangeFilters(String key, String fromTo) {
        String from = StringUtils.substringBetween(fromTo, "from", "to");
        String to = StringUtils.substringAfter(fromTo, "to");
        return QueryBuilders.range()
                .field(key)
                .from(from)
                .to(to)
                .build();
    }

    @Override
    public Optional<Map<String, String>> matchFilterQuery(Optional<Set<String>> filters) {
        if (filters.isPresent()) {
            Map<String, String> matchedFilters = new HashMap<>();
            for (String filter : filters.get()) {
                String facetField = StringUtils.substringBefore(filter, ":");
                if (!termFacets.contains(facetField) && !rangeFacet.contains(facetField)) {
                    throw new IllegalArgumentException("Invalid filter field!");
                }
                String facetValue = StringUtils.substringAfter(filter, ":");
                matchedFilters.put(facetField, facetValue);
            }
            return Optional.of(matchedFilters);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<Query> buildFilterQueries(Map<String, String> filters) {
        List<Query> fq = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (rangeFacet.contains(entry.getKey())) {
                Query rangeQuery = buildRangeFilters(entry.getKey(), entry.getValue())._toQuery();
                fq.add(rangeQuery);
            } else {
                Query termQuery = QueryBuilders.term(f -> f
                        .field(entry.getKey())
                        .value(entry.getValue()));
                fq.add(termQuery);
            }
        }
        return fq;
    }
}
