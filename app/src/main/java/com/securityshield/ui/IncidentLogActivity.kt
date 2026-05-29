package com.securityshield.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.securityshield.databinding.ActivityIncidentLogBinding
import com.securityshield.utils.SecurityDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class IncidentLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncidentLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvIncidents.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            SecurityDatabase.getInstance(this@IncidentLogActivity)
                .incidentDao()
                .getAllIncidents()
                .collectLatest { incidents ->
                    binding.tvIncidentCount.text = "${incidents.size} incidents recorded"
                    // Adapter would be set here
                }
        }
    }
}
