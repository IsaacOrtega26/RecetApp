package com.example.recetapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.ActivityCookingModeBinding
import kotlinx.coroutines.launch

class CookingModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingModeBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var recipeId: String? = null
    private var steps: List<PasoReceta> = emptyList()
    private var currentStepIndex = 0

    private var seconds = 0
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            if (running) {
                seconds++
                updateTimerText()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            running = !running
            binding.btnPlayPause.setImageResource(
                if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }

        binding.btnResetTimer.setOnClickListener {
            running = false
            seconds = 0
            updateTimerText()
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }

        binding.btnNext.setOnClickListener {
            if (currentStepIndex < steps.size - 1) {
                currentStepIndex++
                updateStepUI()
            } else {
                finish()
            }
        }

        binding.btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                updateStepUI()
            }
        }

        loadData()
        handler.post(runnable)
    }

    private fun loadData() {
        recipeId?.let { id ->
            lifecycleScope.launch {
                try {
                    steps = repository.getPasos(id)
                    val ingredients = repository.getIngredientes(id)
                    
                    binding.rvIngredients.adapter = IngredientCheckAdapter(ingredients)
                    updateStepUI()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateStepUI() {
        if (steps.isEmpty()) return

        val step = steps[currentStepIndex]
        binding.tvStepLabel.text = "Paso ${currentStepIndex + 1} de ${steps.size}"
        binding.tvStepDescription.text = step.descripcion
        binding.tvStepTime.text = if (step.tiempoSegundos != null) "${step.tiempoSegundos / 60} min" else ""
        
        binding.btnPrevious.isEnabled = currentStepIndex > 0
        binding.btnNext.text = if (currentStepIndex == steps.size - 1) "¡Listo!" else "Siguiente"

        // Update progress indicators (simplified)
        binding.progressContainer.removeAllViews()
        for (i in steps.indices) {
            val view = View(this)
            val params = LinearLayout.LayoutParams(0, 8, 1f)
            params.setMargins(4, 0, 4, 0)
            view.layoutParams = params
            view.setBackgroundColor(
                if (i <= currentStepIndex) getColor(R.color.orange_primary) else getColor(R.color.white_translucent)
            )
            binding.progressContainer.addView(view)
        }
    }

    private fun updateTimerText() {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvTimer.text = java.util.Locale.getDefault().let { locale ->
            String.format(locale, "%02d:%02d", mins, secs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    inner class IngredientCheckAdapter(private val list: List<IngredienteReceta>) : RecyclerView.Adapter<IngredientCheckAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(CheckBox(parent.context))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            (holder.itemView as CheckBox).apply {
                text = "${item.nombre} — ${item.cantidad ?: ""} ${item.unidad ?: ""}"
                setTextColor(getColor(R.color.white))
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
