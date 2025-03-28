package com.example.resqcall

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnHelp: Button
    private lateinit var btnStop: Button
    private lateinit var txtTimer: TextView
    private lateinit var btnSelectContact: Button
    private lateinit var waveView: View
    private var mediaPlayer: MediaPlayer? = null
    private var emergencyContact: String? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnHelp = findViewById(R.id.btnHelp)
        btnStop = findViewById(R.id.btnStop)
        txtTimer = findViewById(R.id.txtTimer)
        waveView = findViewById(R.id.waveView)
        btnSelectContact = findViewById(R.id.btnSelectContact)

        btnHelp.setOnClickListener { startSOS() }
        btnStop.setOnClickListener { stopSOS() }
        btnSelectContact.setOnClickListener { selectContact() }

        requestPermissions()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS
            ), 1)
        }
    }

    private fun startSOS() {
        if (emergencyContact.isNullOrEmpty()) {
            txtTimer.text = "Select an emergency contact first!"
            return
        }

        waveView.visibility = View.VISIBLE
        animateWave()
        increaseVolume()
        playAlarmSound()

        countdownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                txtTimer.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                txtTimer.text = "Calling Help!"
                callEmergencyContact()
            }
        }.start()

        btnHelp.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
    }

    private fun stopSOS() {
        countdownTimer?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        waveView.visibility = View.GONE
        txtTimer.text = "Press HELP"
        btnHelp.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
    }

    private fun animateWave() {
        val animator = ValueAnimator.ofInt(100, 1000).apply {
            duration = 30000
            addUpdateListener {
                val value = it.animatedValue as Int
                waveView.layoutParams.width = value
                waveView.layoutParams.height = value
                waveView.requestLayout()
            }
        }
        animator.start()
    }

    private fun playAlarmSound() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm).apply {
            isLooping = true
            start()
        }
    }

    private fun increaseVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
    }

    private fun callEmergencyContact() {
        emergencyContact?.let { number ->
            sendEmergencySMS(number)

            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }
        }
    }

    private fun sendEmergencySMS(contactNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "🚨 SOS ALERT! I need help immediately. Please call me now!"
            smsManager.sendTextMessage(contactNumber, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun selectContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.data?.let { uri ->
                val cursor = contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        emergencyContact = it.getString(0)
                        txtTimer.text = "Emergency Contact: $emergencyContact"
                    }
                }
            }
        }
    }
}
