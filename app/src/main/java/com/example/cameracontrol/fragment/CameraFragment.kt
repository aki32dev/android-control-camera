package com.example.cameracontrol.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cameracontrol.R
import com.example.cameracontrol.databinding.FragmentCameraBinding

class CameraFragment : Fragment() {
    private var _binding        : FragmentCameraBinding?         = null
    private val binding get()                                   = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvFrag2.text = getString(R.string.frag2)
    }
}