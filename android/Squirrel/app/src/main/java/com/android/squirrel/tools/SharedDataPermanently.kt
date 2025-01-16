package com.android.squirrel.tools

import android.content.Context

/**
 *
 * 作用：在整个APP 中  永久键值数据 保存与提取
 *
 *用法：
 *  1 。实例化参数
 *  SharedDataPermanently (string: String,context: Context)
 *      string  传入要存取的表名（不存在的名称就创建）
 *      context 上下文（Activity 中为 this Fragment中为 requireActivity()）
 *  例子 ： val parameter = SharedDataPermanently("parameter",requireContext())
 *
 *  2 。保存数据
 *  setParameter(name, type)
 *  name 保存在实例化表名下的 键值的名称
 *  type 键值的内容
 *  例子 ：parameter.setParameter("height", 500)
 *  3 。取数据
 *  getParameter(name, type)
 *  name 键值的名称
 *  type 键值的内容
 *  注意：取的时候如果没有获取成功就返回 type 传入的内容
 *
 *  例子 ：val size =  parameter.getParameter("height",0) as Int
 *
 * */
class SharedDataPermanently (string: String,context: Context) {
    private var parameter = context.getSharedPreferences(string,Context.MODE_PRIVATE)
    private var editor = parameter.edit()


    fun getParameter(name: String, type: Any): Comparable<*>? {
        return when (type) {
            is Boolean -> parameter.getBoolean(name, type)      //布尔值
            is Int -> parameter.getInt(name, type)              //整数
            is Long -> parameter.getLong(name, type)        //长整型
            is Float -> parameter.getFloat(name, type)        //单精度浮点数
            is String -> parameter.getString(name,type)
            else -> throw IllegalArgumentException("Unsupported type: ${type::class.simpleName}")
        }
    }

    fun setParameter(name : String ,type : Any){

        when (type) {
            is Boolean -> editor.putBoolean(name, type)
            is Int -> editor.putInt(name, type)
            is Long -> editor.putLong(name, type)       //长整型
            is Float -> editor.putFloat(name, type)      //单精度浮点数
            is String -> editor.putString(name, type)
            else -> throw IllegalArgumentException("Unsupported type: ${type::class.simpleName}")
        }

        editor.apply() //异步提交数据

    }
}