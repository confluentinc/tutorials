SELECT FIRST_NAME + ' ' + LAST_NAME + 
       ' purchased ' +
       CAST(NUM_SHARES AS VARCHAR) +
       ' shares of ' +
       SYMBOL AS SUMMARY
FROM ACTIVITY_STREAM
EMIT CHANGES
LIMIT 4;
