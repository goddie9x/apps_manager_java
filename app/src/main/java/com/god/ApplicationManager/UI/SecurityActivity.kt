package com.god.ApplicationManager.UI

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.god.ApplicationManager.R
import com.god.ApplicationManager.databinding.ActivitySecurityBinding

/**
 * Security center: a dedicated menu for finding strange / junk / malicious
 * apps. Four tabs: scan results, sensitive-permission index, intel sources,
 * and hand-set flags.
 */
class SecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.pager.adapter = SecurityPagerAdapter(this)
        // Keep the sources tab alive so its refresh() can be called after edits.
        binding.pager.offscreenPageLimit = 3
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.security_tab_scan
                    1 -> R.string.security_tab_permissions
                    2 -> R.string.security_tab_sources
                    else -> R.string.security_tab_flags
                },
            )
        }.attach()
    }

    private class SecurityPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> SecurityScanFragment()
            1 -> SecurityPermissionsFragment()
            2 -> SecuritySourcesFragment()
            else -> SecurityFlagsFragment()
        }
    }
}
