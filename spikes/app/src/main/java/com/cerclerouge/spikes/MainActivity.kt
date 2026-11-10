package com.cerclerouge.spikes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

/**
 * Écran unique des prototypes S0.18 à S0.21. Interface volontairement brute : ce code est jetable.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingAction: (() -> Unit)? = null
    private lateinit var micStatus: TextView
    private lateinit var evidenceStatus: TextView
    private lateinit var smsStatus: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var smsSpike: SmsSpike

    private val refresh = object : Runnable {
        override fun run() {
            micStatus.text = SpikeLog(this@MainActivity, MicService.LOG_NAME).tail(4)
            gpsStatus.text = gpsSummary()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smsSpike = SmsSpike(this) { msg -> runOnUiThread { smsStatus.append("\n$msg") } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 80) }
        setContentView(ScrollView(this).apply { addView(root) })

        root.title("Cercle Rouge · prototypes S0")
        root.text(SpikeLog.deviceInfo(this))
        root.text("Débranche le chargeur pendant les mesures. Les résultats restent sur ce téléphone jusqu'à « Partager ».")

        // S0.18
        root.title("S0.18 · Micro écran éteint + IA")
        root.text("Lance, verrouille l'écran, attends 2 h. Un « battement » par minute doit apparaître sans trou.")
        val wake = root.check("Garder le processeur éveillé (wake lock)", true)
        root.row(
            "Démarrer" to { withPermissions(listOf(Manifest.permission.RECORD_AUDIO) + notifPerm()) {
                startForegroundService(Intent(this, MicService::class.java).putExtra(MicService.EXTRA_WAKELOCK, wake.isChecked))
            } },
            "Arrêter" to { stopService(Intent(this, MicService::class.java)) }
        )
        micStatus = root.mono()

        // S0.19
        root.title("S0.19 · Audio chiffré, sans fichier en clair")
        root.text("Enregistre 30 s (parle près du micro), puis vérifie l'absence de fichier en clair et rejoue l'audio déchiffré.")
        val aac = root.check("Utiliser AAC au lieu d'Opus (si Opus échoue)", false)
        evidenceStatus = root.mono()
        root.row("Enregistrer 30 s et vérifier" to {
            withPermissions(listOf(Manifest.permission.RECORD_AUDIO)) {
                evidenceStatus.text = "En cours…"
                Thread {
                    val spike = EvidenceSpike(this) { msg -> runOnUiThread { evidenceStatus.append("\n$msg") } }
                    runCatching { spike.run(30, aac.isChecked) }
                        .onFailure { e ->
                            SpikeLog(this, EvidenceSpike.LOG_NAME).line("echec", e.javaClass.simpleName, e.message)
                            runOnUiThread { evidenceStatus.append("\nÉCHEC : ${e.javaClass.simpleName} ${e.message}") }
                        }
                    spike.wipe()
                }.start()
            }
        })

        // S0.20
        root.title("S0.20 · SMS avec accents")
        root.text("Numéro de test (+509…). Fais un essai vers un numéro Digicel, puis un numéro Natcom.")
        val number = EditText(this).apply { hint = "+509…"; inputType = InputType.TYPE_CLASS_PHONE }
        root.addView(number)
        val message = EditText(this).apply { setText(SmsSpike.SAMPLE_TEXT) }
        root.addView(message)
        smsStatus = root.mono()
        root.row("Envoyer le SMS de test" to {
            withPermissions(listOf(Manifest.permission.SEND_SMS)) {
                smsStatus.text = ""
                runCatching { smsSpike.send(number.text.toString().trim(), message.text.toString()) }
                    .onFailure { smsStatus.append("\nÉCHEC : ${it.message}") }
            }
        })

        // S0.21
        root.title("S0.21 · GPS pendant un trajet réel")
        root.text("Démarre avant un trajet d'1 h (tap-tap, moto), écran éteint, puis arrête à l'arrivée.")
        val noPlay = root.check("Tester sans Google Play Services (LocationManager)", false)
        root.row(
            "Démarrer" to { withPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) + notifPerm()) {
                startForegroundService(Intent(this, GpsService::class.java).putExtra(GpsService.EXTRA_FUSED, !noPlay.isChecked))
            } },
            "Arrêter" to { stopService(Intent(this, GpsService::class.java)) }
        )
        gpsStatus = root.mono()

        // Essai automatisé (émulateur, adb) : am start -n com.cerclerouge.spikes/.MainActivity --es autotest mic|stopmic|evidence|sms|gps|stopgps
        when (intent.getStringExtra("autotest")) {
            "mic" -> startForegroundService(Intent(this, MicService::class.java))
            "stopmic" -> stopService(Intent(this, MicService::class.java))
            "evidence" -> Thread {
                val spike = EvidenceSpike(this) { }
                runCatching { spike.run(10, intent.getBooleanExtra("aac", false)) }
                    .onFailure { SpikeLog(this, EvidenceSpike.LOG_NAME).line("echec", it.javaClass.simpleName, it.message) }
                spike.wipe()
            }.start()
            "sms" -> runCatching { smsSpike.send(intent.getStringExtra("to") ?: "5554", SmsSpike.SAMPLE_TEXT) }
                .onFailure { SpikeLog(this, SmsSpike.LOG_NAME).line("echec", it.message) }
            "gps" -> startForegroundService(Intent(this, GpsService::class.java).putExtra(GpsService.EXTRA_FUSED, false))
            "stopgps" -> stopService(Intent(this, GpsService::class.java))
        }

        // Résultats
        root.title("Résultats")
        root.row(
            "Partager les résultats" to ::shareLogs,
            "Effacer les mesures" to {
                listOf(MicService.LOG_NAME, EvidenceSpike.LOG_NAME, SmsSpike.LOG_NAME, GpsService.LOG_NAME)
                    .forEach { SpikeLog(this, it).clear() }
            }
        )
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { smsSpike.release(); super.onDestroy() }

    private fun gpsSummary(): String {
        val lines = SpikeLog(this, GpsService.LOG_NAME).file.takeIf { it.exists() }?.readLines().orEmpty()
        val fixes = lines.filter { it.contains(",position,") }
        if (fixes.isEmpty()) return "(aucune position)"
        fun field(l: String, key: String) = l.split(',').firstOrNull { it.startsWith(key) }?.substringAfter('=')
        val acc = fixes.mapNotNull { field(it, "precision_m")?.toIntOrNull() }.sorted()
        val gaps = fixes.mapNotNull { field(it, "ecart_s")?.toLongOrNull() }
        val batt = fixes.mapNotNull { field(it, "batterie")?.toIntOrNull() }
        return "positions=${fixes.size} · précision médiane=${acc[acc.size / 2]} m · pire écart=${gaps.maxOrNull()} s" +
            " · batterie ${batt.first()}% → ${batt.last()}%"
    }

    private fun shareLogs() {
        val files = File(filesDir, "logs").listFiles().orEmpty().filter { it.length() > 0 }
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(this, "com.cerclerouge.spikes.files", it) })
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/csv")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Partager les mesures"
        ))
    }

    private fun notifPerm() =
        if (android.os.Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    private fun withPermissions(perms: List<String>, action: () -> Unit) {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else { pendingAction = action; requestPermissions(missing.toTypedArray(), 1) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Le micro, la position ou le SMS sont indispensables ; les notifications non.
        val essentialDenied = permissions.indices.any {
            grantResults[it] != PackageManager.PERMISSION_GRANTED && permissions[it] != Manifest.permission.POST_NOTIFICATIONS
        }
        if (!essentialDenied) pendingAction?.invoke()
        pendingAction = null
    }

    // --- Petits constructeurs de vues ---
    private fun LinearLayout.title(t: String) = addView(TextView(context).apply {
        text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 48, 0, 8)
    })
    private fun LinearLayout.text(t: String) = addView(TextView(context).apply { text = t; textSize = 14f })
    private fun LinearLayout.mono(): TextView = TextView(context).apply {
        typeface = Typeface.MONOSPACE; textSize = 11f; setPadding(0, 12, 0, 12); setTextIsSelectable(true)
    }.also { addView(it) }
    private fun LinearLayout.check(t: String, checked: Boolean) =
        CheckBox(context).apply { text = t; isChecked = checked }.also { addView(it) }
    private fun LinearLayout.row(vararg buttons: Pair<String, () -> Unit>) = addView(LinearLayout(context).apply {
        buttons.forEach { (label, onClick) -> addView(Button(context).apply { text = label; setOnClickListener { onClick() } }) }
    })
}
