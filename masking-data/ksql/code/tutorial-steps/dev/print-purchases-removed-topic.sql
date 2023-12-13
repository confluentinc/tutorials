SELECT *
    FROM purchases_pii_removed
    EMIT CHANGES
    LIMIT 6;
