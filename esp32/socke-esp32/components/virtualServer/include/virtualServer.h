#pragma once

#include "esp_err.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "freertos/semphr.h" 

#ifdef __cplusplus
extern "C" {
#endif


/**开启AP的名称与密码 **/
#define WIFI_SSID      "squirrel"    //wifi名称
#define WIFI_PASS      "squirrel"    //wifi密码 当为空是为开放
#define MAX_NUM        8             //wifi 允许连接的最大站数 
#define PORT  36870        /**服务端口号**/
#define MAX_RX_DATA_SIZE 1024              //缓冲区的大小
#define BUF_SIZE 512           // 缓冲区大小
#define QUEUE_SIZE 10          // 队列大小

extern QueueHandle_t dataQueue;   // 外部声明 dataQueue，其他文件可以引用 作一个全局变量来用
bool create_data_queue();// 队列创建函数声明

void udp_receive_task(void *pvParameters);
void udp_send_task(void *pvParameters);
void wifiInit(); 
void usbHidInit();
// void sendHidMsg(uint8_t *data,int num);

#ifdef __cplusplus
}
#endif