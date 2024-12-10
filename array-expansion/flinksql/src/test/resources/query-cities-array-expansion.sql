SELECT
    traveler_name,
    city
FROM traveler_locations
CROSS JOIN UNNEST(cities_visited) AS city;
