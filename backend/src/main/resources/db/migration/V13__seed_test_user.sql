-- V13: Seed do usuário de teste Kalleb para desenvolvimento
-- Senha: keeply123 (hash BCrypt com strength 10)
-- Idempotente: ON CONFLICT DO NOTHING — seguro para re-execuções

INSERT INTO users (id, name, email, password_hash, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Kalleb',
    'kalleb@keeply.com',
    '$2a$10$FaVlHUMIMDHsQ4rHFdKzxegYOvwNs9Q36YXqucW3OJPYCk9BLTwv.',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
