package com.squirrel;


import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

	/**
 	 * 打包为jar的命令  mvn package
     * */
	

public class Squirrel {
	private static int queueSize = 10; 
	private static LinkedBlockingQueue<DatagramPacket> receiveQueue = new LinkedBlockingQueue<>(queueSize); // 报告符队列
	private static JLabel clientJLabel = null;
	private static JLabel msgJLabel = null;
	private static DatagramSocket socket = null;	
	private static Robot robot = null;
	private static SendReport sendReport = null;
	
	// 存储客户端信息  0 最后活动时间	1客户端最后发送数据的序号
	private static Map<InetAddress, Long[]> clientLastActivity = new ConcurrentHashMap<>(); 	
	private static AtomicBoolean isRunning = new AtomicBoolean(false);
	private static String hostAddress = null;
	public static void main(String[] args) {
		
        JFrame frame = new JFrame("Squirrel"); // 创建一个主窗口

		// 设置窗口的默认操作
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);// 设置窗口的大小 
		// frame.setResizable(false);//禁用最大化
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));//垂直排列
		
        // 创建一个标签并添加到窗口
		msgJLabel = new JLabel("无法启用服务,请查看网络状态...", JLabel.CENTER);
		msgJLabel.setForeground(Color.RED);  // 设置字体颜色
		msgJLabel.setAlignmentX(Component.CENTER_ALIGNMENT);  // 水平居中
		Font currentFont = msgJLabel.getFont();  // 获取当前字体
        Font newFont = currentFont.deriveFont(16f);  // 修改字体大小
        msgJLabel.setFont(newFont);// 应用新字体
		frame.add(msgJLabel);

		frame.add(Box.createVerticalGlue());  // 在顶部添加弹性空间，推组件向下
        clientJLabel = new JLabel("", JLabel.CENTER);
		clientJLabel.setForeground(Color.BLUE);  // 设置字体颜色
		clientJLabel.setAlignmentX(Component.CENTER_ALIGNMENT);  // 水平居中
        frame.add(clientJLabel);

        frame.add(Box.createVerticalGlue());  // 在底部添加弹性空间，推组件向上
        frame.setVisible(true);// 显示窗口
		try {
			robot = new Robot();
			sendReport = new SendReport();
		} catch (AWTException e) {
			e.printStackTrace();
		}
		
		// new SendReportSymbol(reportSymbolQueue).start(); // 发送报告符线程
		
		startUdpSocket();
	}
	
	private static void startUdpSocket() {
		
        if (isRunning.get()) {
            return;  // 如果已经启动，则不再重复启动
        }
		
		while (true) {
			hostAddress = getValidLocalIPv4Address(); // 获取本机地址	
			if (hostAddress != null) {
				isRunning.set(true);
				break;

			}

			System.out.println("网络有问题 ,准备重新启动中。");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		

		new Thread(() -> {

			int port = 36870; //监听的端口
			int timeoutMillis = 3000; // 接收数据超时时间
			try {
				// 创建 DatagramSocket 并绑定到指定的本地IP和端口
				socket = new DatagramSocket(port, InetAddress.getByName(hostAddress));
				socket.setSoTimeout(timeoutMillis);//读取超时 目的是主要是判断自身的网络状态
				byte[] buffer = new byte[512]; // 缓冲区用于存放接收到的数据包
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			
				while (isRunning.get()) {	
					try{
						socket.receive(packet);  // 接收数据			
						// 显式复制 byte[] 数据，避免引用共享
						byte[] packetDataCopy = new byte[packet.getLength()];
						System.arraycopy(packet.getData(), packet.getOffset(), packetDataCopy, 0, packet.getLength());
						DatagramPacket packetCopy = new DatagramPacket(packetDataCopy, packetDataCopy.length, packet.getAddress(), packet.getPort());
						// 将复制的数据包放入队列  (在这儿采用阻塞方式)
						receiveQueue.put(packetCopy);
						
					}
					catch (SocketTimeoutException e) {
						if (!isNetworkInterfaceUp(hostAddress)) {
							isRunning.set(false);
							System.out.println("IP 对应的网卡关闭了，重新启动。");
						}	
					} 	
					
				}
			} 
			catch (BindException e) {
				// 如果抛出 BindException，说明端口已经被占用
				msgJLabel.setForeground(Color.RED);  
				msgJLabel.setText("端口：" + port +" 被占用.3秒后退出");
				try {
					Thread.sleep(3000); // 休眠3秒
				} catch (InterruptedException ie) {
					ie.printStackTrace(); // 处理线程中断异常
				}
				System.exit(0); // 退出程序		 
			} catch (Exception e) {
				msgJLabel.setText("错误: " + e.getMessage());
			}finally {
				// 确保资源被释放
				if (socket != null && !socket.isClosed()) {
					socket.close(); // 如果 socket 不为空且未关闭，则关闭
				}
				System.out.println("重启");
				startUdpSocket();
			}
		}).start();

		new Thread(() -> {
			String msg = "I am the HID-USB service!";
			byte [] queryMsg = msg.getBytes();
		
			try {
				while (isRunning.get()) {	
					DatagramPacket packet = receiveQueue.poll(500, TimeUnit.MILLISECONDS);
					if (packet != null) {
						int length = packet.getLength();
						byte[] receivedData = packet.getData(); 
						InetAddress clientAddress = packet.getAddress();  // 客户端的地址
						int clientPort = packet.getPort();  // 客户端的端口
						ByteBuffer byteBuffer = ByteBuffer.wrap(receivedData, 0,length);
						checkClientTimeout(1000); //显示没有超时的客户端				
						if (length == 3) {
							String data = new String(receivedData, 0, length); 
							if ("Hi!".equals(data)) {	
								Long[] values = new Long[2];
								values[0] = System.currentTimeMillis();
								values[1] = 0L;
								clientLastActivity.put(clientAddress, values);	
								DatagramPacket replyPacket = new DatagramPacket(queryMsg, queryMsg.length, clientAddress, clientPort);
								if (socket != null && !socket.isClosed()) {
									socket.send(replyPacket);
								} 
							}	
						}
						else if(calculateChecksum(byteBuffer) && length >= 10){
							
							if (!clientLastActivity.containsKey(clientAddress)) {
								Long[] values = new Long[2];
								values[0] = System.currentTimeMillis();
								values[1] = 0L;
								clientLastActivity.put(clientAddress, values);	
							}
							Long[] values = clientLastActivity.get(clientAddress);
							values[0] = System.currentTimeMillis();
							byte senderFlag = byteBuffer.get();//发送者标志
							long sequenceNumber = byteBuffer.getLong();//序号（8字节）
							if (length - 10 > 0) {
								byte[] content = new byte[length - 10];
								byteBuffer.get(content);
								if (senderFlag == (byte)100 ) {			
									if (values[1]< sequenceNumber ) {
										// processReceivedData(content);
										for (int i = 0; i+8 <= content.length; i += 8) {
											byte[] chunk = new byte[8]; 
											System.arraycopy(content, i, chunk, 0, 8);
											if (chunk[6] == (byte)0xff && chunk[7] == (byte)0xff) {
												byte[] mouse = new byte[5]; 
												System.arraycopy(chunk, 0, mouse, 0, 5);
												sendReport.mouse(robot,mouse);
											}else{
												sendReport.keyboard(robot,chunk);
											}						
											Thread.sleep(5);
										}
										values[1] = sequenceNumber;			
									}
									
								}else if(senderFlag == (byte)50) {
									// processReceivedData(content);
									System.out.println("接收数据");
									for (int i = 0; i+8 <= content.length; i += 8) {
										byte[] chunk = new byte[8]; 
										System.arraycopy(content, i, chunk, 0, 8);
										if (chunk[6] == (byte)0xff && chunk[7] == (byte)0xff) {
											byte[] mouse = new byte[5];
											System.arraycopy(chunk, 0, mouse, 0, 5);
											sendReport.mouse(robot,mouse);
											
										}else{
											sendReport.keyboard(robot,chunk);
										}						
										Thread.sleep(5);
									}	
								}	
							}
												
							byte[] sendData = assembleSendData(sequenceNumber,senderFlag);
							DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
							if (socket != null && !socket.isClosed()) {
								socket.send(sendPacket);
							}
							clientLastActivity.put(clientAddress, values);		
						}	
						
					} else {
						clientJLabel.setText("无设备发数据");
						// 超时，没有接收到数据包
						byte[] key = new byte[8]; 
						sendReport.keyboard(robot,key);
						Thread.sleep(5);
						byte[] mosue = new byte[5]; 
						sendReport.mouse(robot,mosue);
						
					}	
				}
			
					
			} catch (InterruptedException e) {
				e.printStackTrace();
			}catch (IOException e) {
				e.printStackTrace();
			}	
			
		}).start();

		
	}
	//发送数据
	private static void processReceivedData(byte[] data){
		for (int i = 0; i+8 <= data.length; i += 8) {
			byte[] chunk = new byte[8]; 
			System.arraycopy(data, i, chunk, 0, 8);
			if (chunk[6] == (byte)0xff && chunk[7] == (byte)0xff) {
				byte[] mouse = new byte[5]; 	
				System.arraycopy(chunk, 0, mouse, 0, 5);
				sendReport.mouse(robot,mouse);	
			}else{
				sendReport.keyboard(robot,chunk);
			}						
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}
	
	// 获取本机的有效 IPv4 地址
	private static String getValidLocalIPv4Address() {
		try {
			// 获取所有网络接口
			Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
			while (nifs.hasMoreElements()) {
				NetworkInterface nif = nifs.nextElement();
				if (!nif.isUp()) {
					continue; // 跳过不启用的接口
				}
				// 获取该接口绑定的所有 IP 地址
				Enumeration<InetAddress> addresses = nif.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					// 只关心非回环地址并且是 IPv4 地址，且排除通配地址
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
						msgJLabel.setForeground(Color.BLUE);  // 设置字体颜色
						StringBuilder list = new StringBuilder("<html>");
						list.append("网卡名称:"+nif.getDisplayName());
						list.append("<br>");  
						list.append("获取IP地址:"+ addr.getHostAddress());
						list.append("</html>");
						msgJLabel.setText(list.toString());  // 更新标签内容
						return addr.getHostAddress(); // 返回第一个找到的有效外部地址
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		msgJLabel.setForeground(Color.RED);  // 设置字体颜色
		msgJLabel.setText("未找到合适的 IP 地址");
		return null; // 如果未找到合适的 IP 地址
	}

	// 判断校验和
	private static Boolean calculateChecksum(ByteBuffer buffer) {
		// 获取原始校验和在数据的最后1位
		int position = buffer.limit() - 1;  // 获取最后一个字节的位置
		byte expectedChecksum = buffer.get(position);  // 获取最后的校验和
	
		// 将 buffer 中的校验和位置设置为 0，准备进行计算
		buffer.put(position, (byte) 0);
	
		// 初始化校验和计算
		byte checksum = 0;
	
		// 遍历 ByteBuffer 中的数据进行累加
		for (int i = 0; i < buffer.limit(); i++) {
			checksum += buffer.get(i);  // 累加每个字节
		}
	
		// 将计算出的校验和放回到 buffer 中的校验和位置
		buffer.put(position, checksum);
	
		// 检查计算的校验和是否与存储的校验和匹配
		return checksum == expectedChecksum;
	}
	
	 // 组装返回数据
	private static byte[] assembleSendData(long index,byte type) {
		
		ByteBuffer buffer = ByteBuffer.allocate(11);   
		buffer.putLong(index);    
		buffer.put(type);        
		buffer.put((byte) receiveQueue.remainingCapacity());           
		// 5. 计算校验和（包括所有数据，不包括校验和）
		int checksum = 0;
		for (byte b : buffer.array()) {
			checksum += b;  
		}					
		buffer.put((byte)checksum);// 8. 写入校验和（1字节）
		buffer.flip(); // 将缓冲区从写模式切换为读模式
	
		// 8. 返回最终的数据包字节数组
		return buffer.array();
	}
	
	// 检查指定IP地址对应的网卡是否处于活动状态
	private static boolean isNetworkInterfaceUp(String ipAddress) throws IOException {
		if (ipAddress == null) {
			return false;
		}
		InetAddress inetAddress = InetAddress.getByName(ipAddress);
		// 获取本机所有的网络接口
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		while (interfaces.hasMoreElements()) {
			NetworkInterface networkInterface = interfaces.nextElement();
			// 获取与InetAddress关联的网络接口
			if (networkInterface.getInetAddresses().hasMoreElements()) {
				Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
				while (inetAddresses.hasMoreElements()) {
					InetAddress address = inetAddresses.nextElement();
					// 如果网卡的IP地址与目标IP匹配
					if (address.equals(inetAddress)) {
						// 检查该网络接口是否处于活动状态
						return networkInterface.isUp();
					}
				}
			}
		}
		// 没有找到对应IP的网卡
		return false;
	}


	// 客户端超时检查
	private static void checkClientTimeout(int time) {
		long currentTime = System.currentTimeMillis();  // 获取当前时间
		Iterator<Map.Entry<InetAddress, Long[]>> iterator = clientLastActivity.entrySet().iterator();  // 遍历所有客户端
		StringBuilder clientList = new StringBuilder("<html>");
		
		while (iterator.hasNext()) {
			Map.Entry<InetAddress, Long[]> entry = iterator.next();	
			Long[] values = entry.getValue();
			InetAddress clientAddress = entry.getKey();
			long lastActivityTime = values[0];  // 获取客户端最后活动的时间戳
	
			if (currentTime - lastActivityTime < time)  {
				// 如果客户端没有超时，添加到客户端列表中
				clientList.append(clientAddress.getHostAddress()).append("<br>");
				
			}
		}
		clientList.append("</html>");
		clientJLabel.setText(clientList.toString());  
	}
	
	// 将字节数组转换为十六进制字符串
	private static  void bytesToHex(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : bytes) {
			hexString.append(String.format("%02X ", b));
		}
		System.err.println("数据： "+hexString.toString() );
		
	}


}
