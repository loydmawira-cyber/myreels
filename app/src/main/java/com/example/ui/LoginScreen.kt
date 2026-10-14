package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppColors
import com.example.data.SupabaseRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(repo: SupabaseRepository, onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("in") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Faceless videos on autopilot",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AppColors.Text,
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a niche, pick a style, get a ready-to-post video in minutes.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Muted,
            modifier = Modifier.padding(bottom = 28.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.Border,
                focusedLabelColor = AppColors.Accent,
                unfocusedLabelColor = AppColors.Muted,
                focusedTextColor = AppColors.Text,
                unfocusedTextColor = AppColors.Text
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("At least 6 characters") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.Border,
                focusedLabelColor = AppColors.Accent,
                unfocusedLabelColor = AppColors.Muted,
                focusedTextColor = AppColors.Text,
                unfocusedTextColor = AppColors.Text
            )
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                busy = true
                scope.launch {
                    if (mode == "in") {
                        val res = repo.signIn(email.trim(), password)
                        busy = false
                        res.fold(
                            onSuccess = { onLoggedIn() },
                            onFailure = { 
                                Toast.makeText(context, "Sign in failed: ${it.localizedMessage}. You can also use Demo Mode.", Toast.LENGTH_LONG).show() 
                            }
                        )
                    } else {
                        val res = repo.signUp(email.trim(), password)
                        busy = false
                        res.fold(
                            onSuccess = { Toast.makeText(context, "Account created! Check your email to confirm or sign in.", Toast.LENGTH_LONG).show() },
                            onFailure = { Toast.makeText(context, "Sign up failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            if (busy) {
                CircularProgressIndicator(color = AppColors.Text, modifier = Modifier.size(24.dp))
            } else {
                Text(if (mode == "in") "Sign in" else "Create account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                repo.continueAsGuest()
                Toast.makeText(context, "Welcome to MyReels Demo!", Toast.LENGTH_SHORT).show()
                onLoggedIn()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Continue in Demo Mode (Instant Access)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { mode = if (mode == "in") "up" else "in" }) {
            Text(
                if (mode == "in") "New here? Create an account" else "Have an account? Sign in",
                color = AppColors.Muted
            )
        }
    }
}
