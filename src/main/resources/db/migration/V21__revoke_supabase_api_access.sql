-- Fecha a Data API do Supabase para as tabelas do UniMove.
--
-- Por que isto existe:
-- Um projeto Supabase novo vem com
--   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;
-- concedido pelo role `postgres`. Como o Flyway conecta justamente como
-- `postgres`, TODA tabela criada por estas migrations nasceria com
-- SELECT/INSERT/UPDATE/DELETE liberados para o role `anon` — o role por tras da
-- chave publica do projeto. Sem RLS (nao usamos: quem autoriza e o Spring
-- Security), isso deixaria `users`, `rides` e `chat_messages` legiveis e
-- graváveis por qualquer um que tivesse a anon key, que e publica por design.
--
-- O backend nao usa PostgREST, Auth nem Storage do Supabase: fala Postgres puro
-- via JDBC como `postgres`, entao revogar `anon`/`authenticated` nao tira nada
-- de que precisamos. `service_role` fica intacto — exige a chave secreta, que
-- nunca sai do servidor, e e o que o dashboard e as ferramentas usam.
--
-- O guarda por pg_roles mantem a migration inofensiva fora do Supabase
-- (Postgres local do docker-compose, CI), onde esses roles nao existem.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        -- Vale para as tabelas que as PROXIMAS migrations criarem.
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES    FROM anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated';

        -- Vale para tudo que as migrations V1..V20 ja criaram.
        EXECUTE 'REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated';
        EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated';

        -- Defesa em profundidade. Nao surte efeito sozinho: o USAGE em `public`
        -- tambem chega pelo pseudo-role PUBLIC, e revogar de PUBLIC quebraria
        -- componentes internos do Supabase. Quem barra de fato e a ausencia de
        -- privilegio nas tabelas acima (a Data API responde 401).
        EXECUTE 'REVOKE USAGE ON SCHEMA public FROM anon, authenticated';

        RAISE NOTICE 'Supabase detectado: Data API revogada para anon/authenticated no schema public';
    END IF;
END $$;
