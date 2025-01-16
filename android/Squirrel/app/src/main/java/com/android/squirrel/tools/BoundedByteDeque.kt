package com.android.squirrel.tools

/**
 * 有限数组双端队列
 */
import java.util.ArrayDeque

class BoundedByteDeque(private val capacity: Int) {
    private val deque = ArrayDeque<Byte>(capacity)

    // 添加元素到队列尾部
    @Synchronized
    fun add(element: Byte) {
        if (deque.size >= capacity) {
            deque.removeFirst() // 超过容量，移除最旧的元素
        }
        deque.addLast(element) // 添加元素到队列尾部
    }

    // 添加元素到队列头部
    @Synchronized
    fun addFirst(element: Byte) {
        if (deque.size >= capacity) {
            deque.removeLast() // 超过容量，移除最新的元素
        }
        deque.addFirst(element) // 添加元素到队列头部
    }

    // 移除并返回队列头部的元素
    @Synchronized
    fun poll(): Byte? {
        return if (deque.isEmpty()) null else deque.removeFirst() // 移除并返回队列头部的元素
    }

    // 获取队列头部的元素，但不移除
    @Synchronized
    fun peek(): Byte? {
        return deque.peekFirst() // 返回队列头部的元素，但不移除
    }

    // 返回队列中的元素数量
    @Synchronized
    fun size(): Int {
        return deque.size // 返回队列的元素数量
    }

    // 清空队列
    @Synchronized
    fun clear() {
        deque.clear() // 清空队列中的所有元素
    }

    // 批量添加元素到队列尾部
    @Synchronized
    fun addAll(elements: Collection<Byte>) {
        for (element in elements) {
            add(element) // 将每个元素添加到队列尾部
        }
    }

    // 清空队列
    @Synchronized
    fun removeAll() {
        clear() // 清空队列
    }

    // 返回队列的所有数据
    @Synchronized
    fun getAll(): ByteArray {
        return deque.toByteArray() // 返回队列中的所有元素
    }
}
