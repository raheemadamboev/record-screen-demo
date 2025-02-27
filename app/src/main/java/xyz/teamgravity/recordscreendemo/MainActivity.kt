package xyz.teamgravity.recordscreendemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.teamgravity.recordscreendemo.ui.theme.RecordScreenDemoTheme

class MainActivity : ComponentActivity() {

    private val mediaProjectionManager: MediaProjectionManager by lazy { getSystemService()!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecordScreenDemoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { padding ->
                    val isServiceRunning by RecordScreenService.running.collectAsStateWithLifecycle()

                    var hasNotificationPermission by remember {
                        mutableStateOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else true
                        )
                    }

                    val recordScreenLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult(),
                        onResult = { result ->
                            val data = result.data ?: return@rememberLauncherForActivityResult
                            val config = RecordScreenService.Config(
                                resultCode = result.resultCode,
                                data = data
                            )
                            val intent = Intent(applicationContext, RecordScreenService::class.java)
                            intent.action = RecordScreenService.Action.StartRecording.name
                            intent.putExtra(RecordScreenService.EXTRA_CONFIG, config)
                            startForegroundService(intent)
                        }
                    )

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted ->
                            hasNotificationPermission = granted
                            if (hasNotificationPermission && !isServiceRunning) {
                                recordScreenLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            }
                        }
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Button(
                            onClick = {
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    if (isServiceRunning) {
                                        val intent = Intent(applicationContext, RecordScreenService::class.java)
                                        intent.action = RecordScreenService.Action.StopRecording.name
                                        startForegroundService(intent)
                                    } else {
                                        recordScreenLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) Color.Red else Color.Green
                            )
                        ) {
                            Text(
                                text = stringResource(if (isServiceRunning) R.string.stop_recording else R.string.start_recording),
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}