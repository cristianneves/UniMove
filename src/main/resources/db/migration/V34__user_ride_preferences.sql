-- Preferencias operacionais do passageiro: o app pre-seleciona o metodo de
-- pagamento e a categoria na tela de pedido. NULL = sem preferencia.
ALTER TABLE users
    ADD COLUMN preferred_payment_method VARCHAR(10) NULL,
    ADD COLUMN preferred_category       VARCHAR(10) NULL;

ALTER TABLE users ADD CONSTRAINT chk_users_preferred_payment_method
    CHECK (preferred_payment_method IN ('DINHEIRO', 'PIX'));
ALTER TABLE users ADD CONSTRAINT chk_users_preferred_category
    CHECK (preferred_category IN ('MOTO', 'CARRO'));
