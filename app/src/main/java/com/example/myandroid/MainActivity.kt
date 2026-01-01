package com.example.myandroid

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Root Layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 2. Tab Layout & ViewPager
        val tabLayout = TabLayout(this)
        val viewPager = ViewPager2(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        root.addView(tabLayout)
        root.addView(viewPager)
        setContentView(root)

        // 3. Set Adapter
        viewPager.adapter = InfoPagerAdapter(this)

        // 4. Link Tabs
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "📱 Device"
                1 -> "🤖 System"
                2 -> "🖥️ Display"
                else -> "Info"
            }
        }.attach()
    }

    // --- ADAPTER ---
    class InfoPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment = InfoFragment.newInstance(position)
    }

    // --- FRAGMENT ---
    class InfoFragment : Fragment() {
        companion object {
            fun newInstance(pos: Int) = InfoFragment().apply {
                arguments = Bundle().apply { putInt("pos", pos) }
            }
        }

        override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val context = requireContext()
            val pos = arguments?.getInt("pos") ?: 0
            
            val scroll = ScrollView(context).apply {
                isFillViewport = true
                setBackgroundColor(0xFFF5F5F5.toInt()) // Light gray bg
            }
            
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            val data = when (pos) {
                0 -> getHardwareInfo()
                1 -> getSystemInfo()
                2 -> getDisplayInfo()
                else -> emptyMap()
            }

            data.forEach { (k, v) ->
                content.addView(createCard(context, k, v))
            }

            scroll.addView(content)
            return scroll
        }

        private fun createCard(ctx: android.content.Context, label: String, value: String): View {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 30)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    color = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                    cornerRadius = 24f
                }
                elevation = 10f

                addView(TextView(ctx).apply {
                    text = label
                    textSize = 14f
                    setTextColor(0xFF666666.toInt())
                })
                addView(TextView(ctx).apply {
                    text = value
                    textSize = 20f
                    setTextColor(android.graphics.Color.BLACK)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 10, 0, 0)
                })
            }
        }

        private fun getHardwareInfo(): Map<String, String> = mapOf(
            "Manufacturer" to Build.MANUFACTURER.uppercase(),
            "Model" to Build.MODEL,
            "Brand" to Build.BRAND,
            "Device" to Build.DEVICE,
            "Board" to Build.BOARD,
            "Hardware" to Build.HARDWARE
        )

        private fun getSystemInfo(): Map<String, String> = mapOf(
            "Android Version" to Build.VERSION.RELEASE,
            "SDK Level" to Build.VERSION.SDK_INT.toString(),
            "Security Patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Unknown"),
            "Bootloader" to Build.BOOTLOADER,
            "Build ID" to Build.ID
        )

        private fun getDisplayInfo(): Map<String, String> {
            val dm = resources.displayMetrics
            return mapOf(
                "Resolution" to "${dm.widthPixels} x ${dm.heightPixels}",
                "Density (DPI)" to "${dm.densityDpi}",
                "Scale Factor" to "${dm.density}x",
                "Font Scale" to "${dm.scaledDensity}"
            )
        }
    }
}