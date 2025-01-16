#include "virtualServer.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwip/sockets.h"
#include "string.h"
#include "stdio.h"
#include "freertos/queue.h"
#include "esp_log.h"
#include <arpa/inet.h>  // 用于 ntohl
#include <stdint.h>
#include "tinyusb.h"
#include "class/hid/hid_device.h"

static const char *TAG = "UDP_Async";
// 定义队列
QueueHandle_t dataQueue = NULL;
static const char *target = "Hi!";
static const char *response = "I am the HID-USB service!";
int sock = -1;  // 初始化为无效的套接字

// 定义一个结构体，用来存放接收到的数据包和客户端地址
typedef struct {
    struct sockaddr_in client_addr;
    char data[BUF_SIZE];
    int length;
} udp_packet_t;

// 客户端信息结构体
typedef struct ClientInfo {
    struct sockaddr_in client_ip;  // 客户端的 IP 地址
    uint64_t index;              // 客户端最后发送的序号
    struct ClientInfo *next;   // 指向下一个客户端的信息
} ClientInfo;

ClientInfo* head = NULL;// 链表头指针

//ntohll（网络字节序到主机字节序转换）
uint64_t ntohll(uint64_t val) {
    // 如果主机是小端字节序（Little Endian），则需要交换字节
    uint64_t result = ((uint64_t)ntohl(val & 0xFFFFFFFF) << 32) | ntohl(val >> 32);
    return result;
}

uint32_t ntohl(uint32_t val) {
    // 假设小端系统，将字节顺序反转
    return ((val & 0xFF) << 24) |
           ((val & 0xFF00) << 8) |
           ((val >> 8) & 0xFF00) |
           ((val >> 24) & 0xFF);
}

// 添加或更新客户端信息
static void add_or_update_client(struct sockaddr_in ip, uint64_t sequence_number) {
    ClientInfo* current = head;  
    // 查找是否已经存在该客户端
    while (current != NULL) {
        if (current->client_ip.sin_addr.s_addr == ip.sin_addr.s_addr) {
            // 客户端已存在，更新序号
            current->index = sequence_number;
            return;
        }
        current = current->next;
    }
    
    // 客户端不存在，创建新节点并插入
    ClientInfo* new_client = (ClientInfo*)malloc(sizeof(ClientInfo));
    if (new_client == NULL) {
        printf("内存分配失败\n");
        return;
    }
    new_client->client_ip = ip;
    new_client->index = sequence_number;  // 设置为收到的序号
    new_client->next = head;  // 新节点指向当前头节点
    head = new_client;  // 更新头节点
}
// 查询客户端序号
static uint64_t get_client_sequence_number(struct sockaddr_in client_addr) {
    ClientInfo* current = head;

    // 查找客户端信息
    while (current != NULL) {
        if (current->client_ip.sin_addr.s_addr == client_addr.sin_addr.s_addr) {
            // 找到客户端，返回其序号
            return current->index;
        }
        current = current->next;
    }

    // 如果没有找到该客户端，返回 0
    return 0;
}

// 队列创建函数
bool create_data_queue() {
    // 创建一个队列用于接收数据
    dataQueue = xQueueCreate(QUEUE_SIZE, sizeof(udp_packet_t));
    if (dataQueue == NULL) {
        return false;// 队列创建失败
    }
    return true;
}

static bool check_checksum(char* data, size_t len) {
    uint8_t checksum = 0;
    for (size_t i = 0; i < len - 1; ++i) {
        checksum += data[i];
    }
    return checksum == data[len - 1];
}


// 组装并返回数据
static unsigned char* assembleSendData(uint64_t index, unsigned char type) {
    static unsigned char buffer[11];  // 静态数组，固定长度 11 字节
    unsigned char checksum = 0;

    // 1. 将 long 型 index 转换为字节序列（大端字节序）
    buffer[0] = (index >> 56) & 0xFF;  // 最高字节
    buffer[1] = (index >> 48) & 0xFF;
    buffer[2] = (index >> 40) & 0xFF;
    buffer[3] = (index >> 32) & 0xFF;
    buffer[4] = (index >> 24) & 0xFF;
    buffer[5] = (index >> 16) & 0xFF;
    buffer[6] = (index >> 8) & 0xFF;
    buffer[7] = index & 0xFF;  // 最低字节

    // 2. 填充 type（数据类型）
    buffer[8] = type;  // 数据类型

    // 3. 使用 uxQueueMessagesWaiting 获取队列中等待的消息数量并填充 buffer[9]
    buffer[9] = (unsigned char) uxQueueSpacesAvailable(dataQueue);  // 队列中等待的消息数量

    // 4. 计算校验和（包含前 10 个字节）
    checksum = 0;
    for (int i = 0; i < 10; i++) {
        checksum += buffer[i];
    }
    buffer[10] = checksum;  // 校验和放入最后一个字节
    return buffer;
}

void udp_receive_task(void *pvParameters)
{
    struct sockaddr_in server_addr, client_addr;
    socklen_t addr_len = sizeof(client_addr);
    char buffer[BUF_SIZE];
    udp_packet_t packet;

    // 创建 UDP 套接字
    sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock < 0) {
        ESP_LOGE(TAG, "Failed to create socket");
        vTaskDelete(NULL);
    }

    // 设置本地地址
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(PORT);
    server_addr.sin_addr.s_addr = INADDR_ANY;

    // 绑定套接字
    if (bind(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        ESP_LOGE(TAG, "Socket bind failed");
        close(sock);
        vTaskDelete(NULL);
    }

    while (1) {
        // 接收数据
        int len = recvfrom(sock, buffer, BUF_SIZE, 0, (struct sockaddr *)&client_addr, &addr_len);
        if (len < 0) {
            ESP_LOGE(TAG, "Error receiving data");
        } else {
            // 填充 udp_packet_t 结构体
            memcpy(packet.data, buffer, len);
            packet.length = len;
            packet.client_addr = client_addr;

            // 将接收到的数据放入队列
            if (xQueueSend(dataQueue, &packet, portMAX_DELAY) != pdTRUE) {
                ESP_LOGE(TAG, "队列繁忙...");
            }
        }
    }

    // 关闭套接字
    close(sock);
    vTaskDelete(NULL);
}

// 打印十六进制数据
void printHex(const char *data, int length) {
    char hexString[length * 3 + 1];  // 每个字节 2 个十六进制字符，加一个空格，最后加 '\0' 字符
    int offset = 0;
    
    for (int i = 0; i < length; i++) {
        offset += snprintf(hexString + offset, sizeof(hexString) - offset, "%02X ", (unsigned char)data[i]);
    }
    
    // 打印整行十六进制数据
    ESP_LOGI("收到：", "%s", hexString);
}

static void processReceivedData(uint8_t chunk[]){
    
    while (true){
        if (tud_hid_ready()){
            if (chunk[6] == 0xff && chunk[7] == 0xff) {
                tud_hid_mouse_report(HID_ITF_PROTOCOL_MOUSE, chunk[0], chunk[1], chunk[2], chunk[3], chunk[4]);
            }else{
                uint8_t keycode[6] = {0};
                memcpy(keycode, &chunk[2], 6);  
                tud_hid_keyboard_report(HID_ITF_PROTOCOL_KEYBOARD, chunk[0], keycode);      
            }       
            break;   
        }                                       
        vTaskDelay(10 / portTICK_PERIOD_MS);  
    } 
}

// 发送 UDP 数据包
void udp_send_task(void *pvParameters) {
    udp_packet_t packet;

    while (1) {
        // 从队列中接收数据(阻塞700ms) 
        if (xQueueReceive(dataQueue, &packet, 500 / portTICK_PERIOD_MS) == pdTRUE) {
            
            if (!tud_mounted()) {
                //查询usb接口准备好没
                unsigned char *buffer =  assembleSendData(0, 44);         
                sendto(sock, buffer, 11, 0, (struct sockaddr *)&packet.client_addr, sizeof(packet.client_addr));
            }else {
                if (packet.length == 3) {
                    if (strncmp(packet.data, target, packet.length) == 0) { 
                        add_or_update_client(packet.client_addr, 0);
                        sendto(sock, response, strlen(response), 0,  (struct sockaddr *)&packet.client_addr, sizeof(packet.client_addr));
                    }          
                } 
                else  if (check_checksum(packet.data, packet.length) && packet.length >= 10) {
                
                    unsigned char type = packet.data[0];  // 1字节：数据类型
                    // 解析序号字段（8字节）
                    uint64_t sequence_number = 0;
                    memcpy(&sequence_number, &packet.data[1], sizeof(sequence_number));  // 从 data[1] 读取序号
                    sequence_number = ntohll(sequence_number); //转换字节序
                    int content_length = packet.length - 10;    // 计算 content 数据的长度
                   
                    if (content_length > 0) {
                        
                        unsigned char *content = (unsigned char *)&packet.data[9];   // 获取内容部分
                        if (type == 100) {
                            if(get_client_sequence_number(packet.client_addr) < sequence_number){
                                for (int i = 0; i + 8 <= content_length; i += 8) {
                                    uint8_t *chunk = &content[i];  // 指向当前 8 字节的数据
                                    processReceivedData(chunk);
                                    vTaskDelay(10 / portTICK_PERIOD_MS); 
                                }
                                // 更新客户端信息
                                add_or_update_client(packet.client_addr, sequence_number);
                            }         
                        }
                        else if (type == 50) {
                            for (int i = 0; i + 8 <= content_length; i += 8) {
                                uint8_t *chunk = &content[i];  // 指向当前 8 字节的数据
                                processReceivedData(chunk);
                                vTaskDelay(10 / portTICK_PERIOD_MS); 
                            }         
                        } 
                        
                    }
                    // 组装并发送返回数据
                    unsigned char *buffer =  assembleSendData(sequence_number, type);         
                    sendto(sock, buffer, 11, 0, (struct sockaddr *)&packet.client_addr, sizeof(packet.client_addr));
                                       
                }
            }    
        }else{
            //防止意外长按。提交一个键鼠空操作
            if (tud_mounted() && tud_hid_ready()) {
                tud_hid_keyboard_report(HID_ITF_PROTOCOL_KEYBOARD, 0, NULL);
                vTaskDelay(10/ portTICK_PERIOD_MS);  
                tud_hid_mouse_report(HID_ITF_PROTOCOL_MOUSE, 0, 0,0, 0, 0);
            }
        }
    }
}
