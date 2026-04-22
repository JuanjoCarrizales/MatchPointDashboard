package com.mypadelapp.matchpointdashboard.estadisticas

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
import com.mypadelapp.matchpointdashboard.firebase.DataGenerator

class EstadisticasActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var txtVictorias: TextView
    private lateinit var txtDerrotas: TextView
    private lateinit var barraVictorias: ProgressBar
    private lateinit var barraDerrotas: ProgressBar
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

        inicializarVistas()
        cargarTodo()
    }

    private fun inicializarVistas() {
        val txtLogout = findViewById<TextView>(R.id.txtLogout)
        val txtNombre = findViewById<TextView>(R.id.txtNombre)

        // Mostramos el correo del usuario logueado:
        FirebaseManager.usuarioActual?.let { user ->
            FirebaseManager.db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val nombre = doc.getString("nombre") ?: ""
                    val apellido = doc.getString("apellido") ?: ""
                    txtNombre.text = "$nombre $apellido"
                }
        }

        //Botón temporal para generar datos sintéticos:
        /*val btnGenerar = findViewById<Button>(R.id.btnGenerar)
        btnGenerar.setOnClickListener {
            btnGenerar.isEnabled = false
            btnGenerar.text = "Generando..."
                DataGenerator.generarPartidos {
                    runOnUiThread {
                        btnGenerar.text = "Datos generados"
                        Toast.makeText(this, "20 partidos subidos!", Toast.LENGTH_SHORT).show()
                    }
                }
        }*/

        pieChart = findViewById(R.id.pieChart)
        txtVictorias = findViewById(R.id.txtVictorias)
        txtDerrotas = findViewById(R.id.txtDerrotas)
        barraVictorias = findViewById(R.id.barraVictorias)
        barraDerrotas = findViewById(R.id.barraDerrotas)
        txtSets = findViewById(R.id.txtSets)
        txtDuracionMedia = findViewById(R.id.txtDuracionMedia)
        txtMediaPunto = findViewById(R.id.txtMediaPunto)
        listaPartidos = findViewById(R.id.listaPartidos)
        chartDias = findViewById(R.id.chartDias)
        chartMeses = findViewById(R.id.chartMeses)
        mapa = findViewById(R.id.mapa)

        configurarMapa()

        txtLogout.setOnClickListener {
            FirebaseManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun cargarTodo() {
        cargarVictoriasDerrotas()
        cargarSets()
        cargarDuracionMedia()
        cargarMediaPunto()
        cargarUltimosPartidos()
        cargarPartidosPorDia()
        cargarPartidosPorMes()
        cargarUbicaciones()
    }

    //Partidos ganados/perdidos:
    private fun cargarVictoriasDerrotas() {
        FirebaseManager.getPartidosGanadosYPerdidos {ganados, perdidos ->
            runOnUiThread {
                val total = ganados + perdidos
                val pctGanados  = if (total > 0) (ganados * 100 / total) else 0
                val pctPerdidos = if (total > 0) (perdidos * 100 / total) else 0

                txtVictorias.text  = "$ganados ($pctGanados%)"
                txtDerrotas.text = "$perdidos ($pctPerdidos%)"
                barraVictorias.progress  = pctGanados
                barraDerrotas.progress = pctPerdidos

                //Gráfico en forma de donut:
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

    //Sets:
    private fun cargarSets() {
        FirebaseManager.getSetsGanadosYPerdidos {ganados, perdidos ->
            runOnUiThread {txtSets.text = "$ganados / $perdidos"}
        }
    }

    //Duración media de partido:
    private fun cargarDuracionMedia() {
        FirebaseManager.getDuracionMediaPartido {duracion ->
            runOnUiThread {
                val min = duracion / 60
                val seg = duracion % 60
                txtDuracionMedia.text = if (duracion > 0) "${"%02d:%02d".format(min, seg)}" else "—"
            }
        }
    }

    //Media por punto:
    private fun cargarMediaPunto() {
        FirebaseManager.getDuracionMediaPunto {seg ->
            runOnUiThread {
                txtMediaPunto.text = if (seg > 0) "${seg}s" else "—"
            }
        }
    }

    //Últimos partidos jugados:
    private fun cargarUltimosPartidos() {
        val uid = FirebaseManager.auth.currentUser?.uid ?: return
        FirebaseManager.db.collection("usuarios").document(uid)
            .collection("partidos")
            .orderBy("fecha_inicio", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { partidos ->
                listaPartidos.removeAllViews() //Limpia la lista antes de cargar (si se añade un nuevo partido y se recarga la pantalla)
                val total = partidos.size()
                //Array para mantener el orden:
                val resultados = arrayOfNulls<Triple<String, String, List<String>>>(total)
                var procesados = 0

                partidos.forEachIndexed { index, partido ->
                    FirebaseManager.getResumenPartido(partido.id) { fecha, resultado, sets ->
                        //Guardamos en la posición correcta:
                        resultados[index] = Triple(fecha, resultado, sets)
                        procesados++
                        //Cuando todos estén listos, mostramos en orden:
                        if (procesados == total) {
                            runOnUiThread {
                                resultados.filterNotNull().forEach { (fecha, resultado, sets) ->
                                    aFilaPartido(fecha, resultado, sets)
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun aFilaPartido(fecha: String, resultado: String, sets: List<String>) {
        val fila = layoutInflater.inflate(R.layout.icono_victoria_derrota, listaPartidos, false)

        val txtIcono = fila.findViewById<TextView>(R.id.txtIcono)
        val txtSetsPartido = fila.findViewById<TextView>(R.id.txtSetsPartido)
        val txtFechaPartido = fila.findViewById<TextView>(R.id.txtFechaPartido)
        val txtBadge = fila.findViewById<TextView>(R.id.txtBadge)
        val Victoria = resultado == "Victoria"

        txtIcono.text = if (Victoria) "\uD83C\uDFC6" else "\uD83D\uDE13"
        txtSetsPartido.text = if (sets.isNotEmpty()) sets.joinToString(", ") else "Sin sets"
        txtFechaPartido.text = fecha.take(10)

        txtBadge.text = resultado
        txtBadge.setTextColor(Color.parseColor(if (Victoria) "#2ECC71" else "#E74C3C"))
        txtBadge.setBackgroundResource(if (Victoria) R.drawable.badge_victoria else R.drawable.badge_derrota)

        listaPartidos.addView(fila)
    }

    //Partidos jugados por día:
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

    //Partidos jugados distribuidos por mes:
    private fun cargarPartidosPorMes() {
        FirebaseManager.getPartidosPorMes { mesesMap ->
            runOnUiThread {
                val meses = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun",
                    "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
                val entries = (1..12).map { mes ->
                    val key = mesesMap.keys.firstOrNull { it.endsWith("-%02d".format(mes)) }
                    BarEntry(mes.toFloat() - 1, (key?.let { mesesMap[it] } ?: 0).toFloat())
                }
                configurarBarChart(chartMeses, entries, meses, Color.parseColor("#1A2E45"))
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
            setDoubleTapToZoomEnabled(false)
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

    //Mapa:
    private fun configurarMapa() {
        mapa.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(41.58539967291195, 1.5441157996530586))//Mapa de Cataluña por defecto
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