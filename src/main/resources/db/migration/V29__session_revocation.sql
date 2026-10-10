-- ============================================================================
-- V29: Revogacao de sessao e troca obrigatoria de senha
-- ============================================================================
-- tokens_valid_after: JWT emitido antes deste instante deixa de valer. E
--   gravado na troca e no reset de senha — um token roubado nao sobrevive a
--   troca da senha, sem precisar de blacklist (o filtro ja consulta o usuario
--   a cada request).
-- must_change_password: senha temporaria (reset pelo admin) so serve para
--   escolher outra; enquanto true, o filtro so libera a troca de senha.
--
-- O admin do seed (V2) ainda com a senha publicada no arquivo da migration
-- nasce obrigado a troca-la: compara com o hash exato que a V2 gravou.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN tokens_valid_after   TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN must_change_password BOOLEAN                  NOT NULL DEFAULT FALSE;

UPDATE users
SET must_change_password = TRUE
WHERE role = 'ADMIN'
  AND password_hash = '$2a$10$/MIN3aOqSwuUutlNEbfFrevb3cpia8IMkEKWMfGHVJefzVLFIfJpS';
