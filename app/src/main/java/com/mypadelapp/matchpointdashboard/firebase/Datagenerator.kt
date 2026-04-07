package com.mypadelapp.matchpointdashboard.firebase

import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlin.random.Random
import android.util.Log

object DataGenerator {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseManager.auth

    fun generarPartidos(onFinalizado: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        var subidos = 0
        val total = 1

        repeat(total) { i ->
            val cal = Calendar.getInstance()
            //Fechas variadas en los últimos 12 meses:
            cal.add(Calendar.DAY_OF_YEAR, -Random.nextInt(1, 365))
            val fechaInicio = "%04d-%02d-%02dT%02d:%02d:00".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                Random.nextInt(8, 22), // hora entre 8 y 22
                Random.nextInt(0, 59)
            )

            val ganador    = Random.nextInt(1, 3) // 1 o 2
            val duracion   = Random.nextInt(1800, 7200) // 30min - 2h
            val diaSemana  = cal.get(Calendar.DAY_OF_WEEK)
            val horaInicio = cal.get(Calendar.HOUR_OF_DAY)
            val minutoInicio = cal.get(Calendar.MINUTE)

            //Ubicaciones variadas en España:
            val ubicaciones = listOf(
                Pair(41.58981786713462, 2.2502560027140834),  //Padel Lliça
                Pair(41.63080571712787, 2.2914078320130398),   //Padel les Franqueses
                Pair(41.58189724773944, 2.2737908500916393),  //Padel indoor Granollers
                Pair(41.5373525953293, 2.2229578703246706),  //Padel Mollet
                Pair(41.513705109757645, 2.196077383814871),  //Padel La Llagosta
                Pair(41.55537850900299, 2.280726943089949)   //Padel indoor Vilanova
            ).random()

            val partido = hashMapOf(
                "fecha_inicio"   to fechaInicio,
                "fecha_fin"      to fechaInicio,
                "duracion_total" to duracion,
                "ganador"        to ganador,
                "dia_semana"     to diaSemana,
                "hora_inicio"    to horaInicio,
                "minuto_inicio"  to minutoInicio,
                "latitud"        to ubicaciones.first,
                "longitud"       to ubicaciones.second
            )

            db.collection("usuarios").document(uid)
                .collection("partidos")
                .add(partido)
                .addOnSuccessListener { docRef ->
                    //Generamos los puntos del partido:
                    generarPuntos(uid, docRef.id, ganador, duracion)
                    subidos++
                    if (subidos == total) onFinalizado()
                }
                .addOnFailureListener {
                    subidos++
                    if (subidos == total) onFinalizado()
                }
        }
    }

    private fun generarPuntos(uid: String, idPartido: String, ganadorPartido: Int, duracion: Int) {
        Log.d("DEBUG", "Start")
        Log.d("DEBUG", ganadorPartido.toString())
        var timestamp = 0
        var setsP1 = 0
        var setsP2 = 0

        //30% partidos a 2 sets, 70% a 3 sets:
        val perdedor = if (ganadorPartido == 1) 2 else 1
        val ganadorPorSet = if (Random.nextFloat() < 0.5f) {
            listOf(ganadorPartido, ganadorPartido)
        } else {
            listOf(ganadorPartido, perdedor, ganadorPartido)
        }
        Log.d("DEBUG", "1--")
        Log.d("DEBUG", perdedor.toString())
        Log.d("DEBUG", ganadorPorSet.toString())

        for (ganadorSet in ganadorPorSet) {
            Log.d("DEBUG", "Loop")
            Log.d("DEBUG", ganadorSet.toString())
            var juegosP1 = 0
            var juegosP2 = 0

            //Jugamos hasta que alguien gane el set:
            var setAcabado = false
            while (!setAcabado) {

                //Jugamos un juego:
                var puntosP1 = 0
                var puntosP2 = 0
                var ventajaP1 = false
                var ventajaP2 = false
                var juegoAcabado = false
                var ganadorJuego = 0

                while (!juegoAcabado) {
                    timestamp += 5 + Random.nextInt(40)
                    val prob = if (ganadorSet == 1) 0.6f else 0.4f
                    val ganadorPunto = if (Random.nextFloat() < prob) 1 else 2

                    //Replicamos exactamente la lógica de PartidoPadel.java:
                    if (puntosP1 >= 3 && puntosP2 >= 3) {
                        //Iguales:
                        when {
                            ganadorPunto == 1 && ventajaP2 -> ventajaP2 = false
                            ganadorPunto == 1 && ventajaP1 -> {
                                ganadorJuego = 1; juegoAcabado = true
                            }

                            ganadorPunto == 1 -> ventajaP1 = true
                            ganadorPunto == 2 && ventajaP1 -> ventajaP1 = false
                            ganadorPunto == 2 && ventajaP2 -> {
                                ganadorJuego = 2; juegoAcabado = true
                            }

                            else -> ventajaP2 = true
                        }
                    } else {
                        if (ganadorPunto == 1) puntosP1++ else puntosP2++
                        if (puntosP1 >= 4 && puntosP1 >= puntosP2 + 2) {
                            ganadorJuego = 1; juegoAcabado = true
                        }
                        if (puntosP2 >= 4 && puntosP2 >= puntosP1 + 2) {
                            ganadorJuego = 2; juegoAcabado = true
                        }
                    }
                    if (!juegoAcabado) {
                        val punto = hashMapOf(
                            "timestamp" to timestamp,
                            "pareja_ganadora" to ganadorPunto,
                            "puntos_pareja1" to minOf(puntosP1, 3),
                            "puntos_pareja2" to minOf(puntosP2, 3),
                            "juegos_pareja1" to juegosP1,
                            "juegos_pareja2" to juegosP2,
                            "sets_pareja1" to setsP1,
                            "sets_pareja2" to setsP2,
                            "tiebreak" to false
                        )

                        Log.d("DEBUG", "Punto")
                        Log.d("DEBUG", punto.toString())

                        db.collection("usuarios").document(uid)
                            .collection("partidos").document(idPartido)
                            .collection("puntos").add(punto)
                    }
                }

                //Actualizamos juegos tras el juego:
                if (ganadorJuego == 1) juegosP1++ else juegosP2++
                Log.d("DEBUG", "Ganador y juegos")
                Log.d("DEBUG", ganadorJuego.toString())
                Log.d("DEBUG", juegosP1.toString())
                Log.d("DEBUG", juegosP2.toString())

                setAcabado = (juegosP1 >= 6 && juegosP1 >= juegosP2 + 2) ||
                        (juegosP2 >= 6 && juegosP2 >= juegosP1 + 2) ||
                        (juegosP1 == 7 || juegosP2 == 7)

                if (setAcabado) {
                    if (juegosP1 > juegosP2) setsP1++ else setsP2++
                    //Guardamos el último punto del juego ANTES de actualizar juegos:
                    val puntoFinal = hashMapOf(
                        "timestamp"       to timestamp + 1,
                        "pareja_ganadora" to ganadorJuego,
                        "puntos_pareja1"  to 0,
                        "puntos_pareja2"  to 0,
                        "juegos_pareja1"  to 0,
                        "juegos_pareja2"  to 0,
                        "sets_pareja1"    to setsP1,
                        "sets_pareja2"    to setsP2,
                        "tiebreak"        to false
                    )
                    db.collection("usuarios").document(uid)
                        .collection("partidos").document(idPartido)
                        .collection("puntos").add(puntoFinal)
                } else {
                    val punto = hashMapOf(
                        "timestamp"       to timestamp,
                        "pareja_ganadora" to ganadorJuego,
                        "puntos_pareja1"  to minOf(puntosP1, 3),
                        "puntos_pareja2"  to minOf(puntosP2, 3),
                        "juegos_pareja1"  to juegosP1,  // ← ya incrementados
                        "juegos_pareja2"  to juegosP2,
                        "sets_pareja1"    to setsP1,
                        "sets_pareja2"    to setsP2,
                        "tiebreak"        to false
                    )
                    db.collection("usuarios").document(uid)
                        .collection("partidos").document(idPartido)
                        .collection("puntos").add(punto)
                }

                Log.d("DEBUG", "Sets")
                Log.d("DEBUG", setsP1.toString())
                Log.d("DEBUG", setsP2.toString())
            }
        }
    }
}