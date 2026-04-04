package com.mypadelapp.matchpointdashboard.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mypadelapp.matchpointdashboard.R
import com.mypadelapp.matchpointdashboard.firebase.FirebaseManager
import com.mypadelapp.matchpointdashboard.historial.HistorialActivity
import com.mypadelapp.matchpointdashboard.login.LoginActivity
import com.mypadelapp.matchpointdashboard.estadisticas.EstadisticasActivity

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val txtSaludo = findViewById<TextView>(R.id.txtSaludo)
        val txtEmail = findViewById<TextView>(R.id.txtEmail)
        val botonEstadisticas = findViewById<Button>(R.id.botonEstadisticas)
        val botonHistorial = findViewById<Button>(R.id.botonHistorial)
        val txtLogout = findViewById<TextView>(R.id.txtLogout)

        // Mostramos el correo del usuario logueado:
        FirebaseManager.usuarioActual?.let {
            txtEmail.text = it.email
        }

        botonEstadisticas.setOnClickListener {
            startActivity(Intent(this, EstadisticasActivity::class.java))
        }

        botonHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        txtLogout.setOnClickListener {
            FirebaseManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}