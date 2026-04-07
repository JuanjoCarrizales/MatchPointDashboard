package com.mypadelapp.matchpointdashboard.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.mypadelapp.matchpointdashboard.R
import com.mypadelapp.matchpointdashboard.firebase.FirebaseManager

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etNombre = findViewById<TextInputEditText>(R.id.etNombre)
        val etApellido = findViewById<TextInputEditText>(R.id.etApellido)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etPasswordConfirm)
        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)
        val txtError = findViewById<TextView>(R.id.txtError)
        val txtLogin = findViewById<TextView>(R.id.txtLogin)

        btnRegistrar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val apellido = etApellido.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm = etPasswordConfirm.text.toString().trim()

            //Validaciones:
            when {
                email.isEmpty() || password.isEmpty() || confirm.isEmpty() ->
                    txtError.text = "Rellena todos los campos"
                nombre.isEmpty() || apellido.isEmpty() ->
                    txtError.text = "Introduce tu nombre"
                password != confirm ->
                    txtError.text = "Las contraseñas no coinciden"
                password.length < 6 ->
                    txtError.text = "La contraseña debe tener al menos 6 caracteres"
                else -> {
                    FirebaseManager.registrar(email, password, nombre, apellido,
                        onExito = {
                            FirebaseManager.logout()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        },
                        onError = {txtError.text = it}
                    )
                }
            }
        }

        txtLogin.setOnClickListener {
            //Vuelve al Login:
            finish()
        }
    }
}