package edu.nix.search.internetstoresearch.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import edu.nix.search.internetstoresearch.models.Item;
import edu.nix.search.internetstoresearch.repository.MongoItemRepository;
import edu.nix.search.internetstoresearch.services.elasticsearchutils.ElasticsearchFacetsService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


@Component
@RequiredArgsConstructor
public class ElasticItemService implements SearchService {

    private static final Logger logger = LogManager.getLogger(ElasticItemService.class);

    private final MongoItemRepository ITEM_REPOSITORY;
    private final ElasticsearchClient ELASTICSEARCH_CLIENT;
    private final ElasticsearchFacetsService ELASTICSEARCH_FACETS_SERVICE;

    @Value("#{${search.search-fields}}")
    private Map<String, Float> fields;

    @Value("${elastic.index}")
    private String index;

    private static final String BROWSE_FILTER = "category";

    @SneakyThrows
    @Override
    public Map<String, Object> findByDescriptionAndItemTitle(String query, Optional<Set<String>> filters) {
        logger.debug("Input params: q - {}, fq - {}", query, filters);
        SearchRequest request = new SearchRequest.Builder()
                .index(index)
                .query(buildSearchQuery(fields, query, ELASTICSEARCH_FACETS_SERVICE.matchFilterQuery(filters)).build()._toQuery())
                .aggregations(ELASTICSEARCH_FACETS_SERVICE.buildTermFacets(ELASTICSEARCH_FACETS_SERVICE.getTermFacets()))
                .build();

        return prepareSearchResult(request);
    }

    @SneakyThrows
    @Override
    public Map<String, Object> findByCategory(String query, Optional<Set<String>> filters) {
        logger.debug("Input params: q - {}, fq - {}", query, filters);
        Set<String> facets = ELASTICSEARCH_FACETS_SERVICE.getTermFacets();
        facets.remove(BROWSE_FILTER);
        SearchRequest request = new SearchRequest.Builder()
                .index(index)
                .query(buildBrowseQuery(query, filters).build()._toQuery())
                .aggregations(ELASTICSEARCH_FACETS_SERVICE.buildTermFacets(facets))
                .build();

//        AggregationBuilder aggregation =
//                AggregationBuilders
//                        .dateRange("agg")
//                        .field("dateOfBirth")
//                        .format("yyyy")
//                        .addUnboundedTo("1950")    // from -infinity to 1950 (excluded)
//                        .addRange("1950", "1960")  // from 1950 to 1960 (excluded)
//                        .addUnboundedFrom("1960");

        return prepareSearchResult(request);
    }

    @SneakyThrows
    @Override
    public Item addItem(Item item) {
        Item savedItem = ITEM_REPOSITORY.save(item);
        ELASTICSEARCH_CLIENT.index(i -> i
                .index(index)
                .document(savedItem)
        );
        logger.info("item {} was added to index", savedItem.getItemTitle());
        return savedItem;
    }

    private Map<String, Object> prepareTermsFilters (String name, Aggregate buckets) {
        List<Object> buketList = new ArrayList<>();
        for (StringTermsBucket bucket : buckets.sterms().buckets().array()) {
            Map<String, Object> values = new HashMap<>();
            values.put("name", bucket.key());
            values.put("count", bucket.docCount());
            values.put("fq" , StringUtils.join("+", name, ":", bucket.key()));
            buketList.add(values);
        }
        return Map.of(name, buketList);
    }

    private BoolQuery.Builder buildSearchQuery(Map<String, Float> fields, String query) {
        List<Query> matchQueries = new ArrayList<>();
        BoolQuery.Builder boolQuery = QueryBuilders.bool();
        for (Map.Entry<String, Float> entry : fields.entrySet()) {
            Query q = QueryBuilders
                    .match()
                    .field(entry.getKey())
                    .query(query)
                    .boost(entry.getValue())
                    .operator(Operator.And)
                    .build()._toQuery();
            matchQueries.add(q);
        }
        return boolQuery.should(matchQueries);
    }

    private BoolQuery.Builder buildSearchQuery(Map<String, Float> fields, String query,
                                               Optional<Map<String, String>> filters) {
        if (filters.isPresent()) {
            List<Query> fq = ELASTICSEARCH_FACETS_SERVICE.buildFilterQueries(filters.get());
            return buildSearchQuery(fields, query).filter(fq);
        } else {
            return buildSearchQuery(fields, query);
        }
    }

    private BoolQuery.Builder buildBrowseQuery(String query, Optional<Set<String>> fq) {
        BoolQuery.Builder boolQuery = QueryBuilders.bool();
        Query categoryFilter = TermQuery.of(q -> q
                        .field(BROWSE_FILTER)
                        .value(query))
                ._toQuery();
        boolQuery.filter(categoryFilter);
        if (fq.isPresent()) {
            boolQuery.filter(ELASTICSEARCH_FACETS_SERVICE.buildFilterQueries(ELASTICSEARCH_FACETS_SERVICE.matchFilterQuery(fq).get()));
        }
        return boolQuery;
    }

    private List<Long> getIdsArray(SearchResponse<Item> response) {
        return response.hits().hits().stream()
                .map(hit -> hit.source().getId())
                .collect(Collectors.toList());
    }

    private List<Item> getItemsByIds(List<Long> ids) {
        List<Item> items = ITEM_REPOSITORY.findByItemId(ids).orElseThrow();
        items.sort(Comparator.comparing(v->ids.indexOf(v.getId()))); // sort by relevance
        return items;
    }

    @SneakyThrows
    private Map<String, Object> prepareSearchResult(SearchRequest request) {
        logger.info("Executing elastic query: {}", request.query());
        SearchResponse<Item> response = ELASTICSEARCH_CLIENT.search(request, Item.class);
        Map<String, Object> result = new HashMap<>();
        result.put("items", getItemsByIds(getIdsArray(response)));
        List<Object> filterList = new ArrayList<>();
        result.put("filters", filterList);
        response.aggregations().entrySet().forEach(e -> filterList.add(prepareTermsFilters(e.getKey(), e.getValue())));
        return result;
    }
}
