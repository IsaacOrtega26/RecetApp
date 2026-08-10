package com.example.recetapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
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
        hideSystemUI()
        binding = ActivityCookingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")
        Log.d("RecetApp", "Modo Cocina - Cargando ID: $recipeId")

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            running = !running
            binding.btnPlayPause.setImageResource(if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
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
                    
                    Log.d("RecetApp", "Modo Cocina - Pasos: ${steps.size}, Ingredientes: ${ingredients.size}")

                    if (steps.isEmpty()) {
                        Toast.makeText(this@CookingModeActivity, "No se encontraron pasos para esta receta", Toast.LENGTH_SHORT).show()
                    }

                    binding.rvIngredients.adapter = IngredientCheckAdapter(ingredients)
                    if (steps.isNotEmpty()) {
                        updateStepUI()
                    } else {
                        binding.tvStepDescription.text = "Esta receta no tiene pasos registrados en la base de datos."
                        binding.tvStepLabel.text = "No hay pasos"
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error cargando datos modo cocina", e)
                }
            }
        }
    }

    private fun updateStepUI() {
        if (steps.isEmpty()) return

        val step = steps[currentStepIndex]
        binding.tvStepLabel.text = "Paso ${currentStepIndex + 1} de ${steps.size}"
        binding.tvStepDescription.text = step.descripcion
        binding.tvStepTime.text = if (step.tiempoSegundos != null) "${step.tiempoSegundos / 60} min" else "Sin tiempo"
        
        binding.btnPrevious.isEnabled = currentStepIndex > 0
        binding.btnNext.text = if (currentStepIndex == steps.size - 1) "¡Terminar!" else "Siguiente"

        binding.progressContainer.removeAllViews()
        for (i in steps.indices) {
            val v = View(this)
            val p = LinearLayout.LayoutParams(0, 15, 1f)
            p.setMargins(6, 0, 6, 0)
            v.layoutParams = p
            v.setBackgroundColor(if (i <= currentStepIndex) getColor(R.color.orange_primary) else getColor(R.color.white_translucent))
            binding.progressContainer.addView(v)
        }
    }

    private fun updateTimerText() {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvTimer.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    inner class IngredientCheckAdapter(private val list: List<IngredienteReceta>) : RecyclerView.Adapter<IngredientCheckAdapter.ViewHolder>() {
        
        // Mapa para persistir el estado de los checkboxes mientras la actividad esté viva
        private val checkedStates = mutableMapOf<Int, Boolean>()

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
            LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_multiple_choice, p, false)
        )

        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            val textView = h.itemView.findViewById<android.widget.CheckedTextView>(android.R.id.text1)
            
            textView.apply {
                text = "${item.nombre} (${item.cantidad ?: ""} ${item.unidad ?: ""})"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                
                // Restaurar estado
                isChecked = checkedStates[pos] ?: false
                
                // Configurar el click para alternar el checkbox
                setOnClickListener {
                    val newState = !isChecked
                    isChecked = newState
                    checkedStates[pos] = newState
                    
                    // Feedback visual opcional
                    if (newState) {
                        alpha = 0.5f
                        paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        alpha = 1.0f
                        paintFlags = paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                }

                // Aplicar estilo según el estado cargado
                if (isChecked) {
                    alpha = 0.5f
                    paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    alpha = 1.0f
                    paintFlags = paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
        }

        override fun getItemCount() = list.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
