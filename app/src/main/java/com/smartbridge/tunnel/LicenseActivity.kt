package com.smartbridge.tunnel

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * LicenseActivity — 激活码输入界面
 *
 * 显示: 机器码、试用状态、激活码输入框
 */
class LicenseActivity : AppCompatActivity() {

    private lateinit var machineIdText: TextView
    private lateinit var trialStatusText: TextView
    private lateinit var licenseCodeInput: EditText
    private lateinit var btnActivate: Button
    private lateinit var btnTrial: Button
    private lateinit var btnCopyMachineId: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        machineIdText = findViewById(R.id.machineIdText)
        trialStatusText = findViewById(R.id.trialStatusText)
        licenseCodeInput = findViewById(R.id.licenseCodeInput)
        btnActivate = findViewById(R.id.btnActivate)
        btnTrial = findViewById(R.id.btnTrial)
        btnCopyMachineId = findViewById(R.id.btnCopyMachineId)

        // 显示机器码
        val machineId = LicenseManager.getMachineId(this)
        machineIdText.text = "机器码: $machineId"

        // 显示试用状态
        updateTrialStatus()

        // 复制机器码
        btnCopyMachineId.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("machine_id", machineId))
            Toast.makeText(this, "机器码已复制", Toast.LENGTH_SHORT).show()
        }

        // 激活码输入格式化 (自动加 -)
        licenseCodeInput.addTextChangedListener(object : TextWatcher {
            private var formatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (formatting || s == null) return
                formatting = true
                val raw = s.toString().replace("-", "").uppercase()
                val formatted = raw.chunked(4).joinToString("-")
                if (formatted != s.toString()) {
                    s.replace(0, s.length, formatted)
                }
                formatting = false
                // 自动激活（输入完整后）
                btnActivate.isEnabled = raw.length == 16
            }
        })

        // 激活
        btnActivate.setOnClickListener {
            val code = licenseCodeInput.text.toString().trim()
            if (code.replace("-", "").length != 16) {
                Toast.makeText(this, "请输入完整的16位激活码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (LicenseManager.activate(this, code)) {
                Toast.makeText(this, "✅ 激活成功！感谢购买", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } else {
                Toast.makeText(this, "❌ 激活码无效，请检查后重试", Toast.LENGTH_LONG).show()
                licenseCodeInput.setError("激活码无效")
            }
        }

        // 继续试用
        btnTrial.setOnClickListener {
            if (TrialManager.isTrialValid(this)) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } else {
                Toast.makeText(this, "试用期已结束，请购买激活码", Toast.LENGTH_LONG).show()
            }
        }

        // 尝试从预置文件自动激活（assets/pre_license.txt 格式: CODE|MACHINE_ID）
        tryAutoActivate(machineId)

        // 如果已激活，直接跳转
        if (LicenseManager.isActivated(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        // 如果试用过期，隐藏试用按钮
        if (TrialManager.isTrialExpired(this)) {
            btnTrial.visibility = View.GONE
            btnTrial.isEnabled = false
        }
    }

    /**
     * 从 assets/pre_license.txt 自动激活
     * 文件格式: CODE|MACHINE_ID （一行一条，支持通配 * 匹配所有设备）
     */
    private fun tryAutoActivate(machineId: String) {
        if (LicenseManager.isActivated(this)) return
        try {
            val content = assets.open("pre_license.txt").bufferedReader().readText()
            for (line in content.lines()) {
                val parts = line.trim().split("|")
                if (parts.size != 2) continue
                val code = parts[0]
                val mid = parts[1]
                if (mid == "*" || mid == machineId) {
                    if (LicenseManager.activate(this, code)) {
                        Toast.makeText(this, "✅ 已自动激活", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
        } catch (_: Exception) {
            // 无预置文件，正常流程
        }
    }

    private fun updateTrialStatus() {
        if (TrialManager.isTrialValid(this)) {
            val remaining = TrialManager.getRemainingHuman(this)
            trialStatusText.text = "试用期剩余: $remaining"
            trialStatusText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
        } else {
            trialStatusText.text = "⚠️ 试用期已结束 (24小时)"
            trialStatusText.setTextColor(android.graphics.Color.parseColor("#EA4335"))
        }
    }
}
