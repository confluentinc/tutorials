SELECT *
    FROM purchases_pii_obfuscated
    EMIT CHANGES
    LIMIT 6;
