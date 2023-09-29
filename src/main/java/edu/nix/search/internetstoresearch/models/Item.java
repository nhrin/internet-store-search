package edu.nix.search.internetstoresearch.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;

@Document(collection = "items")
@Data
@Entity
@Indexed
@Table(name = "items")
public class Item {

    @Id
    @Transient
    private String _id; // for mongodb

    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Field("item_title")
    @JsonProperty("item_title")
    @FullTextField(name = "itemtitle")
    @Column(name = "itemtitle")
    private String itemTitle;

    @FullTextField()
    private String description;

    @KeywordField(aggregable = Aggregable.YES)
    private String category;

    @Field("manufacturer_country")
    @JsonProperty("manufacturer_country")
    @GenericField(name = "manufacturer_country", aggregable = Aggregable.YES)
    private String manufacturerCountry;

    @GenericField(aggregable = Aggregable.YES)
    private String brand;

    @GenericField(aggregable = Aggregable.YES)
    private Double price;

    @Field("in_stock")
    @JsonProperty("in_stock")
    private Long inStock;

    private Long popularity;
}

