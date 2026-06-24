@echo off
set PGPASSWORD=9450
"C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -U postgres -d fmcg_shop_prod -c "SELECT id, name, phone, role FROM users;"
