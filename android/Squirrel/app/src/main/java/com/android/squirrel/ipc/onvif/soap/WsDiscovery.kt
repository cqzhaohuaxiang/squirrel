package com.android.squirrel.ipc.onvif.soap

/**


import org.simpleframework.xml.Element
import org.simpleframework.xml.Namespace
import org.simpleframework.xml.NamespaceList
import org.simpleframework.xml.Order
import org.simpleframework.xml.Root
/**onvif 设备发现的数据类*/

@Root(name = "Envelope", strict = true)
@NamespaceList(
    Namespace(reference = "http://www.onvif.org/ver10/network/wsdl", prefix = "dn"),
    Namespace(reference = "http://www.w3.org/2003/05/soap-envelope", prefix = "")
)
@Order(elements = ["Header", "Body"]) // 这里指定元素顺序
data class WsDiscovery(
    @field:Element(name = "Header") val header: Header,
    @field:Element(name = "Body") val body: Body
){

    @Root(name = "Header", strict = true)
    data class Header(
        @field:Element(name = "MessageID", required = true)
        @field:Namespace(reference = "http://schemas.xmlsoap.org/ws/2004/08/addressing", prefix = "wsa")
        val messageID: String,

        @field:Element(name = "To", required = true)
        @field:Namespace(reference = "http://schemas.xmlsoap.org/ws/2004/08/addressing", prefix = "wsa")
        val to: String,

        @field:Element(name = "Action", required = true)
        @field:Namespace(reference = "http://schemas.xmlsoap.org/ws/2004/08/addressing", prefix = "wsa")
        val action: String
    )

    data class Body(
        @field:Element(name = "Probe")
        val probe: Probe
    ){

        @NamespaceList(
            Namespace(reference = "http://www.w3.org/2001/XMLSchema", prefix = "xsd"),
            Namespace(reference = "http://www.w3.org/2001/XMLSchema-instance", prefix = "xsi") ,
            Namespace(reference = "http://schemas.xmlsoap.org/ws/2005/04/discovery", prefix = "")
        )
        data class Probe(
            @field:Element(name = "Types")
            val types: String,

            @field:Element(name = "Scopes")
            val scopes: String = "" // 这里为空，代表无内容
        )
    }
}

 */


//采用 xml 序列化的方式 生成发现设备的数据
/**

private fun getWsDiscovery(): String{
val header = WsDiscovery.Header(
messageID = "uuid:${UUID.randomUUID()}",
to = "urn:schemas-xmlsoap-org:ws:2005:04:discovery",
action = "http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe"
)
//要发现我设备类型   为流媒体视频输出设备
val probe = WsDiscovery.Body.Probe(types = "dn:NetworkVideoTransmitter")

val body = WsDiscovery.Body(probe)
val envelope = WsDiscovery(header, body)

val serializer: Serializer = Persister()
val writer = StringWriter()
serializer.write(envelope, writer)
return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$writer"
}
 */