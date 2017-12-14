package fhc.tfsandbox.capsnettweak.capsule_tweak

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.MenuItem
import android.view.View
import fhc.tfsandbox.capsnettweak.R
import fhc.tfsandbox.capsnettweak.common.feed
import fhc.tfsandbox.capsnettweak.common.runAndFetch
import fhc.tfsandbox.capsnettweak.models.PredictionRow
import fhc.tfsandbox.capsnettweak.models.ShapeDimensions
import kotlinx.android.synthetic.main.activity_tweak.*
import kotlinx.coroutines.experimental.launch
import org.tensorflow.contrib.android.TensorFlowInferenceInterface

class TweakActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        const val EXTRA_PREDICTION_ROW = "EXTRA_PREDICTION_ROW"
        fun newIntent(context: Context, predictionRow: PredictionRow): Intent {
            val intent = Intent(context, TweakActivity::class.java)
            intent.putExtra(EXTRA_PREDICTION_ROW, predictionRow)
            return intent
        }
    }

    private lateinit var predictionRow: PredictionRow
    private lateinit var tfInference: TensorFlowInferenceInterface

    private lateinit var adapter: CapsuleParamAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tweak)
        title = "Reconstruction"
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // get extra
        predictionRow = if (savedInstanceState == null) {
            intent.getParcelableExtra(EXTRA_PREDICTION_ROW)!!
        } else {
            savedInstanceState.getParcelable(EXTRA_PREDICTION_ROW)!!
        }

        // get tf inference
        tfInference = TensorFlowInferenceInterface(assets, "model_graph.pb")

        // pull out the 1 capsule that has data
        val focusedCapsule = predictionRow.capsules[predictionRow.realDigit]
        adapter = CapsuleParamAdapter(focusedCapsule)
        tweak_rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        tweak_rv.adapter = adapter

        with(reconstruct_button) {
            isEnabled = false
            setOnClickListener(this@TweakActivity)
        }
        launch { runInference(predictionRow) }
    }

    suspend private fun runInference(inferencePredictionRow: PredictionRow) {
        tfInference.feed("input:0", inferencePredictionRow, ShapeDimensions(intArrayOf(1, 1, 10, 16, 1)))
        val floatOutputs = FloatArray(784).toTypedArray().toFloatArray()
        tfInference.runAndFetch("output", floatOutputs)

        runOnUiThread {
            image_view.setArray(floatOutputs)
            reconstruct_button.isEnabled = true
        }
    }

    override fun onClick(v: View?) {
        reconstruct_button.isEnabled = false
        val updatedCapsule = adapter.getUpdatedCapsule()

        val updatedPredictionRow = ArrayList(predictionRow.capsules)
        updatedPredictionRow[predictionRow.realDigit] = updatedCapsule
        launch { runInference(PredictionRow(updatedPredictionRow)) }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.let {
            if (it.itemId == android.R.id.home) {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }
}
