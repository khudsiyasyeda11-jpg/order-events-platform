-- Synthetic sample data for demos. Customer ids and SKUs are fictitious.
INSERT INTO orders (id, customer_id, status, currency, total_amount, created_at, version) VALUES
    ('0b6f1d2e-1111-4c1a-9a01-000000000001', 'CUST-1001', 'CREATED',   'USD', 109.97, '2025-01-15 10:00:00+00:00', 0),
    ('0b6f1d2e-1111-4c1a-9a01-000000000002', 'CUST-1001', 'CONFIRMED', 'USD',  24.50, '2025-01-16 14:30:00+00:00', 0),
    ('0b6f1d2e-1111-4c1a-9a01-000000000003', 'CUST-2002', 'CREATED',   'EUR', 315.00, '2025-02-01 09:15:00+00:00', 0);

INSERT INTO order_items (order_id, sku, quantity, unit_price) VALUES
    ('0b6f1d2e-1111-4c1a-9a01-000000000001', 'SKU-KB-001',   1, 59.99),
    ('0b6f1d2e-1111-4c1a-9a01-000000000001', 'SKU-MS-002',   2, 24.99),
    ('0b6f1d2e-1111-4c1a-9a01-000000000002', 'SKU-CB-010',   2, 12.25),
    ('0b6f1d2e-1111-4c1a-9a01-000000000003', 'SKU-MN-027',   1, 315.00);
