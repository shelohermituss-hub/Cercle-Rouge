-- 1.8, 0.2 · Envoi serveur automatique d'une alerte (SMS Vonage et e-mail), avec accusé et déduplication (R-NET-02, R-NET-03, R-SY-05).
-- Le téléphone reste le premier canal : il envoie ses SMS aussitôt et le déclare (report_delivery). Le serveur envoie
-- l'e-mail tout de suite, et le SMS seulement si le téléphone ne l'a pas déclaré après 20 s (téléphone sans crédit, sans SIM,
-- SMS bloqués, ou qui s'est éteint). Un même proche ne reçoit jamais deux fois le même canal pour la même alerte.
-- Numéros, e-mails et textes sont chiffrés (pgcrypto, clé dans Vault) et effacés une heure après la fin de l'alerte.

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'cr_alert_key') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'cr_alert_key', 'Chiffrement des destinataires et textes des alertes');
  end if;
end $$;

create table private.alert_recipients (
  alert_id uuid not null references public.alerts (id) on delete cascade,
  position int not null check (position between 0 and 4),
  -- Identifiant local du contact dans l'app (aucune donnée personnelle) : une ligne par contact et par alerte.
  contact_ref text not null check (char_length(contact_ref) between 8 and 64),
  phone_enc bytea,
  email_enc bytea,
  message_enc bytea,
  phone_hash text not null,
  lang text not null check (lang in ('fr', 'ht', 'en')),
  channels text[] not null check (channels <@ array['sms', 'email'] and cardinality(channels) > 0),
  queued_at timestamptz not null default now(),
  primary key (alert_id, position),
  unique (alert_id, contact_ref)
);

-- Accusés : une ligne par alerte, proche (hachage) et canal. `source` : le téléphone ou le serveur.
create table private.deliveries (
  alert_id uuid not null references public.alerts (id) on delete cascade,
  phone_hash text not null,
  channel text not null check (channel in ('sms', 'email')),
  source text not null check (source in ('phone', 'server')),
  status text not null check (status in ('sending', 'sent', 'failed', 'skipped')),
  provider_id text check (char_length(provider_id) <= 80),
  at timestamptz not null default now(),
  primary key (alert_id, phone_hash, channel)
);

alter table private.alert_recipients enable row level security;
alter table private.deliveries enable row level security;
revoke all on private.alert_recipients, private.deliveries from public, anon, authenticated;

-- Proches à prévenir par le serveur (5 au plus), chiffrés aussitôt. Ajoute ou met à jour chaque proche (par `ref`).
-- `p_items` : [{ref, phone, email?, message, lang, channels:[sms|email]}]. Les numéros STOP sont ignorés.
create function public.set_alert_recipients(p_alert_id uuid, p_items jsonb)
returns int language plpgsql security definer set search_path = public as $$
declare
  a public.alerts;
  it jsonb;
  v_phone text;
  v_email text;
  v_msg text;
  v_ref text;
  v_lang text;
  v_channels text[];
  v_pos int;
  n int := 0;
begin
  a := private.own_alert_for_update(p_alert_id);
  if a.status <> 'open' and (a.closed_at is null or a.closed_at < now() - interval '1 hour') then raise exception 'alert_closed'; end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) > 5 then raise exception 'invalid_recipients'; end if;
  for it in select * from jsonb_array_elements(p_items) loop
    v_ref := it ->> 'ref';
    v_phone := it ->> 'phone';
    v_email := nullif(trim(coalesce(it ->> 'email', '')), '');
    v_msg := it ->> 'message';
    v_lang := coalesce(it ->> 'lang', 'fr');
    select coalesce(array_agg(distinct c), '{}') into v_channels
      from jsonb_array_elements_text(coalesce(it -> 'channels', '[]'::jsonb)) c where c in ('sms', 'email');
    if v_ref is null or char_length(v_ref) not between 8 and 64 then raise exception 'invalid_recipients'; end if;
    if v_phone is null or v_phone !~ '^\+[1-9][0-9]{7,14}$' then raise exception 'invalid_phone'; end if;
    if v_msg is null or char_length(v_msg) not between 10 and 480 or position('Cercle Rouge' in v_msg) = 0 then raise exception 'invalid_message'; end if;
    if v_email is not null and v_email !~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$' then raise exception 'invalid_email'; end if;
    if v_lang not in ('fr', 'ht', 'en') then v_lang := 'fr'; end if;
    if 'email' = any (v_channels) and v_email is null then v_channels := array_remove(v_channels, 'email'); end if;
    if cardinality(v_channels) = 0 then continue; end if;
    if exists (select 1 from private.contact_optouts where phone_hash = public.phone_hash(v_phone)) then continue; end if;
    select position into v_pos from private.alert_recipients where alert_id = a.id and contact_ref = v_ref;
    if v_pos is null then
      select coalesce(max(position) + 1, 0) into v_pos from private.alert_recipients where alert_id = a.id;
      if v_pos > 4 then raise exception 'invalid_recipients'; end if;
    end if;
    insert into private.alert_recipients (alert_id, position, contact_ref, phone_enc, email_enc, message_enc, phone_hash, lang, channels)
    values (a.id, v_pos, v_ref,
            extensions.pgp_sym_encrypt(v_phone, private.secret('cr_alert_key')),
            case when v_email is null then null else extensions.pgp_sym_encrypt(v_email, private.secret('cr_alert_key')) end,
            extensions.pgp_sym_encrypt(v_msg, private.secret('cr_alert_key')),
            public.phone_hash(v_phone), v_lang, v_channels)
    on conflict (alert_id, position) do update
      set phone_enc = excluded.phone_enc, email_enc = excluded.email_enc, message_enc = excluded.message_enc,
          phone_hash = excluded.phone_hash, lang = excluded.lang, channels = excluded.channels, queued_at = now();
    n := n + 1;
  end loop;
  return n;
end $$;

-- Le téléphone déclare un envoi réussi (SMS direct) : le serveur n'enverra pas le même canal à ce proche (R-NET-03).
create function public.report_delivery(p_alert_id uuid, p_phone text, p_channel text)
returns void language plpgsql security definer set search_path = public as $$
declare a public.alerts;
begin
  a := private.own_alert_for_update(p_alert_id);
  if p_channel not in ('sms', 'email') then raise exception 'invalid_channel'; end if;
  if p_phone is null or p_phone !~ '^\+[1-9][0-9]{7,14}$' then raise exception 'invalid_phone'; end if;
  insert into private.deliveries (alert_id, phone_hash, channel, source, status)
  values (a.id, public.phone_hash(p_phone), p_channel, 'phone', 'sent')
  on conflict (alert_id, phone_hash, channel) do nothing;
end $$;

-- Ce qui est parti, par qui et quand (sans identifier le proche) : lu par l'app du propriétaire.
create function public.alert_deliveries(p_alert_id uuid)
returns table (channel text, source text, status text, at timestamptz)
language sql stable security definer set search_path = public as $$
  select d.channel, d.source, d.status, d.at
  from private.deliveries d join public.alerts a on a.id = d.alert_id
  where d.alert_id = p_alert_id and a.owner_id = (select auth.uid())
  order by d.at
$$;

-- Envois à faire par le serveur (numéros et textes déchiffrés) : e-mail tout de suite, SMS après le délai de grâce si le
-- téléphone ne l'a pas déclaré. Jamais exposé à l'API (Edge Functions seulement).
create function private.alert_sends_due(p_alert_id uuid default null, p_grace interval default interval '20 seconds')
returns table (alert_id uuid, owner_id uuid, phone_hash text, channel text, phone text, email text, message text, lang text)
language sql security definer set search_path = public as $$
  select r.alert_id, a.owner_id, r.phone_hash, c.channel,
         extensions.pgp_sym_decrypt(r.phone_enc, private.secret('cr_alert_key')),
         case when c.channel = 'email' then extensions.pgp_sym_decrypt(r.email_enc, private.secret('cr_alert_key')) end,
         extensions.pgp_sym_decrypt(r.message_enc, private.secret('cr_alert_key')),
         r.lang
  from private.alert_recipients r
  join public.alerts a on a.id = r.alert_id
  cross join lateral unnest(r.channels) as c(channel)
  where r.phone_enc is not null
    and (p_alert_id is null or r.alert_id = p_alert_id)
    and (a.status = 'open' or a.closed_at > now() - interval '1 hour')
    and (c.channel = 'email' or r.queued_at <= now() - p_grace)
    and not exists (select 1 from private.contact_optouts o where o.phone_hash = r.phone_hash)
    and not exists (
      select 1 from private.deliveries d
      where d.alert_id = r.alert_id and d.phone_hash = r.phone_hash and d.channel = c.channel
        and (d.status <> 'sending' or d.at > now() - interval '2 minutes')
    )
$$;

-- Réserve un envoi (idempotent, deux appels simultanés ne l'obtiennent pas tous les deux). Une réservation restée sans suite
-- au-delà de 2 minutes peut être reprise.
create function private.alert_send_claim(p_alert_id uuid, p_phone_hash text, p_channel text) returns boolean
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  insert into private.deliveries (alert_id, phone_hash, channel, source, status)
  values (p_alert_id, p_phone_hash, p_channel, 'server', 'sending')
  on conflict (alert_id, phone_hash, channel) do update set at = now(), source = 'server'
    where private.deliveries.status = 'sending' and private.deliveries.at <= now() - interval '2 minutes';
  get diagnostics n = row_count;
  return n > 0;
end $$;

create function private.alert_send_done(p_alert_id uuid, p_phone_hash text, p_channel text, p_status text, p_provider text default null)
returns void language sql security definer set search_path = public as $$
  update private.deliveries set status = p_status, provider_id = left(p_provider, 80), at = now()
  where alert_id = p_alert_id and phone_hash = p_phone_hash and channel = p_channel and source = 'server'
$$;

-- E-mails : 5 par alerte au plus et 20 par jour et par compte (comme les SMS serveur).
create function private.server_email_allowed(p_owner uuid, p_alert_id uuid) returns boolean
language plpgsql security definer set search_path = public as $$
declare v_count int;
begin
  if (select count(*) from private.deliveries where alert_id = p_alert_id and channel = 'email' and source = 'server' and status = 'sent') >= 5 then
    return false;
  end if;
  insert into public.rate_limits (user_id, action, count) values (p_owner, 'alert_email', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  return v_count <= 20;
end $$;

-- Chaque minute : efface numéros, e-mails et textes une heure après la fin de l'alerte, et réveille `notify-alert` s'il reste
-- des envois à faire (filet de sécurité : l'app l'appelle déjà directement).
create function private.alert_notify_tick() returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  update private.alert_recipients r set phone_enc = null, email_enc = null, message_enc = null
  from public.alerts a
  where a.id = r.alert_id and a.status <> 'open' and a.closed_at < now() - interval '1 hour' and r.phone_enc is not null;
  select count(*) into n from private.alert_sends_due();
  if n > 0 and private.secret('cr_functions_url') is not null and exists (select 1 from pg_extension where extname = 'pg_net') then
    perform net.http_post(
      url := private.secret('cr_functions_url') || '/notify-alert',
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-cr-cron', private.secret('cr_cron_secret')),
      body := '{}'::jsonb
    );
  end if;
  return n;
end $$;

do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule('cr-alert-notify', '* * * * *', 'select private.alert_notify_tick()');
  end if;
end $$;

revoke execute on function public.set_alert_recipients(uuid, jsonb), public.report_delivery(uuid, text, text), public.alert_deliveries(uuid) from public, anon;
grant execute on function public.set_alert_recipients(uuid, jsonb), public.report_delivery(uuid, text, text), public.alert_deliveries(uuid) to authenticated;
revoke execute on function private.alert_sends_due(uuid, interval), private.alert_send_claim(uuid, text, text),
  private.alert_send_done(uuid, text, text, text, text), private.server_email_allowed(uuid, uuid), private.alert_notify_tick()
  from public, anon, authenticated;
