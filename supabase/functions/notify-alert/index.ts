// Edge Function `notify-alert` (S3.04 sans FCM, 1.8) : envoie les alertes que le téléphone n'a pas (ou pas seul) envoyées.
// - SMS (Vonage) seulement si le téléphone ne l'a pas déclaré après 20 s ; e-mail tout de suite (Resend, si configuré).
// - Deux entrées : l'app de l'utilisateur (JWT, vérifié auprès de Supabase Auth) ou pg_cron (en-tête `x-cr-cron`).
// - Jamais deux envois pour le même proche et le même canal (réservation en base, R-NET-02, R-NET-03).
// Numéros, e-mails et textes ne sont jamais écrits dans les journaux (statuts seulement).
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const VONAGE_KEY = Deno.env.get("VONAGE_API_KEY") ?? "";
const VONAGE_SECRET = Deno.env.get("VONAGE_API_SECRET") ?? "";
const FROM = Deno.env.get("VONAGE_FROM") ?? "CercleRouge";
const RESEND_KEY = Deno.env.get("RESEND_API_KEY") ?? "";
const MAIL_FROM = Deno.env.get("MAIL_FROM") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const GRACE_MS = 21_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

type Due = { alert_id: string; owner_id: string; phone_hash: string; channel: "sms" | "email"; phone: string; email: string | null; message: string; lang: string };

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0 && x.length > 0;
}

/** Identité vérifiée par Supabase Auth : la passerelle ne vérifie pas le JWT de cette fonction (appel pg_cron sans JWT). */
async function verifiedUser(req: Request): Promise<string | null> {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  if (!token || !SUPABASE_URL || !ANON) return null;
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { Authorization: `Bearer ${token}`, apikey: ANON } });
  if (!res.ok) return null;
  const u = await res.json();
  return typeof u.id === "string" ? u.id : null;
}

const SUBJECT: Record<string, string> = {
  fr: "Alerte Cercle Rouge",
  ht: "Alèt Cercle Rouge",
  en: "Cercle Rouge alert",
};

async function sendSms(d: Due): Promise<{ ok: boolean; id?: string }> {
  if (!VONAGE_KEY || !VONAGE_SECRET) return { ok: false };
  const [allowed] = await sql`select private.server_sms_allowed(${d.owner_id}::uuid, ${d.alert_id}::uuid, ${d.phone}) as ok`;
  if (!allowed.ok) return { ok: false };
  const res = await fetch("https://api.nexmo.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Basic " + btoa(`${VONAGE_KEY}:${VONAGE_SECRET}`) },
    body: JSON.stringify({ message_type: "text", channel: "sms", to: d.phone.slice(1), from: FROM, text: d.message }),
  });
  if (!res.ok) {
    console.error("notify-alert: vonage", res.status);
    return { ok: false };
  }
  const { message_uuid } = await res.json();
  return { ok: true, id: message_uuid };
}

async function sendEmail(d: Due): Promise<{ ok: boolean; id?: string; skipped?: boolean }> {
  if (!RESEND_KEY || !MAIL_FROM || !d.email) return { ok: false, skipped: true };
  const [allowed] = await sql`select private.server_email_allowed(${d.owner_id}::uuid, ${d.alert_id}::uuid) as ok`;
  if (!allowed.ok) return { ok: false };
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${RESEND_KEY}` },
    body: JSON.stringify({ from: MAIL_FROM, to: [d.email], subject: SUBJECT[d.lang] ?? SUBJECT.fr, text: d.message }),
  });
  if (!res.ok) {
    console.error("notify-alert: resend", res.status);
    return { ok: false };
  }
  const { id } = await res.json();
  return { ok: true, id };
}

/** Envoie ce qui est dû (réservé en base avant l'envoi). Rend le nombre d'envois réussis. */
async function process(rows: Due[]): Promise<number> {
  let sent = 0;
  for (const d of rows) {
    const [claim] = await sql`select private.alert_send_claim(${d.alert_id}::uuid, ${d.phone_hash}, ${d.channel}) as ok`;
    if (!claim.ok) continue; // un autre appel s'en occupe déjà
    try {
      const r = d.channel === "sms" ? await sendSms(d) : await sendEmail(d);
      const status = r.ok ? "sent" : (d.channel === "email" && (r as { skipped?: boolean }).skipped ? "skipped" : "failed");
      await sql`select private.alert_send_done(${d.alert_id}::uuid, ${d.phone_hash}, ${d.channel}, ${status}, ${r.id ?? null})`;
      if (r.ok) sent++;
    } catch (e) {
      console.error("notify-alert: envoi", (e as Error).name);
      await sql`select private.alert_send_done(${d.alert_id}::uuid, ${d.phone_hash}, ${d.channel}, 'failed', null)`;
    }
  }
  return sent;
}

const due = (alertId: string | null, graceMs: number) =>
  sql<Due[]>`select * from private.alert_sends_due(${alertId}::uuid, make_interval(secs => ${graceMs / 1000}))`;

declare const EdgeRuntime: { waitUntil(p: Promise<unknown>): void };

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  try {
    // Entrée pg_cron : tout ce qui est dû (délai de grâce de 20 s pour les SMS).
    const cron = req.headers.get("x-cr-cron");
    if (cron !== null) {
      const [{ s }] = await sql`select private.secret('cr_cron_secret') as s`;
      if (!sameSecret(cron, s ?? "")) return json(401, { error: "unauthorized" });
      return json(200, { sent: await process(await due(null, 20_000)) });
    }
    // Entrée de l'app : l'alerte de l'utilisateur seulement.
    const uid = await verifiedUser(req);
    if (!uid) return json(401, { error: "unauthorized" });
    let alertId = "";
    try {
      alertId = String((await req.json()).alert_id ?? "");
    } catch { /* corps absent */ }
    if (!UUID.test(alertId)) return json(400, { error: "bad_request" });
    const [own] = await sql`select 1 as ok from public.alerts where id = ${alertId}::uuid and owner_id = ${uid}::uuid`;
    if (!own) return json(404, { error: "not_found" });
    const emails = (await due(alertId, 0)).filter((d) => d.channel === "email");
    const sentNow = await process(emails);
    // SMS de secours : si le téléphone ne les a pas déclarés d'ici 20 s.
    EdgeRuntime.waitUntil((async () => {
      await new Promise((r) => setTimeout(r, GRACE_MS));
      await process((await due(alertId, 20_000)).filter((d) => d.channel === "sms"));
    })());
    return json(202, { emails: sentNow });
  } catch (e) {
    console.error("notify-alert: erreur", (e as Error).name);
    return json(500, { error: "server" });
  }
});
