package edu.nix.search.internetstoresearch.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;

@Configuration
public class HibernateSearchIndexBuild implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LogManager.getLogger(HibernateSearchIndexBuild.class);

    @Autowired
    private EntityManager entityManager;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Started Initializing Indexes");
        SearchSession searchSession = Search.session(entityManager);
        MassIndexer indexer = searchSession.massIndexer()
                .idFetchSize(150)
                .batchSizeToLoadObjects(25)
                .threadsToLoadObjects(12);
        try {
            indexer.startAndWait();
        } catch (InterruptedException e) {
            logger.error("Failed to load data from database. Stacktrace: {}", Arrays.toString(e.getStackTrace()));
            Thread.currentThread().interrupt();
        }
        logger.info("Completed Indexing");
    }
}
