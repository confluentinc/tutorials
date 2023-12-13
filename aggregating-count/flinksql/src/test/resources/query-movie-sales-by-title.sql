SELECT title,
       COUNT(total_ticket_value) AS tickets_sold
FROM movie_ticket_sales
GROUP BY title;