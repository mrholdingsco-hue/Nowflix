-- NOWFLIX admin web authentication (STEP 8)
--
-- The kiosk app reads `public.settings` with the ANON key, so any column there is
-- world-readable. The web login password hash and the brute-force lockout counters
-- must NOT be exposed to anon, so they live in their own single-row table with RLS
-- enabled and NO policies: anon is denied by default, and only the service_role key
-- (used exclusively by the admin web's server side) bypasses RLS to read/write it.

create table public.web_auth (
    id            int primary key default 1,
    -- bcrypt hash of the web login password. Initial value = bcrypt('nowflix2026').
    password_hash text        not null,
    -- true until the operator changes the password; drives the "change me" prompt.
    is_default    boolean     not null default true,
    -- brute-force guard: 5 consecutive failures -> locked_until = now()+60s.
    failed_count  int         not null default 0,
    locked_until  timestamptz,
    updated_at    timestamptz not null default now(),
    constraint web_auth_singleton check (id = 1)
);

create trigger web_auth_set_updated_at
    before update on public.web_auth
    for each row execute function public.set_updated_at();

-- RLS on, zero policies -> anon/authenticated denied entirely. service_role bypasses RLS.
alter table public.web_auth enable row level security;

-- Seed the single row with bcrypt('nowflix2026'). is_default=true so the web shows the
-- "please change the initial password" banner until the operator changes it.
insert into public.web_auth (id, password_hash, is_default)
values (1, '$2a$10$Ld8gsx2RvBnqVuQTA56ZEOg.6bpdAwmZSA3d2P2zd/gGZdmq20Ac2', true)
on conflict (id) do nothing;
