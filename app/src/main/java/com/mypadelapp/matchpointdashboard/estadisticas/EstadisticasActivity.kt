package com.mypadelapp.matchpointdashboard.estadisticas

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.mypadelapp.matchpointdashboard.R
import com.mypadelapp.matchpointdashboard.firebase.FirebaseManager
import com.mypadelapp.matchpointdashboard.login.LoginActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class EstadisticasActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var txtGanados: TextView
    private lateinit var txtPerdidos: TextView
    private lateinit var barraGanados: ProgressBar
    private lateinit var barraPerdidos: ProgressBar
    private lateinit var txtSets: TextView
    private lateinit var txtDuracionMedia: TextView
    private lateinit var txtMediaPunto: TextView
    private lateinit var listaPartidos: LinearLayout
    private lateinit var chartDias: BarChart
    private lateinit var chartMeses: BarChart
    private lateinit var mapa: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración osmdroid:
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_estadisticas)

        val txtSaludo = findViewById<TextView>(R.id.txtSaludo)
        val txtEmail = findViewById<TextView>(R.id.txtEmail)
        // Mostramos el correo del usuario logueado:
        FirebaseManager.usuarioActual?.let {
            txtEmail.text = it.email
        }

        inicializarVistas()
        cargarTodo()

        val txtLogout = findViewById<TextView>(R.id.txtLogout)
        txtLogout.setOnClickListener {
            FirebaseManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun inicializarVistas() {
        pieChart        = findViewById(R.id.pieChart)
        txtGanados      = findViewById(R.id.txtGanados)
        txtPerdidos     = findViewById(R.id.txtPerdidos)
        barraGanados    = findViewById(R.id.barraGanados)
        barraPerdidos   = findViewById(R.id.barraPerdidos)
        txtSets         = findViewById(R.id.txtSets)
        txtDuracionMedia = findViewById(R.id.txtDuracionMedia)
        txtMediaPunto   = findViewById(R.id.txtMediaPunto)
        listaPartidos   = findViewById(R.id.listaPartidos)
        chartDias       = findViewById(R.id.chartDias)
        chartMeses      = findViewById(R.id.chartMeses)
        mapa            = findViewById(R.id.mapa)

        configurarMapa()
    }

    private fun cargarTodo() {
        cargarPartidosGanadosPerdidos()
        cargarSets()
        cargarDuracionMedia()
        cargarMediaPunto()
        cargarUltimosPartidos()
        cargarPartidosPorDia()
        cargarPartidosPorMes()
        cargarUbicaciones()
    }

    // ─── Partidos ganados/perdidos ────────────────────────────
    private fun cargarPartidosGanadosPerdidos() {
        FirebaseManager.getPartidosGanadosYPerdidos { ganados, perdidos ->
            runOnUiThread {
                val total = ganados + perdidos
                val pctGanados  = if (total > 0) (ganados * 100 / total) else 0
                val pctPerdidos = if (total > 0) (perdidos * 100 / total) else 0

                txtGanados.text  = "$ganados ($pctGanados%)"
                txtPerdidos.text = "$perdidos ($pctPerdidos%)"
                barraGanados.progress  = pctGanados
                barraPerdidos.progress = pctPerdidos

                // Gráfico donut:
                val entries = listOf(
                    PieEntry(ganados.toFloat(), ""),
                    PieEntry(perdidos.toFloat(), "")
                )
                val dataSet = PieDataSet(entries, "").apply {
                    colors = listOf(Color.parseColor("#2ECC71"), Color.parseColor("#E74C3C"))
                    sliceSpace = 2f
                    setDrawValues(false)
                }
                pieChart.apply {
                    data = PieData(dataSet)
                    isDrawHoleEnabled = true
                    holeRadius = 60f
                    setHoleColor(Color.WHITE)
                    centerText = "$total\ntotal"
                    setCenterTextSize(12f)
                    description.isEnabled = false
                    legend.isEnabled = false
                    invalidate()
                }
            }
        }
    }

    // ─── Sets ─────────────────────────────────────────────────
    private fun cargarSets() {
        FirebaseManager.getSetsGanadosYPerdidos { ganados, perdidos ->
            runOnUiThread { txtSets.text = "$ganados / $perdidos" }
        }
    }

    // ─── Duración media partido ───────────────────────────────
    private fun cargarDuracionMedia() {
        FirebaseManager.getDuracionMediaPartido { duracion ->
            runOnUiThread {
                val min = duracion / 60
                val seg = duracion % 60
                txtDuracionMedia.text = if (duracion > 0) "%02d:%02d".format(min, seg) else "—"
            }
        }
    }

    // ─── Media por punto ──────────────────────────────────────
    private fun cargarMediaPunto() {
        FirebaseManager.getDuracionMediaPunto { seg ->
            runOnUiThread {
                txtMediaPunto.text = if (seg > 0) "${seg}s" else "—"
            }
        }
    }

    // ─── Últimos partidos ─────────────────────────────────────
    private fun cargarUltimosPartidos() {
        val uid = FirebaseManager.auth.currentUser?.uid ?: return
        FirebaseManager.db.collection("usuarios").document(uid)
            .collection("partidos")
            .orderBy("fecha_inicio", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { partidos ->
                for (partido in partidos) {
                    FirebaseManager.getResumenPartido(partido.id) { fecha, resultado, sets ->
                        runOnUiThread {
                            añadirFilaPartido(fecha, resultado, sets)
                        }
                    }
                }
            }
    }

    private fun añadirFilaPartido(fecha: String, resultado: String, sets: List<String>) {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            gravity = Gravity.CENTER_VERTICAL
        }

        // Icono victoria/derrota:
        val icono = TextView(this).apply {
            text = if (resultado == "Victoria") "🏆" else "❌"
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(48, 48)
        }

        // Info del partido:
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(12, 0, 0, 0) }
        }

        val txtSetsPartido = TextView(this).apply {
            text = sets.joinToString(", ")
            textSize = 14f
            setTextColor(Color.parseColor("#1a1a1a"))
        }

        val txtFechaPartido = TextView(this).apply {
            text = fecha.take(10) // Solo la fecha sin hora
            textSize = 11f
            setTextColor(Color.parseColor("#999999"))
        }

        info.addView(txtSetsPartido)
        info.addView(txtFechaPartido)

        // Badge victoria/derrota:
        val badge = TextView(this).apply {
            text = resultado
            textSize = 12f
            setPadding(12, 6, 12, 6)
            setTextColor(if (resultado == "Victoria") Color.parseColor("#2ECC71") else Color.parseColor("#E74C3C"))
            background = if (resultado == "Victoria")
                getDrawable(android.R.drawable.btn_default) else getDrawable(android.R.drawable.btn_default)
        }

        fila.addView(icono)
        fila.addView(info)
        fila.addView(badge)
        listaPartidos.addView(fila)
    }

    // ─── Partidos por día ─────────────────────────────────────
    private fun cargarPartidosPorDia() {
        FirebaseManager.getPartidosPorDia { diasMap ->
            runOnUiThread {
                val dias = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
                val entries = (1..7).map { dia ->
                    BarEntry(dia.toFloat() - 1, (diasMap[dia] ?: 0).toFloat())
                }
                configurarBarChart(chartDias, entries, dias, Color.parseColor("#2ECC71"))
            }
        }
    }

    // ─── Partidos por mes ─────────────────────────────────────
    private fun cargarPartidosPorMes() {
        FirebaseManager.getPartidosPorMes { mesesMap ->
            runOnUiThread {
                val meses = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun",
                    "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
                val entries = (1..12).map { mes ->
                    val key = mesesMap.keys.firstOrNull { it.endsWith("-%02d".format(mes)) }
                    BarEntry(mes.toFloat() - 1, (key?.let { mesesMap[it] } ?: 0).toFloat())
                }
                configurarBarChart(chartMeses, entries, meses, Color.parseColor("#F39C12"))
            }
        }
    }

    private fun configurarBarChart(chart: BarChart, entries: List<BarEntry>,
                                   labels: List<String>, color: Int) {
        val dataSet = BarDataSet(entries, "").apply {
            this.color = color
            setDrawValues(false)
        }
        chart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.setDrawGridLines(true)
            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }

    // ─── Mapa ─────────────────────────────────────────────────
    private fun configurarMapa() {
        mapa.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(40.4168, -3.7038)) // España por defecto
        }
    }

    private fun cargarUbicaciones() {
        FirebaseManager.getPartidosPorUbicacion { ubicaciones ->
            runOnUiThread {
                for ((lat, lng, count) in ubicaciones) {
                    val marker = Marker(mapa).apply {
                        position = GeoPoint(lat, lng)
                        title = "$count partido${if (count > 1) "s" else ""}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapa.overlays.add(marker)
                }
                if (ubicaciones.isNotEmpty()) {
                    val primera = ubicaciones.first()
                    mapa.controller.setCenter(GeoPoint(primera.first, primera.second))
                    mapa.controller.setZoom(8.0)
                }
                mapa.invalidate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapa.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapa.onPause()
    }
}