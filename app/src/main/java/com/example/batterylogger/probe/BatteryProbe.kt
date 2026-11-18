package com.example.batterylogger.probe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * Снимок батареи. ВСЕ значения пишутся сырыми, без фильтрации и подстановок.
 *
 * - Sticky-интент ACTION_BATTERY_CHANGED получаем через registerReceiver(null, ...):
 *   приёмник не регистрируется, callback'ов нет, расход нулевой.
 *   Вызываем на applicationContext: из контекста BroadcastReceiver обычный
 *   registerReceiver запрещён, а applicationContext — безопасный путь.
 * - Если extra в интенте нет, пишем "absent" (а не значение по умолчанию getIntExtra,
 *   которое было бы подстановкой).
 * - getIntProperty при targetSdk >= 28 для неподдерживаемого свойства возвращает
 *   Integer.MIN_VALUE; getLongProperty — Long.MIN_VALUE. Пишем как есть.
 *   Единицы по документации: CHARGE_COUNTER — µAh, CURRENT_* — µA, ENERGY_COUNTER — nWh.
 *   Знак тока: + заряд, − разряд (по документации; у OEM бывает иначе — калибруйте).
 */
object BatteryProbe {

    fun read(ctx: Context, row: MutableList<Pair<String, String>>) {
        val app = ctx.applicationContext
        val i: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        fun extra(key: String): String = when {
            i == null -> "no_intent"
            !i.hasExtra(key) -> "absent"
            else -> i.getIntExtra(key, 0).toString()
        }

        row += "level" to extra(BatteryManager.EXTRA_LEVEL)
        row += "scale" to extra(BatteryManager.EXTRA_SCALE)
        row += "status" to extra(BatteryManager.EXTRA_STATUS)
        row += "plugged" to extra(BatteryManager.EXTRA_PLUGGED)
        row += "health" to extra(BatteryManager.EXTRA_HEALTH)
        row += "temperature_dC" to extra(BatteryManager.EXTRA_TEMPERATURE) // десятые доли °C
        row += "voltage_mV" to extra(BatteryManager.EXTRA_VOLTAGE)

        val bm = app.getSystemService(BatteryManager::class.java)
        row += "bm_capacity" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
        row += "bm_charge_counter_uAh" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toString()
        row += "bm_current_now_uA" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toString()
        row += "bm_current_avg_uA" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE).toString()
        row += "bm_energy_counter_nWh" to bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).toString()
        // Доп. колонки (дёшево): статус из другого источника и isCharging — для сверки с EXTRA_STATUS.
        row += "bm_status" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS).toString()
        row += "bm_is_charging" to bm.isCharging.toString()
    }

    /** Поля, которые нужны один раз — в паспорт. */
    fun passportFields(ctx: Context): List<Pair<String, String>> {
        val i = ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val out = mutableListOf<Pair<String, String>>()
        out += "battery_technology" to (i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "absent")
        // EXTRA_CYCLE_COUNT добавлен в API 34 — перепроверьте по reference, если сборка ругается.
        out += "battery_cycle_count" to if (Build.VERSION.SDK_INT >= 34 && i != null) {
            if (i.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT))
                i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, 0).toString() else "absent"
        } else "NA"
        return out
    }
}
