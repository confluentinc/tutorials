SELECT ticker, vwap(bid, bidqty, ask, askqty) AS vwap FROM raw_quotes EMIT CHANGES LIMIT 12;
