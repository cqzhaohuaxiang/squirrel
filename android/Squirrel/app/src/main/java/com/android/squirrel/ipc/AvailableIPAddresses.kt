package com.android.squirrel.ipc

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class AvailableIPAddresses {

    /**
     * 获取当前子网内所有可用的 IP 地址，排除回环地址、网络地址和广播地址
     * @param context Android 上下文
     * @return 返回一个包含可用 IP 地址的 List<String>
     */
    fun getAvailableIPAddresses(context: Context): List<String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo

        // 检查 DHCP 信息是否有效
        if (dhcpInfo.ipAddress == 0 || dhcpInfo.netmask == 0) {
            return emptyList() // 如果未连接 Wi-Fi 或 IP 信息无效，返回空列表
        }

        // 获取本机 IP 地址和子网掩码（修正字节序）
        val localIPBytes = intToBytesBigEndian(dhcpInfo.ipAddress)
        val subnetMaskBytes = intToBytesBigEndian(dhcpInfo.netmask)

        // 计算网络地址和广播地址
        val networkAddressBytes = localIPBytes.mapIndexed { index, byte -> byte and subnetMaskBytes[index] }.toByteArray()
        val broadcastAddressBytes = localIPBytes.mapIndexed { index, byte -> byte or subnetMaskBytes[index].inv() }.toByteArray()

        val networkAddress = InetAddress.getByAddress(networkAddressBytes)
        val broadcastAddress = InetAddress.getByAddress(broadcastAddressBytes)


        // 转换为整数
        val startIP = bytesToIntBigEndian(networkAddressBytes)
        val endIP = bytesToIntBigEndian(broadcastAddressBytes)


        // 遍历 IP 范围，排除回环地址、网络地址和广播地址
        val ipRange = mutableListOf<String>()
        if (startIP < endIP) {
            // 遍历所有可用 IP
            for (ip in startIP + 1 until endIP) {
                val ipBytes = intToBytesBigEndian(ip)
                val address = InetAddress.getByAddress(ipBytes)

                // 排除回环地址
                if (!isLoopbackAddress(address)) {
                    ipRange.add(address.hostAddress)
                }
            }
        }

        return ipRange
    }

    /**
     * 判断是否为回环地址
     * @param inetAddress 要检查的 InetAddress
     * @return 如果是回环地址，返回 true；否则返回 false
     */
    private fun isLoopbackAddress(inetAddress: InetAddress): Boolean {
        return try {
            val networkInterface = NetworkInterface.getByInetAddress(inetAddress)
            networkInterface?.isLoopback ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 将 Int 类型的 IP 地址转换为字节数组（Big-Endian）
     * @param value Int 类型的 IP 地址
     * @return 字节数组（Big-Endian）
     */
    private fun intToBytesBigEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24 and 0xFF).toByte(),         // 第一个字节
            (value shr 16 and 0xFF).toByte(),         // 第二个字节
            (value shr 8 and 0xFF).toByte(),          // 第三个字节
            (value and 0xFF).toByte()                 // 第四个字节
        )
    }

    /**
     * 将字节数组转换为 Int
     * @param bytes 字节数组
     * @return Int 类型的 IP 地址
     */
    private fun bytesToIntBigEndian(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF) or
                (bytes[1].toInt() and 0xFF shl 8) or
                (bytes[2].toInt() and 0xFF shl 16) or
                (bytes[3].toInt() and 0xFF shl 24)
    }




}
