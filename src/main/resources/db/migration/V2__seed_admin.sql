-- ============================================================================
-- V2: Seed do usuario admin inicial
-- ============================================================================
-- Email:  admin@unimove.local
-- Senha:  ChangeMe123!   (hash BCrypt 10 rounds pre-calculado)
-- ATENCAO: trocar a senha apos o primeiro login em ambientes nao-dev.
-- ============================================================================

INSERT INTO users (id, email, password_hash, name, phone, role, cidade)
VALUES (
    gen_random_uuid(),
    'admin@unimove.local',
    '$2a$10$/MIN3aOqSwuUutlNEbfFrevb3cpia8IMkEKWMfGHVJefzVLFIfJpS',
    'Administrador UniMove',
    NULL,
    'ADMIN',
    'sao-jose-do-rio-preto'
);
