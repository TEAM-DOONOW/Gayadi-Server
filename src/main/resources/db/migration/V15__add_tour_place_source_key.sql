ALTER TABLE places
    ADD CONSTRAINT uk_places_source_place UNIQUE (source, source_place_id);
