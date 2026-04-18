-- Insert default admin user (password: Admin@123)
-- Hash generated with BCrypt strength 12
INSERT INTO users (username, email, password, first_name, last_name, role, enabled, account_non_locked, credentials_non_expired, account_non_expired, created_at, updated_at)
VALUES ('admin', 'admin@toyshop.com', '$2a$12$N1JkZN7QH/aL/f.VT6tOoewYKcZymunVgkilN6cb0agHFaE2aaH1.', 'Admin', 'User', 'ADMIN', true, true, true, true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET
  password = EXCLUDED.password,
  role = EXCLUDED.role,
  enabled = EXCLUDED.enabled,
  account_non_locked = EXCLUDED.account_non_locked,
  credentials_non_expired = EXCLUDED.credentials_non_expired,
  account_non_expired = EXCLUDED.account_non_expired;

-- Insert default staff user (password: Test@123)
INSERT INTO users (username, email, password, first_name, last_name, role, enabled, account_non_locked, credentials_non_expired, account_non_expired, created_at, updated_at)
VALUES ('staff', 'staff@toyshop.com', '$2a$12$N1JkZN7QH/aL/f.VT6tOoewYKcZymunVgkilN6cb0agHFaE2aaH1.', 'Staff', 'User', 'STAFF', true, true, true, true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET
  password = EXCLUDED.password,
  role = EXCLUDED.role,
  enabled = EXCLUDED.enabled,
  account_non_locked = EXCLUDED.account_non_locked,
  credentials_non_expired = EXCLUDED.credentials_non_expired,
  account_non_expired = EXCLUDED.account_non_expired;
