package com.android.squirrel.mouse
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import com.android.squirrel.R
import com.android.squirrel.tools.GlobalVariable


class OnMouse : Fragment(){

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.mouse, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragmentContainer = view.findViewById<FrameLayout>(R.id.mouse_fragment_container)
        val buttonOne = view.findViewById<Button>(R.id.mouse_button_touch)
        val buttonTwo = view.findViewById<Button>(R.id.mouse_button_sensor)


        buttonOne.setOnClickListener {
            showFragment(MouseTouch())
            buttonOne.setBackgroundResource(R.drawable.keyboard_win_buttons_down)
            buttonTwo.setBackgroundResource(R.drawable.keyboard_win_buttons_up)
        }
        buttonTwo.setOnClickListener {
            showFragment(MouseSensor())
            buttonOne.setBackgroundResource(R.drawable.keyboard_win_buttons_up)
            buttonTwo.setBackgroundResource(R.drawable.keyboard_win_buttons_down)

        }

        GlobalVariable.mouseButtonReset.observe(viewLifecycleOwner) { data ->
            if (data){
                buttonOne.setBackgroundResource(R.drawable.keyboard_win_buttons_up)
                buttonTwo.setBackgroundResource(R.drawable.keyboard_win_buttons_up)
                buttonOne.isEnabled = true
                buttonOne.isClickable = true
                buttonTwo.isEnabled = true
                buttonTwo.isClickable = true
            }
        }

        // 模拟点击按钮
        buttonOne.performClick()

    }

    //    替换掉容器视图中的现有 Fragment
    private fun showFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.mouse_fragment_container, fragment)
            .addToBackStack(null) // 可选：将事务添加到返回栈
            .commit()
    }


}