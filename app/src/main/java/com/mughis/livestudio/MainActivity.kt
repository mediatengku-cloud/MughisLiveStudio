package com.mughis.livestudio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mughis.livestudio.databinding.ActivityMainBinding
import com.pedro.common.ConnectChecker

class MainActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var binding: ActivityMainBinding
    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStreaming = false
    private var selectedVideoUri: Uri? = null

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            setupPlayer(it)
            binding.txtStatus.text = "Status: Video siap diputar berulang"
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Izin diperlukan untuk live streaming", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtMarquee.isSelected = true

        checkPermissions()
        setupListeners()
        setupWakeLock()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WAKE_LOCK
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupListeners() {
        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/mp4")
        }

        binding.btnEtalase1.setOnClickListener {
            binding.txtCartTitle.text = "🛒 Etalase #1 Disematkan"
            binding.txtMarquee.text = "🔥 DISKON ETALASE 1 HARI INI • KUALITAS ORIGINAL GARANSI RESMI 🔥"
        }

        binding.btnEtalase2.setOnClickListener {
            binding.txtCartTitle.text = "🛒 Etalase #2 Disematkan"
            binding.txtMarquee.text = "⚡ FLASH SALE ETALASE 2 • TERJUAL RATUSAN PCS • KLIK KERANJANG SEKARANG ⚡"
        }

        binding.btnToggleLive.setOnClickListener {
            if (!isStreaming) {
                startStreaming()
            } else {
                stopStreaming()
            }
        }
    }

    private fun setupPlayer(uri: Uri) {
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            play()
        }
    }

    private fun setupWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MughisLiveStudio::StreamingLock"
        )
    }

    private fun startStreaming() {
        val rtmpUrl = binding.inputRtmpUrl.text.toString().trim()
        val streamKey = binding.inputStreamKey.text.toString().trim()

        if (rtmpUrl.isEmpty() || streamKey.isEmpty()) {
            Toast.makeText(this, "Harap isi RTMP URL dan Stream Key", Toast.LENGTH_SHORT).show()
            return
        }

        wakeLock?.acquire(12 * 60 * 60 * 1000L)
        isStreaming = true
        binding.btnToggleLive.text = "HENTIKAN SIARAN"
        binding.btnToggleLive.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        binding.txtStatus.text = "Status: Menghubungkan ke server..."

        onConnectionSuccess()
    }

    private fun stopStreaming() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        isStreaming = false
        binding.btnToggleLive.text = "MULAI SIARAN LIVE"
        binding.btnToggleLive.setBackgroundColor(getColor(R.color.tiktok_red))
        binding.txtStatus.text = "Status: Siaran Berhenti"
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread { binding.txtStatus.text = "Status: Menghubungkan..." }
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            binding.txtStatus.text = "Status: LIVE TIKTOK BERJALAN (Looping Aktif)"
            Toast.makeText(this, "Siaran Berhasil Terhubung!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            stopStreaming()
            binding.txtStatus.text = "Status: Gagal Terhubung ($reason)"
            Toast.makeText(this, "Koneksi Gagal: $reason", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        runOnUiThread {
            val kbps = bitrate / 1000
            binding.txtStatus.text = "Status: LIVE • Bitrate: ${kbps} Kbps"
        }
    }

    override fun onDisconnect() {
        runOnUiThread { stopStreaming() }
    }

    override fun onAuthError() {
        runOnUiThread {
            Toast.makeText(this, "Autentikasi Stream Key Salah!", Toast.LENGTH_SHORT).show()
            stopStreaming()
        }
    }

    override fun onAuthSuccess() {}

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
