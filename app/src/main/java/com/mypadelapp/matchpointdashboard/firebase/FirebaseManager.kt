package com.mypadelapp.matchpointdashboard.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object FirebaseManager {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    //Usuario actual logueado:
    val usuarioActual get() = auth.currentUser
    val logueado get() = auth.currentUser != null

    //Registrar con correo y contraseña:
    fun registrar(email: String, password: String, nombre: String, apellido: String, onExito: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                //Guardamos el nombre y apellido en Firestore:
                db.collection("usuarios").document(uid)
                    .set(mapOf(
                        "nombre" to nombre,
                        "apellido" to apellido,
                        "email" to email
                    )).addOnSuccessListener {onExito()}.addOnFailureListener {onError(it.message ?: "Error")}
            }
            .addOnFailureListener {onError(it.message ?: "Error")}
    }

    //Login con correo y contraseña:
    fun login(email: String, password: String, onExito: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {onExito()}
            .addOnFailureListener {onError(it.message ?: "Error")}
    }

    //Cerramos la sesión:
    fun logout() {
        auth.signOut()
    }

    //Partidos ganados y perdidos:
    fun getPartidosGanadosYPerdidos(onResultado: (ganados: Int, perdidos: Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                val ganados  = partidos.count {it.getLong("ganador")?.toInt() == 1}
                val perdidos = partidos.count {it.getLong("ganador")?.toInt() == 2}
                onResultado(ganados, perdidos)
            }
            .addOnFailureListener {onResultado(0, 0)}
    }

    //Sets ganados y perdidos:
    fun getSetsGanadosYPerdidos(onResultado: (ganados: Int, perdidos: Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                var setsGanados = 0
                var setsPerdidos = 0
                var procesados = 0
                val total = partidos.size()

                if (total == 0) {
                    onResultado(0, 0); return@addOnSuccessListener
                }

                for (partido in partidos) {
                    db.collection("usuarios").document(uid)
                        .collection("partidos").document(partido.id)
                        .collection("puntos")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { puntos ->
                            val ultimo = puntos.firstOrNull()
                            setsGanados  += ultimo?.getLong("sets_pareja1")?.toInt() ?: 0
                            setsPerdidos += ultimo?.getLong("sets_pareja2")?.toInt() ?: 0
                            procesados++
                            if (procesados == total) onResultado(setsGanados, setsPerdidos)
                        }
                        .addOnFailureListener {
                            procesados++
                            if (procesados == total) onResultado(setsGanados, setsPerdidos)
                        }
                }
            }
            .addOnFailureListener {onResultado(0, 0)}
    }

    //Resumen de un partido por id_partido:
    fun getResumenPartido(idPartido: String,
                          onResultado: (fecha: String, resultado: String, sets: List<String>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(uid)
            .collection("partidos").document(idPartido)
            .get()
            .addOnSuccessListener { doc ->
                val fecha = doc.getString("fecha_inicio") ?: "Desconocida"
                val ganador = doc.getLong("ganador")?.toInt() ?: 0
                val resultado = if (ganador == 1) "Victoria" else "Derrota"

                //Leemos los puntos para calcular el resultado por sets:
                db.collection("usuarios").document(uid)
                    .collection("partidos").document(idPartido)
                    .collection("puntos")
                    .orderBy("timestamp")
                    .get()
                    .addOnSuccessListener { puntos ->
                        val sets = mutableListOf<String>()
                        var setsP1Anterior = 0
                        var setsP2Anterior = 0
                        var juegosP1Anterior = 0
                        var juegosP2Anterior = 0

                        for (punto in puntos) {
                            val setsP1 = punto.getLong("sets_pareja1")?.toInt() ?: 0
                            val setsP2 = punto.getLong("sets_pareja2")?.toInt() ?: 0
                            val juegosP1 = punto.getLong("juegos_pareja1")?.toInt() ?: 0
                            val juegosP2 = punto.getLong("juegos_pareja2")?.toInt() ?: 0

                            //Detectamos el cambio de set:
                            if (setsP1 != setsP1Anterior || setsP2 != setsP2Anterior) {
                                //El juego ganador ya está incluido en juegosP1Anterior/juegosP2Anterior + 1:
                                if (setsP1 > setsP1Anterior) {
                                    //Pareja 1 ganó el set:
                                    sets.add("${juegosP1Anterior + 1}-$juegosP2Anterior")
                                } else {
                                    //Pareja 2 ganó el set:
                                    sets.add("$juegosP1Anterior-${juegosP2Anterior + 1}")
                                }
                                setsP1Anterior = setsP1
                                setsP2Anterior = setsP2
                            }

                            juegosP1Anterior = juegosP1
                            juegosP2Anterior = juegosP2
                        }
                        onResultado(fecha, resultado, sets)
                    }
                    .addOnFailureListener {onResultado(fecha, resultado, emptyList())}
            }
            .addOnFailureListener {onResultado("Error", "Error", emptyList())}
    }

    //Nº de partidos jugados al día:
    fun getPartidosPorDia(onResultado: (Map<Int, Int>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                //1=Domingo ... 7=Sábado:
                val conteo = mutableMapOf<Int, Int>()
                for (partido in partidos) {
                    val dia = partido.getLong("dia_semana")?.toInt() ?: continue
                    conteo[dia] = (conteo[dia] ?: 0) + 1
                }
                onResultado(conteo)
            }
            .addOnFailureListener {onResultado(emptyMap())}
    }

    //Nº de partidos jugados al mes:
    fun getPartidosPorMes(onResultado: (Map<String, Int>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                val conteo = mutableMapOf<String, Int>()
                for (partido in partidos) {
                    val fecha = partido.getString("fecha_inicio") ?: continue
                    //Extraemos el año y el mes en formato "2026-04":
                    val mes = fecha.substring(0, 7)
                    conteo[mes] = (conteo[mes] ?: 0) + 1
                }
                onResultado(conteo)
            }
            .addOnFailureListener {onResultado(emptyMap())}
    }

    //Nº de partidos jugados por ubicación:
    fun getPartidosPorUbicacion(onResultado: (List<Triple<Double, Double, Int>>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                val conteo = mutableMapOf<Pair<Double, Double>, Int>()
                for (partido in partidos) {
                    val lat = partido.getDouble("latitud") ?: continue
                    val lng = partido.getDouble("longitud") ?: continue
                    if (lat == 0.0 && lng == 0.0) continue

                    //Redondeamos a 3 decimales para agrupar ubicaciones cercanas:
                    val latR = Math.round(lat * 1000).toDouble() / 1000
                    val lngR = Math.round(lng * 1000).toDouble() / 1000
                    val key = Pair(latR, lngR)
                    conteo[key] = (conteo[key] ?: 0) + 1
                }
                onResultado(conteo.map { Triple(it.key.first, it.key.second, it.value) })
            }
            .addOnFailureListener {onResultado(emptyList())}
    }

    //Duración media de los partidos:
    fun getDuracionMediaPartido(onResultado: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                val duraciones = partidos.mapNotNull {
                    it.getLong("duracion_total")?.toInt()
                }
                val media = if (duraciones.isNotEmpty()) duraciones.average().toInt() else 0
                onResultado(media)
            }
            .addOnFailureListener {onResultado(0)}
    }

    //Duración media por punto:
    fun getDuracionMediaPunto(onResultado: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("partidos")
            .get()
            .addOnSuccessListener { partidos ->
                var totalDuracion = 0
                var totalPuntos = 0
                var procesados = 0
                val total = partidos.size()

                if (total == 0) {onResultado(0); return@addOnSuccessListener}

                for (partido in partidos) {
                    val duracion = partido.getLong("duracion_total")?.toInt() ?: 0

                    db.collection("usuarios").document(uid)
                        .collection("partidos").document(partido.id)
                        .collection("puntos")
                        .get()
                        .addOnSuccessListener { puntos ->
                            totalDuracion += duracion
                            totalPuntos   += puntos.size()
                            procesados++
                            if (procesados == total) {
                                val media = if (totalPuntos > 0) totalDuracion / totalPuntos else 0
                                onResultado(media)
                            }
                        }
                        .addOnFailureListener {
                            procesados++
                            if (procesados == total) {
                                val media = if (totalPuntos > 0) totalDuracion / totalPuntos else 0
                                onResultado(media)
                            }
                        }
                }
            }
            .addOnFailureListener {onResultado(0)}
    }
}