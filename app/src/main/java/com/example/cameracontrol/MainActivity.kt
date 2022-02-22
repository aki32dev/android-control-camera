package com.example.cameracontrol

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import com.example.cameracontrol.databinding.ActivityMainBinding
import com.example.cameracontrol.fragment.BluetoothFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.menu_bluetooth -> {
                //ON
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadFragment (fragment : Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.flMain, fragment)
        transaction.disallowAddToBackStack()
        transaction.commit()
    }
}