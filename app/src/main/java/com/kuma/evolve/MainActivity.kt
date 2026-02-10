package com.kuma.evolve

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.navigation.NavigationView
import com.kuma.evolve.auth.AuthManager
import coil.load
import coil.transform.CircleCropTransformation

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = AuthManager(this)
        
        // Apply saved theme preference
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("is_dark_mode", true)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        // Check if action bar is already supplied by the window decor
        // If the theme is .NoActionBar, we should set the support action bar.
        // The error suggests that even with .NoActionBar, something is providing one.
        // However, the error usually happens when setSupportActionBar is called multiple times or
        // if the theme already has an ActionBar.
        try {
            setSupportActionBar(toolbar)
        } catch (e: IllegalStateException) {
            android.util.Log.e("MainActivity", "Toolbar error: ${e.message}")
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            navView.setCheckedItem(R.id.nav_home)
        }

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                backCallback.isEnabled = true
                updateNavHeader()
            }

            override fun onDrawerClosed(drawerView: android.view.View) {
                backCallback.isEnabled = false
            }
        })

        // Initial header update
        updateNavHeader()
    }

    fun updateNavHeader() {
        val navView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navView.getHeaderView(0)
        
        if (headerView != null) {
            val userPhoto = headerView.findViewById<ImageView>(R.id.nav_user_photo)
            val userName = headerView.findViewById<TextView>(R.id.nav_user_name)

            val user = authManager.currentUser
            if (user != null) {
                userName.text = user.displayName ?: "Usuario Kuma"
                userPhoto.load(user.photoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.kuma_logo)
                    error(R.drawable.kuma_logo)
                    transformations(CircleCropTransformation())
                }
            } else {
                userName.text = getString(R.string.nav_header_title)
                userPhoto.setImageResource(R.drawable.kuma_logo)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        var fragment: Fragment? = null
        
        when (item.itemId) {
            R.id.nav_home -> fragment = HomeFragment()
            R.id.nav_athletes -> fragment = AthletesFragment()
            R.id.nav_attendance -> fragment = AttendanceFragment()
            R.id.nav_settings -> fragment = SettingsFragment()
            R.id.nav_logout -> {
                authManager.signOut()
                updateNavHeader()
                // Force reload home fragment to update login button
                fragment = HomeFragment()
            }
        }

        if (fragment != null) {
            loadFragment(fragment)
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }


}
