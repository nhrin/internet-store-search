package edu.nix.search.internetstoresearch.controllers;

import edu.nix.search.internetstoresearch.models.Item;
import edu.nix.search.internetstoresearch.services.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@CrossOrigin
@RestController
public class DataAccessController {

    @Autowired
    @Qualifier("hibernateSearchService")
    private SearchService service;

    @GetMapping("/search")
    @ResponseBody
    public Map<String, Object> findItemsByContent(@RequestParam("q") String content, @RequestParam("fq") Optional<Set<String>> fq) {
        return service.findByDescriptionAndItemTitle(content, fq);
    }

    @GetMapping("/browse")
    @ResponseBody
    public Map<String, Object> findItemsByCategory(@RequestParam("category") String category, @RequestParam("fq") Optional<Set<String>> fq) {
        return service.findByCategory(category, fq);
    }

    @PostMapping("/update")
    public void addItem(@RequestBody Item item) {
        service.addItem(item);
    }

}
