// Edge Function `guardian-invite` (S1.17) : SMS (Vonage) à l'adulte référent choisi par un jeune de 12 à 15 ans.
// Appelée par l'app du jeune juste après la RPC request_guardian_consent (quotas : 3 demandes par jour).
// Un seul SMS par demande (private.guardian_invite_target) ; numéro jamais journalisé.
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const KEY = Deno.env.get("VONAGE_API_KEY") ?? "";
const SECRET = Deno.env.get("VONAGE_API_SECRET") ?? "";
const FROM = Deno.env.get("VONAGE_FROM") ?? "CercleRouge";

// GSM-7, un seul SMS.
const TEXT: Record<string, (n: string) => string> = {
  fr: (n) => `Cercle Rouge: ${n} te demande d'etre son adulte de confiance. Installe Cercle Rouge, verifie ton numero (Reglages), puis accepte la demande.`,
  ht: (n) => `Cercle Rouge: ${n} mande w pou w se granmoun li fe konfyans. Enstale Cercle Rouge, verifye nimewo w (Parametr), epi aksepte demann nan.`,
  en: (n) => `Cercle Rouge: ${n} asks you to be their trusted adult. Install Cercle Rouge, verify your number (Settings), then accept the request.`,
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
  let lang = "fr";
  try {
    const body = await req.json();
    if (["fr", "ht", "en"].includes(String(body.lang))) lang = String(body.lang);
  } catch { /* corps facultatif */ }

  let target: { phone: string; minor_name: string } | undefined;
  try {
    [target] = await sql`select phone, minor_name from private.guardian_invite_target(${uid}::uuid)`;
  } catch (e) {
    console.error("guardian-invite: base", (e as Error).name);
    return json(500, { error: "server" });
  }
  if (!target) return json(409, { error: "no_pending_request" });

  const res = await fetch("https://api.nexmo.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Basic " + btoa(`${KEY}:${SECRET}`) },
    body: JSON.stringify({ message_type: "text", channel: "sms", to: target.phone.slice(1), from: FROM, text: TEXT[lang](target.minor_name ?? "?") }),
  });
  if (!res.ok) {
    console.error("guardian-invite: vonage", res.status);
    return json(502, { error: "provider" });
  }
  return json(202, { sent: true });
});
