CREATE TABLE items (
                       id serial PRIMARY KEY,
                       itemtitle TEXT NOT NULL,
                       description TEXT,
                       category VARCHAR ( 255 ),
                       manufacturer_country VARCHAR ( 255 ),
                       brand VARCHAR ( 255 ),
                       price DECIMAL,
                       in_stock INTEGER,
                       popularity INTEGER
);