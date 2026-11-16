-- Corrections trouvées par l'essai réel de 1.16 (comptes de test jetables) :
-- 1. current_phone_hash() rendait le haché d'une chaîne vide pour un compte SANS numéro (phone_hash(null) = haché de '').
--    Sans effet jusqu'ici (aucune invitation n'a ce haché) mais faux : un compte sans numéro pouvait publier une clé de partage.
--    Désormais : null tant qu'aucun numéro n'est rattaché (Supabase ne remplit `phone` qu'après vérification du code).
-- 2. La règle de lecture des preuves partagées interrogeait evidence_uploads, que la RLS cache au proche : elle ne laissait
--    rien passer. Elle passe maintenant par une fonction SECURITY DEFINER qui ne répond que pour l'appelant.

create or replace function public.current_phone_hash() returns text
language sql stable security definer set search_path = public as $$
  select public.phone_hash(phone) from auth.users where id = (select auth.uid()) and nullif(phone, '') is not null
$$;

-- Vrai si l'appelant a reçu l'enveloppe de cette alerte, non échue, et que l'invitation est toujours acceptée.
create function public.evidence_readable(p_owner text, p_alert text)
returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.evidence_key_wraps w
    join public.evidence_uploads u on u.alert_id = w.alert_id
    where w.recipient_id = (select auth.uid())
      and u.owner_id::text = p_owner and u.alert_id::text = p_alert
      and u.delete_after > now()
      and exists (
        select 1 from public.contact_invites ci
        where ci.owner_id = u.owner_id and ci.status = 'accepted' and ci.phone_hash = public.current_phone_hash()
      )
  )
$$;
revoke execute on function public.evidence_readable(text, text) from public, anon;
grant execute on function public.evidence_readable(text, text) to authenticated;

do $$
begin
  if exists (select 1 from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname = 'evidence_select_shared') then
    execute $p$
      alter policy evidence_select_shared on storage.objects
      using (bucket_id = 'evidence' and public.evidence_readable((storage.foldername(name))[1], (storage.foldername(name))[2]))
    $p$;
  end if;
end $$;
