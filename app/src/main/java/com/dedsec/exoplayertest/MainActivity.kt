package com.dedsec.exoplayertest

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.dedsec.exoplayertest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVideoPlayer.setOnClickListener {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            startActivity(intent)
        }
    }
}