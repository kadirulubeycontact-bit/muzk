package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MasivaBlack
import com.example.ui.theme.MasivaCharcoal
import com.example.ui.theme.MasivaTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    // Permission states
    private val permissionsGrantedState = mutableStateOf(false)
    private val didRequestPermissionsState = mutableStateOf(false)

    // Formulate permissions according to target API boundaries
    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    // Permission launcher receiver
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        didRequestPermissionsState.value = true
        val allGranted = requiredPermissions.all { perm -> results[perm] == true }
        permissionsGrantedState.value = allGranted

        // Always scan: if granted scan device, otherwise synth files are preloaded.
        viewModel.scanMusicFiles(allGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Perform initial device permission analysis
        checkDevicePermissions()

        setContent {
            MasivaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MasivaBlack
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    if (showSplash) {
                        SplashScreen(onSplashFinished = { showSplash = false })
                    } else {
                        val isGranted = permissionsGrantedState.value
                        val didRequest = didRequestPermissionsState.value

                        if (!isGranted && didRequest) {
                            // Permission Denied Explanation Page
                            PermissionRationaleScreen(
                                onGrantClicked = { requestNeededPermissions() },
                                onProceedShowcase = {
                                    // Let developer / user explore with synthesizer tracks
                                    permissionsGrantedState.value = true
                                }
                            )
                        } else {
                            // Perfect execution flow
                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun checkDevicePermissions() {
        val allGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        permissionsGrantedState.value = allGranted
        if (allGranted) {
            viewModel.scanMusicFiles(true)
        } else {
            requestNeededPermissions()
        }
    }

    private fun requestNeededPermissions() {
        requestPermissionLauncher.launch(requiredPermissions)
    }
}

@Composable
fun PermissionRationaleScreen(
    onGrantClicked: () -> Unit,
    onProceedShowcase: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MasivaBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MasivaCharcoal),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Müzik Erişimi Gerekli",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Masiva, cihazınızdaki MP3, FLAC, WAV ve diğer ses formatlarını taramak ve oynatmak için dosya depolama iznine ihtiyaç duyar.",
                    color = Color(0xFF9EABA2),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onGrantClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("İzin Ver ve Tara", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onProceedShowcase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Sentezleyici ile Denemeye Başla (İzinsiz)",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
