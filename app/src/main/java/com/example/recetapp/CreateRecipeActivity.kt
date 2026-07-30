package com.example.recetapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.ActivityCreateRecipeBinding
import com.example.recetapp.databinding.ItemIngredientBinding
import com.example.recetapp.databinding.ItemRecipeStepBinding
import com.example.recetapp.databinding.StepIngredientsBinding
import com.example.recetapp.databinding.StepInfoBinding
import com.example.recetapp.databinding.StepRecipeStepsBinding
import com.example.recetapp.databinding.StepVisibilityBinding

class CreateRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private var currentStep = 0
    private val steps = listOf("Información", "Ingredientes", "Pasos", "Imagen y visibilidad")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            } else {
                finish()
            }
        }

        binding.btnContinue.setOnClickListener {
            if (currentStep < 3) {
                currentStep++
                renderStep()
            } else {
                // Logic to save all data to Supabase
                finish()
            }
        }

        renderStep()
    }

    private fun renderStep() {
        binding.fragmentContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        when (currentStep) {
            0 -> renderStepInfo(inflater)
            1 -> renderStepIngredients(inflater)
            2 -> renderStepRecipeSteps(inflater)
            3 -> renderStepVisibility(inflater)
        }

        binding.tvStepInfo.text = "Paso ${currentStep + 1} de 4: ${steps[currentStep]}"
        
        val activeColor = getColor(R.color.orange_primary)
        val inactiveColor = getColor(R.color.input_background)
        binding.progress1.setBackgroundColor(if (currentStep >= 0) activeColor else inactiveColor)
        binding.progress2.setBackgroundColor(if (currentStep >= 1) activeColor else inactiveColor)
        binding.progress3.setBackgroundColor(if (currentStep >= 2) activeColor else inactiveColor)
        binding.progress4.setBackgroundColor(if (currentStep >= 3) activeColor else inactiveColor)

        binding.btnContinue.text = if (currentStep == 3) "Publicar receta" else "Continuar"
    }

    private fun renderStepInfo(inflater: LayoutInflater) {
        StepInfoBinding.inflate(inflater, binding.fragmentContainer, true)
    }

    private fun renderStepIngredients(inflater: LayoutInflater) {
        val stepBinding = StepIngredientsBinding.inflate(inflater, binding.fragmentContainer, true)
        stepBinding.rvIngredients.adapter = IngredientEditAdapter(mutableListOf(IngredienteReceta(recetaId = "", nombre = "")))
        stepBinding.btnAddIngredient.setOnClickListener {
            (stepBinding.rvIngredients.adapter as IngredientEditAdapter).addEmpty()
        }
    }

    private fun renderStepRecipeSteps(inflater: LayoutInflater) {
        val stepBinding = StepRecipeStepsBinding.inflate(inflater, binding.fragmentContainer, true)
        stepBinding.rvSteps.adapter = StepEditAdapter(mutableListOf(PasoReceta(recetaId = "", orden = 1, descripcion = "")))
        stepBinding.btnAddStep.setOnClickListener {
            (stepBinding.rvSteps.adapter as StepEditAdapter).addEmpty()
        }
    }

    private fun renderStepVisibility(inflater: LayoutInflater) {
        StepVisibilityBinding.inflate(inflater, binding.fragmentContainer, true)
    }

    inner class IngredientEditAdapter(private val list: MutableList<IngredienteReceta>) : RecyclerView.Adapter<IngredientEditAdapter.ViewHolder>() {
        fun addEmpty() {
            list.add(IngredienteReceta(recetaId = "", nombre = ""))
            notifyItemInserted(list.size - 1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.binding.etName.setText(item.nombre)
            holder.binding.etQty.setText(item.cantidad?.toString() ?: "")
            val units = listOf("g", "ml", "kg", "l", "u", "cdta", "cda")
            val adapter = ArrayAdapter(holder.binding.root.context, android.R.layout.simple_dropdown_item_1line, units)
            holder.binding.actvUnit.setAdapter(adapter)
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class StepEditAdapter(private val list: MutableList<PasoReceta>) : RecyclerView.Adapter<StepEditAdapter.ViewHolder>() {
        fun addEmpty() {
            list.add(PasoReceta(recetaId = "", orden = list.size + 1, descripcion = ""))
            notifyItemInserted(list.size - 1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemRecipeStepBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.binding.tvStepNumber.text = item.orden.toString()
            holder.binding.etStepText.setText(item.descripcion)
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemRecipeStepBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
