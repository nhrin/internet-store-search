package edu.nix.search.internetstoresearch.services;

import edu.nix.search.internetstoresearch.models.Item;
import edu.nix.search.internetstoresearch.repository.MongoItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolrItemService implements SearchService {

    private static final Logger logger = LogManager.getLogger(ElasticItemService.class);

    @Value("${search.term-facets}")
    private Set<String> termFacets;

    @Value("${search.range-facets}")
    private Set<String> rangeFacet;

    @Value("#{${search.search-fields}}")
    private Map<String, Float> fields;

    private static final String BROWSE_FILTER = "category";

    private final MongoItemRepository ITEM_REPOSITORY;
    private final SolrClient SOLR_CLIENT;

    @Override
    @SneakyThrows
    public Map<String, Object> findByDescriptionAndItemTitle(String q, Optional<Set<String>> fq) {
        logger.debug("Input params: q - {}, fq - {}", q, fq);
        SolrQuery query = new SolrQuery();
        query.add("q", buildSearchQuery(q));
        for (String facet : termFacets) {
            query.addFacetField(facet);
        }
        addFiltersToQuery(query, fq);
        return prepareSearchResults(query);
    }

    @Override
    @SneakyThrows
    public Map<String, Object> findByCategory(String q, Optional<Set<String>> fq) {
        logger.debug("Input params: q - {}, fq - {}", q, fq);
        SolrQuery query = new SolrQuery();
        query.add("q", BROWSE_FILTER + ":\"" + q + "\"");
        for (String facet : termFacets) {
            if (!facet.equals(BROWSE_FILTER)) {
                query.addFacetField(facet);
            }
        }
        addFiltersToQuery(query, fq);
        return prepareSearchResults(query);
    }

    @Override
    @SneakyThrows
    public Item addItem(Item item) {
        Item savedItem = ITEM_REPOSITORY.save(item);
        SOLR_CLIENT.add(prepareSolrDocument(item));
        SOLR_CLIENT.commit();
        logger.info("item {} was added to index", savedItem.getItemTitle());
        return savedItem;
    }

    private Map<String, Object> prepareTermsFilters(String name, List<FacetField.Count> fields) {
        List<Object> bucketList = new ArrayList<>();
        for (FacetField.Count facetField : fields) {
            Map<String, Object> values = new HashMap<>();
            values.put("name", facetField.getName());
            values.put("count", facetField.getCount());
            values.put("fq", StringUtils.join("+", name, ":", facetField.getName()));
            bucketList.add(values);
        }
        return Map.of(name, bucketList);
    }

    private List<Long> getIdsArray(QueryResponse response) {
        return response.getResults().stream()
                .map(doc -> Long.parseLong(doc.get("id").toString()))
                .collect(Collectors.toList());
    }

    private List<Item> getItemsByIds(List<Long> ids) {
        List<Item> items = ITEM_REPOSITORY.findByItemId(ids).orElseThrow();
        items.sort(Comparator.comparing(v -> ids.indexOf(v.getId()))); // sort by relevance
        return items;
    }

    private String buildSearchQuery(String q) {
        logger.info("building search query q={}", q);
        StringJoiner stringJoiner = new StringJoiner(" OR ");
        for (Map.Entry<String, Float> field : fields.entrySet()) {
            stringJoiner.add(field.getKey() + ":" + q + "^" + field.getValue());
        }
        return stringJoiner.toString();
    }

    public Set<String> validateFilterQuery(Optional<Set<String>> filters) {
        if (filters.isPresent()) {
             for (String filter : filters.get()) {
                String facetField = StringUtils.substringBefore(filter, ":");
                if (!termFacets.contains(facetField) && !rangeFacet.contains(facetField)) {
                    logger.error("Invalid filter field {}", facetField);
                    throw new IllegalArgumentException("Invalid filter field " + facetField);
                }
                if (rangeFacet.contains(facetField)) {
                    filters.get().remove(filter);
                    String fromTo = StringUtils.substringAfter(filter, ":");
                    String from = StringUtils.substringBetween(fromTo, "from", "to");
                    String to = StringUtils.substringAfter(fromTo, "to");
                    StringBuilder rangeFilter = new StringBuilder(facetField);
                    rangeFilter.append(":[")
                            .append(from)
                            .append(" TO ")
                            .append(to)
                            .append("]");
                    filters.get().add(rangeFilter.toString());
                }
            }
        }
        return filters.get();
    }

    private void addFiltersToQuery (SolrQuery query, Optional<Set<String>> fq) {
        query.setFacetMinCount(1);
        if (fq.isPresent()) {
            Set<String> validatedFQ = validateFilterQuery(fq);
            for (String filter : validatedFQ) {
                query.addFilterQuery(filter);
            }
        }
    }

    @SneakyThrows
    private Map<String, Object> prepareSearchResults (SolrQuery query) {
        logger.info("Executing solr query: {}", query);
        QueryResponse response = SOLR_CLIENT.query(query);
        Map<String, Object> result = new HashMap<>();
        result.put("items", getItemsByIds(getIdsArray(response)));
        List<Object> filterList = new ArrayList<>();
        result.put("filters", filterList);
        response.getFacetFields().forEach(e -> filterList.add(prepareTermsFilters(e.getName(), e.getValues())));
        return result;
    }

    private SolrInputDocument prepareSolrDocument(Item item) {
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", item.getId());
        document.addField("itemtitle", item.getItemTitle());
        document.addField("description", item.getDescription());
        document.addField("category", item.getCategory());
        document.addField("manufacturer_country", item.getManufacturerCountry());
        document.addField("brand", item.getBrand());
        document.addField("price", item.getPrice());
        document.addField("in_stock", item.getInStock());
        document.addField("popularity", item.getPopularity());
        return document;
    }
}
