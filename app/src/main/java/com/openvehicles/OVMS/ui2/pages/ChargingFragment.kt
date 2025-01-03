package com.openvehicles.OVMS.ui2.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.RangeSlider
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.api.OnResultCommandListener
import com.openvehicles.OVMS.entities.CarData
import com.openvehicles.OVMS.ui.BaseFragment
import com.openvehicles.OVMS.utils.AppPrefs
import com.openvehicles.OVMS.utils.CarsStorage
import kotlin.math.max


class ChargingFragment : BaseFragment(), OnResultCommandListener {

    private var carData: CarData? = null
    private lateinit var appPrefs: AppPrefs
    private lateinit var commandProgress: LinearProgressIndicator

    private var chargeSuffRange = 0
    private var chargeSuffSOC = 0
    private var chargeLimitAction = -1
    private var chargeRangeDrop = -1
    private var chargeSocDrop = -1


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_charging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        appPrefs = AppPrefs(requireContext(), "ovms")
        carData = CarsStorage.getSelectedCarData()

        commandProgress = findViewById(R.id.saveProgressBar) as LinearProgressIndicator

        initialiseBatteryStats(carData)
        initialiseBatteryControls(carData)

        if (carData?.car_type == "RT" || carData?.car_type == "VWUP" || carData?.car_type == "NL") {
            // Request info about limits
            sendCommand("203", this)
        }
    }


    private fun initialiseBatteryStats(carData: CarData?) {
        val progressBar = findViewById(R.id.chargingProgressBar) as LinearProgressIndicator

        progressBar.visibility = View.INVISIBLE

        // Charging time

        val chargingTimes: TextView = findViewById(R.id.chargingTimes) as TextView

        val etrFull = carData?.car_chargefull_minsremaining ?: 0
        val suffSOC = carData?.car_chargelimit_soclimit ?: 0
        val etrSuffSOC = carData?.car_chargelimit_minsremaining_soc ?: 0
        val suffRange = carData?.car_chargelimit_rangelimit_raw ?: 0
        val etrSuffRange = carData?.car_chargelimit_minsremaining_range ?: 0

        var chargingNote = emptyList<String>()
        if (appPrefs.appUIPrefs.getBoolean("charging_always_show_time_est", true) || carData?.car_charging == true) {
            if (suffSOC > 0 && etrSuffSOC > 0) {
                chargingNote += String.format("~%s: %d%%", String.format("%02d:%02d", etrSuffSOC / 60, etrSuffSOC % 60), suffSOC)
            }
            if (suffRange > 0 && etrSuffRange > 0) {
                chargingNote += String.format("~%s: %d%%", String.format("%02d:%02d", etrSuffRange / 60, etrSuffRange % 60), suffRange)
            }
            if (etrFull > 0) {
                chargingNote += String.format("~%s: 100%%", String.format("%02d:%02d", etrFull / 60, etrFull % 60))
            }

        }
        chargingTimes.text = chargingNote.joinToString(separator = "\n")
        chargingTimes.visibility = if (chargingNote.isNotEmpty()) View.VISIBLE else View.GONE


        if (carData?.car_charging == true) {
            progressBar.visibility = View.VISIBLE
            progressBar.hide()
            progressBar.indicatorDirection =
                LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_DISJOINT
            context?.resources?.getColor(R.color.chargeOngoingColor)
                ?.let { progressBar.setIndicatorColor(it) }
            progressBar.isIndeterminate = true
            progressBar.show()
        }

        if (carData?.car_charge_state_i_raw == 4) {
            progressBar.visibility = View.VISIBLE
            progressBar.hide()
            progressBar.isIndeterminate = false
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_DISJOINT
            context?.resources?.getColor(R.color.chargeOngoingColor)
                ?.let { progressBar.setIndicatorColor(it) }
            progressBar.progress = 100
            progressBar.show()

        }

        if (carData?.car_charge_timer == true) {
            progressBar.visibility = View.VISIBLE
            progressBar.hide()
            progressBar.isIndeterminate = false
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_DISJOINT
            context?.resources?.getColor(R.color.chargePendingColor)
                ?.let { progressBar.setIndicatorColor(it) }
            progressBar.progress = 100
            progressBar.show()
        }

        if (carData?.car_charge_state == "powerwait") {
            progressBar.visibility = View.VISIBLE
            progressBar.hide()
            context?.resources?.getColor(R.color.chargeErrorColor)
                ?.let { c1 -> progressBar.setIndicatorColor(c1, Color.RED, c1) }
            progressBar.indicatorDirection =
                LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_CONTIGUOUS
            progressBar.isIndeterminate = true
            progressBar.show()
        }

        if (carData?.car_charge_state_i_raw == 0x101) {
            progressBar.visibility = View.VISIBLE
            progressBar.hide()
            // Starting
            context?.resources?.getColor(R.color.chargePendingColor)
                ?.let { c1 ->
                    context?.resources?.getColor(R.color.chargePendingColor2)
                        ?.let { c2 -> progressBar.setIndicatorColor(c1, c2, c1) }
                }
            progressBar.indicatorDirection =
                LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_CONTIGUOUS
            progressBar.isIndeterminate = true
            progressBar.show()
        }

        if (carData?.car_charge_state_i_raw == 0x115) {
            progressBar.visibility = View.VISIBLE
            // Stopping
            progressBar.hide()
            progressBar.isIndeterminate = true
            context?.resources?.getColor(R.color.chargeOtherColor)
                ?.let { c1 ->
                    context?.resources?.getColor(R.color.chargeOtherColor2)
                        ?.let { c2 -> progressBar.setIndicatorColor(c1, c2, c1) }
                }
            progressBar.indicatorDirection =
                LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT
            progressBar.indeterminateAnimationType =
                LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_CONTIGUOUS
            progressBar.show()
        }

        // SOC icon and label
        val socText: TextView = findViewById(R.id.battSoc) as TextView
        val socBattIcon = findViewById(R.id.battIndicatorImg) as ImageView

        var socBattLayers = emptyList<Drawable>()

        socBattLayers += ContextCompat.getDrawable(requireContext(), R.drawable.ic_batt_l0)!!

        val limitIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_chargelimit)!!.toBitmap()
        val socLimit = carData?.car_chargelimit_soclimit ?: 0
        val limitIconWidth = limitIcon.height.minus(22).times(((socLimit / 100.0))).plus(7.5)
        if (limitIconWidth > 0) {
            val matrix = Matrix()
            matrix.postRotate(180f)
            val mBitmap =
                Bitmap.createBitmap(limitIcon, 0, 0, limitIcon.width, limitIconWidth.toInt(), matrix, true)
            val layer1Drawable = BitmapDrawable(resources, mBitmap)
            layer1Drawable.gravity = Gravity.BOTTOM

            socBattLayers += layer1Drawable
        }

        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_batt_l1)!!.toBitmap()

        val soc = carData?.car_soc_raw ?: 0f
        val iconWidth = icon.height.minus(22).times(((soc / 100.0))).plus(7.5)
        if (iconWidth > 0) {
            val matrix = Matrix()
            matrix.postRotate(180f)
            val mBitmap =
                Bitmap.createBitmap(icon, 0, 0, icon.width, iconWidth.toInt(), matrix, true)
            val layer1Drawable = BitmapDrawable(resources, mBitmap)
            layer1Drawable.gravity = Gravity.BOTTOM

            if (carData?.car_charging == true) {
                layer1Drawable.setTint(
                    context?.resources?.getColor(R.color.colorAccent) ?: Color.GREEN
                )
            } else if (soc <= 10) {
                layer1Drawable.setTint(Color.RED)
            } else if (soc <= 20) {
                layer1Drawable.setTint(Color.YELLOW)
            } else {
                layer1Drawable.setTint(Color.WHITE)
            }
            socBattLayers += layer1Drawable
        }

        val socBattLayer = LayerDrawable(socBattLayers.toTypedArray())
        socBattIcon.setImageDrawable(socBattLayer)
        socText.text = carData?.car_soc
        var showSoc = true
        socText.setOnClickListener {
            showSoc = !showSoc
            socText.text = if (!showSoc) carData?.car_range_estimated else carData?.car_soc
        }

        // Battery temp

        val batteryTemp = findViewById(R.id.battTemp) as TextView
        batteryTemp.text = carData?.car_temp_battery

        // Battery charging Volt, Amp and kW

        val battVolt = findViewById(R.id.battVolt) as TextView
        val battAmp = findViewById(R.id.battAmp) as TextView
        val battkW = findViewById(R.id.battkW) as TextView
        battVolt.text = carData?.car_charge_linevoltage
        battAmp.text = carData?.car_charge_current

        var chargingPower = 0.0
        if ((carData?.car_charge_power_input_kw_raw ?: 0f) > 0) {
            chargingPower = carData!!.car_charge_power_input_kw_raw.toDouble()
        }
        if ((carData?.car_charge_power_kw_raw ?: 0.0) > 0) {
            chargingPower = carData!!.car_charge_power_kw_raw
        }

        if (carData?.car_charge_linevoltage_raw != null) {
            // Divide by -1000, because current is negative when charging
            chargingPower =
                (carData.car_charge_linevoltage_raw.toDouble() * carData.car_charge_current_raw.toDouble()) / -1000.0
        }

        battkW.text = String.format("%2.2f kW", chargingPower)


        // Charging status

        val statusText = findViewById(R.id.chargingState) as TextView

        statusText.text = ""

        if (carData?.car_charge_state_i_raw == 4 || carData?.car_charging == true || carData?.car_charge_timer == true) {
            statusText.text = String.format("%2.2f kWh charged", carData.car_charge_kwhconsumed)
        }

        if (carData?.car_charging == true) {
            statusText.text = carData.car_charge_mode

            val etrFull = carData.car_chargefull_minsremaining
            val suffSOC = carData.car_chargelimit_soclimit
            val etrSuffSOC = carData.car_chargelimit_minsremaining_soc
            val suffRange = carData.car_chargelimit_rangelimit_raw
            val etrSuffRange = carData.car_chargelimit_minsremaining_range

            if (suffSOC > 0 && etrSuffSOC > 0) {
                statusText.text = String.format(
                    getString(R.string.charging_estimation_soc),
                    String.format("%02d:%02d", etrSuffSOC / 60, etrSuffSOC % 60)
                )
            } else if (suffRange > 0 && etrSuffRange > 0) {
                statusText.text = String.format(
                    getString(R.string.charging_estimation_range),
                    String.format("%02d:%02d", etrSuffRange / 60, etrSuffRange % 60)
                )
            } else if (etrFull > 0 && etrSuffRange > 0) {
                statusText.text = String.format(
                    getString(R.string.charging_estimation_full),
                    String.format("%02d:%02d", etrFull / 60, etrFull % 60)
                )
            }

            var chargeStateInfo = 0

            when (carData.car_charge_state_i_raw) {
                2 -> chargeStateInfo = R.string.state_topping_off_label
                4 -> chargeStateInfo = R.string.state_done_label
                14 -> chargeStateInfo = R.string.timedcharge
                21 -> chargeStateInfo = R.string.state_stopped_label
            }

            if (chargeStateInfo != 0) {
                statusText.setText(chargeStateInfo)
            }

            if (carData.car_charge_state == "powerwait") {
                statusText.setText(R.string.nopower)
            }

        }
    }

    private fun initialiseBatteryControls(carData: CarData?) {
        // Action buttons
        val action1 = findViewById(R.id.startCharging) as Button
        val action2 = findViewById(R.id.stopCharging) as Button

        action1.isEnabled = carData?.car_charging == false && carData.car_charge_state_i_raw != 0x101 && carData.car_charge_state_i_raw != 0x115
        action2.isEnabled = carData?.car_charging == true && carData.car_charge_state_i_raw != 0x101 && carData.car_charge_state_i_raw != 0x115

        action1.setOnClickListener {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.lb_charger_confirm_start)
                .setNegativeButton(R.string.Cancel) {_, _ ->}
                .setPositiveButton(android.R.string.ok) { dlg, which -> startCharge() }
                .show()
        }

        action2.setOnClickListener {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.lb_charger_confirm_stop)
                .setNegativeButton(R.string.Cancel) {_, _ ->}
                .setPositiveButton(android.R.string.ok) { dlg, which -> stopCharge() }
                .show()
        }

        // Amp limit

        val ampLimitSlider = findViewById(R.id.ampSeekbar) as RangeSlider
        val ampLimit = findViewById(R.id.ampLimit2) as TextView


        ampLimit.text = carData?.car_charge_currentlimit
        ampLimitSlider.setValues(carData?.car_charge_currentlimit_raw)
        ampLimitSlider.isEnabled = (carData?.car_chargeport_open != false || carData.car_charge_substate_i_raw != 0x07)


        if (ampLimitSlider.values.first() < 1.0f)
            ampLimitSlider.values = listOf(1.0f)
        ampLimitSlider.valueFrom = 1.0f

        val touchListener: RangeSlider.OnSliderTouchListener = object :
            RangeSlider.OnSliderTouchListener {


            override fun onStartTrackingTouch(slider: RangeSlider) {
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.lb_charger_confirm_amp_change)
                    .setNegativeButton(R.string.Cancel) {_, _ ->}
                    .setPositiveButton(android.R.string.ok) { dlg, which -> sendCommandWithProgress(
                        String.format("10,%d", ampLimitSlider.values.first().toInt()),
                        this@ChargingFragment
                    );dlg.dismiss()}
                    .show()
            }
        }

        ampLimitSlider.clearOnSliderTouchListeners()
        ampLimitSlider.addOnSliderTouchListener(touchListener)

        ampLimitSlider.clearOnChangeListeners()
        ampLimitSlider.addOnChangeListener { slider, value, fromUser ->
            ampLimit.text = "${value.toInt()}A"
        }

        // Charge mode
        val modeSwitcher = findViewById(R.id.standard_modeswitch) as MaterialButtonToggleGroup
        val chargingInfo = findViewById(R.id.chargeModeNote) as TextView
        modeSwitcher.isEnabled = carData?.car_type != "RT" && carData?.car_type != "VWUP"

        modeSwitcher.clearOnButtonCheckedListeners()
        when (carData?.car_charge_mode_i_raw) {
            0 -> modeSwitcher.check(R.id.standardChargeMode)
            1 -> modeSwitcher.check(R.id.storageChargeMode)
            3 -> modeSwitcher.check(R.id.rangeChargeMode)
            4 -> modeSwitcher.check(R.id.performanceChargeMode)
        }
        when (carData?.car_charge_mode_i_raw) {
            3 -> {chargingInfo.setText(R.string.msg_charger_range);chargingInfo.visibility = View.VISIBLE}
            4 -> {chargingInfo.setText(R.string.msg_charger_perform);chargingInfo.visibility = View.VISIBLE}
            else -> {chargingInfo.visibility = View.GONE}
        }
        modeSwitcher.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked)
                return@addOnButtonCheckedListener
            val checkedMode = when (checkedId) {
                modeSwitcher[0].id -> 0
                modeSwitcher[1].id -> 1
                modeSwitcher[2].id -> 3
                modeSwitcher[3].id -> 4
                else -> -1
            }
            when (checkedMode) {
                3 -> {chargingInfo.setText(R.string.msg_charger_range);chargingInfo.visibility = View.VISIBLE}
                4 -> {chargingInfo.setText(R.string.msg_charger_perform);chargingInfo.visibility = View.VISIBLE}
                else -> {chargingInfo.visibility = View.GONE}
            }
            if (checkedMode != -1 && checkedMode != carData?.car_charge_mode_i_raw) {
                sendCommandWithProgress(String.format("10,%d", checkedMode), this@ChargingFragment)
            }
        }
    }

    private fun initialiseSpecialBatteryControls(carData: CarData?) {
        // Sufficient SOC and range switches
        val socLimit = findViewById(R.id.socLimit) as TextView
        val rangeLimit = findViewById(R.id.rangeLimit) as TextView

        val sufficientSocLimitSwitch = findViewById(R.id.sufficientSocLimitSwitch) as MaterialSwitch
        val sufficientSocSeekbar = findViewById(R.id.seekBar4) as RangeSlider
        sufficientSocLimitSwitch.isEnabled = carData?.car_type == "RT" || carData?.car_type == "VWUP" || carData?.car_type == "NL"
        sufficientSocSeekbar.isEnabled = carData?.car_type == "RT" || carData?.car_type == "VWUP" || carData?.car_type == "NL"
        sufficientSocLimitSwitch.isChecked = chargeSuffSOC > 0
        sufficientSocSeekbar.isEnabled = sufficientSocLimitSwitch.isChecked && sufficientSocLimitSwitch.isEnabled
        sufficientSocSeekbar.values =
            if (chargeSuffSOC > 0) listOf(chargeSuffSOC.toFloat())
            else listOf(1.0f)

        socLimit.text = "${sufficientSocSeekbar.values.first().toInt()}%"

        sufficientSocLimitSwitch.setOnCheckedChangeListener { compoundButton, b ->
            sufficientSocSeekbar.isEnabled = sufficientSocLimitSwitch.isChecked
            if (!sufficientSocLimitSwitch.isChecked) {
                if (carData?.car_type == "VWUP") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d,%d",
                            0, carData?.car_charge_currentlimit_raw?.toInt() ?: 0, carData?.car_charge_mode_i_raw ?: 0
                        ),
                        this@ChargingFragment
                    )
                    return@setOnCheckedChangeListener
                }
                if (carData?.car_type == "NL") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d",
                            chargeSuffRange, 0
                        ),
                        this@ChargingFragment
                    )
                    return@setOnCheckedChangeListener
                }
                sendCommandWithProgress(
                    String.format(
                        "204,%d,%d,%d,%d",
                        chargeSuffRange, carData?.car_charge_currentlimit_raw?.toInt() ?: 0, carData?.car_charge_mode_i_raw ?: 0
                    ),
                    this@ChargingFragment
                )
            }
        }

        val socListener: RangeSlider.OnSliderTouchListener = object :
            RangeSlider.OnSliderTouchListener {


            override fun onStartTrackingTouch(slider: RangeSlider) {
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {
                if (carData?.car_type == "VWUP") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d,%d",
                            sufficientSocSeekbar.values.first().toInt(), carData?.car_charge_currentlimit_raw?.toInt() ?: 0, carData?.car_charge_mode_i_raw ?: 0
                        ),
                        this@ChargingFragment
                    )
                    return
                }
                if (carData?.car_type == "NL") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d",
                            chargeSuffRange, sufficientSocSeekbar.values.first().toInt()
                        ),
                        this@ChargingFragment
                    )
                    return
                }
                sendCommandWithProgress(
                    String.format(
                        "204,%d,%d,%d,%d",
                        chargeSuffRange, sufficientSocSeekbar.values.first().toInt(), carData?.car_charge_currentlimit_raw?.toInt() ?: 0, carData?.car_charge_mode_i_raw ?: 0
                    ),
                    this@ChargingFragment
                )
            }
        }

        sufficientSocSeekbar.clearOnSliderTouchListeners()
        sufficientSocSeekbar.addOnSliderTouchListener(socListener)

        sufficientSocSeekbar.clearOnChangeListeners()
        sufficientSocSeekbar.addOnChangeListener { slider, value, fromUser ->
            socLimit.text = "${value.toInt()}%"
        }

        val sufficientRangeLimitSwitch = findViewById(R.id.sufficientRangeLimitSwitch) as MaterialSwitch
        val sufficientRangeSeekbar = findViewById(R.id.seekBar5) as RangeSlider
        sufficientRangeLimitSwitch.isEnabled = carData?.car_type == "RT" || carData?.car_type == "NL"
        sufficientRangeSeekbar.isEnabled = carData?.car_type == "RT" || carData?.car_type == "NL"

        sufficientRangeLimitSwitch.text = getString(R.string.lb_sufficient_range, carData?.car_distance_units)
        sufficientRangeLimitSwitch.isChecked = chargeSuffRange > 0
        sufficientRangeSeekbar.isEnabled = sufficientRangeLimitSwitch.isChecked && sufficientRangeLimitSwitch.isEnabled
        sufficientRangeSeekbar.values =
            if ((chargeSuffRange) > 0) listOf(chargeSuffRange.toFloat())
            else listOf(1.0f)
        sufficientRangeSeekbar.valueTo = max((carData!!.car_max_idealrange_raw + 25).toDouble(), 50.0).toFloat()
        rangeLimit.text = "${sufficientRangeSeekbar.values.first().toInt()} ${carData?.car_distance_units}"

        sufficientRangeLimitSwitch.setOnCheckedChangeListener { compoundButton, b ->
            sufficientRangeSeekbar.isEnabled = sufficientRangeLimitSwitch.isChecked
            if (!sufficientRangeLimitSwitch.isChecked) {
                if (carData.car_type == "NL") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d",
                            0
                        ),
                        this@ChargingFragment
                    )
                    return@setOnCheckedChangeListener
                }
                sendCommandWithProgress(
                    String.format(
                        "204,%d,%d,%d,%d",
                        0, chargeSuffSOC, carData?.car_charge_currentlimit_raw?.toInt() ?: 0, carData?.car_charge_mode_i_raw
                    ),
                    this@ChargingFragment
                )
            }
        }

        val rangeListener: RangeSlider.OnSliderTouchListener = object :
            RangeSlider.OnSliderTouchListener {


            override fun onStartTrackingTouch(slider: RangeSlider) {
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {
                sendCommandWithProgress(
                    String.format(
                        "204,%d",
                        sufficientRangeSeekbar.values.first().toInt()
                    ),
                    this@ChargingFragment
                )
            }
        }

        sufficientRangeSeekbar.clearOnSliderTouchListeners()
        sufficientRangeSeekbar.addOnSliderTouchListener(rangeListener)

        sufficientRangeSeekbar.clearOnChangeListeners()
        sufficientRangeSeekbar.addOnChangeListener { slider, value, fromUser ->
            rangeLimit.text = "${value.toInt()} ${carData?.car_distance_units}"
        }

        // Charge notification mode
        val limitActionSwitcher = findViewById(R.id.limit_actionswitch) as MaterialButtonToggleGroup
        limitActionSwitcher.isEnabled = (carData?.car_type == "RT" || carData?.car_type == "VWUP" || carData?.car_type == "NL") && chargeLimitAction != -1
        limitActionSwitcher.clearOnButtonCheckedListeners()
        when (chargeLimitAction) {
            0 -> limitActionSwitcher.check(R.id.cl_action_notify)
            1 -> limitActionSwitcher.check(R.id.cl_action_stop)
        }
        limitActionSwitcher.clearOnButtonCheckedListeners()
        limitActionSwitcher.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked)
                return@addOnButtonCheckedListener
            var checkedMode = -1
            when (checkedId) {
                R.id.cl_action_notify -> checkedMode = 0
                R.id.cl_action_stop -> checkedMode = 1
            }
            if (checkedMode != -1) {
                if (carData.car_type == "VWUP") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d,%d",
                            chargeSuffSOC, carData?.car_charge_currentlimit_raw?.toInt() ?: 0, checkedMode
                        ),
                        this@ChargingFragment
                    )
                    return@addOnButtonCheckedListener
                }
                if (carData.car_type == "NL") {
                    sendCommandWithProgress(
                        String.format(
                            "204,%d,%d,%d",
                            chargeSuffRange, chargeSuffSOC, checkedMode
                        ),
                        this@ChargingFragment
                    )
                    return@addOnButtonCheckedListener
                }
                sendCommandWithProgress(
                    String.format(
                        "204,%d,%d,%d,%d",
                        chargeSuffRange, chargeSuffSOC, carData?.car_charge_currentlimit_raw?.toInt() ?: 0, checkedMode
                    ),
                    this@ChargingFragment
                )
            }
        }
    }

    private fun startCharge() {
        sendCommand("11", this)
        carData!!.car_charge_linevoltage_raw = 0f
        carData!!.car_charge_current_raw = 0f
        carData!!.car_charge_state_s_raw = "starting"
        carData!!.car_charge_state_i_raw = 0x101
        update(carData)
    }

    private fun stopCharge() {
        sendCommand("12", this)
        carData!!.car_charge_linevoltage_raw = 0f
        carData!!.car_charge_current_raw = 0f
        carData!!.car_charge_state_s_raw = "stopping"
        carData!!.car_charge_state_i_raw = 0x115
        update(carData)
    }

    override fun update(carData: CarData?) {
        this.carData = carData
        initialiseBatteryStats(carData)
        initialiseBatteryControls(carData)
    }

    override fun onResultCommand(result: Array<String>) {
        if (result.size <= 1) return
        commandProgress.visibility = View.INVISIBLE
        val resCode = result[1].toInt()
        val resText = if (result.size > 2) result[2] else ""
        val cmdMessage = getSentCommandMessage(result[0])
        val context: Context? = activity
        if (context != null) {
            when (resCode) {
                0 -> {
                    if (result.size > 4) {
                        // If command returns parameters for adjustment, enable these controls.
                        when (carData?.car_type) {
                            "NL" -> {
                                chargeSuffRange = result[2].toIntOrNull() ?: -1
                                chargeSuffSOC = result[3].toIntOrNull() ?: -1
                                chargeLimitAction = result[4].toIntOrNull() ?: -1
                                chargeRangeDrop = result[5].toIntOrNull() ?: -1
                                chargeSocDrop = result[6].toIntOrNull() ?: -1
                                initialiseSpecialBatteryControls(carData)
                            }
                            "VWUP" -> {
                                chargeSuffSOC = result[2].toIntOrNull() ?: -1
                                chargeLimitAction = result.lastOrNull()?.toIntOrNull() ?: -1
                                initialiseSpecialBatteryControls(carData)
                            }
                            "RT" -> {
                                chargeSuffRange = result[2].toIntOrNull() ?: -1
                                chargeSuffSOC = result[3].toIntOrNull() ?: -1
                                chargeLimitAction = result.lastOrNull()?.toIntOrNull() ?: -1
                                initialiseSpecialBatteryControls(carData)
                            }
                        }
                    }
                }
                1 -> Toast.makeText(
                    context, cmdMessage + " " + getString(R.string.err_failed, resText),
                    Toast.LENGTH_SHORT
                ).show()

                2 -> Toast.makeText(
                    context, cmdMessage + " " + getString(R.string.err_unsupported_operation),
                    Toast.LENGTH_SHORT
                ).show()
                3 -> Toast.makeText(
                    context, cmdMessage + " " + getString(R.string.err_unimplemented_operation),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        cancelCommand()
    }

    fun sendCommandWithProgress(command: String?,
                                        onResultCommandListener: OnResultCommandListener?) {
        sendCommand(command, onResultCommandListener)
        commandProgress.visibility = View.VISIBLE
    }

}