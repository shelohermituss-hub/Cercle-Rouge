// Edge Function `alert-sms` (S3.04, partiel) : SMS de secours par Vonage quand le téléphone ne peut pas envoyer lui-même
// (pas de crédit, SMS bloqués, permission refusée). Appelée par l'app avec la session de l'utilisateur (JWT vérifié).
// Garde-fous en base (private.server_sms_allowed) : alerte du demandeur, ouverte ou close depuis < 1 h, 5 destinataires
// et 12 SMS par alerte, 20 SMS par jour et par compte. Le texte doit être un message Cercle Rouge.
// Le numéro et le texte ne sont jamais écrits dans les journaux ni en base (seul un hachage du numéro, pour les quotas).
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const KEY = Deno.env.get("VONAGE_API_KEY") ?? "";
const SECRET = Deno.env.get("VONAGE_API_SECRET") ?? "";
const FROM = Deno.env.get("VONAGE_FROM") ?? "CercleRouge";
const E164 = /^\+[1-9][0-9]{7,14}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

/** Identifiant de l'utilisateur : la passerelle a déjà vérifié la signature du JWT (verify_jwt). */
function userId(req: Request): string | null {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.role === "authenticated" && typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  if (!KEY || !SECRET) return json(503, { error: "not_configured" });
  const uid = userId(req);
  if (!uid) return json(401, { error: "unauthorized" });

  let body: { alert_id?: unknown; to?: unknown; text?: unknown };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "bad_request" });
  }
  const alertId = String(body.alert_id ?? "");
  const to = String(body.to ?? "");
  const text = String(body.text ?? "");
  if (!UUID.test(alertId) || !E164.test(to) || text.length < 10 || text.length > 480 || !text.includes("Cercle Rouge")) {
    return json(400, { error: "bad_request" });
  }

  try {
    const [row] = await sql`select private.server_sms_allowed(${uid}::uuid, ${alertId}::uuid, ${to}) as ok`;
    if (!row.ok) return json(429, { error: "not_allowed" });
  } catch (e) {
    console.error("alert-sms: base", (e as Error).name);
    return json(500, { error: "server" });
  }

  const res = await fetch("https://api.nexmo.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Basic " + btoa(`${KEY}:${SECRET}`) },
    body: JSON.stringify({ message_type: "text", channel: "sms", to: to.slice(1), from: FROM, text }),
  });
  if (!res.ok) {
    console.error("alert-sms: vonage", res.status); // statut seulement
    return json(502, { error: "provider", status: res.status });
  }
  const { message_uuid } = await res.json();
  return json(202, { sent: true, id: message_uuid });
});
