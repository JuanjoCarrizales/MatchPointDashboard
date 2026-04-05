package com.mypadelapp.matchpointdashboard.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.mypadelapp.matchpointdashboard.R
import com.mypadelapp.matchpointdashboard.firebase.FirebaseManager
import com.mypadelapp.matchpointdashboard.estadisticas.EstadisticasActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Si el usuario está logueado, ir a "estadísticas":
        if(FirebaseManager.logueado) {
            estadisticas()
            return
        }

        setContentView(R.layout.activity_login)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val botonLogin = findViewById<Button>(R.id.botonLogin)
        val txtError = findViewById<TextView>(R.id.txtError)
        val txtRegistro = findViewById<TextView>(R.id.txtRegistro)

        botonLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if(email.isEmpty() || password.isEmpty()) {
                txtError.text = "Rellena todos los campos obligatorios"
                return@setOnClickListener
            }

            FirebaseManager.login(email, password,
                onExito = { estadisticas() },
                onError = { txtError.text = it }
            )
        }

        txtRegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun estadisticas() {
        startActivity(Intent(this, EstadisticasActivity::class.java))
    }
}