@echo off
set PGPASSWORD=9450
"C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -U postgres -d fmcg_shop_prod -c "SELECT COALESCE(SUM(amount), 0) AS total_stock_purchase FROM expenses WHERE category = 'STOCK_PURCHASE' AND expense_date >= '2026-06-01' AND expense_date < '2026-07-01';"
