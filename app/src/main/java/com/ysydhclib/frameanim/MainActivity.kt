package com.ysydhclib.frameanim

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }


    private fun initView() {
        start_stop_btn.setOnClickListener {
            if (frame_view.isPlaying) {
                start_stop_btn.text = "点我开始"
                frame_view.pause()
            } else {
                frame_view.play()
                start_stop_btn.text = "点我暂停"
            }
        }
        touch_enable_btn.setOnClickListener {
            frame_view.setFingerGestureEnable(!frame_view.isFingerGestureEnable())
        }

    }


}
