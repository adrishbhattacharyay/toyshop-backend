-- Insert default admin user
INSERT INTO users (username, email, password, first_name, last_name, role, enabled, account_non_locked, credentials_non_expired, account_non_expired, created_at, updated_at)
VALUES ('admin', 'admin@toyshop.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4aYJGYxMnC6C5.Oy', 'Admin', 'User', 'ADMIN', true, true, true, true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Insert default staff user
INSERT INTO users (username, email, password, first_name, last_name, role, enabled, account_non_locked, credentials_non_expired, account_non_expired, created_at, updated_at)
VALUES ('staff', 'staff@toyshop.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4aYJGYxMnC6C5.Oy', 'Staff', 'User', 'STAFF', true, true, true, true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;