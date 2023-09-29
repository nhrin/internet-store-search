package edu.nix.search.internetstoresearch.services;

import edu.nix.search.internetstoresearch.models.Item;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.engine.search.aggregation.SearchAggregation;
import org.hibernate.search.engine.search.aggregation.dsl.SearchAggregationFactory;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class HibernateSearchService implements SearchService {

    private static final Logger logger = LogManager.getLogger(HibernateSearchService.class);

    @Value("${search.term-facets}")
    private Set<String> termFacets;

    @Value("${search.range-facets}")
    private Set<String> rangeFacets;

    @Value("#{${search.search-fields}}")
    private Map<String, Float> fields;

    private static final String BROWSE_FILTER = "category";

    private final EntityManager entityManager;

    @Override
    public Map<String, Object> findByDescriptionAndItemTitle(String q, Optional<Set<String>> fq) {
        logger.debug("Input params: q - {}, fq - {}", q, fq);
        SearchSession searchSession = Search.session(entityManager);
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> aggregations = new HashMap<>();
        Map<String, SearchAggregation<Map<String, Long>>> termAggregationsMap = prepareTermAggregations();

        SearchQueryOptionsStep<?, Item, SearchLoadingOptionsStep, ?, ?> searchResult = searchSession.search(Item.class)
                .where(prepareSearchBoolPredicate(q, fq, fields));

        for (Map.Entry<String, SearchAggregation<Map<String, Long>>> searchAggregation : termAggregationsMap.entrySet()) {
            searchResult.aggregation(AggregationKey.of(searchAggregation.getKey()), searchAggregation.getValue());
        }

        SearchResult<Item> items = searchResult.fetchAll();

        for (Map.Entry<String, SearchAggregation<Map<String, Long>>> searchAggregation : termAggregationsMap.entrySet()) {
            aggregations.put(searchAggregation.getKey(), items.aggregation(AggregationKey.of(searchAggregation.getKey())));
        }

        results.put("Items", searchResult.fetchAllHits());
        results.put("Facets", aggregations);
        return results;
    }

    @Override
    public Map<String, Object> findByCategory(String q, Optional<Set<String>> fq) {
        logger.debug("Input params: q - {}, fq - {}", q, fq);
        SearchSession searchSession = Search.session(entityManager);
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> aggregations = new HashMap<>();
        Map<String, SearchAggregation<Map<String, Long>>> termAggregationsMap = prepareTermAggregations();

        SearchQueryOptionsStep<?, Item, SearchLoadingOptionsStep, ?, ?> searchResult = searchSession.search(Item.class)
                .where(prepareSearchBoolPredicate(q, fq, Map.of(BROWSE_FILTER, 1.0f)));

        for (Map.Entry<String, SearchAggregation<Map<String, Long>>> searchAggregation : termAggregationsMap.entrySet()) {
            if (!searchAggregation.getKey().equals(BROWSE_FILTER)) {
                searchResult.aggregation(AggregationKey.of(searchAggregation.getKey()), searchAggregation.getValue());
            }
        }

        SearchResult<Item> items = searchResult.fetchAll();

        for (Map.Entry<String, SearchAggregation<Map<String, Long>>> searchAggregation : termAggregationsMap.entrySet()) {
            if (!searchAggregation.getKey().equals(BROWSE_FILTER)) {
                aggregations.put(searchAggregation.getKey(), items.aggregation(AggregationKey.of(searchAggregation.getKey())));
            }
        }

        results.put("Items", searchResult.fetchAllHits());
        results.put("Facets", aggregations);
        return results;
    }

    @Override
    @Transactional
    public Item addItem(Item item) {
        entityManager.persist(item);
        logger.info("item {} was added to index", item.getItemTitle());
        return entityManager.find(Item.class, item.getId());
    }

    private SearchPredicate prepareSearchBoolPredicate(String q, Optional fq, Map<String, Float> fields) {
        SearchSession searchSession = Search.session(entityManager);
        SearchScope<Item> scope = searchSession.scope(Item.class);
        SearchPredicateFactory factory = scope.predicate();
        List<SearchPredicate> predicates = new ArrayList<>();
        Optional<Map<String, String>> validateFilters = validateFilters(fq);
        for (Map.Entry<String, Float> field : fields.entrySet()) {
            SearchPredicate predicate = factory.match()
                    .fields(field.getKey()).boost(field.getValue())
                    .matching(q)
                    .toPredicate();
            predicates.add(predicate);
        }

        BooleanPredicateClausesStep<?> booleanJunction = factory.bool();

        for (SearchPredicate predicate : predicates) {
            booleanJunction.should(predicate);
        }
        addFiltersToBooleanPredicate(booleanJunction, validateFilters);


        return booleanJunction.toPredicate();
    }

    private Map<String, SearchAggregation<Map<String, Long>>> prepareTermAggregations() {
        SearchSession searchSession = Search.session(entityManager);
        SearchScope<Item> scope = searchSession.scope(Item.class);
        SearchAggregationFactory factory = scope.aggregation();
        Map<String, SearchAggregation<Map<String, Long>>> result = new HashMap<>();

        for (String field : termFacets) {
            result.put(field, factory.terms().field(field, String.class).toAggregation());
        }
        return result;
    }

    private Optional<Map<String, String>> validateFilters(Optional<Set<String>> filters) {
        if (filters.isPresent()) {
            Map<String, String> matchedFilters = new HashMap<>();
            for (String filter : filters.get()) {
                String filterField = StringUtils.substringBefore(filter, ":");
                if (!termFacets.contains(filterField) && !rangeFacets.contains(filterField)) {
                    logger.error("Invalid filter field {}", filter);
                    throw new IllegalArgumentException("Invalid filter field!");
                }
                String filterValue = StringUtils.substringAfter(filter, ":");
                matchedFilters.put(filterField, filterValue);
            }
            return Optional.of(matchedFilters);
        } else {
            return Optional.empty();
        }
    }

    private void addFiltersToBooleanPredicate(BooleanPredicateClausesStep<?> booleanJunction, Optional<Map<String, String>> fq) {
        if (!fq.isPresent()) {
            logger.info("Filters are empty");
            return;
        }
        for (Map.Entry<String, String> filter : fq.get().entrySet()) {
            if (rangeFacets.contains(filter.getKey())) {
                addRangeFiltersToBooleanPredicate(booleanJunction, filter);
            } else {
                booleanJunction.filter(f -> f.terms()
                        .field(filter.getKey())
                        .matchingAll(filter.getValue()));
            }
        }
    }

    private void addRangeFiltersToBooleanPredicate(BooleanPredicateClausesStep<?> booleanJunction, Map.Entry<String, String> rangeFilter) {
        String from = StringUtils.substringBetween(rangeFilter.getValue(), "from", "to");
        String to = StringUtils.substringAfter(rangeFilter.getValue(), "to");
        booleanJunction.filter(f -> f.range()
                .field(rangeFilter.getKey())
                .between(Double.parseDouble(from), Double.parseDouble(to)));
    }
}
