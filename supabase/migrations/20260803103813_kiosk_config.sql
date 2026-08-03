-- NOWFLIX kiosk remote configuration (STEP 5)
-- parts   : source of truth for the home list (order / title / playlist / active / remote thumb)
-- settings: single-row app configuration (idle timer, admin PIN hash, header text)
-- RLS     : anon may SELECT only; all writes are done later by the admin web via service_role.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
create table public.parts (
    id            uuid primary key default gen_random_uuid(),
    position      int  not null,
    title         text not null,
    thumbnail_url text,
    playlist_id   text not null,
    is_active     boolean     not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table public.settings (
    id                  int primary key default 1,
    idle_return_seconds int  not null default 120,
    admin_pin_hash      text,
    header_text         text,
    updated_at          timestamptz not null default now(),
    -- Enforce the single-row contract.
    constraint settings_singleton check (id = 1)
);

-- ---------------------------------------------------------------------------
-- updated_at maintenance
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger parts_set_updated_at
    before update on public.parts
    for each row execute function public.set_updated_at();

create trigger settings_set_updated_at
    before update on public.settings
    for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Row Level Security: anon = read-only, no writes.
-- (No insert/update/delete policies exist, so those are denied by default.)
-- ---------------------------------------------------------------------------
alter table public.parts    enable row level security;
alter table public.settings enable row level security;

create policy "parts_anon_select"    on public.parts    for select to anon using (true);
create policy "settings_anon_select" on public.settings for select to anon using (true);

-- ---------------------------------------------------------------------------
-- Storage: public 'thumbnails' bucket.
-- Public bucket => object bytes are served over the public CDN URL without a
-- policy. storage.objects RLS stays default-deny, so anon uploads are rejected.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('thumbnails', 'thumbnails', true)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Seed: the current 6 parts (thumbnail_url left NULL -> app uses bundled
-- drawable) and default settings. admin_pin_hash = SHA-256(default PIN).
-- ---------------------------------------------------------------------------
insert into public.parts (position, title, thumbnail_url, playlist_id, is_active) values
    (1, '괜찮Knee TV', null, 'PLfxolKs8oDR66iswGEXMQz10w1seo8O80', true),
    (2, '척척박사 TV',  null, 'PLfxolKs8oDR7o8U1FaBEa60UNFGwEsopE', true),
    (3, '손봐줘 TV',   null, 'PLfxolKs8oDR4vTPuHa9Bp5CV22IHCC7H7', true),
    (4, 'Hip한 NOW',  null, 'PLfxolKs8oDR6LzV8jvySqtmcNfaBaA9pN', true),
    (5, '어깨의 정석',  null, 'PLfxolKs8oDR6a7TCf6Ok1ilR8I5ZqkdC1', true),
    (6, '족집게 TV',   null, 'PLfxolKs8oDR4x2gGkTk29RpJ7aysqo0J3', true);

insert into public.settings (id, idle_return_seconds, admin_pin_hash, header_text) values
    (1, 120, '3b2c2d4629b9403fc17bb4bced473a355883cce7e3e923b08b13a5bbbf3bbc10', '오늘 대한민국의 TOP 콘텐츠');
