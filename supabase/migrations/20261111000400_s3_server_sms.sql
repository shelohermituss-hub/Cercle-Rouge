-- S3.04 (partiel) · SMS de secours envoyés par le serveur (Vonage) quand le téléphone ne peut pas envoyer (pas de crédit, SMS bloqués).
-- Garde-fous contre l'usage abusif (envoi de SMS arbitraires à nos frais) : seulement pour une alerte du demandeur,
-- ouverte ou close depuis moins d'1 h ; 5 destinataires distincts et 12 SMS par alerte ; 20 SMS par jour et par compte.
-- Appelée uniquement par l'Edge Function `alert-sms` (connexion directe), jamais via l'API.

create table private.server_sms_log (
  id bigint generated always as identity primary key,
  alert_id uuid not null references public.alerts (id) on delete cascade,
  owner_id uuid not null,
  phone_hash text not null,
  at timestamptz not null default now()
);
create index server_sms_log_alert on private.server_sms_log (alert_id);
create index server_sms_log_owner on private.server_sms_log (owner_id, at);

-- Vrai (et compté) si l'envoi est permis. Le numéro n'est jamais stocké : seulement son hachage.
create function private.server_sms_allowed(p_owner uuid, p_alert_id uuid, p_phone text) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  a public.alerts;
  v_hash text := public.phone_hash(p_phone);
begin
  select * into a from public.alerts where id = p_alert_id and owner_id = p_owner for update;
  if a.id is null then return false; end if;
  if a.status <> 'open' and (a.closed_at is null or a.closed_at < now() - interval '1 hour') then return false; end if;
  if (select count(distinct phone_hash) from private.server_sms_log where alert_id = a.id and phone_hash <> v_hash) >= 5 then return false; end if;
  if (select count(*) from private.server_sms_log where alert_id = a.id) >= 12 then return false; end if;
  if (select count(*) from private.server_sms_log where owner_id = p_owner and at > now() - interval '24 hours') >= 20 then return false; end if;
  insert into private.server_sms_log (alert_id, owner_id, phone_hash) values (a.id, p_owner, v_hash);
  return true;
end $$;

alter table private.server_sms_log enable row level security;
revoke all on private.server_sms_log from public, anon, authenticated;
revoke execute on function private.server_sms_allowed(uuid, uuid, text) from public, anon, authenticated;
