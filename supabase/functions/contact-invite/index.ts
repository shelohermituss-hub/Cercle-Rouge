// Edge Function `contact-invite` (S3.03) : invite un proche de confiance par SMS (Vonage), avec un lien personnel vers la page
// « accepter / refuser / ne plus recevoir ». Appelée par l'app avec la session de l'utilisateur (JWT vérifié).
// Quotas et liste STOP en base (private.contact_invite_create). Numéro et texte jamais journalisés ni stockés en clair.
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const KEY = Deno.env.get("VONAGE_API_KEY") ?? "";
const SECRET = Deno.env.get("VONAGE_API_SECRET") ?? "";
const FROM = Deno.env.get("VONAGE_FROM") ?? "CercleRouge";
const PAGE = Deno.env.get("CONSENT_PAGE_URL") ?? "https://shelohermituss-hub.github.io/Cercle-Rouge/accord.html#i=";
const E164 = /^\+[1-9][0-9]{7,14}$/;
const REF = /^[A-Za-z0-9_-]{8,64}$/;

// Textes GSM-7 (pas de ê, â, î, ô, û) : un seul SMS avec le lien.
const TEXT: Record<string, (name: string, link: string) => string> = {
  fr: (n, l) => `Cercle Rouge: ${n} veut te prevenir par SMS si un jour il/elle est en danger. Accepte ou refuse: ${l}`,
  ht: (n, l) => `Cercle Rouge: ${n} vle voye w yon SMS si yon jou li an danje. Aksepte oswa refize: ${l}`,
  en: (n, l) => `Cercle Rouge: ${n} wants to text you if they are ever in danger. Accept or decline: ${l}`,
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

function userId(req: Request): string | null {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  try {
    const p = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return p.role === "authenticated" && typeof p.sub === "string" ? p.sub : null;
  } catch {
    return null;
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  if (!KEY || !SECRET) return json(503, { error: "not_configured" });
  const uid = userId(req);
  if (!uid) return json(401, { error: "unauthorized" });
  let body: { client_ref?: unknown; label?: unknown; to?: unknown; lang?: unknown };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "bad_request" });
  }
  const ref = String(body.client_ref ?? "");
  const to = String(body.to ?? "");
  const label = String(body.label ?? "").slice(0, 40);
  const lang = ["fr", "ht", "en"].includes(String(body.lang)) ? String(body.lang) : "fr";
  if (!REF.test(ref) || !E164.test(to)) return json(400, { error: "bad_request" });

  let token: string | null;
  let name: string;
  try {
    const [row] = await sql`select private.contact_invite_create(${uid}::uuid, ${ref}, ${label}, ${to}) as token,
                                   (select display_name from public.profiles where id = ${uid}::uuid) as name`;
    token = row.token;
    name = row.name ?? "?";
  } catch (e) {
    const msg = (e as Error).message ?? "";
    const code = ["rate_limited", "already_accepted", "profile_required"].find((c) => msg.includes(c)) ?? "server";
    if (code === "server") console.error("contact-invite: base", (e as Error).name);
    return json(code === "server" ? 500 : 409, { error: code });
  }
  if (!token) return json(409, { error: "stopped" }); // le numéro a demandé STOP

  const res = await fetch("https://api.nexmo.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Basic " + btoa(`${KEY}:${SECRET}`) },
    body: JSON.stringify({ message_type: "text", channel: "sms", to: to.slice(1), from: FROM, text: TEXT[lang](name, PAGE + token) }),
  });
  if (!res.ok) {
    console.error("contact-invite: vonage", res.status);
    return json(502, { error: "provider" });
  }
  return json(202, { sent: true });
});
