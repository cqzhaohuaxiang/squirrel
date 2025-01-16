package com.android.squirrel
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.squirrel.about.OnAbout
import com.android.squirrel.ipc.SurveillanceCamera
import com.android.squirrel.keyboard.KeyboardWindow
import com.android.squirrel.mouse.OnMouse
import com.android.squirrel.set.OnSet

class PagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int {
        return 5 // Number of tabs
    }

    override fun createFragment(position: Int): Fragment {
        // 选中显示
        return when (position) {
            0 -> KeyboardWindow()
            1 -> OnMouse()
            2 -> SurveillanceCamera()
            3 -> OnSet()
            else -> OnAbout()
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemId in 0..<itemCount
    }

}
